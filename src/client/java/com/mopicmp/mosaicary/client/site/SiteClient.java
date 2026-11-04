package com.mopicmp.mosaicary.client.site;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mopicmp.mosaicary.client.build.Builds;
import com.mopicmp.mosaicary.client.render.Pictures;

/**
 * Talks to mosaicary.com: the public list, one model's card, its file and its picture.
 *
 * <h2>Never on the render thread</h2>
 *
 * Every call returns a future and does its work on the HTTP client's own threads
 * and a small pool of daemon threads for disk and parsing. Callers in the game hop
 * back to the client thread themselves before touching anything of the game's.
 *
 * <h2>What is kept on disk</h2>
 *
 * Under {@code <game dir>/mosaicary/}: {@code cache/list.json} is the last list
 * the site gave, so the catalogue still opens without a connection;
 * {@code cache/previews/} and {@code cache/files/} hold pictures and models keyed
 * by the model's last-changed time, so a changed model is fetched again and an
 * unchanged one never is. {@code models/} holds what the player chose to download,
 * named for people, not for the cache.
 *
 * Reads, and a plain request for the wear relay ({@link #call}), whose rules live in
 * {@code wear/WearRelay}.
 */
public final class SiteClient implements AutoCloseable {

	/** What went wrong, as a code the interface has words for. */
	public enum Failure {
		/** No answer at all: no connection, or the site did not reply in time. */
		OFFLINE,
		/** The site answered that there is no such model, or it is not public. */
		NOT_FOUND,
		/** The site answered with an error of its own. */
		SITE_ERROR,
		/** The answer arrived but could not be read. */
		BAD_ANSWER,
		/** The disk refused to keep it. */
		DISK
	}

	public static final class SiteException extends RuntimeException {
		private final Failure failure;
		private final int status;

		public SiteException(Failure failure, int status, String said, Throwable cause) {
			super(said, cause);
			this.failure = failure;
			this.status = status;
		}

		public Failure failure() {
			return failure;
		}

		public int status() {
			return status;
		}
	}

	/** The list, and whether it came from the disk because the site did not answer. */
	public record Listing(List<ModelCard> cards, boolean fromCache, Failure whyCached) { }

	private volatile String base;
	private final Path root;
	private final String userAgent;
	private final HttpClient http;
	private final ExecutorService work;

	public SiteClient(String base, Path root, String userAgent) {
		this.base = base;
		this.root = root;
		this.userAgent = userAgent;
		AtomicInteger count = new AtomicInteger();
		this.work = Executors.newFixedThreadPool(2, runnable -> {
			Thread thread = new Thread(runnable, "Mosaicary site " + count.incrementAndGet());
			thread.setDaemon(true);
			return thread;
		});
		this.http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.executor(com.mopicmp.mosaicary.platform.Platform.requests(work, "Mosaicary site"))
			.build();
	}

	public String base() {
		return base;
	}

	/** Points the client at another address of the site, from the next request on. */
	public void base(String base) {
		this.base = base;
	}

	public Path root() {
		return root;
	}

	/** The model's page on the site, for "Open on site". */
	public String pageOf(String id) {
		return base + "/models/" + id;
	}

	// ------------------------------------------------------------------ the list

	/**
	 * The public list, newest first, as the site orders it.
	 *
	 * Falls back to the copy kept from the last time the site answered; the
	 * listing says so, and why, so the screen can say it is showing an older list.
	 */
	public CompletableFuture<Listing> list() {
		Path kept = root.resolve("cache/list.json");
		return getBytes("/api/models", Duration.ofSeconds(20)).thenApplyAsync(body -> {
			List<ModelCard> cards = cardsOf(new String(body, StandardCharsets.UTF_8));
			writeQuietly(kept, body);
			return new Listing(cards, false, null);
		}, work).exceptionallyAsync(failure -> {
			SiteException why = unwrap(failure);
			if (Files.exists(kept)) {
				try {
					List<ModelCard> cards = cardsOf(Files.readString(kept, StandardCharsets.UTF_8));
					return new Listing(cards, true, why.failure());
				} catch (IOException | SiteException unreadable) {
					// Falls through to the original failure.
				}
			}
			throw why;
		}, work);
	}

	/** Reads the list endpoint's body. Public for tests. */
	public static List<ModelCard> cardsOf(String body) {
		JsonElement parsed;
		try {
			parsed = JsonParser.parseString(body);
		} catch (JsonParseException broken) {
			throw new SiteException(Failure.BAD_ANSWER, 0, "the list is not JSON", broken);
		}
		if (!(parsed instanceof JsonArray array)) {
			throw new SiteException(Failure.BAD_ANSWER, 0, "the list is not an array", null);
		}
		List<ModelCard> cards = new ArrayList<>();
		for (JsonElement one : array) {
			if (!(one instanceof JsonObject json)) continue;
			ModelCard card = ModelCard.from(json);
			// Without an id nothing about the model can be asked for; the row is skipped.
			if (card.id().matches("[0-9A-Za-z]{12}")) cards.add(card);
		}
		return List.copyOf(cards);
	}

	/** One model's card, with its description. */
	public CompletableFuture<ModelCard> detail(String id) {
		return getBytes("/api/models/" + id, Duration.ofSeconds(20)).thenApplyAsync(body -> {
			try {
				return ModelCard.from(JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject());
			} catch (RuntimeException broken) {
				throw new SiteException(Failure.BAD_ANSWER, 0, "the card is not readable", broken);
			}
		}, work);
	}

	// ------------------------------------------------------------------ files

	/** The model's preview picture (WebP as the site serves it), from the cache when possible. */
	public CompletableFuture<byte[]> preview(ModelCard card) {
		if (card.preview().isEmpty()) {
			return CompletableFuture.failedFuture(new SiteException(Failure.NOT_FOUND, 404, "no preview", null));
		}
		Path file = root.resolve("cache/previews/" + card.id() + "-" + card.versionKey() + ".webp");
		return cached(file, card.id(), card.preview(), Duration.ofSeconds(30));
	}

	/**
	 * The address that fetches a model's file to look at it. The site counts every
	 * fetch of a file as a download unless it is marked {@code for=view}, so every
	 * fetch made to show a model (the 3D preview, a model another player wears, tests,
	 * screenshots) goes through this one.
	 */
	public static String viewPath(String id) {
		return "/api/models/" + id + "/file?for=view";
	}

	/** The address that fetches a model's file as a download, which the site counts. */
	public static String countedPath(String id) {
		return "/api/models/" + id + "/file";
	}

	private Path cacheFile(ModelCard card) {
		return root.resolve("cache/files/" + card.id() + "-" + card.versionKey() + ".bbmodel");
	}

	/** The model's {@code .bbmodel} to look at, from the cache when possible. Never counted. */
	public CompletableFuture<byte[]> file(ModelCard card) {
		return cached(cacheFile(card), card.id(), viewPath(card.id()), Duration.ofSeconds(120));
	}

	/**
	 * The model's {@code .bbmodel}, fetched so that the site counts it as a download:
	 * for an explicit Download press, and for the first time a player wears a model.
	 * Always asks the site, even when the file is cached, since the asking is what
	 * counts; the answer refreshes the cache.
	 */
	public CompletableFuture<byte[]> counted(ModelCard card) {
		Path file = cacheFile(card);
		return getBytes(countedPath(card.id()), Duration.ofSeconds(120)).thenApplyAsync(body -> {
			forgetOlder(file.getParent(), card.id() + "-", file);
			writeQuietly(file, body);
			return body;
		}, work);
	}

	/**
	 * Copies the model into {@code models/} under a readable name and returns where.
	 * This is the explicit download, and the only fetch besides a first wear that the
	 * site counts.
	 */
	public CompletableFuture<Path> download(ModelCard card) {
		if (card.isBuild()) return install(card);
		return counted(card).thenApplyAsync(bytes -> {
			// A Figura avatar comes as a zip and is saved as one.
			boolean zip = bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K' && bytes[2] == 3 && bytes[3] == 4;
			Path target = zip ? withExtension(downloadPath(card), ".zip") : downloadPath(card);
			try {
				Files.createDirectories(target.getParent());
				Files.write(target, bytes);
				return target;
			} catch (IOException failed) {
				throw new SiteException(Failure.DISK, 0, "could not write " + target, failed);
			}
		}, work);
	}

	/** Where a download of this model goes, whether or not it is there yet. */
	public Path downloadPath(ModelCard card) {
		String name = card.name().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
		if (name.isEmpty() || name.equals(".") || name.equals("..")) name = "model";
		if (name.length() > 60) name = name.substring(0, 60).trim();
		return root.resolve("models").resolve(name + " (" + card.id() + ").bbmodel");
	}

	// ------------------------------------------------------------------ builds

	/** The mods installed in this game that place builds, in the order they are installed into. */
	private volatile List<Builds.Format> placers = List.of();

	public void placers(List<Builds.Format> installed) {
		placers = List.copyOf(installed);
	}

	public List<Builds.Format> placers() {
		return placers;
	}

	/**
	 * Installs a build for every mod here that places one: the file as it was put up for
	 * its own mod, turned over (client/build/Builds) for the others. One fetch, counted as
	 * one download. Returns where the first of them went.
	 */
	private CompletableFuture<Path> install(ModelCard card) {
		List<Builds.Format> targets = placers;
		if (targets.isEmpty()) {
			return CompletableFuture.failedFuture(new SiteException(Failure.DISK, 0, "no mod here places builds", null));
		}
		return counted(card).thenComposeAsync(bytes -> {
			Builds.Format source = Builds.formatOf(bytes);
			if (source == null) throw new SiteException(Failure.BAD_ANSWER, 0, "the file is not a build", null);
			// Axiom shows a blueprint by its picture; one written here gets the card's.
			boolean picture = source != Builds.Format.AXIOM && targets.contains(Builds.Format.AXIOM);
			CompletableFuture<byte[]> png = picture
				? preview(card).thenApply(webp -> Pictures.pngScaled(webp, 128)).exceptionally(missing -> null)
				: CompletableFuture.completedFuture(null);
			return png.thenApplyAsync(thumbnail -> writeBuild(card, bytes, source, targets, thumbnail), work);
		}, work);
	}

	private Path writeBuild(ModelCard card, byte[] bytes, Builds.Format source, List<Builds.Format> targets, byte[] thumbnail) {
		Builds.Structure structure = null;
		Path first = null;
		for (Builds.Format target : targets) {
			byte[] out = bytes;
			if (target != source) {
				try {
					if (structure == null) structure = named(Builds.read(bytes), card.name());
					out = Builds.write(structure, target, thumbnail, System.currentTimeMillis());
				} catch (IOException unreadable) {
					throw new SiteException(Failure.BAD_ANSWER, 0, "could not turn the build into " + target.mod + ": " + unreadable.getMessage(), unreadable);
				}
			}
			Path path = buildPath(card, target);
			try {
				Files.createDirectories(path.getParent());
				Files.write(path, out);
			} catch (IOException failed) {
				throw new SiteException(Failure.DISK, 0, "could not write " + path, failed);
			}
			if (first == null) first = path;
		}
		return first;
	}

	private static Builds.Structure named(Builds.Structure structure, String name) {
		if (!structure.name().isEmpty()) return structure;
		return new Builds.Structure(name, structure.author(), structure.tags(), structure.dataVersion(), structure.width(),
			structure.height(), structure.length(), structure.palette(), structure.blocks(), structure.blockEntities(), structure.entities());
	}

	/**
	 * Where a build goes for one mod: that mod's own folder, in a folder of Mosaicary's own
	 * so that what came from the site is kept apart from the player's. Axiom and Litematica
	 * pick a file in a window, so the name is the readable one; WorldEdit loads one by a
	 * command, {@code //schem load mosaicary/<name>}, so its name has no spaces to quote.
	 */
	public Path buildPath(ModelCard card, Builds.Format format) {
		Path game = root.toAbsolutePath().getParent();
		String model = downloadPath(card).getFileName().toString();
		String readable = model.substring(0, model.length() - ".bbmodel".length());
		return switch (format) {
			case AXIOM -> game.resolve("config/axiom/blueprints/Mosaicary").resolve(readable + ".bp");
			case LITEMATICA -> game.resolve("schematics/Mosaicary").resolve(readable + ".litematic");
			case WORLDEDIT -> game.resolve("config/worldedit/schematics/mosaicary").resolve(worldEditName(card) + ".schem");
		};
	}

	/** The name WorldEdit loads a build by: lower case letters, digits and underscores, then the id. */
	public static String worldEditName(ModelCard card) {
		String plain = java.text.Normalizer.normalize(card.name(), java.text.Normalizer.Form.NFKD)
			.replaceAll("\\p{M}+", "")
			.toLowerCase(Locale.ROOT)
			.replaceAll("[^a-z0-9]+", "_")
			.replaceAll("^_+|_+$", "");
		if (plain.length() > 40) plain = plain.substring(0, 40).replaceAll("_+$", "");
		return (plain.isEmpty() ? "build" : plain) + "-" + card.id();
	}

	/** What WorldEdit is told to load a build installed for it. */
	public static String worldEditCommand(ModelCard card) {
		return "//schem load mosaicary/" + worldEditName(card);
	}

	public boolean isDownloaded(ModelCard card) {
		return downloadedPath(card) != null;
	}

	/**
	 * Where an earlier download of this model is, as a model file or an avatar zip, or
	 * null. A build counts as installed when every mod here that places one has it.
	 */
	public Path downloadedPath(ModelCard card) {
		if (card.isBuild()) {
			List<Builds.Format> targets = placers;
			if (targets.isEmpty()) return null;
			for (Builds.Format target : targets) if (!Files.exists(buildPath(card, target))) return null;
			return buildPath(card, targets.get(0));
		}
		Path model = downloadPath(card);
		if (Files.exists(model)) return model;
		Path zip = withExtension(model, ".zip");
		return Files.exists(zip) ? zip : null;
	}

	private static Path withExtension(Path file, String extension) {
		String name = file.getFileName().toString();
		return file.resolveSibling(name.substring(0, name.length() - ".bbmodel".length()) + extension);
	}

	private CompletableFuture<byte[]> cached(Path file, String id, String path, Duration timeout) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return Files.exists(file) ? Files.readAllBytes(file) : null;
			} catch (IOException unreadable) {
				return null;
			}
		}, work).thenCompose(kept -> {
			if (kept != null) return CompletableFuture.completedFuture(kept);
			return getBytes(path, timeout).thenApplyAsync(body -> {
				forgetOlder(file.getParent(), id + "-", file);
				writeQuietly(file, body);
				return body;
			}, work);
		});
	}

	/** Drops cached versions of the same model older than the one just fetched. */
	private static void forgetOlder(Path folder, String prefix, Path keep) {
		if (!Files.isDirectory(folder)) return;
		try (DirectoryStream<Path> found = Files.newDirectoryStream(folder, prefix + "*")) {
			for (Path one : found) if (!one.equals(keep)) Files.deleteIfExists(one);
		} catch (IOException ignored) {
			// A stale copy left behind costs disk space and nothing else.
		}
	}

	private static void writeQuietly(Path file, byte[] body) {
		try {
			Files.createDirectories(file.getParent());
			Path temporary = file.resolveSibling(file.getFileName() + ".part");
			Files.write(temporary, body);
			Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException ignored) {
			// The cache is an optimisation; the answer is still returned.
		}
	}

	// ------------------------------------------------------------------ HTTP

	/** A status and a body, whatever the status: for callers that read refusals themselves. */
	public record Answer(int status, String body) {
		/**
		 * The body, written out: the preprocessor cannot tell the type of a record's component it is not
		 * given, and a pattern of ClientPatterns would miss a call reading it (docs/mod-versions-plan.md).
		 */
		@Override
		public String body() {
			return body;
		}

		public boolean ok() {
			return status >= 200 && status < 300;
		}
	}

	/**
	 * Any request with a JSON body or none, answered with the status and body as they
	 * came. Only a request that got no answer at all fails the future.
	 */
	public CompletableFuture<Answer> call(String method, String path, String json, String bearer, Duration timeout) {
		URI uri;
		try {
			uri = URI.create(base + path);
		} catch (IllegalArgumentException bad) {
			return CompletableFuture.failedFuture(new SiteException(Failure.BAD_ANSWER, 0, "bad address " + path, bad));
		}
		HttpRequest.Builder request = HttpRequest.newBuilder(uri)
			.timeout(timeout)
			.header("User-Agent", userAgent)
			.header("Accept", "application/json");
		if (bearer != null) request.header("Authorization", "Bearer " + bearer);
		if (json != null) {
			request.header("Content-Type", "application/json");
			request.method(method, HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
		} else {
			request.method(method, HttpRequest.BodyPublishers.noBody());
		}
		return http.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
			.handle((response, failure) -> {
				if (failure != null) throw unwrap(failure);
				return new Answer(response.statusCode(), response.body() == null ? "" : response.body());
			});
	}

	/** A GET of a path on the site or of a whole address, failing on anything but 200. */
	public CompletableFuture<byte[]> fetch(String pathOrAddress, Duration timeout) {
		return getBytes(pathOrAddress, timeout);
	}

	/**
	 * A GET kept on disk: read from {@code file} when it is there, else fetched and
	 * written there, dropping other files in the same folder that start with {@code prefix}
	 * (older versions of the same thing).
	 */
	public CompletableFuture<byte[]> fetchKept(Path file, String prefix, String pathOrAddress, Duration timeout) {
		return cached(file, prefix, pathOrAddress, timeout);
	}

	/** The pool for disk and decoding work, off the client thread. */
	public java.util.concurrent.Executor work() {
		return work;
	}

	private CompletableFuture<byte[]> getBytes(String path, Duration timeout) {
		URI uri;
		try {
			uri = URI.create(path.startsWith("http") ? path : base + path);
		} catch (IllegalArgumentException bad) {
			return CompletableFuture.failedFuture(new SiteException(Failure.BAD_ANSWER, 0, "bad address " + path, bad));
		}
		HttpRequest request = HttpRequest.newBuilder(uri)
			.timeout(timeout)
			.header("User-Agent", userAgent)
			.header("Accept", "*/*")
			.GET()
			.build();
		return http.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).handle((response, failure) -> {
			if (failure != null) throw unwrap(failure);
			int status = response.statusCode();
			if (status == 200) return response.body();
			if (status == 404 || status == 403 || status == 410) {
				throw new SiteException(Failure.NOT_FOUND, status, "the site answered " + status + " for " + path, null);
			}
			throw new SiteException(Failure.SITE_ERROR, status, "the site answered " + status + " for " + path, null);
		});
	}

	public static SiteException unwrap(Throwable failure) {
		Throwable cause = failure;
		while ((cause instanceof CompletionException || cause instanceof java.util.concurrent.ExecutionException)
				&& cause.getCause() != null) {
			cause = cause.getCause();
		}
		if (cause instanceof SiteException known) return known;
		// A timeout is an IOException too (HttpTimeoutException), and is not named apart: Java 8 has no such
		// class, and the jar for it (build.gradle.kts, JvmDowngrader) has no copy of it either.
		if (cause instanceof ConnectException || cause instanceof java.nio.channels.UnresolvedAddressException
				|| cause instanceof java.net.UnknownHostException || cause instanceof IOException) {
			return new SiteException(Failure.OFFLINE, 0, String.valueOf(cause.getMessage()), cause);
		}
		return new SiteException(Failure.BAD_ANSWER, 0, String.valueOf(cause), cause);
	}

	/** A short description of a failure for the log. */
	public static String describe(Throwable failure) {
		SiteException why = unwrap(failure);
		return why.failure().name().toLowerCase(Locale.ROOT) + (why.status() > 0 ? " " + why.status() : "")
			+ ": " + why.getMessage();
	}

	@Override
	public void close() {
		work.shutdownNow();
		// An HttpClient can be shut down itself from Java 21, the game's Java since 1.20.5; before,
		// it stops once its executor has and nothing holds it.
		//#if MC>=12005
		http.shutdownNow();
		//#endif
	}
}

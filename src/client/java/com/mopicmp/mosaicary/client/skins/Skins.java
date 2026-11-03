package com.mopicmp.mosaicary.client.skins;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

import com.mojang.blaze3d.platform.NativeImage;
import com.mopicmp.mosaicary.Mosaicary;
import com.mopicmp.mosaicary.client.render.Pictures;
import com.mopicmp.mosaicary.client.site.SiteClient;
import com.mopicmp.mosaicary.wardrobe.Wardrobe;
import com.mopicmp.mosaicary.client.render.Textures;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;

/**
 * What the skin wardrobe knows, kept between openings of the screen: the catalogue's
 * pages, card pictures, skin textures, the wardrobe of the account playing, and the
 * putting on of a skin.
 *
 * Everything here is read and changed on the client thread. Work happens on the site
 * client's threads and Mojang's upload thread, and comes back through
 * {@code minecraft.execute}.
 *
 * <h2>Looking is not downloading</h2>
 *
 * The site counts a fetch of a skin's file as a download unless it is marked
 * {@code for=view}. Card pictures and the 3D preview are only looked at, so they fetch
 * with the mark; putting a skin on and saving it as a file are downloads, and fetch
 * without it.
 */
public final class Skins {

	public enum State { LOADING, READY, FAILED }

	/** How long a loaded query is reused before it is asked again, as the launcher catalogue does. */
	private static final long SHELF_KEPT_MS = 5 * 60_000;

	/** Card pictures kept on the graphics card at once; the least recently drawn go first. */
	private static final int PICTURES_KEPT = 120;

	/** Skin textures kept for previews; the ones worn by somebody are never dropped. */
	private static final int TEXTURES_KEPT = 24;

	private static final Duration TIMEOUT = Duration.ofSeconds(30);

	private final SiteClient site;
	private final MojangSkins mojang = new MojangSkins();
	private final Path root;

	public Skins(SiteClient site) {
		this.site = site;
		this.root = site.root();
	}

	public SiteClient site() {
		return site;
	}

	// ------------------------------------------------------------------ the catalogue

	/** What has been loaded for one query: its pages so far. */
	public static final class Shelf {
		public final List<SkinCard> cards = new ArrayList<>();
		public int pagesLoaded;
		public int pages = 1;
		public int total;
		public boolean loading;
		public SiteClient.Failure failure;
		long at;
	}

	private final Map<SkinQuery, Shelf> shelves = new HashMap<>();

	/** The shelf of a query, starting its first page if nothing is loaded or what is loaded is old. */
	public Shelf shelf(SkinQuery query) {
		Shelf known = shelves.get(query);
		long now = System.currentTimeMillis();
		if (known != null && (known.loading || now - known.at < SHELF_KEPT_MS || known.failure != null)) return known;
		Shelf fresh = new Shelf();
		fresh.at = now;
		shelves.put(query, fresh);
		load(query, fresh, 1);
		return fresh;
	}

	/** Asks for the next page of a query, when there is one and nothing is on its way. */
	public void more(SkinQuery query) {
		Shelf shelf = shelves.get(query);
		if (shelf == null || shelf.loading || shelf.failure != null || shelf.pagesLoaded >= shelf.pages) return;
		load(query, shelf, shelf.pagesLoaded + 1);
	}

	/** Forgets what was loaded for a query and asks again; a failed page is asked again the same way. */
	public void refresh(SkinQuery query) {
		shelves.remove(query);
		tagsState = null;
		shelf(query);
	}

	/** Forgets every loaded page and the tags: after the site's address changed. */
	public void forgetShelves() {
		shelves.clear();
		tagsState = null;
		tags = List.of();
	}

	/** Tries the page that failed again, keeping the pages already loaded. */
	public void retry(SkinQuery query) {
		Shelf shelf = shelves.get(query);
		if (shelf == null || shelf.loading) return;
		shelf.failure = null;
		load(query, shelf, shelf.pagesLoaded + 1);
	}

	private void load(SkinQuery query, Shelf shelf, int page) {
		shelf.loading = true;
		Minecraft minecraft = Minecraft.getInstance();
		String base = site.base();
		site.fetch(query.path(page), TIMEOUT)
			.thenApplyAsync(body -> SkinQuery.page(new String(body, java.nio.charset.StandardCharsets.UTF_8), base), site.work())
			.whenComplete((answer, problem) -> minecraft.execute(() -> {
				shelf.loading = false;
				// A shelf dropped by a refresh while this page was on its way is not touched.
				if (shelves.get(query) != shelf) return;
				if (problem != null) {
					shelf.failure = failureOf(problem);
					Mosaicary.LOGGER.warn("Could not load skins: {}", SiteClient.describe(problem));
					return;
				}
				shelf.failure = null;
				shelf.pagesLoaded = Math.max(shelf.pagesLoaded, answer.page());
				shelf.pages = Math.max(answer.pages(), answer.page());
				shelf.total = answer.total();
				// A skin published while paging moves the rest down by one; it is not shown twice.
				for (SkinCard card : answer.skins()) {
					boolean known = false;
					for (SkinCard had : shelf.cards) known |= had.id().equals(card.id());
					if (!known) shelf.cards.add(card);
				}
			}));
	}

	private static SiteClient.Failure failureOf(Throwable problem) {
		Throwable cause = problem;
		while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
		if (cause instanceof RuntimeException && !(cause instanceof SiteClient.SiteException)) return SiteClient.Failure.BAD_ANSWER;
		return SiteClient.unwrap(problem).failure();
	}

	// ------------------------------------------------------------------ tags

	private State tagsState;
	private List<SkinQuery.Tag> tags = List.of();

	/** The tags 64-pixel skins carry, the commonest first, loading them the first time. */
	public List<SkinQuery.Tag> tags() {
		if (tagsState == null) {
			tagsState = State.LOADING;
			Minecraft minecraft = Minecraft.getInstance();
			site.fetch("/api/skins/tags?size=64", TIMEOUT)
				.thenApplyAsync(body -> SkinQuery.tags(new String(body, java.nio.charset.StandardCharsets.UTF_8)), site.work())
				.whenComplete((found, problem) -> minecraft.execute(() -> {
					if (problem != null) {
						tagsState = State.FAILED;
						return;
					}
					tags = found;
					tagsState = State.READY;
				}));
		}
		return tags;
	}

	public State tagsState() {
		return tagsState;
	}

	// ------------------------------------------------------------------ card pictures

	/** A picture to draw: its texture once decoded and uploaded. */
	public record Picture(State state, Identifier texture, int width, int height) { }

	private final Map<String, Picture> pictures = new LinkedHashMap<>(64, 0.75f, true);

	/**
	 * The site's picture of a skin worn, by its address, starting to fetch it the first time.
	 * Kept on disk under the skin's id and the picture's version, and on the graphics card
	 * while it is drawn often enough.
	 */
	public Picture picture(String id, String address, String version) {
		String key = id + "-" + version;
		Picture known = pictures.get(key);
		if (known != null) return known;
		Picture loading = new Picture(State.LOADING, null, 0, 0);
		pictures.put(key, loading);
		trimPictures();
		if (address == null || !address.startsWith(site.base() + "/")) {
			Picture none = new Picture(State.FAILED, null, 0, 0);
			pictures.put(key, none);
			return none;
		}
		Minecraft minecraft = Minecraft.getInstance();
		Path file = root.resolve("cache/skins/pictures/" + id + "-" + version + ".png");
		site.fetchKept(file, id + "-", address, TIMEOUT)
			.thenApplyAsync(bytes -> Pictures.decodeScaled(bytes, 256), site.work())
			.orTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
			.whenComplete((image, problem) -> minecraft.execute(() -> {
				if (pictures.get(key) != loading) {
					if (image != null) image.close();
					return;
				}
				if (problem != null || image == null) {
					pictures.put(key, new Picture(State.FAILED, null, 0, 0));
					return;
				}
				Identifier id2 = Mosaicary.id("skins/picture/" + safe(key));
				int w = image.getWidth();
				int h = image.getHeight();
				minecraft.getTextureManager().register(id2, Textures.of(() -> "Mosaicary skin picture", image));
				pictures.put(key, new Picture(State.READY, id2, w, h));
			}));
		return loading;
	}

	public Picture picture(SkinCard card) {
		return picture(card.id(), card.picture(), card.pictureKey());
	}

	/**
	 * The same picture for a skin the site has none of, a file of the player's own or a texture
	 * known only by its hash: drawn here from the skin's file ({@link SkinFigure}), once.
	 */
	public Picture figure(Wardrobe.Entry entry) {
		String key = "figure-" + entry.key;
		Picture known = pictures.get(key);
		if (known != null) return known;
		Picture loading = new Picture(State.LOADING, null, 0, 0);
		pictures.put(key, loading);
		trimPictures();
		boolean slim = entry.slim();
		bytesOf(entry, false).thenApplyAsync(Skins::pixelsOf, site.work())
			.thenApplyAsync(pixels -> pixels == null ? null : SkinFigure.render(pixels, slim), site.work())
			.whenComplete((figure, problem) -> Minecraft.getInstance().execute(() -> {
				if (pictures.get(key) != loading) return;
				pictures.put(key, problem != null || figure == null ? new Picture(State.FAILED, null, 0, 0) : uploadFigure(key, figure));
			}));
		return loading;
	}

	/** A file on this computer drawn the same way, under a name of the caller's, who releases it; null when it is no skin. */
	public CompletableFuture<Picture> figureOfFile(Path file, String key) {
		return pixelsOfFile(file)
			.thenApplyAsync(pixels -> pixels == null ? null : SkinFigure.render(pixels, "slim".equals(SkinPixels.armsOf(pixels, 64, 64))), site.work())
			.thenCompose(figure -> {
				CompletableFuture<Picture> done = new CompletableFuture<>();
				Minecraft.getInstance().execute(() -> done.complete(figure == null ? null : uploadFigure(key, figure)));
				return done;
			});
	}

	private static Picture uploadFigure(String key, int[] figure) {
		NativeImage image = new NativeImage(SkinFigure.SIZE, SkinFigure.SIZE, false);
		for (int y = 0; y < SkinFigure.SIZE; y++) {
			for (int x = 0; x < SkinFigure.SIZE; x++) image.setPixel(x, y, figure[y * SkinFigure.SIZE + x]);
		}
		Identifier id = Mosaicary.id("skins/picture/" + safe(key));
		Minecraft.getInstance().getTextureManager().register(id, Textures.of(() -> "Mosaicary skin figure", image));
		return new Picture(State.READY, id, SkinFigure.SIZE, SkinFigure.SIZE);
	}

	private void trimPictures() {
		if (pictures.size() <= PICTURES_KEPT) return;
		var it = pictures.entrySet().iterator();
		while (pictures.size() > PICTURES_KEPT && it.hasNext()) {
			Picture old = it.next().getValue();
			if (old.state() == State.LOADING) continue;
			if (old.texture() != null) Minecraft.getInstance().getTextureManager().release(old.texture());
			it.remove();
		}
	}

	// ------------------------------------------------------------------ what a skin's file says

	/**
	 * What can only be told from a skin's own file, as a card's picture does not say.
	 *
	 * @param seeThrough whether players without this mod see it otherwise ({@link SkinPixels#seeThrough})
	 * @param eyes       whether it carries a copy of its eyes for Fresh Moves
	 */
	public record Look(boolean seeThrough, boolean eyes) { }

	private static final Look NOTHING_KNOWN = new Look(false, false);

	private final Map<String, Look> looks = new HashMap<>();
	private final java.util.Set<String> lookedFor = new java.util.HashSet<>();

	/**
	 * What a skin's file says, starting to fetch it the first time; null until it has come. The
	 * site does the same for its own cards: fetched for looking at, so neither counted as a
	 * download nor held to the limit on files, and kept on disk.
	 */
	public Look look(Wardrobe.Entry entry) {
		Look known = looks.get(entry.key);
		if (known != null || !lookedFor.add(entry.key)) return known;
		Minecraft minecraft = Minecraft.getInstance();
		String key = entry.key;
		boolean slim = entry.slim();
		bytesOf(entry, false).thenApplyAsync(Skins::pixelsOf, site.work()).whenComplete((pixels, problem) -> minecraft.execute(() ->
			looks.put(key, problem != null || pixels == null ? NOTHING_KNOWN
				: new Look(SkinPixels.seeThrough(pixels, slim), FreshEyes.read(pixels) != null))));
		return null;
	}

	public Look look(SkinCard card) {
		Look known = looks.get("site:" + card.id());
		return known != null ? known : look(card.entry(0));
	}

	// ------------------------------------------------------------------ pictures with the eyes moving

	/** Live pictures kept at once, each a texture and its frames; the least recently drawn go first. */
	private static final int LIVE_KEPT = 48;

	private final Map<String, LivePicture> lives = new LinkedHashMap<>(16, 0.75f, true);
	private final java.util.Set<String> livesAsked = new java.util.HashSet<>();

	/** The card's picture with the eyes moving at this moment, or null while there is none to draw. */
	public Identifier livePicture(SkinCard card) {
		return card.live().isEmpty() ? null : livePicture(card.id(), card.live());
	}

	/** The same for a skin in the wardrobe, which keeps only the skin's id: the address is the site's own. */
	public Identifier livePicture(Wardrobe.Entry entry) {
		return entry.id == null ? null : livePicture(entry.id, site.base() + "/api/skins/" + entry.id + "/live.png");
	}

	private Identifier livePicture(String id, String address) {
		LivePicture known = lives.get(id);
		if (known != null) return known.frame();
		if (!address.startsWith(site.base() + "/") || !livesAsked.add(id)) return null;
		Minecraft minecraft = Minecraft.getInstance();
		int at = address.indexOf("?v=");
		String version = (at < 0 ? "0" : address.substring(at + 3)).replaceAll("[^0-9A-Za-z._-]", "_");
		Path file = root.resolve("cache/skins/live/" + id + "-" + version + ".png");
		site.fetchKept(file, id + "-", address, TIMEOUT)
			.thenApplyAsync(LivePicture::decode, site.work())
			.whenComplete((frames, problem) -> minecraft.execute(() -> {
				if (problem != null || frames == null) return;
				lives.put(id, LivePicture.of(frames));
				if (lives.size() > LIVE_KEPT) {
					var oldest = lives.entrySet().iterator().next();
					oldest.getValue().close();
					livesAsked.remove(oldest.getKey());
					lives.remove(oldest.getKey());
				}
			}));
		return null;
	}

	// ------------------------------------------------------------------ skin textures

	/**
	 * A skin as the game draws it, uploaded as a texture.
	 *
	 * @param pixels the 64 x 64 pixels, for flat drawings of the skin's front
	 */
	public record Texture(State state, Identifier texture, PlayerModelType model, int[] pixels, String problem) {
		public boolean ready() {
			return state == State.READY;
		}
	}

	private final Map<String, Texture> textures = new LinkedHashMap<>(16, 0.75f, true);

	/** The skin of a wardrobe entry or a catalogue card, fetched to look at (never counted). */
	public Texture texture(Wardrobe.Entry entry) {
		String key = entry.key;
		Texture known = textures.get(key);
		if (known != null) return known;
		Texture loading = new Texture(State.LOADING, null, modelOf(entry.arms), null, null);
		textures.put(key, loading);
		Minecraft minecraft = Minecraft.getInstance();
		bytesOf(entry, false)
			.thenApplyAsync(Skins::pixelsOf, site.work())
			.whenComplete((pixels, problem) -> minecraft.execute(() -> {
				if (textures.get(key) != loading) return;
				if (problem != null || pixels == null) {
					textures.put(key, new Texture(State.FAILED, null, loading.model(), null, problem == null ? "size" : "fetch"));
					return;
				}
				textures.put(key, upload(key, pixels, loading.model()));
				trimTextures();
			}));
		return loading;
	}

	public Texture texture(SkinCard card) {
		return texture(card.entry(0));
	}

	private static Texture upload(String key, int[] pixels, PlayerModelType model) {
		NativeImage image = new NativeImage(64, 64, false);
		for (int y = 0; y < 64; y++) {
			for (int x = 0; x < 64; x++) image.setPixel(x, y, pixels[y * 64 + x]);
		}
		Identifier id = Mosaicary.id("skins/texture/" + safe(key));
		Minecraft.getInstance().getTextureManager().register(id, Textures.of(() -> "Mosaicary skin", image));
		return new Texture(State.READY, id, model, pixels, null);
	}

	private void trimTextures() {
		if (textures.size() <= TEXTURES_KEPT) return;
		var it = textures.entrySet().iterator();
		while (textures.size() > TEXTURES_KEPT && it.hasNext()) {
			Map.Entry<String, Texture> old = it.next();
			if (old.getValue().state() == State.LOADING || worn.containsValue(old.getKey())) continue;
			if (old.getValue().texture() != null) Minecraft.getInstance().getTextureManager().release(old.getValue().texture());
			it.remove();
		}
	}

	/** The pixels of a skin on Mojang's servers, by its address; for the figure beside the main menu. */
	public CompletableFuture<int[]> pixelsOfTexture(String url) {
		return bytesOf(Wardrobe.Entry.ofTexture(MojangSkins.textureOf(url), "", "classic", 0), false)
			.thenApplyAsync(Skins::pixelsOf, site.work());
	}

	/** The pixels of a skin file on this computer, read off the client thread; null when it is not one. */
	public CompletableFuture<int[]> pixelsOfFile(Path file) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return Files.size(file) > 1 << 20 ? null : pixelsOf(Files.readAllBytes(file));
			} catch (IOException unreadable) {
				return null;
			}
		}, site.work());
	}

	/**
	 * A skin file without its filled corners ({@link SkinPixels#clearFilledCorners}), for what goes
	 * out to others: players with the Fresh Moves pack and without this mod would see them black.
	 * The same bytes when there are none, or when it cannot be read.
	 */
	static byte[] cleaned(byte[] bytes) {
		try (NativeImage image = NativeImage.read(bytes)) {
			int w = image.getWidth();
			int h = image.getHeight();
			if (w != 64 || (h != 64 && h != 32)) return bytes;
			int[] sheet = new int[64 * 64];
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) sheet[y * 64 + x] = image.getPixel(x, y);
			}
			if (!SkinPixels.clearFilledCorners(sheet)) return bytes;
			for (int[] corner : SkinPixels.CORNERS) {
				for (int y = corner[1]; y < corner[1] + 8; y++) {
					for (int x = corner[0]; x < corner[0] + 8; x++) image.setPixel(x, y, sheet[y * 64 + x]);
				}
			}
			Path temporary = Files.createTempFile("mosaicary-skin", ".png");
			try {
				image.writeToFile(temporary);
				return Files.readAllBytes(temporary);
			} finally {
				Files.deleteIfExists(temporary);
			}
		} catch (IOException unreadable) {
			return bytes;
		}
	}

	/** Reads a skin file into the 64 x 64 pixels the game would draw; null for a size it refuses. */
	static int[] pixelsOf(byte[] bytes) {
		int[] size = SkinPixels.pngSize(bytes);
		if (size == null) return null;
		try (NativeImage image = NativeImage.read(bytes)) {
			int w = image.getWidth();
			int h = image.getHeight();
			if (w != 64 || (h != 64 && h != 32)) return null;
			int[] raw = new int[w * h];
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) raw[y * w + x] = image.getPixel(x, y);
			}
			return SkinPixels.normalise(raw, w, h);
		} catch (IOException unreadable) {
			return null;
		}
	}

	private static PlayerModelType modelOf(String arms) {
		return "slim".equals(arms) ? PlayerModelType.SLIM : PlayerModelType.WIDE;
	}

	/**
	 * The skin file of an entry: from the site (counted as a download or marked as a look),
	 * from textures.minecraft.net by its hash, or from the wardrobe's own folder.
	 */
	CompletableFuture<byte[]> bytesOf(Wardrobe.Entry entry, boolean counted) {
		if (entry.id != null) {
			String path = "/api/skins/" + entry.id + ".png";
			if (counted) return site.fetch(path, TIMEOUT);
			Path file = root.resolve("cache/skins/files/" + entry.id + ".png");
			return site.fetchKept(file, entry.id + ".", path + "?for=view", TIMEOUT);
		}
		if (entry.file != null && wardrobe != null) {
			Path file = wardrobe.fileOf(entry);
			if (file != null && Files.exists(file)) {
				return CompletableFuture.supplyAsync(() -> {
					try {
						return Files.readAllBytes(file);
					} catch (IOException unreadable) {
						throw new CompletionException(unreadable);
					}
				}, site.work());
			}
		}
		if (entry.texture != null) {
			Path file = root.resolve("cache/skins/textures/" + entry.texture + ".png");
			return site.fetchKept(file, entry.texture + ".", MojangSkins.textureUrl(entry.texture), TIMEOUT);
		}
		if (entry.file != null) {
			// Not on this computer: kept with the wardrobe on the site, by its name.
			Path file = root.resolve("cache/skins/wardrobe/" + entry.file + ".png");
			return site.fetchKept(file, entry.file + ".", "/api/wardrobe/files/" + entry.file + ".png", TIMEOUT);
		}
		return CompletableFuture.failedFuture(new IOException("nothing to fetch the skin from"));
	}

	static String safe(String key) {
		return key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
	}

	// ------------------------------------------------------------------ the wardrobe

	private Wardrobe wardrobe;

	/** The wardrobe of the account playing now, made the first time it is asked for. */
	public Wardrobe wardrobe() {
		String owner = Minecraft.getInstance().getUser().getProfileId().toString();
		if (wardrobe == null || !wardrobe.owner().equals(owner)) {
			wardrobe = new Wardrobe(root.resolve("wardrobe"), owner);
			for (Consumer<Wardrobe> one : wardrobeListeners) one.accept(wardrobe);
		}
		return wardrobe;
	}

	private final List<Consumer<Wardrobe>> wardrobeListeners = new ArrayList<>();

	/** Called with every wardrobe made, so a copy that keeps it elsewhere can listen to it. */
	public void onWardrobe(Consumer<Wardrobe> listener) {
		wardrobeListeners.add(listener);
	}

	/**
	 * Adds a file of the player's own: kept in the wardrobe's folder by its SHA-1, with the
	 * arms read off its pixels. Off the client thread; the entry arrives on it, or the reason
	 * the file was refused.
	 */
	public void importFile(Path source, Consumer<Wardrobe.Entry> added, Consumer<String> refused) {
		Wardrobe into = wardrobe();
		Minecraft minecraft = Minecraft.getInstance();
		CompletableFuture.supplyAsync(() -> {
			try {
				byte[] bytes = Files.readAllBytes(source);
				if (bytes.length > 1 << 20) throw new IllegalArgumentException("big");
				int[] size = SkinPixels.pngSize(bytes);
				if (size == null) throw new IllegalArgumentException("png");
				if (size[0] != 64 || (size[1] != 64 && size[1] != 32)) throw new IllegalArgumentException("size");
				int[] pixels = pixelsOf(bytes);
				if (pixels == null) throw new IllegalArgumentException("png");
				bytes = cleaned(bytes);
				String sha1 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
				Path kept = into.files().resolve(sha1 + ".png");
				Files.createDirectories(kept.getParent());
				if (!Files.exists(kept)) Files.write(kept, bytes);
				String name = source.getFileName().toString().replaceFirst("(?i)\\.png$", "");
				if (name.length() > 60) name = name.substring(0, 60);
				return Wardrobe.Entry.ofFile(sha1, name, size[1] == 64 ? SkinPixels.armsOf(pixels, 64, 64) : "classic",
					System.currentTimeMillis());
			} catch (IllegalArgumentException why) {
				throw new CompletionException(why);
			} catch (Exception failed) {
				throw new CompletionException(new IllegalArgumentException("read"));
			}
		}, site.work()).whenComplete((entry, problem) -> minecraft.execute(() -> {
			if (problem != null) {
				Throwable cause = problem instanceof CompletionException && problem.getCause() != null ? problem.getCause() : problem;
				refused.accept(cause instanceof IllegalArgumentException why ? why.getMessage() : "read");
				return;
			}
			into.add(entry);
			added.accept(entry);
		}));
	}

	// ------------------------------------------------------------------ putting a skin on

	/** Where putting a skin on stands, for the button that does it. */
	public record Wear(State state, MojangSkins.Failure failure) { }

	private final Map<String, Wear> wears = new HashMap<>();
	private boolean uploading;

	/** Players and the wardrobe key of the skin shown on them here, the local player included. */
	private final Map<UUID, String> worn = new HashMap<>();

	private final List<Consumer<Applied>> appliedListeners = new ArrayList<>();

	/** A skin just put on the local player's account. */
	public record Applied(Wardrobe.Entry entry, MojangSkins.Applied mojang) { }

	/** Called after every skin put on, for the copies and relays that pass it on. */
	public void onApplied(Consumer<Applied> listener) {
		appliedListeners.add(listener);
	}

	public Wear wearState(String key) {
		return wears.get(key);
	}

	/** Whether a skin is being sent to Mojang right now; one at a time. */
	public boolean uploading() {
		return uploading;
	}

	/** Whether this game can put a skin on its account at all: a Mojang account with a session token. */
	public static boolean canUpload() {
		var user = Minecraft.getInstance().getUser();
		String token = user.getAccessToken();
		return user.getProfileId().version() == 4 && token != null && token.length() >= 20;
	}

	/**
	 * Puts a skin on the account: fetches it (a download for a skin of the site), sends it to
	 * Mojang, shows it on the local player at once, and keeps it in the wardrobe.
	 */
	public void wear(Wardrobe.Entry entry) {
		if (uploading) return;
		if (!canUpload()) {
			if (signedIn.getAsBoolean()) wearThroughSite(entry);
			else wears.put(entry.key, new Wear(State.FAILED, MojangSkins.Failure.SIGN_IN));
			return;
		}
		uploading = true;
		wears.put(entry.key, new Wear(State.LOADING, null));
		Minecraft minecraft = Minecraft.getInstance();
		String token = minecraft.getUser().getAccessToken();
		bytesOf(entry, true).thenCompose(bytes -> {
			int[] pixels = pixelsOf(bytes);
			if (pixels == null) throw new MojangSkins.Refused(MojangSkins.Failure.REFUSED, 0, "not a skin the game takes");
			return mojang.upload(token, cleaned(bytes), entry.arms).thenApply(applied -> new Object[] { applied, pixels });
		}).whenComplete((done, problem) -> minecraft.execute(() -> {
			uploading = false;
			if (problem != null) {
				Throwable cause = problem;
				while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
				MojangSkins.Failure failure = cause instanceof MojangSkins.Refused refused ? refused.failure
					: MojangSkins.Failure.NO_ANSWER;
				Mosaicary.LOGGER.warn("Could not put skin {} on: {} ({})", entry.key, failure, cause.getMessage());
				wears.put(entry.key, new Wear(State.FAILED, failure));
				return;
			}
			MojangSkins.Applied applied = (MojangSkins.Applied) done[0];
			int[] pixels = (int[]) done[1];
			Mosaicary.LOGGER.info("Put skin {} on the account: texture {}", entry.key, applied.texture());
			wears.clear();
			wears.put(entry.key, new Wear(State.READY, null));
			ownChanges++;
			ownTexture = applied.texture();
			Texture texture = upload("worn-" + entry.key, pixels, modelOf(applied.arms()));
			show(minecraft.getUser().getProfileId(), "worn-" + entry.key, texture);
			Wardrobe into = wardrobe();
			into.worn(entry, applied.texture(), applied.arms(), System.currentTimeMillis());
			Applied told = new Applied(into.get(entry.key) != null ? into.get(entry.key) : entry, applied);
			for (Consumer<Applied> listener : appliedListeners) listener.accept(told);
		}));
	}

	private java.util.function.BooleanSupplier signedIn = () -> false;

	/** How to tell whether the game is signed in to an account on the site. */
	public void signedIn(java.util.function.BooleanSupplier signedIn) {
		this.signedIn = signedIn;
	}

	/** Whether "Wear" can do anything here: through Mojang, or through an account on the site. */
	public boolean canWear() {
		return canUpload() || signedIn.getAsBoolean();
	}

	/**
	 * Puts a skin on for a game Mojang will not take one from: nothing changes on any account of
	 * Mojang's, the skin shows here at once and goes to the other players with the mod through
	 * the site ({@code WearRelay}), under the account the game is signed in to.
	 */
	private void wearThroughSite(Wardrobe.Entry entry) {
		uploading = true;
		wears.put(entry.key, new Wear(State.LOADING, null));
		Minecraft minecraft = Minecraft.getInstance();
		bytesOf(entry, true).thenApplyAsync(Skins::pixelsOf, site.work()).whenComplete((pixels, problem) -> minecraft.execute(() -> {
			uploading = false;
			if (problem != null || pixels == null) {
				wears.put(entry.key, new Wear(State.FAILED, MojangSkins.Failure.NO_ANSWER));
				return;
			}
			wears.clear();
			wears.put(entry.key, new Wear(State.READY, null));
			showOnMe("worn-" + entry.key, upload("worn-" + entry.key, pixels, modelOf(entry.arms)));
			Wardrobe into = wardrobe();
			into.worn(entry, null, entry.arms, System.currentTimeMillis());
			Applied told = new Applied(into.get(entry.key) != null ? into.get(entry.key) : entry, null);
			for (Consumer<Applied> listener : appliedListeners) listener.accept(told);
		}));
	}

	/**
	 * Puts on what was last worn through the site: in a game that has just started, as Mojang keeps
	 * nothing for such a player and nothing else would, and when another copy of the game turns
	 * out to have worn something newer. Does nothing if that is what is on already.
	 */
	public void restoreWorn() {
		if (canUpload()) return;
		UUID me = Minecraft.getInstance().getUser().getProfileId();
		Wardrobe.Entry latest = null;
		for (Wardrobe.Entry entry : wardrobe().shown()) {
			if (entry.worn > 0 && (latest == null || entry.worn > latest.worn)) latest = entry;
		}
		if (latest == null || ("worn-" + latest.key).equals(worn.get(me))) return;
		Wardrobe.Entry entry = latest;
		String before = worn.get(me);
		Minecraft minecraft = Minecraft.getInstance();
		bytesOf(entry, false).thenApplyAsync(Skins::pixelsOf, site.work()).whenComplete((pixels, problem) -> minecraft.execute(() -> {
			// Something put on meanwhile by hand stays on.
			if (problem != null || pixels == null || !java.util.Objects.equals(before, worn.get(me))) return;
			Mosaicary.LOGGER.info("Put back the skin last worn: {}", entry.key);
			showOnMe("worn-" + entry.key, upload("worn-" + entry.key, pixels, modelOf(entry.arms)));
			Applied told = new Applied(entry, null);
			for (Consumer<Applied> listener : appliedListeners) listener.accept(told);
		}));
	}

	/** The UUID an offline server makes up from a player's name. */
	public static UUID offlineId(String name) {
		return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}

	/** Shows a skin on the local player: by its account, and by its name, which is who it is on an offline server. */
	private void showOnMe(String key, Texture texture) {
		var user = Minecraft.getInstance().getUser();
		show(user.getProfileId(), key, texture);
		UUID byName = offlineId(user.getName());
		if (!byName.equals(user.getProfileId())) {
			worn.put(byName, key);
			shown.put(byName, new Shown(texture.texture(), texture.model(), texture.pixels()));
		}
	}

	/** How often the skin on the account is asked for while a menu is open, and when one has just opened. */
	private static final long OWN_AGAIN_MS = 60_000;
	private static final long OWN_SOON_MS = 15_000;

	private String ownTexture;
	private long ownAskedAt = Long.MIN_VALUE / 2;
	private boolean ownAsking;
	private int ownChanges;

	/**
	 * Brings what the local player is shown in step with the account, for a game Mojang takes skins
	 * from. What the game itself knows was read once when it started and may be old or missing, so
	 * another copy of the game changing the skin would not show here until both were restarted.
	 *
	 * @param opening whether a menu has just opened, which is worth asking sooner for
	 */
	public void refreshOwn(boolean opening) {
		if (!canUpload() || ownAsking) return;
		long now = net.minecraft.util.Util.getMillis();
		if (now - ownAskedAt < (opening ? OWN_SOON_MS : OWN_AGAIN_MS)) return;
		ownAskedAt = now;
		ownAsking = true;
		int changes = ownChanges;
		Minecraft minecraft = Minecraft.getInstance();
		mojang.current(minecraft.getUser().getAccessToken()).whenComplete((applied, problem) -> minecraft.execute(() -> {
			// A skin put on here meanwhile is newer than this answer.
			if (problem != null || changes != ownChanges || applied.texture().equals(ownTexture)) {
				if (problem != null) Mosaicary.LOGGER.debug("Could not ask for the skin on the account: {}", problem.getMessage());
				ownAsking = false;
				return;
			}
			String hash = applied.texture();
			String arms = applied.arms();
			bytesOf(Wardrobe.Entry.ofTexture(hash, "", arms, 0), false).thenApplyAsync(Skins::pixelsOf, site.work())
				.whenComplete((pixels, failed) -> minecraft.execute(() -> {
					ownAsking = false;
					if (failed != null || pixels == null || changes != ownChanges) return;
					ownTexture = hash;
					Mosaicary.LOGGER.info("The skin on the account: texture {}", hash);
					show(minecraft.getUser().getProfileId(), "tex:" + hash, upload("shown-tex:" + hash, pixels, modelOf(arms)));
				}));
		}));
	}

	/** Whether the skin on the account is being asked for; the figure beside the menu waits for it. */
	public boolean askingOwn() {
		return ownAsking;
	}

	/** Which wardrobe key is shown on the local player after a skin was put on here, or null. */
	public String wornHere() {
		return worn.get(Minecraft.getInstance().getUser().getProfileId());
	}

	// ------------------------------------------------------------------ skins shown on players

	/** A skin drawn on a player instead of the one the server gave: the texture, the arms and its pixels. */
	public record Shown(Identifier texture, PlayerModelType model, int[] pixels) { }

	private final Map<UUID, Shown> shown = new HashMap<>();

	/** The skin to draw on a player instead of the server's, or null; read by the player info mixin. */
	public Shown shownOn(UUID player) {
		return shown.get(player);
	}

	private void show(UUID player, String key, Texture texture) {
		String before = worn.put(player, key);
		shown.put(player, new Shown(texture.texture(), texture.model(), texture.pixels()));
		textures.put(key, texture);
		if (before != null && !before.equals(key) && !worn.containsValue(before)) forgetTexture(before);
	}

	/** Shows a skin already fetched on a player, the way putting it on does; for the game test. */
	public void showFetched(UUID player, String key, Texture fetched) {
		show(player, key, upload("shown-" + key, fetched.pixels(), fetched.model()));
	}

	/**
	 * Shows another player's skin, by its Mojang texture, as soon as it is fetched; null
	 * stops showing anything over what the server gave. For skins changed while the
	 * server still hands out the old one.
	 */
	public void showOther(UUID player, String texture, String arms) {
		if (texture == null) {
			String before = worn.remove(player);
			shown.remove(player);
			if (before != null && !worn.containsValue(before)) forgetTexture(before);
			return;
		}
		showOther(player, Wardrobe.Entry.ofTexture(texture, "", arms, 0));
	}

	/** The same for a skin known otherwise: of the catalogue, or a file kept with a wardrobe on the site. */
	public void showOther(UUID player, Wardrobe.Entry skin) {
		String key = skin.key;
		if (key.equals(worn.get(player))) return;
		String arms = skin.arms;
		Minecraft minecraft = Minecraft.getInstance();
		bytesOf(skin, false).thenApplyAsync(Skins::pixelsOf, site.work()).whenComplete((pixels, problem) -> minecraft.execute(() -> {
			if (problem != null || pixels == null) return;
			show(player, key, upload("shown-" + key, pixels, modelOf(arms)));
		}));
	}

	/**
	 * The texture hash of the skin the server handed out for a player, from the signed
	 * {@code textures} property of their profile in the player list; null when there is none.
	 */
	public static String serverTexture(UUID player) {
		var connection = Minecraft.getInstance().getConnection();
		var info = connection == null ? null : connection.getPlayerInfo(player);
		if (info == null) return null;
		for (com.mojang.authlib.properties.Property property : info.getProfile().properties().get("textures")) {
			try {
				String decoded = new String(java.util.Base64.getDecoder().decode(property.value()), java.nio.charset.StandardCharsets.UTF_8);
				var skin = com.google.gson.JsonParser.parseString(decoded).getAsJsonObject().getAsJsonObject("textures")
					.getAsJsonObject("SKIN");
				if (skin != null) return MojangSkins.textureOf(skin.get("url").getAsString());
			} catch (RuntimeException unreadable) {
				// Not a textures property the game itself could read either.
			}
		}
		return null;
	}

	/** Forgets every other player's skin: on leaving a world. The local player's stays. */
	public void forgetOthers() {
		var user = Minecraft.getInstance().getUser();
		UUID me = user.getProfileId();
		UUID byName = offlineId(user.getName());
		for (UUID player : new ArrayList<>(shown.keySet())) {
			if (!player.equals(me) && !player.equals(byName)) showOther(player, (String) null, null);
		}
	}

	private void forgetTexture(String key) {
		Texture old = textures.remove(key);
		if (old != null && old.texture() != null) Minecraft.getInstance().getTextureManager().release(old.texture());
	}

	// ------------------------------------------------------------------ saving a file

	/** Saves a skin's file to {@code mosaicary/skins} under a readable name; counted as a download. */
	public CompletableFuture<Path> savePng(Wardrobe.Entry entry) {
		return bytesOf(entry, true).thenApplyAsync(bytes -> {
			String name = (entry.name == null || entry.name.isBlank() ? "skin" : entry.name)
				.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
			if (name.length() > 60) name = name.substring(0, 60).trim();
			String tail = entry.id != null ? entry.id : entry.texture != null ? entry.texture.substring(0, 12) : entry.file.substring(0, 12);
			Path target = root.resolve("skins").resolve(name + " (" + tail + ").png");
			try {
				Files.createDirectories(target.getParent());
				Files.write(target, bytes);
				return target;
			} catch (IOException failed) {
				throw new CompletionException(failed);
			}
		}, site.work());
	}

	public void close() {
		mojang.close();
	}
}

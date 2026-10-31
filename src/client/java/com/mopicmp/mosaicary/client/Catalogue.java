package com.mopicmp.mosaicary.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.mojang.blaze3d.platform.NativeImage;
import com.mopicmp.mosaicary.Mosaicary;
import com.mopicmp.mosaicary.client.build.Builds;
import com.mopicmp.mosaicary.client.model.BbParser;
import com.mopicmp.mosaicary.client.model.Figura;
import com.mopicmp.mosaicary.client.render.ModelMesh;
import com.mopicmp.mosaicary.client.render.Pictures;
import com.mopicmp.mosaicary.client.site.ModelCard;
import com.mopicmp.mosaicary.client.site.SiteClient;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.Identifier;

/**
 * What the catalogue knows, kept between openings of the screen.
 *
 * Everything here is read and changed on the client thread only. Work happens in
 * the site client's threads; results come back through {@code minecraft.execute},
 * which runs them on the client thread before the next frame.
 */
public final class Catalogue {

	public enum State { LOADING, READY, FAILED }

	/** A card picture: its texture once it is decoded and uploaded. */
	public record Picture(State state, Identifier texture, int width, int height) { }

	/**
	 * A model opened for the 3D preview.
	 *
	 * @param scripts how many Figura scripts came with it, none of which is run
	 */
	public record Model(State state, ModelMesh mesh, String problem, int scripts) { }

	/** What reading a model's file gave: the mesh to upload and the scripts that were not run. */
	private record Read(ModelMesh.Prepared prepared, int scripts) { }

	/** An avatar that could not be unpacked or read from disk. */
	private static final class AvatarProblem extends RuntimeException {
		AvatarProblem(Throwable cause) {
			super(cause);
		}
	}

	/** A download in progress or done. */
	public record Download(State state, Path where, SiteClient.Failure failure) { }

	/** The longest side card pictures are kept at, in pixels. */
	private static final int PICTURE_SIZE = 256;

	/** How many models stay uploaded at once; older ones are closed. */
	private static final int MESHES_KEPT = 4;

	private final SiteClient site;
	private State listState;
	private List<ModelCard> cards = List.of();
	private boolean fromCache;
	private SiteClient.Failure failure;
	private int generation;

	private final Map<String, Picture> pictures = new HashMap<>();
	private final Map<String, Model> models = new LinkedHashMap<>(16, 0.75f, true);
	private final Map<String, Download> downloads = new HashMap<>();
	private final Map<String, ModelCard> details = new HashMap<>();

	public Catalogue(SiteClient site) {
		this.site = site;
	}

	public SiteClient site() {
		return site;
	}

	// ------------------------------------------------------------------ the list

	public State listState() {
		return listState;
	}

	public List<ModelCard> cards() {
		return cards;
	}

	public boolean fromCache() {
		return fromCache;
	}

	public SiteClient.Failure failure() {
		return failure;
	}

	/** Loads the list if it has never been loaded. */
	public void ensureList() {
		if (listState == null) refresh();
	}

	/** Asks the site for the list again. */
	public void refresh() {
		if (listState == State.LOADING) return;
		listState = State.LOADING;
		int asked = ++generation;
		long started = System.currentTimeMillis();
		Minecraft minecraft = Minecraft.getInstance();
		// Pictures that failed are tried again with the new list.
		pictures.values().removeIf(picture -> picture.state() == State.FAILED);
		site.list().whenComplete((listing, problem) -> minecraft.execute(() -> {
			if (asked != generation) return;
			Mosaicary.LOGGER.info("Catalogue list answered in {} ms", System.currentTimeMillis() - started);
			if (problem != null) {
				Mosaicary.LOGGER.warn("Could not load the catalogue: {}", SiteClient.describe(problem));
				failure = unwrapFailure(problem);
				listState = State.FAILED;
				return;
			}
			cards = listing.cards();
			fromCache = listing.fromCache();
			failure = listing.whyCached();
			listState = State.READY;
			// Pictures of models that changed since they were fetched are fetched again.
			pictures.entrySet().removeIf(entry -> {
				boolean stale = cards.stream().noneMatch(card -> (card.id() + card.versionKey()).equals(entry.getKey()));
				if (stale && entry.getValue().texture() != null) {
					minecraft.getTextureManager().release(entry.getValue().texture());
				}
				return stale;
			});
		}));
	}

	private static SiteClient.Failure unwrapFailure(Throwable problem) {
		return SiteClient.unwrap(problem).failure();
	}

	// ------------------------------------------------------------------ pictures

	/** The card picture of a model, starting to fetch it if nobody has yet. */
	public Picture picture(ModelCard card) {
		String key = card.id() + card.versionKey();
		Picture known = pictures.get(key);
		if (known != null) return known;
		Picture loading = new Picture(State.LOADING, null, 0, 0);
		pictures.put(key, loading);
		if (card.preview().isEmpty()) {
			Picture none = new Picture(State.FAILED, null, 0, 0);
			pictures.put(key, none);
			return none;
		}
		Minecraft minecraft = Minecraft.getInstance();
		site.preview(card)
			.thenApplyAsync(bytes -> Pictures.decodeScaled(bytes, PICTURE_SIZE))
			// A picture that never arrives would leave a spinner on the card for good.
			.orTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
			.whenComplete((image, problem) -> minecraft.execute(() -> {
				if (pictures.get(key) != loading) {
					if (image != null) image.close();
					return;
				}
				if (problem != null || image == null) {
					if (problem != null) Mosaicary.LOGGER.debug("No picture for {}: {}", card.id(), SiteClient.describe(problem));
					pictures.put(key, new Picture(State.FAILED, null, 0, 0));
					return;
				}
				pictures.put(key, upload(key, image));
			}));
		return loading;
	}

	private static Picture upload(String key, NativeImage image) {
		Identifier id = Mosaicary.id("preview/" + key.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_"));
		int width = image.getWidth();
		int height = image.getHeight();
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "Mosaicary preview", image));
		return new Picture(State.READY, id, width, height);
	}

	/** Whether every card in the list has its picture or has given up on it. */
	public boolean picturesSettled(List<ModelCard> shown) {
		for (ModelCard card : shown) {
			Picture picture = pictures.get(card.id() + card.versionKey());
			if (picture == null || picture.state() == State.LOADING) return false;
		}
		return true;
	}

	// ------------------------------------------------------------------ models

	/** The model for the 3D preview, starting to fetch and read it if needed. */
	/** What a build opens as: nothing to draw, which the detail view shows as its picture instead. */
	private static final Model BUILD_MODEL = new Model(State.FAILED, null, "model.BUILD", 0);

	public Model model(ModelCard card) {
		// A build has no model in it: fetching it to read as one would only fail.
		if (card.isBuild()) return BUILD_MODEL;
		String key = card.id() + card.versionKey();
		Model known = models.get(key);
		if (known != null) return known;
		Model loading = new Model(State.LOADING, null, null, 0);
		models.put(key, loading);
		Minecraft minecraft = Minecraft.getInstance();
		CompletableFuture<Read> prepared = card.isLocal()
			? CompletableFuture.supplyAsync(() -> readLocal(card))
			: site.file(card).thenApplyAsync(bytes -> readFile(card, bytes));
		prepared.whenComplete((ready, problem) -> minecraft.execute(() -> {
			if (models.get(key) != loading) {
				if (ready != null) ready.prepared().discard();
				return;
			}
			if (problem != null) {
				Throwable cause = problem.getCause() != null ? problem.getCause() : problem;
				String said = cause instanceof BbParser.NotAModel refused ? "model." + refused.why().name()
					: cause instanceof AvatarProblem ? "model.AVATAR"
					: "site." + unwrapFailure(problem).name();
				Mosaicary.LOGGER.warn("Could not open model {}: {}", card.id(),
					cause instanceof AvatarProblem ? String.valueOf(cause.getCause()) : cause.toString());
				models.put(key, new Model(State.FAILED, null, said, 0));
				return;
			}
			if (ready.scripts() > 0) {
				Mosaicary.LOGGER.info("Figura avatar {}: {} Lua scripts are not run; its models and animations are shown",
					card.id(), ready.scripts());
			}
			// Texture ids carry the version too: an older mesh of the same model may still be
			// cached, and closing it must not release the newer one's textures.
			models.put(key, new Model(State.READY, ready.prepared().upload(card.id() + "-" + card.versionKey()), null,
				ready.scripts()));
			trim();
		}));
		return loading;
	}

	/**
	 * A file from the site: a single model, or a Figura avatar as a zip, which is
	 * unpacked into the cache under the model's id and version (older versions of it
	 * are removed) and read from there.
	 */
	private Read readFile(ModelCard card, byte[] bytes) {
		try {
			if (!Figura.isZip(bytes)) return new Read(ModelMesh.prepare(BbParser.parse(bytes)), 0);
			Path folder = site.root().resolve("cache/figura/" + safe(card.id()) + "-" + card.versionKey());
			forgetOtherVersions(folder.getParent(), safe(card.id()) + "-", folder);
			Figura.Avatar avatar = Figura.read(Figura.unzip(bytes, folder));
			return new Read(ModelMesh.prepare(avatar.model()), avatar.scripts());
		} catch (BbParser.NotAModel refused) {
			throw new CompletionException(refused);
		} catch (IOException broken) {
			throw new AvatarProblem(broken);
		}
	}

	/** A Figura avatar from the player's own folder: read in place, or unpacked into the cache when zipped. */
	private Read readLocal(ModelCard card) {
		try {
			Path entry = localPath(card);
			if (entry == null) throw new IOException("not in the Figura avatars folder: " + card.id());
			Path folder;
			if (Files.isDirectory(entry)) {
				folder = Figura.avatarIn(entry);
				if (folder == null) throw new IOException("no avatar.json in " + entry);
			} else {
				Path into = site.root().resolve("cache/figura-local/" + safe(card.id()) + "-" + card.versionKey());
				forgetOtherVersions(into.getParent(), safe(card.id()) + "-", into);
				folder = Figura.unzip(Files.readAllBytes(entry), into);
			}
			Figura.Avatar avatar = Figura.read(folder);
			return new Read(ModelMesh.prepare(avatar.model()), avatar.scripts());
		} catch (BbParser.NotAModel refused) {
			throw new CompletionException(refused);
		} catch (IOException broken) {
			throw new AvatarProblem(broken);
		}
	}

	private static String safe(String id) {
		return id.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
	}

	/** Removes unpacked older versions of the same avatar. */
	private static void forgetOtherVersions(Path parent, String prefix, Path keep) {
		if (!Files.isDirectory(parent)) return;
		try (java.util.stream.Stream<Path> found = Files.list(parent)) {
			for (Path one : (Iterable<Path>) found::iterator) {
				String name = one.getFileName().toString();
				if (name.startsWith(prefix) && !one.equals(keep) && !one.equals(keep.resolveSibling(keep.getFileName() + ".part"))) {
					try (java.util.stream.Stream<Path> tree = Files.walk(one)) {
						for (Path gone : (Iterable<Path>) tree.sorted(java.util.Comparator.reverseOrder())::iterator) {
							Files.deleteIfExists(gone);
						}
					}
				}
			}
		} catch (IOException ignored) {
			// A stale copy costs disk space and nothing else.
		}
	}

	// ------------------------------------------------------------------ the player's own Figura avatars

	private Path figuraAvatars;
	private List<ModelCard> localCards = List.of();
	private State localState;

	/** Where Figura keeps the player's avatars: {@code figura/avatars} in the game folder. */
	public void figuraFolder(Path folder) {
		this.figuraAvatars = folder;
	}

	/** Whether the game folder has a Figura avatars folder at all. */
	public boolean hasFiguraFolder() {
		return figuraAvatars != null && Files.isDirectory(figuraAvatars);
	}

	public State localState() {
		return localState;
	}

	public List<ModelCard> localCards() {
		return localCards;
	}

	/**
	 * Lists the avatars in the Figura folder again: every folder with avatar.json at
	 * its top or a little below, and every zip. Off the client thread; the list
	 * arrives on it.
	 */
	public void refreshLocal() {
		if (!hasFiguraFolder() || localState == State.LOADING) return;
		localState = State.LOADING;
		Path folder = figuraAvatars;
		Minecraft minecraft = Minecraft.getInstance();
		CompletableFuture.supplyAsync(() -> listLocal(folder)).whenComplete((found, problem) -> minecraft.execute(() -> {
			if (problem != null) {
				Mosaicary.LOGGER.warn("Could not list the Figura avatars: {}", problem.toString());
				localState = State.FAILED;
				return;
			}
			localCards = found;
			localState = State.READY;
		}));
	}

	private static List<ModelCard> listLocal(Path folder) {
		List<ModelCard> out = new ArrayList<>();
		try (java.util.stream.Stream<Path> entries = Files.list(folder)) {
			for (Path entry : (Iterable<Path>) entries.sorted()::iterator) {
				String file = entry.getFileName().toString();
				String id = ModelCard.LOCAL + "figura/" + file;
				try {
					if (Files.isDirectory(entry)) {
						Path avatar = Figura.avatarIn(entry);
						if (avatar == null) continue;
						Figura.Info info = Figura.info(avatar);
						out.add(ModelCard.local(id, info.name().isBlank() ? file : info.name(), String.join(", ", info.authors()),
							info.description(), latestChange(entry)));
					} else if (file.toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
						out.add(ModelCard.local(id, file.substring(0, file.length() - 4), "", "",
							Files.getLastModifiedTime(entry).toInstant().toString()));
					}
				} catch (IOException | RuntimeException unreadable) {
					Mosaicary.LOGGER.debug("Skipping Figura avatar {}: {}", entry, unreadable.toString());
				}
			}
		} catch (IOException unreadable) {
			throw new java.io.UncheckedIOException(unreadable);
		}
		return List.copyOf(out);
	}

	/** The newest change of any file in a folder, so an edited avatar is read again. */
	private static String latestChange(Path folder) throws IOException {
		long newest = 0;
		try (java.util.stream.Stream<Path> walk = Files.walk(folder)) {
			for (Path one : (Iterable<Path>) walk::iterator) {
				newest = Math.max(newest, Files.getLastModifiedTime(one).toMillis());
			}
		}
		return java.time.Instant.ofEpochMilli(newest).toString();
	}

	/** Where a local card's avatar is, or null when its id points outside the Figura folder. */
	private Path localPath(ModelCard card) {
		if (figuraAvatars == null || !card.id().startsWith(ModelCard.LOCAL + "figura/")) return null;
		Path entry = figuraAvatars.resolve(card.id().substring((ModelCard.LOCAL + "figura/").length())).normalize();
		return entry.startsWith(figuraAvatars.normalize()) && Files.exists(entry) ? entry : null;
	}

	/** Models in use somewhere besides the preview (worn ones), which are never closed by trimming. */
	private final java.util.Set<String> pinned = new java.util.HashSet<>();

	/** Keeps this model's mesh alive while it is worn. */
	public void pin(ModelCard card) {
		pinned.add(card.id() + card.versionKey());
	}

	public void unpin(ModelCard card) {
		pinned.remove(card.id() + card.versionKey());
		trim();
	}

	private void trim() {
		List<String> ready = new ArrayList<>();
		for (Map.Entry<String, Model> entry : models.entrySet()) {
			if (entry.getValue().state() == State.READY && !pinned.contains(entry.getKey())) ready.add(entry.getKey());
		}
		// Access order: the first ones are the least recently looked at.
		for (int i = 0; i < ready.size() - MESHES_KEPT; i++) {
			Model old = models.remove(ready.get(i));
			if (old != null && old.mesh() != null) old.mesh().close();
		}
	}

	/** Forgets a failed model so the next look tries again. */
	public void retryModel(ModelCard card) {
		String key = card.id() + card.versionKey();
		Model known = models.get(key);
		if (known != null && known.state() == State.FAILED) models.remove(key);
	}

	// ------------------------------------------------------------------ details

	/**
	 * The model's full card, with its description, once the site has answered;
	 * the listed card until then, and if the site does not answer.
	 */
	public ModelCard detail(ModelCard listed) {
		if (listed.isLocal()) return listed;
		String key = listed.id() + listed.versionKey();
		ModelCard known = details.get(key);
		if (known != null) return known;
		details.put(key, listed);
		Minecraft minecraft = Minecraft.getInstance();
		site.detail(listed.id()).whenComplete((card, problem) -> minecraft.execute(() -> {
			if (problem != null) {
				Mosaicary.LOGGER.debug("No detail for {}: {}", listed.id(), SiteClient.describe(problem));
				return;
			}
			details.put(key, card);
		}));
		return listed;
	}

	// ------------------------------------------------------------------ downloads

	public Download download(ModelCard card) {
		Download known = downloads.get(card.id());
		if (known != null) return known;
		if (site.isDownloaded(card)) {
			Download done = new Download(State.READY, site.downloadedPath(card), null);
			downloads.put(card.id(), done);
			return done;
		}
		return null;
	}

	public void startDownload(ModelCard card) {
		Download known = downloads.get(card.id());
		if (known != null && known.state() != State.FAILED) return;
		downloads.put(card.id(), new Download(State.LOADING, null, null));
		Minecraft minecraft = Minecraft.getInstance();
		site.download(card).whenComplete((path, problem) -> minecraft.execute(() -> {
			if (problem != null) {
				Mosaicary.LOGGER.warn("Download of {} failed: {}", card.id(), SiteClient.describe(problem));
				downloads.put(card.id(), new Download(State.FAILED, null, unwrapFailure(problem)));
			} else {
				downloads.put(card.id(), new Download(State.READY, path, null));
				if (card.isBuild() && site.placers().contains(Builds.Format.WORLDEDIT)) tellWorldEdit(minecraft, card);
			}
		}));
	}

	/**
	 * WorldEdit is the one of the three that loads a build by a command, so the command is put
	 * in the chat once the build is there, ready to be clicked into the chat line.
	 */
	private static void tellWorldEdit(Minecraft minecraft, ModelCard card) {
		String command = SiteClient.worldEditCommand(card);
		Component line = Component.translatable("mosaicary.build.worldedit", card.name(),
			Component.literal(command).withStyle(style -> style.withColor(ChatFormatting.AQUA).withUnderlined(true)
				.withClickEvent(new ClickEvent.SuggestCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.translatable("mosaicary.build.worldedit.hover")))));
		minecraft.gui.chatListener().handleSystemMessage(line, false);
	}
}

package com.mopicmp.mosaicary.client.model;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * Figura avatars, drawn without running their scripts.
 *
 * <h2>What an avatar is</h2>
 *
 * A folder (or a zip of one) with {@code avatar.json} at its top: name, authors,
 * description and per-part settings. Next to it, any number of {@code .bbmodel}
 * files, in subfolders too, their textures, and Lua scripts. Every model file is a
 * part of the avatar named after the file; they are drawn together.
 *
 * <h2>How groups attach to the player</h2>
 *
 * By the start of a group's name, case and all, checked against a fixed list in a
 * fixed order; the first match decides. These are the names Figura uses (read from
 * its 0.1.6 release for 1.21.4):
 *
 * | Group name starts with | Drawn |
 * | --- | --- |
 * | {@code None}, {@code NONE}, {@code Model}, {@code MODEL} | as it is, following nothing |
 * | {@code Head}, {@code HEAD} | following the head |
 * | {@code Body}, {@code BODY} | following the body |
 * | {@code LeftArm}, {@code LEFT_ARM}, {@code RightArm}, {@code RIGHT_ARM} | following that arm |
 * | {@code LeftLeg}, {@code LEFT_LEG}, {@code RightLeg}, {@code RIGHT_LEG} | following that leg |
 * | pivot names ({@code LeftLeggingPivot}, {@code HelmetPivot}, ...) | as they are: they mark where armour and items go |
 * | {@code LeftElytra}, {@code RightElytra} (and {@code Elytron}), {@code Cape} | not on the body: Figura draws them only with a cape or elytra |
 * | {@code World}, {@code Hud} and its spellings, {@code Skull}, {@code ?}, {@code Portrait}, {@code Arrow}, {@code Trident}, {@code Item} | not on the body: Figura draws them in the world, on screen, on a head block, an arrow or an item |
 * | {@code Camera}, {@code Billboard} | as they are (turning to face the camera is not done) |
 *
 * The pivot names are listed before the leg names they begin with, so
 * {@code LeftLeggingPivot} is a pivot and {@code LeftLegLower} is a leg. A group
 * nested in a group that already follows the same part does not follow it again.
 *
 * Settings in {@code avatar.json} under {@code customizations} with
 * {@code "visible": false} or {@code "remove": true} hide that part; the key is the
 * part's path, file and group names joined by dots.
 *
 * <h2>What is not done</h2>
 *
 * Scripts are not run: they are counted and the count is shown. Animations play
 * through the player's animation roles like any model's, not when a script starts
 * them. Parts with sound, particles, text or items attached by scripts show nothing.
 */
public final class Figura {

	private Figura() { }

	/** What a group does on the player. */
	public enum Attach { NONE, HEAD, BODY, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG, PIVOT, LAYER, ELSEWHERE }

	private record Name(String start, Attach attach) { }

	/** In Figura's order: the first start a name has decides. */
	private static final List<Name> NAMES = names(
		Attach.NONE, "None", "NONE", "Model", "MODEL",
		Attach.HEAD, "Head", "HEAD",
		Attach.BODY, "Body", "BODY",
		Attach.LEFT_ARM, "LeftArm", "LEFT_ARM",
		Attach.RIGHT_ARM, "RightArm", "RIGHT_ARM",
		Attach.PIVOT, "LeftLeggingPivot", "LEFT_LEGGING_PIVOT",
		Attach.PIVOT, "RightLeggingPivot", "RIGHT_LEGGING_PIVOT",
		Attach.LEFT_LEG, "LeftLeg", "LEFT_LEG",
		Attach.RIGHT_LEG, "RightLeg", "RIGHT_LEG",
		Attach.PIVOT, "LeftElytraPivot", "LEFT_ELYTRA_PIVOT", "LeftWingPivot", "LEFT_WING_PIVOT",
		Attach.PIVOT, "RightElytraPivot", "RIGHT_ELYTRA_PIVOT", "RightWingPivot", "RIGHT_WING_PIVOT",
		Attach.LAYER, "LeftElytra", "LEFT_ELYTRA", "LeftElytron", "LEFT_ELYTRON",
		Attach.LAYER, "RightElytra", "RIGHT_ELYTRA", "RightElytron", "RIGHT_ELYTRON",
		Attach.LAYER, "Cape", "CAPE",
		Attach.ELSEWHERE, "World", "WORLD",
		Attach.ELSEWHERE, "Hud", "HUD", "HeadsUpDisplay", "Gui", "GUI", "GraphicalUserInterface", "JraficalUserInterface",
		Attach.NONE, "Camera", "CAMERA", "Billboard", "BILLBOARD",
		Attach.ELSEWHERE, "Skull", "SKULL", "?",
		Attach.ELSEWHERE, "Portrait", "PORTRAIT",
		Attach.ELSEWHERE, "Arrow", "ARROW",
		Attach.ELSEWHERE, "Trident", "TRIDENT",
		Attach.ELSEWHERE, "Item", "ITEM",
		Attach.PIVOT, "LeftItemPivot", "LEFT_ITEM_PIVOT",
		Attach.PIVOT, "RightItemPivot", "RIGHT_ITEM_PIVOT",
		Attach.PIVOT, "LeftSpyglassPivot", "LEFT_SPYGLASS_PIVOT",
		Attach.PIVOT, "RightSpyglassPivot", "RIGHT_SPYGLASS_PIVOT",
		Attach.PIVOT, "LeftParrotPivot", "LEFT_PARROT_PIVOT",
		Attach.PIVOT, "RightParrotPivot", "RIGHT_PARROT_PIVOT",
		Attach.PIVOT, "HelmetItemPivot", "HELMET_ITEM_PIVOT",
		Attach.PIVOT, "HelmetPivot", "HELMET_PIVOT",
		Attach.PIVOT, "ChestplatePivot", "CHESTPLATE_PIVOT", "ChestplateBodyPivot", "CHESTPLATE_BODY_PIVOT",
		Attach.PIVOT, "LeftShoulderPivot", "LEFT_SHOULDER_PIVOT",
		Attach.PIVOT, "RightShoulderPivot", "RIGHT_SHOULDER_PIVOT",
		Attach.PIVOT, "LeggingsPivot", "LEGGINGS_PIVOT", "BeltPivot", "BELT_PIVOT",
		Attach.PIVOT, "LeftBootPivot", "LEFT_BOOT_PIVOT",
		Attach.PIVOT, "RightBootPivot", "RIGHT_BOOT_PIVOT");

	private static List<Name> names(Object... list) {
		List<Name> out = new ArrayList<>();
		Attach current = Attach.NONE;
		for (Object one : list) {
			if (one instanceof Attach attach) current = attach;
			else out.add(new Name((String) one, current));
		}
		return List.copyOf(out);
	}

	/** What a group of this name does on the player. */
	public static Attach attach(String groupName) {
		if (groupName == null) return Attach.NONE;
		for (Name name : NAMES) if (groupName.startsWith(name.start())) return name.attach();
		return Attach.NONE;
	}

	/** Whether a group of this name is drawn on the player at all. */
	public static boolean onBody(String groupName) {
		Attach attach = attach(groupName);
		return attach != Attach.LAYER && attach != Attach.ELSEWHERE;
	}

	/** The humanoid part a group follows, or null. */
	public static Rig.Part part(String groupName) {
		return switch (attach(groupName)) {
			case HEAD -> Rig.Part.HEAD;
			case BODY -> Rig.Part.BODY;
			case LEFT_ARM -> Rig.Part.LEFT_ARM;
			case RIGHT_ARM -> Rig.Part.RIGHT_ARM;
			case LEFT_LEG -> Rig.Part.LEFT_LEG;
			case RIGHT_LEG -> Rig.Part.RIGHT_LEG;
			default -> null;
		};
	}

	// ------------------------------------------------------------------ reading an avatar

	/**
	 * An avatar read from its folder.
	 *
	 * @param name        from {@code avatar.json}, else the folder's name
	 * @param authors     from {@code authors} or {@code author}
	 * @param description from {@code description}
	 * @param model       every model file put together
	 * @param scripts     how many Lua scripts the avatar has, none of which is run
	 */
	public record Avatar(String name, List<String> authors, String description, BbModel model, int scripts) { }

	/** Most bytes one file of an avatar may have; a safety limit against broken or hostile archives. */
	static final long FILE_MOST = 64L << 20;
	/** Most bytes an unpacked avatar may have, all files together. */
	static final long AVATAR_MOST = 256L << 20;
	/** Most files an avatar archive may hold. */
	static final int FILES_MOST = 10_000;

	/** Whether a folder is an avatar: it has {@code avatar.json} at its top. */
	public static boolean isAvatar(Path folder) {
		return Files.isRegularFile(folder.resolve("avatar.json"));
	}

	/**
	 * What {@code avatar.json} says, read without the models: for listing avatars.
	 *
	 * @param hidden the part paths its settings hide
	 */
	public record Info(String name, List<String> authors, String description, Map<String, Boolean> hidden) { }

	/** Reads {@code avatar.json}; one that is not JSON still marks an avatar, its fields just unknown. */
	public static Info info(Path folder) throws IOException {
		JsonObject meta = new JsonObject();
		try {
			JsonElement parsed = JsonParser.parseString(Files.readString(folder.resolve("avatar.json"), StandardCharsets.UTF_8));
			if (parsed.isJsonObject()) meta = parsed.getAsJsonObject();
		} catch (RuntimeException unreadable) {
			meta = new JsonObject();
		}
		List<String> authors = new ArrayList<>();
		if (meta.get("authors") instanceof JsonArray list) {
			for (JsonElement one : list) if (one instanceof JsonPrimitive p && p.isString()) authors.add(p.getAsString());
		}
		if (authors.isEmpty() && !text(meta, "author").isBlank()) authors.add(text(meta, "author"));
		return new Info(text(meta, "name"), List.copyOf(authors), text(meta, "description"), hiddenParts(meta));
	}

	/** Reads an avatar folder. */
	public static Avatar read(Path folder) throws IOException, BbParser.NotAModel {
		Path root = folder.toRealPath();
		Info info = info(root);
		String name = info.name().isBlank() ? root.getFileName().toString() : info.name();
		List<String> authors = info.authors();
		Map<String, Boolean> hidden = info.hidden();

		List<Path> models = new ArrayList<>();
		int scripts = 0;
		try (Stream<Path> walk = Files.walk(root)) {
			for (Path file : (Iterable<Path>) walk.sorted(Comparator.comparing(Path::toString))::iterator) {
				if (!Files.isRegularFile(file)) continue;
				String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
				if (lower.endsWith(".bbmodel")) models.add(file);
				else if (lower.endsWith(".lua")) scripts++;
			}
		}
		List<BbParser.Source> sources = new ArrayList<>();
		for (Path file : models) {
			if (Files.size(file) > FILE_MOST) continue;
			String relative = root.relativize(file).toString().replace('\\', '/');
			String path = relative.substring(0, relative.length() - ".bbmodel".length()).replace('/', '.');
			Path beside = file.getParent();
			sources.add(new BbParser.Source(path, Files.readString(file, StandardCharsets.UTF_8),
				texture -> textureBeside(root, beside, texture)));
		}
		if (sources.isEmpty()) throw new BbParser.NotAModel(BbParser.Why.NOTHING_IN_IT, "no model files in the avatar");
		BbModel model = BbParser.parseAll(name, sources,
			(path, group) -> onBody(group) && !hidden.getOrDefault(path, false));
		return new Avatar(name, authors, info.description(), model, scripts);
	}

	/**
	 * Parts {@code avatar.json} hides: {@code "visible": false} or {@code "remove": true}
	 * under {@code customizations}, by path. A leading {@code models.} is allowed.
	 */
	static Map<String, Boolean> hiddenParts(JsonObject meta) {
		Map<String, Boolean> out = new HashMap<>();
		if (!(meta.get("customizations") instanceof JsonObject all)) return out;
		for (Map.Entry<String, JsonElement> entry : all.entrySet()) {
			if (!(entry.getValue() instanceof JsonObject settings)) continue;
			boolean invisible = settings.get("visible") instanceof JsonPrimitive v && v.isBoolean() && !v.getAsBoolean();
			boolean removed = settings.get("remove") instanceof JsonPrimitive r && r.isBoolean() && r.getAsBoolean();
			if (!invisible && !removed) continue;
			String key = entry.getKey();
			if (key.startsWith("models.")) key = key.substring("models.".length());
			out.put(key, true);
		}
		return out;
	}

	/**
	 * The picture of a texture a model file does not carry: by its relative path
	 * from the file, else by its file name or name beside the file. Never outside
	 * the avatar's folder.
	 */
	private static byte[] textureBeside(Path root, Path beside, JsonObject texture) {
		List<String> tries = new ArrayList<>();
		String relative = text(texture, "relative_path");
		if (!relative.isBlank()) tries.add(relative);
		String path = text(texture, "path");
		if (!path.isBlank()) tries.add(Path.of(path.replace('\\', '/')).getFileName().toString());
		String name = text(texture, "name");
		if (!name.isBlank()) {
			tries.add(name);
			if (!name.toLowerCase(Locale.ROOT).endsWith(".png")) tries.add(name + ".png");
		}
		for (String one : tries) {
			try {
				Path file = beside.resolve(one.replace('\\', '/')).normalize();
				if (!file.startsWith(root) || !Files.isRegularFile(file) || Files.size(file) > FILE_MOST) continue;
				return Files.readAllBytes(file);
			} catch (IOException | RuntimeException unreadable) {
				// The next spelling, or no picture.
			}
		}
		return null;
	}

	private static String text(JsonObject object, String key) {
		JsonElement value = object == null ? null : object.get(key);
		return value instanceof JsonPrimitive primitive && primitive.isString() ? primitive.getAsString() : "";
	}

	// ------------------------------------------------------------------ archives

	/** Whether these bytes are a zip archive rather than a single model file. */
	public static boolean isZip(byte[] bytes) {
		return bytes != null && bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K' && bytes[2] == 3 && bytes[3] == 4;
	}

	/**
	 * Unpacks an avatar archive into a folder and returns the avatar's own folder
	 * inside it: the shallowest one with {@code avatar.json}. Entries that would land
	 * outside the folder are skipped; an archive over the limits on files and bytes
	 * is refused. The folder is filled under a temporary name and moved into place
	 * when complete, so a half-written avatar is never read.
	 */
	public static Path unzip(byte[] zip, Path into) throws IOException {
		if (!isAvatarFolderComplete(into)) {
			Path temporary = into.resolveSibling(into.getFileName() + ".part");
			deleteTree(temporary);
			Files.createDirectories(temporary);
			long total = 0;
			int count = 0;
			try (InputStream raw = new ByteArrayInputStream(zip); ZipInputStream entries = new ZipInputStream(raw)) {
				for (ZipEntry entry; (entry = entries.getNextEntry()) != null;) {
					if (++count > FILES_MOST) throw new IOException("more than " + FILES_MOST + " files in the archive");
					Path target = temporary.resolve(entry.getName().replace('\\', '/')).normalize();
					if (!target.startsWith(temporary) || target.equals(temporary)) continue;
					if (entry.isDirectory()) {
						Files.createDirectories(target);
						continue;
					}
					Files.createDirectories(target.getParent());
					long written = 0;
					try (var out = Files.newOutputStream(target)) {
						byte[] buffer = new byte[64 * 1024];
						for (int read; (read = entries.read(buffer)) > 0;) {
							written += read;
							total += read;
							if (written > FILE_MOST || total > AVATAR_MOST) throw new IOException("the archive unpacks larger than allowed");
							out.write(buffer, 0, read);
						}
					}
				}
			} catch (IOException | RuntimeException broken) {
				deleteTree(temporary);
				throw broken instanceof IOException io ? io : new IOException(broken);
			}
			deleteTree(into);
			Files.move(temporary, into, StandardCopyOption.ATOMIC_MOVE);
		}
		Path found = avatarIn(into);
		if (found == null) throw new IOException("no avatar.json in the archive");
		return found;
	}

	private static boolean isAvatarFolderComplete(Path folder) {
		return Files.isDirectory(folder) && avatarIn(folder) != null;
	}

	/** The shallowest folder under this one with avatar.json at its top, or null. */
	public static Path avatarIn(Path folder) {
		if (isAvatar(folder)) return folder;
		try (Stream<Path> walk = Files.walk(folder, 4)) {
			return walk.filter(Files::isDirectory).filter(Figura::isAvatar)
				.min(Comparator.comparingInt(Path::getNameCount)).orElse(null);
		} catch (IOException unreadable) {
			return null;
		}
	}

	static void deleteTree(Path folder) throws IOException {
		if (!Files.exists(folder)) return;
		try (Stream<Path> walk = Files.walk(folder)) {
			for (Path one : (Iterable<Path>) walk.sorted(Comparator.reverseOrder())::iterator) Files.deleteIfExists(one);
		}
	}
}

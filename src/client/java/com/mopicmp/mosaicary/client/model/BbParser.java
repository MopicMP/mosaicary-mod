package com.mopicmp.mosaicary.client.model;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * Reads a Blockbench project ({@code .bbmodel}) into faces and bones.
 *
 * <h2>Whose rules</h2>
 *
 * The website draws every model with its own viewer (site/src/lib/bbmodel3d.ts),
 * whose rules were taken from Blockbench's preview code. This reader follows the
 * same rules line for line, so a model looks the same in the game as on its page:
 * box UV laid out again from the cube's size, the texture a face shows in
 * single-texture formats, quads split along the diagonal that keeps them from
 * folding, parts marked as not exported left out unless nothing is exported.
 *
 * <h2>What it covers</h2>
 *
 * Project format versions 4.x and 5.x and the older layouts still found in files:
 * elements under {@code cubes}, groups written whole inside the outliner, children
 * under {@code content}. Cubes (from, to, origin, rotation, inflate, stretch,
 * rescale, per-face UV, texture and rotation, box UV with offset and mirroring),
 * meshes (triangles and quads), nested groups with pivot and rotation, visibility
 * and export flags, embedded textures with their UV sizes and animation frames.
 *
 * Left out: smooth shading, layered and emissive textures, texture frames after
 * the first, and animations (only their names are read).
 */
public final class BbParser {

	/** Why a file could not be read, as a code the interface has words for. */
	public enum Why {
		COMPRESSED, NOT_JSON, NOT_A_PROJECT, NO_META, NOTHING_IN_IT
	}

	public static final class NotAModel extends Exception {
		private final Why why;

		public NotAModel(Why why, String said) {
			super(said);
			this.why = why;
		}

		public Why why() {
			return why;
		}
	}

	private BbParser() { }

	/** Blockbench's order of the six faces. */
	private static final String[] FACES = { "east", "west", "up", "down", "south", "north" };

	/**
	 * The corners of each face as they sit in the cube: 0 is the cube's low side on
	 * that axis, 1 its high side. Listed top-left, top-right, bottom-left,
	 * bottom-right as seen from outside, which is the order the UVs are written in.
	 */
	private static final int[][][] CORNERS = {
		{ { 1, 1, 1 }, { 1, 1, 0 }, { 1, 0, 1 }, { 1, 0, 0 } }, // east
		{ { 0, 1, 0 }, { 0, 1, 1 }, { 0, 0, 0 }, { 0, 0, 1 } }, // west
		{ { 0, 1, 0 }, { 1, 1, 0 }, { 0, 1, 1 }, { 1, 1, 1 } }, // up
		{ { 0, 0, 1 }, { 1, 0, 1 }, { 0, 0, 0 }, { 1, 0, 0 } }, // down
		{ { 0, 1, 1 }, { 1, 1, 1 }, { 0, 0, 1 }, { 1, 0, 1 } }, // south
		{ { 1, 1, 0 }, { 0, 1, 0 }, { 1, 0, 0 }, { 0, 0, 0 } }, // north
	};

	private static final float[][] NORMALS = {
		{ 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 },
	};

	/**
	 * The four corners of a face in drawing order: top-left, bottom-left,
	 * bottom-right, top-right, which runs counter-clockwise seen from outside.
	 */
	private static final int[] QUAD = { 0, 2, 3, 1 };

	/**
	 * One file of several read as one model.
	 *
	 * @param path    the file's place among the others, folders and file name without
	 *                the extension, joined by dots; its top level becomes a group of
	 *                that name (the last part)
	 * @param text    the file's contents
	 * @param texture finds the picture of a texture the file does not carry inside it,
	 *                from the texture's fields; returns null when there is none
	 */
	public record Source(String path, String text, java.util.function.Function<JsonObject, byte[]> texture) { }

	/**
	 * Several projects read as one model, the way a Figura avatar is drawn: under a
	 * nameless root, each file's top level is a group named after the file, holding
	 * that file's groups; textures, animations and placeholders are put together.
	 * An animation whose name another file already used is renamed
	 * {@code <file>.<name>}. Files with nothing to draw are passed over; if none has
	 * anything, the first file's reason is given.
	 *
	 * @param rules decides which groups are drawn and which are not, by their path
	 *              (file path and group names joined by dots) and name; null draws all
	 */
	public static BbModel parseAll(String name, List<Source> files, GroupRules rules) throws NotAModel {
		Bone root = new Bone("", "", null, new float[3], new float[3], new float[] { 1, 1, 1 }, true, false);
		List<Texture> textures = new ArrayList<>();
		List<Bone> bones = new ArrayList<>();
		List<Animation> animations = new ArrayList<>();
		java.util.Set<String> animationNames = new java.util.HashSet<>();
		Map<String, Molang.Expression> placeholders = new LinkedHashMap<>();
		int cubes = 0;
		int meshes = 0;
		Traits traits = null;
		String version = "";
		NotAModel first = null;
		for (Source file : files) {
			BbModel part;
			try {
				JsonObject project = projectOf(file.text());
				String partName = file.path().contains(".") ? file.path().substring(file.path().lastIndexOf('.') + 1) : file.path();
				part = new Reading(project, root, partName, file.path(), textures.size(), rules, file.texture()).read();
			} catch (NotAModel refused) {
				if (first == null) first = refused;
				continue;
			}
			if (traits == null) {
				traits = part.traits();
				version = part.version();
			}
			textures.addAll(part.textures());
			bones.add(part.root());
			bones.addAll(part.bones());
			for (Animation animation : part.animations()) {
				String named = animationNames.add(animation.name().toLowerCase(java.util.Locale.ROOT)) ? animation.name()
					: part.root().name() + "." + animation.name();
				animations.add(named.equals(animation.name()) ? animation
					: new Animation(named, animation.uuid(), animation.length(), animation.loop(), animation.tracks()));
			}
			placeholders.putAll(part.placeholders());
			cubes += part.cubes();
			meshes += part.meshes();
		}
		if (traits == null) {
			throw first != null ? first : new NotAModel(Why.NOTHING_IN_IT, "no files");
		}
		Map<String, Bone> byName = new LinkedHashMap<>();
		for (Bone bone : bones) byName.putIfAbsent(bone.name(), bone);
		return new BbModel(name, "figura", version, traits, List.copyOf(textures), root, List.copyOf(bones), byName,
			List.copyOf(animations), placeholders, cubes, meshes);
	}

	/** Which groups of a model put together from several files are drawn. */
	public interface GroupRules {
		/** Whether a group is drawn, by its path (dots between the names) and its own name. */
		boolean drawn(String path, String name);
	}

	private static JsonObject projectOf(String text) throws NotAModel {
		String trimmed = text.stripLeading();
		if (trimmed.startsWith("<lz>")) throw new NotAModel(Why.COMPRESSED, "saved compressed");
		JsonElement parsed;
		try {
			parsed = JsonParser.parseString(trimmed);
		} catch (RuntimeException broken) {
			throw new NotAModel(Why.NOT_JSON, "not JSON inside");
		}
		if (!parsed.isJsonObject()) throw new NotAModel(Why.NOT_A_PROJECT, "not a Blockbench file");
		JsonObject project = parsed.getAsJsonObject();
		if (!(project.get("meta") instanceof JsonObject)) throw new NotAModel(Why.NO_META, "no meta section");
		return project;
	}

	/** Pale colours for a face with nothing to paint it with, by the element's colour number. */
	private static final int[] MARKERS = {
		0xA2EBFF, 0xFFF899, 0xE8BD7B, 0xFFA7A4, 0xC5A6E8, 0xA6C8FF, 0x7BFFA3, 0xBDFFA6, 0xFFA5D5, 0xE0E9FB,
	};

	public static BbModel parse(byte[] bytes) throws NotAModel {
		return parse(new String(bytes, StandardCharsets.UTF_8));
	}

	public static BbModel parse(String text) throws NotAModel {
		return new Reading(projectOf(text), null, "", "", 0, null, null).read();
	}

	// ------------------------------------------------------------------ helpers

	static float num(JsonElement value, float otherwise) {
		if (value instanceof JsonPrimitive primitive) {
			try {
				if (primitive.isNumber()) {
					double x = primitive.getAsDouble();
					return Double.isFinite(x) ? (float) x : otherwise;
				}
				if (primitive.isString()) {
					double x = Double.parseDouble(primitive.getAsString().trim());
					return Double.isFinite(x) ? (float) x : otherwise;
				}
				if (primitive.isBoolean()) return primitive.getAsBoolean() ? 1 : 0;
			} catch (NumberFormatException notANumber) {
				return otherwise;
			}
		}
		return otherwise;
	}

	static float num(JsonObject object, String key, float otherwise) {
		return object == null ? otherwise : num(object.get(key), otherwise);
	}

	static float[] triple(JsonElement value, float otherwise) {
		float[] out = { otherwise, otherwise, otherwise };
		if (value instanceof JsonArray list) {
			for (int i = 0; i < 3 && i < list.size(); i++) out[i] = num(list.get(i), otherwise);
		}
		return out;
	}

	static String text(JsonObject object, String key, String otherwise) {
		if (object == null) return otherwise;
		JsonElement value = object.get(key);
		if (value instanceof JsonPrimitive primitive && !primitive.isJsonNull()) return primitive.getAsString();
		return otherwise;
	}

	static boolean isFalse(JsonObject object, String key) {
		JsonElement value = object == null ? null : object.get(key);
		return value instanceof JsonPrimitive primitive && primitive.isBoolean() && !primitive.getAsBoolean();
	}

	static boolean isTrue(JsonObject object, String key) {
		JsonElement value = object == null ? null : object.get(key);
		return value instanceof JsonPrimitive primitive && primitive.isBoolean() && primitive.getAsBoolean();
	}

	static JsonObject object(JsonObject parent, String key) {
		JsonElement value = parent == null ? null : parent.get(key);
		return value instanceof JsonObject found ? found : null;
	}

	/** Whether version {@code a} is older than {@code b}, compared the way Blockbench compares them. */
	static boolean olderThan(String a, String b) {
		String[] x = a.split("\\.");
		String[] y = b.split("\\.");
		for (int i = 0; i < Math.max(x.length, y.length); i++) {
			double p = i < x.length ? versionPart(x[i]) : 0;
			double q = i < y.length ? versionPart(y[i]) : 0;
			if (p != q) return p < q;
		}
		return false;
	}

	private static double versionPart(String part) {
		try {
			return Double.parseDouble(part);
		} catch (NumberFormatException notANumber) {
			return 0;
		}
	}

	/**
	 * The pixel size of an embedded PNG, read from its header: the first 24 bytes
	 * hold the signature, the IHDR chunk name and the width and height.
	 */
	static int[] pngSize(byte[] bytes) {
		if (bytes == null || bytes.length < 24) return null;
		if (bytes[1] != 'P' || bytes[2] != 'N' || bytes[3] != 'G') return null;
		if (bytes[12] != 'I' || bytes[13] != 'H' || bytes[14] != 'D' || bytes[15] != 'R') return null;
		return new int[] { readInt(bytes, 16), readInt(bytes, 20) };
	}

	private static int readInt(byte[] bytes, int at) {
		return ((bytes[at] & 0xFF) << 24) | ((bytes[at + 1] & 0xFF) << 16)
			| ((bytes[at + 2] & 0xFF) << 8) | (bytes[at + 3] & 0xFF);
	}

	// ------------------------------------------------------------------ the reading

	/** One group of the outliner, placed. */
	private record Node(String uuid, JsonObject data, float[] origin, Node parent, Bone bone) { }

	private static final class Reading {
		private final JsonObject project;
		private final JsonObject meta;
		private final String format;
		private final String version;
		private final Traits traits;
		private final float resolutionWidth;
		private final float resolutionHeight;

		private final List<Texture> textures = new ArrayList<>();
		private final Map<String, Texture> textureByUuid = new HashMap<>();
		private final java.util.Set<Integer> markedDefault = new java.util.HashSet<>();
		private final Map<String, Node> groupOf = new HashMap<>();
		private final Map<String, Boolean> underExport = new HashMap<>();
		private final Map<String, Node> nodes = new LinkedHashMap<>();
		private final Map<String, JsonObject> groupFields = new HashMap<>();
		private final List<Bone> bones = new ArrayList<>();
		private Bone root;
		private Texture fallback;
		private int unnamed;

		/** For one file of several: the shared root this file's top level hangs from, else null. */
		private final Bone attachTo;
		private final String partName;
		private final String partPath;
		/** Where this file's textures start in the shared list. */
		private final int textureBase;
		private final GroupRules rules;
		private final java.util.function.Function<JsonObject, byte[]> textureFile;

		Reading(JsonObject project, Bone attachTo, String partName, String partPath, int textureBase, GroupRules rules,
				java.util.function.Function<JsonObject, byte[]> textureFile) {
			this.attachTo = attachTo;
			this.partName = partName;
			this.partPath = partPath;
			this.textureBase = textureBase;
			this.rules = rules;
			this.textureFile = textureFile;
			this.project = project;
			this.meta = project.getAsJsonObject("meta");
			String declared = text(meta, "model_format", "");
			this.format = !declared.isEmpty() ? declared
				: isTrue(meta, "bone_rig") ? "bedrock_old" : "java_block";
			String v = text(meta, "format_version", null);
			if (v == null) v = text(meta, "format", "1.0");
			this.version = v;
			this.traits = Traits.of(format);
			JsonObject resolution = object(project, "resolution");
			float w = num(resolution, "width", 0);
			float h = num(resolution, "height", 0);
			this.resolutionWidth = w > 0 ? w : 16;
			this.resolutionHeight = h > 0 ? h : 16;
		}

		BbModel read() throws NotAModel {
			readTextures();
			// One file of several: its top level is a group named after the file, drawn
			// or not by the same rules as any group.
			root = attachTo == null
				? new Bone("", "", null, new float[3], new float[3], new float[] { 1, 1, 1 }, true, false)
				: new Bone("file:" + partPath, partName, attachTo, new float[3], new float[3], new float[] { 1, 1, 1 },
					rules == null || rules.drawn(partPath, partName), true);

			if (project.get("groups") instanceof JsonArray groups) {
				for (JsonElement group : groups) {
					if (group instanceof JsonObject fields) {
						String uuid = text(fields, "uuid", null);
						if (uuid != null) groupFields.put(uuid, fields);
					}
				}
			}
			walk(project.get("outliner"), null, true);

			List<JsonObject> elements = new ArrayList<>();
			JsonElement list = project.has("elements") ? project.get("elements") : project.get("cubes");
			if (list instanceof JsonArray array) {
				for (JsonElement one : array) if (one instanceof JsonObject element) elements.add(element);
			}
			if (elements.isEmpty()) throw new NotAModel(Why.NOTHING_IN_IT, "no cubes and no meshes");

			// What is drawn: everything visible that the file says is part of the model.
			// A file where nothing is exported is drawn whole, as the site does: it is a
			// file somebody is part way through, and a model is better than nothing.
			List<JsonObject> visible = new ArrayList<>();
			for (JsonObject element : elements) if (!isFalse(element, "visibility")) visible.add(element);
			List<JsonObject> exported = new ArrayList<>();
			for (JsonObject element : visible) {
				String uuid = text(element, "uuid", "");
				if (!isFalse(element, "export") && !Boolean.FALSE.equals(underExport.get(uuid))) exported.add(element);
			}
			List<JsonObject> drawn = exported.isEmpty() ? visible : exported;

			fallback = pickFallback(drawn);

			for (JsonObject element : drawn) {
				Node parent = groupOf.get(text(element, "uuid", ""));
				String type = text(element, "type", "cube");
				if (type.equals("cube")) addCube(element, parent);
				else if (type.equals("mesh")) addMesh(element, parent);
			}

			Map<String, Bone> byName = new LinkedHashMap<>();
			for (Bone bone : bones) byName.putIfAbsent(bone.name(), bone);

			List<Animation> animations = readAnimations(byName);
			Map<String, Molang.Expression> placeholders = readPlaceholders();
			int cubes = 0;
			int meshes = 0;
			for (JsonObject element : elements) {
				String type = text(element, "type", "cube");
				if (type.equals("cube")) cubes++;
				else if (type.equals("mesh")) meshes++;
			}
			return new BbModel(text(project, "name", ""), format, version, traits, List.copyOf(textures),
				root, List.copyOf(bones), byName, List.copyOf(animations), placeholders, cubes, meshes);
		}

		// ------------------------------------------------------------ animations

		/**
		 * Every animation, with its tracks bound to bones. An animator names its bone
		 * by id, and by name for when the id has changed underneath it, which
		 * Blockbench also looks for. Animators of other kinds (effects, mesh armature
		 * bones, null objects) and channels other than rotation, position and scale are
		 * not drawn and are skipped.
		 */
		private List<Animation> readAnimations(Map<String, Bone> byName) {
			List<Animation> out = new ArrayList<>();
			if (!(project.get("animations") instanceof JsonArray array)) return out;
			Map<String, Bone> byUuid = new HashMap<>();
			for (Bone bone : bones) byUuid.put(bone.uuid(), bone);
			// Format 5.0 turned three axes round; older files keep the old signs.
			boolean old = olderThan(version, "5.0");
			for (JsonElement one : array) {
				if (!(one instanceof JsonObject animation)) continue;
				List<Animation.Track> tracks = new ArrayList<>();
				double latest = 0;
				JsonObject animators = object(animation, "animators");
				if (animators != null) {
					for (Map.Entry<String, JsonElement> entry : animators.entrySet()) {
						if (!(entry.getValue() instanceof JsonObject animator)) continue;
						if (!text(animator, "type", "bone").equals("bone")) continue;
						Bone bone = byUuid.get(entry.getKey());
						if (bone == null) bone = byName.get(text(animator, "name", ""));
						if (bone == null) continue;
						Map<Animation.Channel, List<Animation.Key>> keys = new EnumMap<>(Animation.Channel.class);
						if (animator.get("keyframes") instanceof JsonArray keyframes) {
							for (JsonElement k : keyframes) {
								if (!(k instanceof JsonObject keyframe)) continue;
								Animation.Channel channel = switch (text(keyframe, "channel", "")) {
									case "rotation" -> Animation.Channel.ROTATION;
									case "position" -> Animation.Channel.POSITION;
									case "scale" -> Animation.Channel.SCALE;
									default -> null;
								};
								if (channel == null) continue;
								Animation.Key key = keyOf(keyframe, channel, old);
								if (key == null) continue;
								keys.computeIfAbsent(channel, c -> new ArrayList<>()).add(key);
								latest = Math.max(latest, key.time());
							}
						}
						if (!keys.isEmpty()) {
							for (List<Animation.Key> list : keys.values()) {
								list.sort(Comparator.comparingDouble(Animation.Key::time));
							}
							tracks.add(new Animation.Track(bone, keys));
						}
					}
				}
				String loop = text(animation, "loop", "loop");
				Animation.Loop mode = loop.equals("once") ? Animation.Loop.ONCE
					: loop.equals("hold") ? Animation.Loop.HOLD : Animation.Loop.LOOP;
				float length = num(animation, "length", 0);
				out.add(new Animation(text(animation, "name", ""), text(animation, "uuid", ""),
					length > 0 ? length : (float) latest, mode, List.copyOf(tracks)));
			}
			return out;
		}

		private static Animation.Key keyOf(JsonObject keyframe, Animation.Channel channel, boolean old) {
			if (!(keyframe.get("data_points") instanceof JsonArray list)) return null;
			List<Molang.Expression[]> points = new ArrayList<>();
			float[] signs = !old ? new float[] { 1, 1, 1 }
				: channel == Animation.Channel.ROTATION ? new float[] { -1, -1, 1 }
				: channel == Animation.Channel.POSITION ? new float[] { -1, 1, 1 } : new float[] { 1, 1, 1 };
			String[] names = { "x", "y", "z" };
			// A missing scale component is one, anything else nought.
			double otherwise = channel == Animation.Channel.SCALE ? 1 : 0;
			for (JsonElement p : list) {
				if (!(p instanceof JsonObject point)) continue;
				Molang.Expression[] axes = new Molang.Expression[3];
				for (int i = 0; i < 3; i++) {
					JsonElement value = point.get(names[i]);
					Molang.Expression read;
					if (value instanceof JsonPrimitive primitive && primitive.isNumber()) {
						read = Molang.constant(primitive.getAsDouble());
					} else if (value instanceof JsonPrimitive primitive && primitive.isString()) {
						read = primitive.getAsString().isBlank() ? Molang.constant(otherwise) : Molang.compile(primitive.getAsString());
					} else {
						read = Molang.constant(otherwise);
					}
					axes[i] = signs[i] < 0 ? negated(read) : read;
				}
				points.add(axes);
			}
			if (points.isEmpty()) return null;
			// The Bezier handles' values turn round with the keys: the whole curve is
			// mirrored, not only its ends.
			double[] leftValue = doubles(keyframe.get("bezier_left_value"), 0);
			double[] rightValue = doubles(keyframe.get("bezier_right_value"), 0);
			for (int i = 0; i < 3; i++) {
				leftValue[i] *= signs[i];
				rightValue[i] *= signs[i];
			}
			return new Animation.Key(num(keyframe, "time", 0), text(keyframe, "interpolation", "linear"),
				points.toArray(new Molang.Expression[0][]),
				doubles(keyframe.get("bezier_left_time"), -0.1), leftValue,
				doubles(keyframe.get("bezier_right_time"), 0.1), rightValue);
		}

		private static Molang.Expression negated(Molang.Expression inner) {
			return new Molang.Expression() {
				@Override
				public double eval(Map<String, Double> values) {
					return -inner.eval(values);
				}

				@Override
				public boolean unreadable() {
					return inner.unreadable();
				}
			};
		}

		private static double[] doubles(JsonElement value, double otherwise) {
			float[] three = triple(value, (float) otherwise);
			return new double[] { three[0], three[1], three[2] };
		}

		/**
		 * The preview values an author set for Molang names the game would supply,
		 * such as how fast the entity walks. Only plain assignments are read; a slider
		 * or a toggle has no value until somebody moves it, so it stays at nought.
		 */
		private Map<String, Molang.Expression> readPlaceholders() {
			Map<String, Molang.Expression> out = new LinkedHashMap<>();
			String said = text(project, "animation_variable_placeholders", "");
			for (String line : said.split("\n")) {
				int comment = line.indexOf("//");
				String clean = (comment >= 0 ? line.substring(0, comment) : line).trim();
				int equals = clean.indexOf('=');
				if (equals <= 0) continue;
				String name = clean.substring(0, equals).trim();
				String value = clean.substring(equals + 1).trim();
				if (value.endsWith(";")) value = value.substring(0, value.length() - 1).trim();
				String lower = value.toLowerCase(java.util.Locale.ROOT);
				if (!PLACEHOLDER_NAME.matcher(name).matches() || lower.contains("slider(") || lower.contains("toggle(")) continue;
				out.put(Molang.fullName(name), Molang.compile(value));
			}
			return out;
		}

		private static final java.util.regex.Pattern PLACEHOLDER_NAME = java.util.regex.Pattern.compile("[A-Za-z_][A-Za-z0-9_.]*");

		// ------------------------------------------------------------ textures

		private void readTextures() {
			if (!(project.get("textures") instanceof JsonArray list)) return;
			int index = 0;
			for (JsonElement one : list) {
				if (!(one instanceof JsonObject data)) continue;
				String source = text(data, "source", "");
				byte[] image = null;
				String mime = null;
				if (!source.startsWith("data:") && textureFile != null) {
					// Not carried inside: the picture beside the file, where the avatar keeps it.
					image = textureFile.apply(data);
					if (image != null) mime = "image/png";
				}
				if (source.startsWith("data:")) {
					int comma = source.indexOf(',');
					int semicolon = source.indexOf(';');
					if (comma > 0) {
						mime = source.substring(5, semicolon > 0 && semicolon < comma ? semicolon : comma);
						try {
							image = Base64.getMimeDecoder().decode(source.substring(comma + 1));
						} catch (IllegalArgumentException broken) {
							image = null;
						}
					}
				}
				int width = (int) num(data, "width", 0);
				int height = (int) num(data, "height", 0);
				if (width <= 0 || height <= 0) {
					int[] size = pngSize(image);
					if (size != null) {
						width = size[0];
						height = size[1];
					} else {
						width = 0;
						height = 0;
					}
				}
				float uvWidth = resolutionWidth;
				float uvHeight = resolutionHeight;
				if (traits.perTextureUv()) {
					float w = num(data, "uv_width", 0);
					float h = num(data, "uv_height", 0);
					if (w > 0) uvWidth = w;
					if (h > 0) uvHeight = h;
				}
				int frames = 1;
				if (traits.animatedTextures() && width > 0 && height > 0 && uvHeight > 0) {
					double ratio = (double) width / height;
					if (ratio != uvWidth / uvHeight) {
						int counted = (int) Math.ceil(uvWidth / uvHeight / ratio - 0.05);
						frames = Math.max(counted, 1);
					}
				}
				String sides = text(data, "render_sides", "auto");
				boolean doubleSided = sides.equals("double") || (!sides.equals("front") && !traits.frontOnly());
				Texture texture = new Texture(textureBase + index, text(data, "uuid", ""), text(data, "name", ""), image, mime,
					width, height, uvWidth, uvHeight, frames, doubleSided);
				textures.add(texture);
				if (!texture.uuid().isEmpty()) textureByUuid.put(texture.uuid(), texture);
				if (isTrue(data, "use_as_default")) markedDefault.add(textureBase + index);
				index++;
			}
		}

		private Texture textureFrom(JsonElement value) {
			if (value instanceof JsonPrimitive primitive) {
				if (primitive.isNumber()) {
					int at = primitive.getAsInt();
					return at >= 0 && at < textures.size() ? textures.get(at) : null;
				}
				if (primitive.isString()) return textureByUuid.get(primitive.getAsString());
			}
			return null;
		}

		/**
		 * The one texture a single-texture format paints everything with: the one
		 * marked as default where the format honours that, else the one the faces
		 * name most often, else the first.
		 */
		private Texture pickFallback(List<JsonObject> drawn) {
			if (traits.singleTextureDefault()) {
				for (Texture texture : textures) {
					if (markedDefault.contains(texture.index())) return texture;
				}
			}
			if (textures.size() > 1) {
				Map<Texture, Integer> votes = new IdentityHashMap<>();
				for (JsonObject element : drawn) {
					JsonObject faces = object(element, "faces");
					if (faces == null) continue;
					for (Map.Entry<String, JsonElement> face : faces.entrySet()) {
						if (!(face.getValue() instanceof JsonObject data)) continue;
						Texture texture = textureFrom(data.get("texture"));
						if (texture != null) votes.merge(texture, 1, Integer::sum);
					}
				}
				Texture best = null;
				int most = 0;
				for (Texture texture : textures) {
					int count = votes.getOrDefault(texture, 0);
					if (count > most) {
						best = texture;
						most = count;
					}
				}
				if (best != null) return best;
			}
			return textures.isEmpty() ? null : textures.get(0);
		}

		/**
		 * The texture a face shows. Returns {@link #HIDDEN} for a face hidden on
		 * purpose ({@code "texture": null}) and null for a face with nothing to
		 * show, which is drawn in its marker colour.
		 */
		private Texture textureOf(JsonObject face, Node parent) {
			JsonElement value = face.get("texture");
			boolean hidden = value != null && value.isJsonNull();
			if (traits.perGroupTexture() && parent != null) {
				JsonElement groupTexture = parent.data().get("texture");
				if (groupTexture != null && !groupTexture.isJsonNull()) {
					Texture own = textureFrom(groupTexture);
					if (own != null) return own;
				}
			}
			Texture own = textureFrom(value);
			if (!hidden && (traits.singleTexture()
					|| (traits.singleTextureDefault() && (traits.perGroupTexture() || own == null)))) {
				return fallback;
			}
			if (hidden) return HIDDEN;
			return own;
		}

		private static final Texture HIDDEN = new Texture(-2, "", "", null, null, 0, 0, 16, 16, 1, false);

		// ------------------------------------------------------------ outliner

		private void walk(JsonElement list, Node parent, boolean exported) {
			if (!(list instanceof JsonArray array)) return;
			for (JsonElement item : array) {
				if (item instanceof JsonPrimitive primitive && primitive.isString()) {
					String uuid = primitive.getAsString();
					if (!groupOf.containsKey(uuid)) {
						groupOf.put(uuid, parent);
						underExport.put(uuid, exported);
					}
					continue;
				}
				if (!(item instanceof JsonObject group)) continue;
				// A group written without an id is still a group: Blockbench gives it one
				// when the file opens, and its contents hang from it by their place.
				String uuid = text(group, "uuid", null);
				if (uuid == null || uuid.isEmpty()) uuid = "#" + (++unnamed);
				// A tree that names the same group twice is a broken file; reading it once
				// keeps it from being a loop.
				if (nodes.containsKey(uuid)) continue;

				JsonObject data = group.deepCopy();
				JsonObject fields = groupFields.get(uuid);
				if (fields != null) for (Map.Entry<String, JsonElement> e : fields.entrySet()) data.add(e.getKey(), e.getValue());

				float[] origin = triple(data.get("origin"), 0);
				float[] rotation = triple(data.get("rotation"), 0);
				// Files before 3.2 kept a group's turn about Z the other way round.
				if (olderThan(version, "3.2")) rotation[2] = -rotation[2];
				float[] scale = data.get("scale") instanceof JsonArray ? triple(data.get("scale"), 1) : new float[] { 1, 1, 1 };
				boolean visible = !isFalse(data, "visibility");
				String path = (parent == null ? partPath : pathOf(parent)) + "." + text(data, "name", "");
				if (rules != null && !rules.drawn(path, text(data, "name", ""))) visible = false;
				Bone parentBone = parent == null ? root : parent.bone();
				Bone bone = new Bone(uuid, text(data, "name", ""), parentBone, origin,
					traits.boneRig() ? rotation : new float[3], scale, traits.boneRig() ? visible : true,
					traits.boneRig());
				bones.add(bone);
				Node node = new Node(uuid, data, origin, parent, bone);
				nodes.put(uuid, node);
				JsonElement children = group.has("children") ? group.get("children") : group.get("content");
				walk(children, node, exported && !isFalse(data, "export"));
			}
		}

		/** A group's path: the file's path and the names of the groups down to it, joined by dots. */
		private String pathOf(Node node) {
			String own = text(node.data(), "name", "");
			return (node.parent() == null ? partPath : pathOf(node.parent())) + "." + own;
		}

		/** The bone an element's faces go to: its group in rigged formats, else the root. */
		private Bone boneFor(Node parent) {
			return traits.boneRig() && parent != null ? parent.bone() : root;
		}

		/** Where an element sits in the space of the bone it hangs from. */
		private Matrix4f placing(JsonObject element, Node parent, float[] scale) {
			float[] position = triple(element.get("origin"), 0);
			if (traits.boneRig() && parent != null) {
				position[0] -= parent.origin()[0];
				position[1] -= parent.origin()[1];
				position[2] -= parent.origin()[2];
			}
			float[] rotation = triple(element.get("rotation"), 0);
			Matrix4f matrix = new Matrix4f().translate(position[0], position[1], position[2])
				.rotateZ((float) Math.toRadians(rotation[2]))
				.rotateY((float) Math.toRadians(rotation[1]))
				.rotateX((float) Math.toRadians(rotation[0]));
			if (scale != null) matrix.scale(scale[0], scale[1], scale[2]);
			return matrix;
		}

		/**
		 * The colour number of an element. A file that does not say gets one from the
		 * element's id, the same hash the site uses, so the colour agrees with the page.
		 */
		private int markerOf(JsonObject element) {
			JsonElement color = element.get("color");
			int index;
			if (color != null && !color.isJsonNull()) {
				index = (int) num(color, 0);
			} else {
				String key = text(element, "uuid", null);
				if (key == null) key = text(element, "name", "") + joined(element.get("from")) + joined(element.get("to"));
				int hash = 0x811C9DC5;
				for (int i = 0; i < key.length(); i++) hash = (hash ^ key.charAt(i)) * 16777619;
				index = (int) (Integer.toUnsignedLong(hash) % MARKERS.length);
			}
			return MARKERS[((index % MARKERS.length) + MARKERS.length) % MARKERS.length];
		}

		/** A list written the way JavaScript writes one into a string: values and commas. */
		private static String joined(JsonElement value) {
			if (!(value instanceof JsonArray list)) return value == null ? "undefined" : value.toString();
			StringBuilder out = new StringBuilder();
			for (int i = 0; i < list.size(); i++) {
				if (i > 0) out.append(',');
				JsonElement one = list.get(i);
				out.append(one.isJsonPrimitive() ? one.getAsString() : one.toString());
			}
			return out.toString();
		}

		// ------------------------------------------------------------ cubes

		private void addCube(JsonObject element, Node parent) {
			float[] from = triple(element.get("from"), 0);
			float[] to = triple(element.get("to"), 0);
			float[] origin = triple(element.get("origin"), 0);
			float inflate = num(element, "inflate", 0);
			float[] stretch = triple(element.get("stretch"), 1);
			float[] low = new float[3];
			float[] high = new float[3];
			for (int i = 0; i < 3; i++) {
				float half = (to[i] - from[i]) / 2;
				float middle = from[i] + half;
				low[i] = middle - (half + inflate) * stretch[i] - origin[i];
				high[i] = middle + (half + inflate) * stretch[i] - origin[i];
				if (low[i] == high[i]) high[i] += 0.001f;
			}

			float[] grow = null;
			if (traits.rotateCubes() && isTrue(element, "rescale")) {
				// Blockbench's "rescale": a cube turned by 22.5 degrees grows back the
				// width the turn took off it, the way the game's block models do.
				float[] rotation = triple(element.get("rotation"), 0);
				float[] g = new float[3];
				for (int i = 0; i < 3; i++) {
					float a = Math.abs(rotation[i]);
					if (a > 45) a = 90 - a;
					g[i] = (float) (1 / Math.cos(Math.toRadians(a)));
				}
				grow = new float[] { g[1] * g[2], g[0] * g[2], g[0] * g[1] };
			}
			Matrix4f matrix = placing(element, parent, grow);
			Matrix3f turn = new Matrix3f(matrix).invert().transpose();

			JsonObject faces = object(element, "faces");
			float[][] uvs = new float[6][4];
			for (int f = 0; f < 6; f++) {
				JsonObject face = object(faces, FACES[f]);
				JsonElement uv = face == null ? null : face.get("uv");
				if (uv instanceof JsonArray list) {
					for (int i = 0; i < 4 && i < list.size(); i++) uvs[f][i] = num(list.get(i), 0);
				}
			}

			boolean boxUv = element.has("box_uv") && element.get("box_uv").isJsonPrimitive()
				? element.get("box_uv").getAsBoolean()
				: isTrue(meta, "box_uv");
			if (boxUv) layOutBoxUv(element, from, to, faces, uvs);

			Bone bone = boneFor(parent);
			int marker = markerOf(element);
			Vector3f corner = new Vector3f();
			Vector3f normal = new Vector3f();
			for (int f = 0; f < 6; f++) {
				JsonObject face = object(faces, FACES[f]);
				if (face == null) face = new JsonObject();
				JsonElement value = face.get("texture");
				if (value != null && value.isJsonNull()) continue;
				Texture texture = textureOf(face, parent);
				if (texture == HIDDEN) continue;

				float pw = texture != null ? texture.uvWidth() : resolutionWidth;
				float ph = texture != null ? texture.uvHeight() : resolutionHeight;
				if (texture == null && traits.perTextureUv()) {
					pw = resolutionWidth;
					ph = resolutionHeight;
				}
				int frames = texture != null ? texture.frames() : 1;

				float[] uv = uvs[f].clone();
				if (boxUv) {
					// A sixty-fourth of a pixel in from every edge, so the next face over
					// on the texture does not bleed in along the seams.
					for (int i = 0; i < 2; i++) {
						float margin = uv[i] > uv[i + 2] ? -1f / 64 : 1f / 64;
						uv[i] += margin;
						uv[i + 2] -= margin;
					}
				}
				float[][] at = {
					{ uv[0] / pw, uv[1] / ph / frames },
					{ uv[2] / pw, uv[1] / ph / frames },
					{ uv[0] / pw, uv[3] / ph / frames },
					{ uv[2] / pw, uv[3] / ph / frames },
				};
				for (float rotation = num(face, "rotation", 0); rotation > 0; rotation -= 90) {
					at = new float[][] { at[2], at[0], at[3], at[1] };
				}

				turn.transform(normal.set(NORMALS[f][0], NORMALS[f][1], NORMALS[f][2]));
				if (normal.lengthSquared() > 0) normal.normalize();
				float[] positions = new float[12];
				float[] coords = new float[8];
				for (int k = 0; k < 4; k++) {
					int i = QUAD[k];
					int[] pick = CORNERS[f][i];
					corner.set(pick[0] == 1 ? high[0] : low[0], pick[1] == 1 ? high[1] : low[1],
						pick[2] == 1 ? high[2] : low[2]);
					matrix.transformPosition(corner);
					positions[k * 3] = corner.x;
					positions[k * 3 + 1] = corner.y;
					positions[k * 3 + 2] = corner.z;
					coords[k * 2] = at[i][0];
					coords[k * 2 + 1] = at[i][1];
				}
				boolean doubleSided = texture != null ? texture.doubleSided() : !traits.frontOnly();
				bone.add(new Quad(positions, coords, new float[] { normal.x, normal.y, normal.z },
					texture != null ? texture.index() : -1, marker, doubleSided));
			}
		}

		/**
		 * Box UV, laid out again from the cube's size the way Blockbench does rather
		 * than trusted from the faces: older files did not always write them down.
		 */
		private void layOutBoxUv(JsonObject element, float[] from, float[] to, JsonObject faces, float[][] uvs) {
			float[] size = new float[3];
			for (int i = 0; i < 3; i++) {
				float length = to[i] - from[i];
				size[i] = traits.boxUvFloat() ? length : (float) Math.floor(length + 0.0000001);
			}
			float w = size[0];
			float h = size[1];
			float d = size[2];
			// east, west, up, down, south, north: where each face starts and how big it is.
			float[][] start = { { 0, d }, { d + w, d }, { d + w, d }, { d + w * 2, 0 }, { d * 2 + w, d }, { d, d } };
			float[][] extent = { { d, h }, { d, h }, { -w, -d }, { -w, d }, { w, h }, { w, h } };
			// Before 4.5 a mirrored cube was written as one with shading turned off.
			boolean mirror = isTrue(element, "mirror_uv") || (olderThan(version, "4.5") && isFalse(element, "shade"));
			if (mirror) {
				for (int f = 0; f < 6; f++) {
					start[f][0] += extent[f][0];
					extent[f][0] *= -1;
				}
				float[] s = start[0];
				start[0] = start[1];
				start[1] = s;
				float[] e = extent[0];
				extent[0] = extent[1];
				extent[1] = e;
			}
			float[] offset = { 0, 0 };
			if (element.get("uv_offset") instanceof JsonArray list) {
				if (list.size() > 0) offset[0] = num(list.get(0), 0);
				if (list.size() > 1) offset[1] = num(list.get(1), 0);
			}
			for (int f = 0; f < 6; f++) {
				JsonObject face = object(faces, FACES[f]);
				if (face != null && face.has("texture") && face.get("texture").isJsonNull()) continue;
				uvs[f] = new float[] {
					start[f][0] + offset[0], start[f][1] + offset[1],
					start[f][0] + extent[f][0] + offset[0], start[f][1] + extent[f][1] + offset[1],
				};
			}
		}

		// ------------------------------------------------------------ meshes

		private void addMesh(JsonObject element, Node parent) {
			JsonObject vertices = object(element, "vertices");
			JsonObject faces = object(element, "faces");
			if (vertices == null || faces == null) return;
			Matrix4f matrix = placing(element, parent, null);
			Matrix3f turn = new Matrix3f(matrix).invert().transpose();
			Map<String, Vector3f> points = new HashMap<>();
			for (Map.Entry<String, JsonElement> entry : vertices.entrySet()) {
				if (entry.getValue() instanceof JsonArray) {
					float[] p = triple(entry.getValue(), 0);
					points.put(entry.getKey(), new Vector3f(p[0], p[1], p[2]));
				}
			}

			Bone bone = boneFor(parent);
			int marker = markerOf(element);
			for (Map.Entry<String, JsonElement> entry : faces.entrySet()) {
				if (!(entry.getValue() instanceof JsonObject face)) continue;
				if (!(face.get("vertices") instanceof JsonArray list)) continue;
				List<String> keys = new ArrayList<>();
				for (JsonElement key : list) keys.add(key.getAsString());
				if (keys.size() < 3 || keys.size() > 4) continue;
				boolean known = true;
				for (String key : keys) known &= points.containsKey(key);
				if (!known) continue;

				// A face set to no texture is not drawn, as in Blockbench. (The site's
				// viewer draws such mesh faces in their marker colour; cubes it skips.)
				Texture shown = textureOf(face, parent);
				if (shown == HIDDEN) continue;
				// A single-texture format paints the whole mesh with its one texture,
				// whatever each face says; the UVs still follow the face's own.
				Texture texture = traits.singleTexture() ? fallback : shown;
				float uw = shown != null ? shown.uvWidth() : resolutionWidth;
				float uh = shown != null ? shown.uvHeight() : resolutionHeight;
				int frames = shown != null ? shown.frames() : 1;

				List<String> order = sorted(keys, points);
				Vector3f normal = new Vector3f(points.get(order.get(1))).sub(points.get(order.get(0)));
				Vector3f side = new Vector3f(points.get(order.get(2))).sub(points.get(order.get(0)));
				normal.cross(side);
				if (normal.lengthSquared() > 0) normal.normalize();

				JsonObject uvMap = object(face, "uv");
				float[][] corners = new float[keys.size()][];
				float[] first = null;
				for (int i = 0; i < keys.size(); i++) {
					float[] uv = { 0, 0 };
					if (uvMap != null && uvMap.get(keys.get(i)) instanceof JsonArray pair) {
						if (pair.size() > 0) uv[0] = num(pair.get(0), 0);
						if (pair.size() > 1) uv[1] = num(pair.get(1), 0);
					}
					float u = uv[0] / uw;
					float v = uv[1] / uh / frames;
					// Blockbench's guard against a face whose UVs all sit on one point,
					// which otherwise samples as noise.
					if (first == null) {
						first = new float[] { u, v };
					} else if (first[0] == u && first[1] == v) {
						if (i < 2) u += 0.00005f;
						else v += 0.00005f;
					}
					corners[i] = new float[] { u, v };
				}

				int[] drawOrder;
				if (keys.size() == 3) {
					drawOrder = new int[] { 0, 1, 2, 2 };
				} else {
					drawOrder = new int[4];
					for (int k = 0; k < 4; k++) drawOrder[k] = keys.indexOf(order.get(k));
				}
				Vector3f worldNormal = turn.transform(new Vector3f(normal));
				if (worldNormal.lengthSquared() > 0) worldNormal.normalize();
				float[] positions = new float[12];
				float[] coords = new float[8];
				Vector3f point = new Vector3f();
				for (int k = 0; k < 4; k++) {
					int i = drawOrder[k];
					matrix.transformPosition(point.set(points.get(keys.get(i))));
					positions[k * 3] = point.x;
					positions[k * 3 + 1] = point.y;
					positions[k * 3 + 2] = point.z;
					coords[k * 2] = corners[i][0];
					coords[k * 2 + 1] = corners[i][1];
				}
				boolean doubleSided = texture != null ? texture.doubleSided() : !traits.frontOnly();
				bone.add(new Quad(positions, coords, new float[] { worldNormal.x, worldNormal.y, worldNormal.z },
					texture != null ? texture.index() : -1, marker, doubleSided));
			}
		}

		/**
		 * A quad's corners in the order that splits it into two triangles without
		 * folding. The file keeps them in whatever order they were made in, which
		 * for a quad dragged out of shape is often a bow tie.
		 */
		private static List<String> sorted(List<String> keys, Map<String, Vector3f> at) {
			if (keys.size() < 4) return keys;
			String a = keys.get(0);
			String b = keys.get(1);
			String c = keys.get(2);
			String d = keys.get(3);
			if (across(at.get(b), at.get(c), at.get(a), at.get(d))) return List.of(c, a, b, d);
			if (across(at.get(a), at.get(b), at.get(c), at.get(d))) return List.of(a, c, b, d);
			return keys;
		}

		/**
		 * Whether {@code check} lies on the far side of the line through
		 * {@code base1} and {@code base2} from {@code top}: Blockbench's test for
		 * which way round a quad's corners go.
		 */
		private static boolean across(Vector3f base1, Vector3f base2, Vector3f top, Vector3f check) {
			Vector3f line = new Vector3f(base2).sub(base1);
			float length = line.lengthSquared();
			float t = length == 0 ? 0 : new Vector3f(top).sub(base1).dot(line) / length;
			Vector3f closest = new Vector3f(base1).add(line.mul(t));
			Vector3f normal = closest.sub(top);
			return normal.dot(new Vector3f(check).sub(base2)) > 0;
		}
	}
}

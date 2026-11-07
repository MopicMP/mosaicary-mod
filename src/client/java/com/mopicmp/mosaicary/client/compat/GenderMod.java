package com.mopicmp.mosaicary.client.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

import java.lang.reflect.Constructor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mopicmp.mosaicary.Mosaicary;
import com.mopicmp.mosaicary.wardrobe.Body;
import com.mopicmp.mosaicary.wardrobe.Wardrobe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;

/**
 * Female Gender Mod's body kept with each skin of "My skins" ({@link Body}). When a skin is put
 * on, the body the player has now is kept with the skin worn until then, and the body kept with
 * the new one, if it has one, is put on; a skin with none leaves the body as it is. Put on through
 * the mod's own setters and saved by its own code, which then sends it to the server and to its
 * cloud as after a change in its own screens.
 *
 * In the menus the mod has no settings for the player yet, so a skin put on there could not have
 * the body of the one taken off read: in a world the body of the skin worn is kept every few
 * seconds as well, which is no change at all while the body stays the same.
 *
 * Reached by reflection, so the mod is no dependency. Two shapes of it are known. From 1.18.2 to
 * 1.21.11 (its lines 2.9 to the 5.0 beta) the player's settings are an object with a getter and
 * an {@code updateX} setter for each, found by {@code WildfireGender.getPlayerById}; from 5.0 on
 * 26.3 each setting is a value with {@code get} and {@code update}, reached through
 * {@code WildfireClientAPI}. The line for 1.16.5 knows players by name and is neither; there, as
 * without the mod, nothing is done.
 */
public final class GenderMod {

	private GenderMod() { }

	/**
	 * Whether this build works with Female Gender Mod at all: where a line of it works with this
	 * version of the game and is compiled against (see build.gradle.kts).
	 */
	public static final boolean OFFERED =
		//#if GENDER>=1
		true;
		//#else
		//$$ false;
		//#endif

	/**
	 * One setting: its name in a body, where it lives in the older shape (on the player or on its
	 * breasts), its getters there (the first found is used: up to 1.20.1 the bounce getter scales,
	 * its raw one does not) and setter, and the path of methods to its value in 5.0.
	 */
	private record Setting(String name, boolean onBreasts, String[] getters, String setter, String[] path, boolean optional) { }

	private static final Setting[] SETTINGS = {
		new Setting(Body.GENDER, false, new String[] { "getGender" }, "updateGender", new String[] { "gender" }, false),
		new Setting("bust_size", false, new String[] { "getBustSize" }, "updateBustSize", new String[] { "breasts", "bustSize" }, false),
		new Setting("breasts_xOffset", true, new String[] { "getXOffset" }, "updateXOffset", new String[] { "breasts", "xOffset" }, false),
		new Setting("breasts_yOffset", true, new String[] { "getYOffset" }, "updateYOffset", new String[] { "breasts", "yOffset" }, false),
		new Setting("breasts_zOffset", true, new String[] { "getZOffset" }, "updateZOffset", new String[] { "breasts", "zOffset" }, false),
		new Setting("breasts_cleavage", true, new String[] { "getCleavage" }, "updateCleavage", new String[] { "breasts", "cleavage" }, false),
		new Setting("breasts_uniboob", true, new String[] { "isUniboob" }, "updateUniboob", new String[] { "breasts", "physics", "uniboob" }, false),
		new Setting("breast_physics", false, new String[] { "hasBreastPhysics" }, "updateBreastPhysics", new String[] { "breasts", "physics", "enabled" }, false),
		new Setting("bounce_multiplier", false, new String[] { "getBounceMultiplierRaw", "getBounceMultiplier" }, "updateBounceMultiplier",
			new String[] { "breasts", "physics", "bounceMultiplier" }, false),
		new Setting("floppy_multiplier", false, new String[] { "getFloppiness" }, "updateFloppiness", new String[] { "breasts", "physics", "floppiness" }, false),
		// Only from 1.21.11 on.
		new Setting("voice_pitch", false, new String[] { "getVoicePitch" }, "updateVoicePitch", new String[] { "sounds", "voicePitch" }, true),
	};

	/**
	 * The usual ranges of the lines from 1.21.1 on where they are narrower than up to 1.20.1 (Body has the
	 * widest): what a refused number is held to.
	 */
	private static final Map<String, float[]> NARROWER = Map.of(
		"bust_size", new float[] { 0, 0.8f },
		"bounce_multiplier", new float[] { 0, 0.5f },
		"floppy_multiplier", new float[] { 0.25f, 1 });

	private enum Shape { NONE, SETTERS, VALUES }

	private static Shape shape;
	private static boolean wide;
	/** The older shape: the lookup of a player, and how its settings are saved. */
	private static Method byId;
	private static Method save;
	private static boolean saveIsStatic;
	/** 5.0: the cache of players. */
	private static Object players;
	private static Method cacheGet;
	private static Method valueGet;
	private static Method valueUpdate;

	/**
	 * A body to put on as soon as the player's settings are there: in the menus they may not be yet.
	 * It waits until then, or until another skin is put on, kept in the settings meanwhile
	 * ({@code Settings.genderWaiting}) so that closing the game does not lose it.
	 */
	private static JsonObject pending;
	/** Whether a body left waiting when the game was closed has been read back. */
	private static boolean pendingRead;

	/** How long a body waits for the mod to finish reading the player's settings, in ticks, before it is put on all the same. */
	private static final int SETTLE_TICKS = 100;
	private static int unsettled;

	/** How often the body of the skin worn is kept while the player's settings are there. */
	private static final int KEEP_EVERY_TICKS = 100;
	private static int ticks;

	/** Whether Female Gender Mod is there in a shape this knows; looked up once. */
	public static boolean available() {
		if (shape == null) {
			shape = Shape.NONE;
			try {
				Class<?> api = Class.forName("com.wildfire.api.client.WildfireClientAPI");
				players = api.getMethod("players").invoke(null);
				cacheGet = Class.forName("com.wildfire.api.EntityCache").getMethod("get", UUID.class);
				Class<?> value = Class.forName("com.wildfire.common.config.value.ConfigValue");
				valueGet = java.util.function.Supplier.class.getMethod("get");
				valueUpdate = value.getMethod("update", Object.class);
				shape = Shape.VALUES;
			} catch (ClassNotFoundException notFive) {
				try {
					byId = Class.forName("com.wildfire.main.WildfireGender").getMethod("getPlayerById", UUID.class);
					Class<?> player = byId.getReturnType();
					wide = player.getName().equals("com.wildfire.main.GenderPlayer");
					try {
						save = player.getMethod("save");
						saveIsStatic = false;
					} catch (NoSuchMethodException older) {
						save = player.getMethod("saveGenderInfo", player);
						saveIsStatic = true;
					}
					shape = Shape.SETTERS;
				} catch (ClassNotFoundException absent) {
					shape = Shape.NONE;
				} catch (ReflectiveOperationException | RuntimeException | LinkageError other) {
					unknown(other);
				}
			} catch (ReflectiveOperationException | RuntimeException | LinkageError other) {
				unknown(other);
			}
		}
		return shape != Shape.NONE;
	}

	private static void unknown(Throwable why) {
		Mosaicary.LOGGER.warn("Female Gender Mod is installed, but not with the API the wardrobe knows; skins keep no body: {}", why.toString());
		shape = Shape.NONE;
	}

	/**
	 * A skin is being put on: keeps the body the player has now with the skin worn until now, the
	 * one worn last in the wardrobe, and puts on the body kept with the new one. Called before the
	 * wardrobe records the new skin as worn.
	 */
	public static void wearing(Wardrobe wardrobe, Wardrobe.Entry entry, UUID me) {
		if (!available()) return;
		Wardrobe.Entry current = worn(wardrobe);
		// A body still waiting belongs to the skin worn now: kept with it, not the one in the mod.
		JsonObject now = waiting() != null ? pending : read(me);
		if (current != null && now != null) wardrobe.bodyOf(current.key, now, System.currentTimeMillis());
		if (current != null && current.key.equals(entry.key)) return;
		Wardrobe.Entry next = wardrobe.get(entry.key);
		if (next == null || next.body == null) return;
		waitWith(Body.copy(next.body));
		putPending(me);
	}

	/**
	 * The player's body changed in the wardrobe's own card: put on at once where the mod has the
	 * player's settings, and saved by its own code as after a change in its own screens; in the
	 * menus, where it has none yet, it waits until it has, across a restart too.
	 */
	public static void change(UUID me, JsonObject body) {
		if (!available() || me == null || body == null) return;
		if (waiting() == null && settled(me) && put(me, body)) return;
		waitWith(Body.copy(body));
	}

	/** The body waiting to be put on, or null; one left waiting when the game was closed is read back the first time. */
	private static JsonObject waiting() {
		if (!pendingRead) {
			pendingRead = true;
			JsonObject left = com.mopicmp.mosaicary.client.MosaicaryClient.instance().settings().genderWaiting;
			if (pending == null) pending = Body.clean(left);
		}
		return pending;
	}

	/** Leaves a body waiting to be put on, or none with null, here and in the settings. */
	private static void waitWith(JsonObject body) {
		pending = body;
		pendingRead = true;
		com.mopicmp.mosaicary.client.MosaicaryClient client = com.mopicmp.mosaicary.client.MosaicaryClient.instance();
		client.settings().genderWaiting = Body.copy(body);
		client.saveSettings();
	}

	/**
	 * Called every client tick: puts on a body waiting for the player's settings to be there, and
	 * now and then keeps the body of the skin worn.
	 */
	public static void tick(Wardrobe wardrobe, UUID me) {
		if (shape == Shape.NONE) return;
		if (waiting() != null) {
			putPending(me);
			return;
		}
		if (++ticks % KEEP_EVERY_TICKS != 0 || !available()) return;
		Wardrobe.Entry current = worn(wardrobe);
		if (current == null) return;
		JsonObject now = read(me);
		if (now != null) wardrobe.bodyOf(current.key, now, System.currentTimeMillis());
	}

	private static void putPending(UUID me) {
		if (settled(me) && put(me, pending)) {
			Mosaicary.LOGGER.info("Female Gender Mod: put on the body that was waiting");
			waitWith(null);
		}
	}

	/**
	 * Whether the mod has the player's settings and has finished reading them, from its file or its
	 * cloud, which it does while a world loads: a body put on before that would be read over. Every
	 * line marks the player {@code UNKNOWN} until then; one with nothing to read stays so, and is
	 * taken as read after {@link #SETTLE_TICKS} asked.
	 */
	private static boolean settled(UUID me) {
		try {
			Object player = player(me);
			if (player == null) {
				unsettled = 0;
				return false;
			}
			Object status = player.getClass().getField("syncStatus").get(player);
			if (status != null && !"UNKNOWN".equals(status.toString())) return true;
			return ++unsettled > SETTLE_TICKS;
		} catch (ReflectiveOperationException | RuntimeException unmarked) {
			// A line without the mark, or one it cannot be read from: nothing to wait for.
			return true;
		}
	}

	/**
	 * Whether a skin of "My skins" is the one worn now: its body is the one the mod has, which is kept
	 * with it every few seconds, so a change to it goes into the mod as well.
	 */
	public static boolean isWorn(Wardrobe wardrobe, Wardrobe.Entry entry) {
		Wardrobe.Entry current = worn(wardrobe);
		return current != null && entry != null && current.key.equals(entry.key);
	}

	/** The skin worn now as far as the wardrobe knows: the one put on last; null before any. */
	public static Wardrobe.Entry worn(Wardrobe wardrobe) {
		Wardrobe.Entry current = null;
		for (Wardrobe.Entry kept : wardrobe.shown()) {
			if (kept.worn > 0 && (current == null || kept.worn > current.worn)) current = kept;
		}
		return current;
	}

	/** The usual range of a number of the body on this line of the mod, what its own sliders go between; null for no number. */
	public static float[] range(String name) {
		if (!available()) return null;
		float[] range = !wide && NARROWER.containsKey(name) ? NARROWER.get(name) : Body.NUMBERS.get(name);
		return range == null ? null : range.clone();
	}

	/**
	 * Whether this line of the mod has a setting of the body: all but the voice's pitch, which comes
	 * with 1.21.11; looked up on the classes, as in the menus there is no player to ask.
	 */
	public static boolean has(String name) {
		Setting setting = setting(name);
		if (setting == null || !available()) return false;
		if (!setting.optional()) return true;
		try {
			if (shape == Shape.VALUES) {
				Class<?> at = Class.forName("com.wildfire.common.entities.players.PlayerConfigHolder");
				for (String step : setting.path()) at = at.getMethod(step).getReturnType();
				return true;
			}
			Class<?> owner = byId.getReturnType();
			if (setting.onBreasts()) owner = owner.getMethod("getBreasts").getReturnType();
			for (String getter : setting.getters()) {
				try {
					owner.getMethod(getter);
					return true;
				} catch (NoSuchMethodException next) {
					// The next name.
				}
			}
			return false;
		} catch (ReflectiveOperationException | LinkageError none) {
			return false;
		}
	}

	/** The body the player has now, or null without the mod or before its settings are there. */
	public static JsonObject read(UUID me) {
		if (!available()) return null;
		try {
			Object player = player(me);
			if (player == null) return null;
			JsonObject out = new JsonObject();
			for (Setting setting : SETTINGS) {
				Object value = get(player, setting);
				if (value instanceof Enum<?> gender) out.addProperty(setting.name(), gender.name().toLowerCase(java.util.Locale.ROOT));
				else if (value instanceof Number number) out.addProperty(setting.name(), number);
				else if (value instanceof Boolean flag) out.addProperty(setting.name(), flag);
			}
			return Body.clean(out);
		} catch (ReflectiveOperationException | RuntimeException failed) {
			Mosaicary.LOGGER.warn("Female Gender Mod: could not read the player's body: {}", failed.toString());
			return null;
		}
	}

	/** Puts a body on the player and saves it; false while the player's settings are not there yet. */
	public static boolean put(UUID me, JsonObject body) {
		if (!available()) return true;
		try {
			Object player = player(me);
			if (player == null) return false;
			for (Map.Entry<String, JsonElement> member : body.entrySet()) {
				Setting setting = setting(member.getKey());
				if (setting != null) set(player, setting, member.getValue());
			}
			if (shape == Shape.VALUES) player.getClass().getMethod("save").invoke(player);
			else if (saveIsStatic) save.invoke(null, player);
			else save.invoke(player);
			// As after a change in the mod's own screens: sent to the server on its next tick.
			try {
				Field needsSync = player.getClass().getField("needsSync");
				needsSync.setBoolean(player, true);
			} catch (NoSuchFieldException none) {
				// The line saves and sends in one.
			}
			return true;
		} catch (ReflectiveOperationException | RuntimeException failed) {
			Mosaicary.LOGGER.warn("Female Gender Mod: could not put the skin's body on: {}", failed.toString());
			return true;
		}
	}

	private static Object player(UUID me) throws ReflectiveOperationException {
		return shape == Shape.VALUES ? cacheGet.invoke(players, me) : byId.invoke(null, me);
	}

	private static Setting setting(String name) {
		for (Setting setting : SETTINGS) if (setting.name().equals(name)) return setting;
		return null;
	}

	private static Object get(Object player, Setting setting) throws ReflectiveOperationException {
		if (shape == Shape.VALUES) {
			Object value = value(player, setting);
			return value == null ? null : valueGet.invoke(value);
		}
		Object owner = setting.onBreasts() ? player.getClass().getMethod("getBreasts").invoke(player) : player;
		for (String getter : setting.getters()) {
			try {
				return owner.getClass().getMethod(getter).invoke(owner);
			} catch (NoSuchMethodException next) {
				// The next name.
			}
		}
		if (setting.optional()) return null;
		throw new NoSuchMethodException(setting.getters()[0]);
	}

	/** 5.0: the value a setting is, along its path from the player. */
	private static Object value(Object player, Setting setting) throws ReflectiveOperationException {
		Object at = player;
		for (String step : setting.path()) {
			try {
				at = at.getClass().getMethod(step).invoke(at);
			} catch (NoSuchMethodException missing) {
				if (setting.optional()) return null;
				throw missing;
			}
			if (at == null) return null;
		}
		return at;
	}

	/**
	 * Gives a setting its value: as it is first, as a line with its limits lifted takes it; a line
	 * that refuses it (its setters answer false past their limits) is given it held to its usual range.
	 */
	private static void set(Object player, Setting setting, JsonElement raw) throws ReflectiveOperationException {
		if (shape == Shape.VALUES) {
			Object value = value(player, setting);
			if (value == null) return;
			Object current = valueGet.invoke(value);
			Class<?> type = current == null ? null : current.getClass();
			Object wanted = converted(type, setting.name(), raw, false);
			if (wanted != null && Boolean.FALSE.equals(valueUpdate.invoke(value, wanted))) {
				Object held = converted(type, setting.name(), raw, true);
				if (!held.equals(wanted)) valueUpdate.invoke(value, held);
			}
			return;
		}
		Object owner = setting.onBreasts() ? player.getClass().getMethod("getBreasts").invoke(player) : player;
		for (Method method : owner.getClass().getMethods()) {
			if (!method.getName().equals(setting.setter()) || method.getParameterCount() != 1) continue;
			Class<?> type = method.getParameterTypes()[0];
			Object wanted = converted(type, setting.name(), raw, false);
			if (wanted != null && Boolean.FALSE.equals(method.invoke(owner, wanted))) {
				Object held = converted(type, setting.name(), raw, true);
				if (!held.equals(wanted)) method.invoke(owner, held);
			}
			return;
		}
		if (!setting.optional()) throw new NoSuchMethodException(setting.setter());
	}

	/** A body's value as the mod's own type: its gender enum, a float (held to the line's usual range if asked), or a boolean. */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static Object converted(Class<?> type, String name, JsonElement raw, boolean held) {
		if (type == null || raw == null || !raw.isJsonPrimitive()) return null;
		if (type.isEnum()) {
			try {
				return Enum.valueOf((Class<? extends Enum>) type, raw.getAsString().toUpperCase(java.util.Locale.ROOT));
			} catch (IllegalArgumentException unknown) {
				return null;
			}
		}
		if (type == float.class || type == Float.class) {
			if (!raw.getAsJsonPrimitive().isNumber()) return null;
			float value = raw.getAsFloat();
			float[] range = !wide && NARROWER.containsKey(name) ? NARROWER.get(name) : Body.NUMBERS.get(name);
			return !held || range == null ? value : Math.max(range[0], Math.min(range[1], value));
		}
		if (type == boolean.class || type == Boolean.class) {
			return raw.getAsJsonPrimitive().isBoolean() ? raw.getAsBoolean() : null;
		}
		return null;
	}

	/**
	 * The player's body as the mod has it, in the menus too: from its settings for the player in a
	 * world, before that from the file it saves them to. The mod's own store of players is left
	 * alone in the menus: a player it finds there on joining a world is not sent to the server. A body
	 * waiting to be put on comes first: it is what the player will have. Null without the mod or
	 * without a saved body.
	 */
	public static JsonObject current(UUID me) {
		if (available() && waiting() != null) return pending;
		JsonObject now = read(me);
		if (now != null || !available() || me == null) return now;
		return saved(me).body();
	}

	/**
	 * Where the faces of the player's breasts are read from the skin, as the mod has them saved
	 * (its UV editor changes them from 1.21.11 on): the breasts, left and right, then their outer
	 * layer, left and right, five quads each in GenderBreasts' order; null where the file has none.
	 */
	public static int[][][] layouts(UUID me) {
		return !available() || me == null ? null : saved(me).layouts();
	}

	/** What is read from the mod's file for the player: the body and the layouts, either null. */
	private record Saved(JsonObject body, int[][][] layouts) { }

	private static UUID savedFor;
	private static long savedAt;
	private static Saved saved = new Saved(null, null);
	/** How long what is read from the file is taken as it is: it is asked for on every frame. */
	private static final long SAVED_FOR_MILLIS = 2000;

	private static Saved saved(UUID me) {
		long time = System.currentTimeMillis();
		if (me.equals(savedFor) && time - savedAt < SAVED_FOR_MILLIS) return saved;
		savedFor = me;
		savedAt = time;
		saved = readSaved(me);
		return saved;
	}

	/**
	 * The file the mod saves the player's settings to: in "FemaleGenderMod" from 1.21.11 on, nested
	 * from 5.0 on, and in "WildfireGender" before, with the names a body has.
	 */
	private static Saved readSaved(UUID me) {
		for (String folder : new String[] { "FemaleGenderMod", "WildfireGender" }) {
			java.nio.file.Path file = com.mopicmp.mosaicary.platform.Platform.configDir().resolve(folder).resolve(me + ".json");
			if (!java.nio.file.Files.isRegularFile(file)) continue;
			try {
				String text = new String(java.nio.file.Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8);
				//#if MC>=11700
				JsonElement read = com.google.gson.JsonParser.parseString(text);
				//#else
				//$$ JsonElement read = new com.google.gson.JsonParser().parse(text);
				//#endif
				if (!read.isJsonObject()) return new Saved(null, null);
				return new Saved(Body.clean(flat(read.getAsJsonObject())), layouts(read.getAsJsonObject()));
			} catch (java.io.IOException | RuntimeException unreadable) {
				return new Saved(null, null);
			}
		}
		return new Saved(null, null);
	}

	/** The faces of a breast box in GenderBreasts' order, by the names the mod gives them. */
	private static final String[] FACES = { "east", "west", "down", "up", "north" };

	/** The layouts in the file: 5.0's under "uvs", as four numbers each; 1.21.11's by name, as x1, y1, x2, y2. */
	private static int[][][] layouts(JsonObject in) {
		int[][][] out = new int[4][][];
		if (in.has("uvs") && in.get("uvs").isJsonObject()) {
			JsonObject uvs = in.getAsJsonObject("uvs");
			String[][] where = { { "skin", "left" }, { "skin", "right" }, { "overlay", "left" }, { "overlay", "right" } };
			for (int i = 0; i < 4; i++) {
				JsonObject layer = child(child(uvs, where[i][0]), where[i][1]);
				out[i] = layout(layer);
			}
		} else {
			String[] names = { "leftBreastUVLayout", "rightBreastUVLayout", "leftBreastOverlayUVLayout", "rightBreastOverlayUVLayout" };
			for (int i = 0; i < 4; i++) out[i] = layout(child(in, names[i]));
		}
		for (int[][] one : out) if (one == null) return null;
		return out;
	}

	private static JsonObject child(JsonObject in, String name) {
		return in != null && in.has(name) && in.get(name).isJsonObject() ? in.getAsJsonObject(name) : null;
	}

	private static int[][] layout(JsonObject faces) {
		if (faces == null) return null;
		int[][] out = new int[FACES.length][];
		for (int i = 0; i < FACES.length; i++) {
			JsonElement face = faces.get(FACES[i]);
			if (face == null) return null;
			if (face.isJsonArray() && face.getAsJsonArray().size() == 4) {
				com.google.gson.JsonArray four = face.getAsJsonArray();
				out[i] = new int[] { four.get(0).getAsInt(), four.get(1).getAsInt(), four.get(2).getAsInt(), four.get(3).getAsInt() };
			} else if (face.isJsonObject()) {
				JsonObject named = face.getAsJsonObject();
				out[i] = new int[] { named.get("x1").getAsInt(), named.get("y1").getAsInt(), named.get("x2").getAsInt(), named.get("y2").getAsInt() };
			} else {
				return null;
			}
		}
		return out;
	}

	/**
	 * 5.0's nested settings by the names the older lines save them with, which a body has; older ones
	 * as they are, but for a gender saved by its number (1.21.11), which is the place in female, male, other.
	 */
	private static JsonObject flat(JsonObject in) {
		if (!in.has("breasts") || !in.get("breasts").isJsonObject()) {
			JsonElement gender = in.get(Body.GENDER);
			if (gender != null && gender.isJsonPrimitive() && gender.getAsJsonPrimitive().isNumber()) {
				int at = gender.getAsInt();
				JsonObject named = Body.copy(in);
				if (at >= 0 && at < Body.GENDERS.length) named.addProperty(Body.GENDER, Body.GENDERS[at]);
				return named;
			}
			return in;
		}
		JsonObject out = new JsonObject();
		if (in.has(Body.GENDER)) out.add(Body.GENDER, in.get(Body.GENDER));
		JsonObject breasts = in.getAsJsonObject("breasts");
		if (breasts.has("size")) out.add("bust_size", breasts.get("size"));
		if (breasts.has("cleavage")) out.add("breasts_cleavage", breasts.get("cleavage"));
		if (breasts.has("offset") && breasts.get("offset").isJsonArray() && breasts.getAsJsonArray("offset").size() == 3) {
			com.google.gson.JsonArray offset = breasts.getAsJsonArray("offset");
			out.add("breasts_xOffset", offset.get(0));
			out.add("breasts_yOffset", offset.get(1));
			out.add("breasts_zOffset", offset.get(2));
		}
		if (breasts.has("physics") && breasts.get("physics").isJsonObject()) {
			JsonObject physics = breasts.getAsJsonObject("physics");
			if (physics.has("enabled")) out.add("breast_physics", physics.get("enabled"));
			if (physics.has("uniboob")) out.add("breasts_uniboob", physics.get("uniboob"));
			if (physics.has("bounce_multiplier")) out.add("bounce_multiplier", physics.get("bounce_multiplier"));
			if (physics.has("floppiness")) out.add("floppy_multiplier", physics.get("floppiness"));
		}
		return out;
	}

	/** Whether Female Gender Mod is installed at all, known to this or not. */
	public static boolean installed() {
		if (available()) return true;
		try {
			Class.forName("com.wildfire.main.WildfireGender");
			return true;
		} catch (ClassNotFoundException absent) {
			return false;
		}
	}

	/** The mod's own window, the one its key opens; the same class in every line, in two packages. */
	private static Class<?> windowClass() throws ClassNotFoundException {
		return Class.forName(shape == Shape.VALUES ? "com.wildfire.client.gui.screen.WardrobeBrowserScreen"
			: "com.wildfire.gui.screen.WardrobeBrowserScreen");
	}

	/**
	 * Whether the mod's window can be opened outside a world. Its lines for 26.3 and 1.21.11 draw the
	 * figure in it only when there is one and look for the player and the world with care, and they
	 * are the ones with {@code WardrobeBrowserScreen.create}; the older ones read the world in their
	 * inner windows unchecked, so that one opened from the menus would stop the game.
	 */
	public static boolean windowInMenus() {
		if (!available()) return false;
		try {
			windowClass().getMethod("create", LocalPlayer.class, Screen.class);
			return true;
		} catch (ReflectiveOperationException | LinkageError none) {
			return false;
		}
	}

	/**
	 * The mod's own window for the player, as its key opens it, which goes back to {@code parent} when
	 * closed; null without the mod, or outside a world where its line needs one ({@link #windowInMenus}).
	 * In a world it is opened the mod's own way, with its first-time questions if they are still to be
	 * asked; in the menus the player's settings are first read from where the mod keeps them.
	 */
	public static Screen window(Screen parent, UUID me) {
		if (!available()) return null;
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null && !windowInMenus()) return null;
		try {
			Class<?> window = windowClass();
			if (player != null) {
				try {
					return (Screen) window.getMethod("create", LocalPlayer.class, Screen.class).invoke(null, player, parent);
				} catch (NoSuchMethodException older) {
					// Up to 1.21.1: built for the player's id.
				}
			}
			Object config;
			if (shape == Shape.VALUES) {
				config = cacheGet.getDeclaringClass().getMethod("getOrCreate", UUID.class).invoke(players, me);
			} else {
				Class.forName("com.wildfire.main.WildfireGender").getMethod("getOrAddPlayerById", UUID.class).invoke(null, me);
				config = me;
			}
			for (Constructor<?> made : window.getConstructors()) {
				Class<?>[] takes = made.getParameterTypes();
				if (takes.length == 2 && takes[0] == Screen.class && takes[1].isInstance(config)) {
					return (Screen) made.newInstance(parent, config);
				}
			}
			// NeoForge 1.21.1 builds it for the id alone and goes back to no screen; its parent is set after.
			Screen opened = (Screen) window.getConstructor(UUID.class).newInstance(me);
			for (Class<?> at = window; at != null && at != Screen.class; at = at.getSuperclass()) {
				try {
					Field back = at.getDeclaredField("parent");
					back.setAccessible(true);
					back.set(opened, parent);
					break;
				} catch (NoSuchFieldException higher) {
					// Its superclass.
				}
			}
			return opened;
		} catch (ReflectiveOperationException | RuntimeException | LinkageError failed) {
			Mosaicary.LOGGER.warn("Female Gender Mod: could not open its window: {}", failed.toString());
			return null;
		}
	}
}

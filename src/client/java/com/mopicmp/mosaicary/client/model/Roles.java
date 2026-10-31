package com.mopicmp.mosaicary.client.model;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * Which animation of a model plays for which action of the player wearing it.
 *
 * Nothing in the model file is renamed: the player picks, per role, one of the
 * model's animations, the game's own motion ({@link #VANILLA}) or nothing at all
 * ({@link #NOTHING}). What the player has not picked is filled in from the
 * animation names.
 *
 * <h2>The roles</h2>
 *
 * Body actions, one at a time ({@link Section#MOVEMENT}); for every body action an
 * arm layer per arm ({@code <action>_left_arm}, {@code <action>_right_arm}), which
 * plays on that arm only, over the body, while that hand holds nothing; items in
 * hand, per arm ({@link Section#ITEMS}); and events ({@link Section#EVENTS}): hurt,
 * once over everything, and death, held. Left and right are the model's own arms.
 *
 * <h2>Filling in</h2>
 *
 * By name, with Bedrock's {@code animation.<model>.} prefix and case left out:
 * the names measured on the seed sample ({@code idle}, {@code walk}, {@code run},
 * {@code sit} ...) and the full set of a rigged character from the starter models
 * ({@code stand}, {@code walk_forward}, {@code air_back}, {@code sneak_forward},
 * {@code hold_item_left}, {@code spin_attack} ...). An animation named
 * {@code <name>_left_arm} becomes the left-arm layer of whichever body action
 * {@code <name>} fills. A role nothing fills takes the role it is a variant of
 * (walking backwards takes walking, sprinting takes walking, eating takes holding,
 * an attack with an item takes the bare swing), else the game's own motion; arm
 * layers take nothing.
 *
 * The mapping travels with a worn model so other players see it the same: as a JSON
 * object from role name to value, at most {@link #MAX_BYTES} bytes.
 */
public final class Roles {

	/** How the detail view groups the roles. */
	public enum Section { MOVEMENT, ITEMS, EVENTS }

	/** Body actions, items in hand and events, in the order the detail view lists them. */
	public enum Action {
		IDLE("idle", Section.MOVEMENT, null),
		WALK("walk", Section.MOVEMENT, null),
		WALK_BACK("walk_back", Section.MOVEMENT, "walk"),
		SPRINT("sprint", Section.MOVEMENT, "walk"),
		CROUCH("crouch", Section.MOVEMENT, null),
		CROUCH_WALK("crouch_walk", Section.MOVEMENT, null),
		CROUCH_BACK("crouch_back", Section.MOVEMENT, "crouch_walk"),
		JUMP("jump", Section.MOVEMENT, null),
		JUMP_BACK("jump_back", Section.MOVEMENT, "jump"),
		SWIM("swim", Section.MOVEMENT, null),
		CRAWL("crawl", Section.MOVEMENT, "swim"),
		CRAWL_WALK("crawl_walk", Section.MOVEMENT, "crawl"),
		CRAWL_BACK("crawl_back", Section.MOVEMENT, "crawl_walk"),
		ELYTRA("elytra", Section.MOVEMENT, null),
		SPIN("spin", Section.MOVEMENT, null),
		SLEEP("sleep", Section.MOVEMENT, null),
		RIDE("ride", Section.MOVEMENT, null),
		HOLD_LEFT("hold_left", Section.ITEMS, null),
		HOLD_RIGHT("hold_right", Section.ITEMS, null),
		CHARGE_LEFT("charge_left", Section.ITEMS, null),
		CHARGE_RIGHT("charge_right", Section.ITEMS, null),
		THRUST_LEFT("thrust_left", Section.ITEMS, "swing"),
		THRUST_RIGHT("thrust_right", Section.ITEMS, "swing"),
		USE_SELF_LEFT("use_self_left", Section.ITEMS, "hold_left"),
		USE_SELF_RIGHT("use_self_right", Section.ITEMS, "hold_right"),
		SWING("swing", Section.ITEMS, null),
		HURT("hurt", Section.EVENTS, null),
		DEATH("death", Section.EVENTS, null);

		/** The key in the JSON that travels with a worn model. */
		public final String key;
		public final Section section;
		/** The role this one takes when no animation fills it, or null. */
		public final String fallback;

		Action(String key, Section section, String fallback) {
			this.key = key;
			this.section = section;
			this.fallback = fallback;
		}

		public static Action byKey(String key) {
			for (Action action : values()) if (action.key.equals(key)) return action;
			return null;
		}

		/** Whether this is a body action, one of which plays at a time and which has arm layers. */
		public boolean body() {
			return section == Section.MOVEMENT || this == DEATH;
		}
	}

	/** The model's arms. */
	public enum Side {
		LEFT("left"), RIGHT("right");

		public final String key;

		Side(String key) {
			this.key = key;
		}
	}

	public static final String VANILLA = "@vanilla";
	public static final String NOTHING = "@none";

	/** The most a mapping may weigh as JSON, in bytes; the site's own limit. */
	public static final int MAX_BYTES = 8192;

	/** The body actions that have arm layers, in order. */
	public static final List<Action> BODY = java.util.Arrays.stream(Action.values()).filter(a -> a.section == Section.MOVEMENT).toList();

	/** The key of a body action's arm layer. */
	public static String layerKey(Action body, Side side) {
		return body.key + "_" + side.key + "_arm";
	}

	/** Every role key: actions in order, then the arm layers in the order of their body actions. */
	public static final List<String> KEYS;

	private static final Set<String> KNOWN;

	static {
		List<String> keys = new ArrayList<>();
		for (Action action : Action.values()) keys.add(action.key);
		for (Action body : BODY) for (Side side : Side.values()) keys.add(layerKey(body, side));
		KEYS = List.copyOf(keys);
		KNOWN = Set.copyOf(keys);
	}

	public static boolean isLayer(String key) {
		return key.endsWith("_left_arm") || key.endsWith("_right_arm");
	}

	// ------------------------------------------------------------------ names

	private static final Map<String, Set<String>> EXACT = new HashMap<>();
	private static final Map<String, List<String>> CONTAINS = new HashMap<>();

	static {
		EXACT.put("idle", Set.of("idle", "misc.idle", "ground_idle", "idle1", "standing", "stand", "idle_0"));
		EXACT.put("walk", Set.of("walk", "walking", "walk_forward", "walk_forwards", "move.walk", "ground_walk", "walk1", "move"));
		EXACT.put("walk_back", Set.of("walk_back", "walk_backward", "walk_backwards", "walk_bw", "walkback", "move.walk_back"));
		EXACT.put("sprint", Set.of("run", "running", "sprint", "sprinting", "move.run", "run_fast", "move.sprint",
			"run_forward", "sprint_forward"));
		EXACT.put("crouch", Set.of("crouch", "crouching", "sneak", "sneaking", "move.sneak", "move.crouch", "sneak_idle",
			"crouch_idle"));
		EXACT.put("crouch_walk", Set.of("crouch_walk", "crouchwalk", "sneak_walk", "sneakwalk", "crouch_walking",
			"sneak_walking", "sneak_forward", "crouch_forward", "move.crouch_walk", "move.sneak_walk"));
		EXACT.put("crouch_back", Set.of("crouch_back", "sneak_back", "crouch_backward", "sneak_backward",
			"crouch_walk_back", "sneak_walk_back"));
		EXACT.put("jump", Set.of("jump", "fall", "falling", "jumping", "move.jump", "in_air", "air", "air_forward",
			"jump_forward"));
		EXACT.put("jump_back", Set.of("jump_back", "air_back", "fall_back", "jump_backward"));
		EXACT.put("swim", Set.of("swim", "swimming", "move.swim", "swim_forward"));
		EXACT.put("crawl", Set.of("crawl", "crawling", "crawl_idle", "prone"));
		EXACT.put("crawl_walk", Set.of("crawl_walk", "crawl_forward", "crawling_forward"));
		EXACT.put("crawl_back", Set.of("crawl_back", "crawl_backward", "crawling_back"));
		EXACT.put("elytra", Set.of("elytra", "gliding", "glide", "fly", "flying", "fly_loop"));
		EXACT.put("spin", Set.of("spin", "spin_attack", "riptide"));
		EXACT.put("sleep", Set.of("sleep", "sleeping", "sleep_loop", "lay", "laying", "lie"));
		EXACT.put("ride", Set.of("ride", "riding", "sit", "sitting", "misc.sit", "mount"));
		for (Side side : Side.values()) {
			String s = side.key;
			EXACT.put("hold_" + s, Set.of("hold_item_" + s, "hold_" + s, "holding_" + s));
			EXACT.put("charge_" + s, Set.of("charge_item_" + s, "charge_" + s, "charging_" + s));
			EXACT.put("thrust_" + s, Set.of("thrust_item_" + s, "thrust_" + s));
			EXACT.put("use_self_" + s, Set.of("use_on_self_" + s, "use_self_" + s, "self_use_" + s));
		}
		EXACT.put("swing", Set.of("swing", "attack", "attack.swing", "attack1", "punch", "mine", "use"));
		EXACT.put("hurt", Set.of("hurt", "damage", "damaged", "hit", "take_damage", "get_hit"));
		EXACT.put("death", Set.of("death", "die", "dying", "dead"));
		CONTAINS.put("idle", List.of("idle"));
		CONTAINS.put("walk", List.of("walk"));
		CONTAINS.put("sprint", List.of("sprint", "run"));
		CONTAINS.put("crouch", List.of("crouch", "sneak"));
		CONTAINS.put("jump", List.of("jump", "fall"));
		CONTAINS.put("swim", List.of("swim"));
		CONTAINS.put("elytra", List.of("elytra", "glid"));
		CONTAINS.put("sleep", List.of("sleep"));
		CONTAINS.put("ride", List.of("ride", "riding", "sit"));
		CONTAINS.put("swing", List.of("attack", "swing"));
		CONTAINS.put("hurt", List.of("hurt"));
		CONTAINS.put("death", List.of("death", "dying"));
	}

	/** A name the body action fills, from its exact names or its own key. */
	private static String bodyNamed(String name) {
		for (Action body : BODY) {
			if (body.key.equals(name) || EXACT.get(body.key).contains(name)) return body.key;
		}
		return null;
	}

	/** Names that belong to a layer, the first person or an arm, which no body role takes by a part of its name. */
	private static boolean special(String name) {
		return isLayer(name) || name.startsWith("viewmodel") || name.startsWith("fp_") || name.contains("first_person");
	}

	// ------------------------------------------------------------------ the mapping

	private final Map<String, String> values;

	private Roles(Map<String, String> values) {
		this.values = values;
	}

	/** Filled in from the animation names; see the class comment. */
	public static Roles auto(BbModel model) {
		Map<String, String> found = new LinkedHashMap<>();
		// Exact names first, over all animations, so a near miss never wins over a match.
		for (Animation animation : model.animations()) {
			String name = simple(animation.name());
			if (isLayer(name)) {
				Side side = name.endsWith("_left_arm") ? Side.LEFT : Side.RIGHT;
				String base = name.substring(0, name.length() - ("_" + side.key + "_arm").length());
				String body = bodyNamed(base);
				if (body != null) found.putIfAbsent(body + "_" + side.key + "_arm", animation.name());
				continue;
			}
			for (Action action : Action.values()) {
				if (!found.containsKey(action.key) && (EXACT.get(action.key).contains(name) || action.key.equals(name))) {
					found.put(action.key, animation.name());
				}
			}
		}
		// Then a part of a name, for the roles that have one.
		for (Action action : Action.values()) {
			if (found.containsKey(action.key) || !CONTAINS.containsKey(action.key)) continue;
			for (Animation animation : model.animations()) {
				String name = simple(animation.name());
				if (special(name)) continue;
				// "crouch_walk" is not a walk, and "sneak_forward" is not standing crouched.
				if (action == Action.WALK && (name.contains("crouch") || name.contains("sneak") || name.contains("back")
					|| name.contains("crawl"))) continue;
				if (action == Action.CROUCH && (name.contains("walk") || name.contains("forward") || name.contains("back"))) continue;
				if (CONTAINS.get(action.key).stream().anyMatch(name::contains)) {
					found.put(action.key, animation.name());
					break;
				}
			}
		}
		Map<String, String> values = new LinkedHashMap<>();
		for (String key : KEYS) values.put(key, resolve(key, found));
		return new Roles(values);
	}

	/** What a role gets: its own animation, else what the role it varies gets, else its default. */
	private static String resolve(String key, Map<String, String> found) {
		String own = found.get(key);
		if (own != null) return own;
		if (isLayer(key)) {
			Side side = key.endsWith("_left_arm") ? Side.LEFT : Side.RIGHT;
			Action body = Action.byKey(key.substring(0, key.length() - ("_" + side.key + "_arm").length()));
			if (body != null && body.fallback != null) return resolve(body.fallback + "_" + side.key + "_arm", found);
			return NOTHING;
		}
		Action action = Action.byKey(key);
		if (action != null && action.fallback != null) return resolve(action.fallback, found);
		return VANILLA;
	}

	/** These roles with the player's own choices on top; unknown roles and missing animations are ignored. */
	public Roles with(Map<String, String> chosen, BbModel model) {
		Map<String, String> merged = new LinkedHashMap<>(values);
		if (chosen != null) {
			for (Map.Entry<String, String> entry : chosen.entrySet()) {
				String value = entry.getValue();
				if (!KNOWN.contains(entry.getKey()) || value == null) continue;
				if (value.equals(VANILLA) || value.equals(NOTHING) || model.animation(value) != null) {
					merged.put(entry.getKey(), value);
				}
			}
		}
		return new Roles(merged);
	}

	public String get(String key) {
		return values.getOrDefault(key, isLayer(key) ? NOTHING : VANILLA);
	}

	public String get(Action action) {
		return get(action.key);
	}

	public String layer(Action body, Side side) {
		return get(layerKey(body, side));
	}

	/** The animation for a role, or null when the role is the game's motion or nothing. */
	public Animation animation(BbModel model, String key) {
		String value = get(key);
		return value.startsWith("@") ? null : model.animation(value);
	}

	public Animation animation(BbModel model, Action action) {
		return animation(model, action.key);
	}

	/** The choices that differ from {@code base}, as role key to value: what is worth storing. */
	public Map<String, String> differencesFrom(Roles base) {
		Map<String, String> out = new LinkedHashMap<>();
		for (String key : KEYS) if (!get(key).equals(base.get(key))) out.put(key, get(key));
		return out;
	}

	/** Every role, as role key to value: what travels with a worn model. */
	public Map<String, String> all() {
		Map<String, String> out = new LinkedHashMap<>();
		for (String key : KEYS) out.put(key, get(key));
		return out;
	}

	/**
	 * The mapping as the JSON object that travels with a worn model, never more than
	 * {@link #MAX_BYTES} bytes: if a pathological set of long names would pass that,
	 * the roles that are a default anyway (the game's motion, an empty arm layer) are
	 * left out first, then the rest from the end.
	 */
	public static String toJson(Map<String, String> roles) {
		Map<String, String> kept = new LinkedHashMap<>(roles);
		while (true) {
			JsonObject json = new JsonObject();
			for (Map.Entry<String, String> entry : kept.entrySet()) json.addProperty(entry.getKey(), entry.getValue());
			String text = json.toString();
			if (text.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES || kept.isEmpty()) return text;
			String drop = null;
			for (Map.Entry<String, String> entry : kept.entrySet()) {
				boolean isDefault = isLayer(entry.getKey()) ? NOTHING.equals(entry.getValue()) : VANILLA.equals(entry.getValue());
				if (isDefault) {
					drop = entry.getKey();
					break;
				}
			}
			if (drop == null) drop = kept.keySet().stream().reduce((a, b) -> b).orElse(null);
			kept.remove(drop);
		}
	}

	/**
	 * Reads a mapping that came from elsewhere (the site). Anything that is not a
	 * known role with a string value is dropped; text over {@link #MAX_BYTES} bytes
	 * is refused whole.
	 */
	public static Map<String, String> fromJson(String text) {
		Map<String, String> out = new LinkedHashMap<>();
		if (text == null || text.isBlank() || text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) return out;
		try {
			JsonElement parsed = JsonParser.parseString(text);
			if (!(parsed instanceof JsonObject object)) return out;
			for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
				if (!KNOWN.contains(entry.getKey())) continue;
				if (entry.getValue() instanceof JsonPrimitive primitive && primitive.isString()) {
					String value = primitive.getAsString();
					if (value.length() <= 128) out.put(entry.getKey(), value);
				}
			}
		} catch (RuntimeException unreadable) {
			// An unreadable mapping is no mapping; the names fill in the roles.
		}
		return out;
	}

	/**
	 * An animation name without case and without Bedrock's prefix:
	 * {@code animation.<model>.<name>} and {@code animation.<name>} both give the name.
	 */
	static String simple(String name) {
		String lower = name.toLowerCase(Locale.ROOT).trim();
		if (lower.startsWith("animation.")) {
			lower = lower.substring("animation.".length());
			int dot = lower.indexOf('.');
			if (dot > 0 && dot < lower.length() - 1) lower = lower.substring(dot + 1);
		}
		return lower;
	}
}

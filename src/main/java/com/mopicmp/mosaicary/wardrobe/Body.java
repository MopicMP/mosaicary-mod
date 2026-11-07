package com.mopicmp.mosaicary.wardrobe;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * The body a skin was last worn with in Female Gender Mod, kept with its entry in "My skins"
 * so that putting the skin on again brings the body back (client/compat/GenderMod).
 *
 * The names are the mod's own flat ones, which every line of it reads, 5.0 included through
 * its legacy codec; the gender is its lower-case name. Only what is about the body is kept:
 * hurt sounds, armour and holiday settings stay the player's own. Anything else, or of the
 * wrong kind, is dropped, as the site does (site/shared/wardrobeWire.ts). Numbers are kept as they
 * are, to four places, past the mod's own limits too: those are lifted by mods such as Over The
 * Limits, and a line that refuses a number is given it held to its usual range when it is put on.
 *
 * Gson's older API only: the oldest game versions ship Gson 2.8.0.
 */
public final class Body {

	public static final String GENDER = "gender";

	/**
	 * The numbers and the widest of the mod's usual ranges, from its own config keys (1.18.2 to 5.0):
	 * what a number is held to when a line refuses it as it is.
	 */
	public static final Map<String, float[]> NUMBERS = new LinkedHashMap<>();
	/** The switches. */
	public static final String[] FLAGS = { "breasts_uniboob", "breast_physics" };
	public static final String[] GENDERS = { "female", "male", "other" };

	static {
		NUMBERS.put("bust_size", new float[] { 0, 1 });
		NUMBERS.put("breasts_xOffset", new float[] { -1, 1 });
		NUMBERS.put("breasts_yOffset", new float[] { -1, 1 });
		NUMBERS.put("breasts_zOffset", new float[] { -1, 0 });
		NUMBERS.put("breasts_cleavage", new float[] { 0, 0.1f });
		NUMBERS.put("bounce_multiplier", new float[] { 0, 1 });
		NUMBERS.put("floppy_multiplier", new float[] { 0, 1 });
		NUMBERS.put("voice_pitch", new float[] { 0.8f, 1.2f });
	}

	/** Anything further from zero is no setting but a broken number. */
	public static final double MOST = 1000;

	private Body() { }

	/** What of {@code raw} is a body, or null when nothing is. */
	public static JsonObject clean(JsonElement raw) {
		if (raw == null || !raw.isJsonObject()) return null;
		JsonObject in = raw.getAsJsonObject();
		JsonObject out = new JsonObject();
		String gender = string(in.get(GENDER));
		if (gender != null) {
			for (String known : GENDERS) if (known.equalsIgnoreCase(gender)) out.addProperty(GENDER, known);
		}
		for (Map.Entry<String, float[]> number : NUMBERS.entrySet()) {
			JsonElement value = in.get(number.getKey());
			if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) continue;
			double read = value.getAsDouble();
			if (!Double.isFinite(read) || Math.abs(read) > MOST) continue;
			// Four places, as a double: a float such as 0.6f read back from the file is 0.6 and no
			// longer equal to it, so the same body would count as a change on every wear.
			out.addProperty(number.getKey(), Math.round(read * 10_000) / 10_000.0);
		}
		for (String flag : FLAGS) {
			JsonElement value = in.get(flag);
			if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) out.addProperty(flag, value.getAsBoolean());
		}
		return out.entrySet().isEmpty() ? null : out;
	}

	/** A copy that shares nothing with {@code body}; its values are primitives, which do not change. */
	public static JsonObject copy(JsonObject body) {
		if (body == null) return null;
		JsonObject out = new JsonObject();
		for (Map.Entry<String, JsonElement> member : body.entrySet()) out.add(member.getKey(), member.getValue());
		return out;
	}

	private static String string(JsonElement value) {
		if (value == null || !value.isJsonPrimitive()) return null;
		JsonPrimitive primitive = value.getAsJsonPrimitive();
		return primitive.isString() ? primitive.getAsString() : null;
	}
}

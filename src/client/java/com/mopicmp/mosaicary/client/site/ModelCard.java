package com.mopicmp.mosaicary.client.site;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mopicmp.mosaicary.client.build.Builds;

/**
 * One model as the site's list and detail endpoints describe it.
 *
 * Read leniently: a field the site stops sending, or one it sends as null, comes
 * out as an empty value rather than failing the whole list, because one odd row
 * should not take the catalogue down with it.
 *
 * @param use         what the model is for, in the site's four groups: java, bedrock, mods, free
 * @param license     the site's licence code: cc0, cc-by, cc-by-sa, cc-by-nc, cc-by-nc-sa, arr
 * @param authorName  who made it, as credited on the site; may list several names
 * @param authorUrl   where the credit links to, or empty
 * @param byHandle    the site account that published it
 * @param preview     the preview path relative to the site, or empty when there is none
 * @param updated     when it last changed, as the site writes it; part of cache keys
 * @param description only filled in by the detail endpoint
 * @param spin        the angle the card picture is taken from, around the vertical axis, in radians
 * @param tilt        the same, up and down
 */
public record ModelCard(String id, String name, String format, String use, String version,
		boolean boxUv, int cubes, int meshes, int bones, int animations, int textures,
		int textureWidth, int textureHeight, double density, long bytes, List<String> tags,
		String license, String authorName, String authorUrl, String byHandle, String published,
		String updated, int downloads, int likes, int comments, String preview, boolean adult,
		String background, boolean hasLook, double spin, double tilt, String description) {

	/** The licences the site knows, in the order it offers them. */
	public static final List<String> LICENSES = List.of("cc0", "cc-by", "cc-by-sa", "cc-by-nc", "cc-by-nc-sa", "arr");

	/** The four groups of use, in the site's order. */
	public static final List<String> USES = List.of("java", "bedrock", "mods", "free");

	public static ModelCard from(JsonObject json) {
		JsonObject author = object(json, "author");
		JsonObject by = object(json, "by");
		JsonObject look = object(json, "look");
		List<String> tags = new ArrayList<>();
		if (json.get("tags") instanceof JsonArray list) {
			for (JsonElement tag : list) if (tag instanceof JsonPrimitive p) tags.add(p.getAsString());
		}
		return new ModelCard(
			text(json, "id"), text(json, "name"), text(json, "format"), text(json, "use"),
			text(json, "version"), bool(json, "boxUv"), integer(json, "cubes"), integer(json, "meshes"),
			integer(json, "bones"), integer(json, "animations"), integer(json, "textures"),
			integer(json, "textureWidth"), integer(json, "textureHeight"), number(json, "density"),
			(long) number(json, "bytes"), List.copyOf(tags), text(json, "license"),
			text(author, "name"), text(author, "url"), text(by, "handle"), text(json, "published"),
			text(json, "updated"), integer(json, "downloads"), integer(json, "likes"),
			integer(json, "comments"), text(json, "preview"), bool(json, "adult"),
			text(json, "background"), look != null, number(look, "spin"), number(look, "tilt"),
			text(json, "description"));
	}

	/**
	 * A card with only what a saved slot or worn model keeps: enough to fetch its
	 * file and picture and to show its name.
	 */
	public static ModelCard minimal(String id, String name, String updated, String preview, boolean adult) {
		return new ModelCard(id, name == null ? "" : name, "", "", "", false, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(), "",
			"", "", "", "", updated == null ? "" : updated, 0, 0, 0, preview == null ? "" : preview, adult, "", false, 0,
			0, "");
	}

	/** Ids of models from the player's own files start with this; the site never sees them. */
	public static final String LOCAL = "local:";

	/** A card for a Figura avatar in the player's own folder. */
	public static ModelCard local(String id, String name, String authors, String description, String updated) {
		return new ModelCard(id, name, "figura", "", "", false, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(), "", authors, "", "", "",
			updated, 0, 0, 0, "", false, "", false, 0, 0, description);
	}

	/**
	 * Whether this is a build: a piece of a world for Axiom, Litematica or WorldEdit to
	 * place (the site's BUILD_FORMATS), with nothing to draw or wear, only its picture to
	 * show and the file to install.
	 */
	public boolean isBuild() {
		return buildFormat() != null;
	}

	/** The format of a build, or null for a model. */
	public Builds.Format buildFormat() {
		return Builds.Format.ofSite(format);
	}

	/** Whether this model comes from the player's own files rather than the site. */
	public boolean isLocal() {
		return id.startsWith(LOCAL);
	}

	/** Whether this card matches a search: every word somewhere in name, author, publisher or tags. */
	public boolean matches(String query) {
		if (query == null || query.isBlank()) return true;
		String haystack = (name + " " + authorName + " " + byHandle + " " + String.join(" ", tags) + " " + format)
			.toLowerCase(Locale.ROOT);
		for (String word : query.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
			if (!haystack.contains(word)) return false;
		}
		return true;
	}

	/** The background colour the author picked for the card, as 0xRRGGBB, or the given default. */
	public int backgroundRgb(int otherwise) {
		if (background == null || !background.matches("#[0-9a-fA-F]{6}")) return otherwise;
		return Integer.parseInt(background.substring(1), 16);
	}

	/** A key that changes whenever the model does, for cache file names. */
	public String versionKey() {
		return Integer.toHexString((updated == null ? "" : updated).hashCode());
	}

	static JsonObject object(JsonObject parent, String key) {
		return parent != null && parent.get(key) instanceof JsonObject found ? found : null;
	}

	static String text(JsonObject json, String key) {
		if (json != null && json.get(key) instanceof JsonPrimitive p) return p.getAsString();
		return "";
	}

	static boolean bool(JsonObject json, String key) {
		return json != null && json.get(key) instanceof JsonPrimitive p && p.isBoolean() && p.getAsBoolean();
	}

	static double number(JsonObject json, String key) {
		if (json != null && json.get(key) instanceof JsonPrimitive p && p.isNumber()) {
			double value = p.getAsDouble();
			return Double.isFinite(value) ? value : 0;
		}
		return 0;
	}

	static int integer(JsonObject json, String key) {
		return (int) number(json, key);
	}
}

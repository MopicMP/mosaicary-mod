package com.mopicmp.mosaicary.client.skins;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mopicmp.mosaicary.wardrobe.Wardrobe;

/**
 * One skin of the site's open skins API ({@code GET /api/skins}, described in
 * {@code site/API.md}), as the wardrobe uses it.
 *
 * Every address in it must lie on the site the mod talks to. The skin file goes to
 * Mojang as it comes, so an answer that pointed somewhere else could put any picture
 * on the player's account; such a row is dropped rather than shown.
 *
 * @param arms     {@code classic} or {@code slim}, what Mojang calls the variant
 * @param picture  the skin worn and turned, 256 x 256 ({@code render}); {@code front} when there is none
 * @param credit   who drew it; whoever published it when that is them; for a skin found on
 *                 players, the player last seen wearing it
 * @param wearer   whether {@code credit} names a player wearing the skin rather than its author
 * @param license  the licence's code, or empty for a skin found on players
 * @param eyes     whether the eyes come alive with the Fresh Moves resource pack
 * @param live     the picture with the eyes moving, an animated PNG; empty for a skin without such eyes
 */
public record SkinCard(String id, String name, String arms, int width, int height, String png, String picture, String page,
		String credit, String creditUrl, boolean wearer, String license, String category, List<String> tags, int likes,
		int downloads, String published, boolean eyes, String live) {

	public boolean slim() {
		return "slim".equals(arms);
	}

	/** The skin as a wardrobe entry, added now. */
	public Wardrobe.Entry entry(long now) {
		return Wardrobe.Entry.ofSite(id, name, arms, picture, now);
	}

	/** The part of the picture's address that changes when the picture is drawn again, for the cache. */
	public String pictureKey() {
		int at = picture.indexOf("?v=");
		String version = at < 0 ? "0" : picture.substring(at + 3);
		return version.replaceAll("[^0-9A-Za-z._-]", "_");
	}

	/** Whether every word of a search appears in the name, the credit or a tag. */
	public boolean matches(String query) {
		if (query == null || query.isBlank()) return true;
		String haystack = (name + " " + credit + " " + String.join(" ", tags)).toLowerCase(Locale.ROOT);
		for (String word : query.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
			if (!haystack.contains(word)) return false;
		}
		return true;
	}

	/**
	 * Reads one row of the list, or null when it is not a usable skin: no id, not 64 wide
	 * (Mojang takes nothing else), or an address that is not on the site.
	 *
	 * @param base the site's address, without a trailing slash
	 */
	public static SkinCard from(JsonObject json, String base) {
		String id = text(json, "id");
		if (!id.matches("[0-9A-Za-z]{12}")) return null;
		int width = number(json, "width");
		int height = number(json, "height");
		if (width != 64 || (height != 64 && height != 32)) return null;
		String png = text(json, "png");
		String render = text(json, "render");
		String front = text(json, "front");
		String page = text(json, "page");
		String picture = onSite(render, base) ? render : onSite(front, base) ? front : "";
		if (!onSite(png, base) || picture.isEmpty() || !onSite(page, base)) return null;

		String arms = "slim".equals(text(json, "arms")) ? "slim" : "classic";
		JsonElement foundFlag = json.get("found");
		boolean found = foundFlag != null && foundFlag.isJsonPrimitive() && foundFlag.getAsBoolean();
		String author = nameOf(json.get("author"));
		String authorUrl = urlOf(json.get("author"), "url");
		String publisher = nameOf(json.get("publisher"));
		String publisherUrl = urlOf(json.get("publisher"), "page");
		String wearerName = text(json, "wearer");
		String credit;
		String creditUrl;
		boolean byWearer = false;
		if (!author.isEmpty()) {
			credit = author;
			creditUrl = authorUrl;
		} else if (found) {
			// A skin found on players has no known author: the player wearing it is named,
			// never the site's own account that lists it.
			credit = wearerName;
			creditUrl = "";
			byWearer = !wearerName.isEmpty();
		} else {
			credit = publisher;
			creditUrl = publisherUrl;
		}
		List<String> tags = new ArrayList<>();
		if (json.get("tags") instanceof JsonArray array) {
			for (JsonElement tag : array) {
				if (tag.isJsonPrimitive() && !tag.getAsString().isBlank() && tags.size() < 40) tags.add(tag.getAsString().trim());
			}
		}
		JsonElement eyesFlag = json.get("eyes");
		boolean eyes = eyesFlag != null && eyesFlag.isJsonPrimitive() && eyesFlag.getAsBoolean();
		return new SkinCard(id, text(json, "name").isBlank() ? id : text(json, "name").trim(), arms, width, height, png,
			picture, page, credit, creditUrl, byWearer, text(json, "license"), text(json, "category"), List.copyOf(tags),
			number(json, "likes"), number(json, "downloads"), text(json, "published"), eyes,
			onSite(text(json, "live"), base) ? text(json, "live") : "");
	}

	/** Whether an address lies on the site: starts with its address and a slash. */
	static boolean onSite(String address, String base) {
		return address != null && address.startsWith(base + "/");
	}

	private static String text(JsonObject json, String key) {
		JsonElement value = json.get(key);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
	}

	private static int number(JsonObject json, String key) {
		JsonElement value = json.get(key);
		try {
			return value != null && value.isJsonPrimitive() ? value.getAsInt() : 0;
		} catch (NumberFormatException odd) {
			return 0;
		}
	}

	private static String nameOf(JsonElement value) {
		return value instanceof JsonObject object ? text(object, "name").trim() : "";
	}

	private static String urlOf(JsonElement value, String key) {
		if (!(value instanceof JsonObject object)) return "";
		String url = text(object, key);
		return url.startsWith("https://") ? url : "";
	}
}

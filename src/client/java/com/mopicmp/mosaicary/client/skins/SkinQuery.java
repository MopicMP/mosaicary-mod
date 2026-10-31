package com.mopicmp.mosaicary.client.skins;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * What the skin catalogue is asked for: the search and the filters, the same set the
 * launcher catalogue offers.
 *
 * Only 64-pixel skins are ever asked for ({@code size=64}): they are the only ones Mojang
 * puts on an account. Tags are kept sorted and without repeats, so the same tags picked in
 * another order are the same query and share what was loaded for it.
 *
 * @param arms     {@code any}, {@code classic} or {@code slim}
 * @param category {@code any} or one of {@link #CATEGORIES}
 * @param sort     one of {@link #SORTS}
 * @param eyes     only skins whose eyes come alive with the Fresh Moves resource pack
 * @param tagsAll  with two tags or more: every one of them rather than any
 */
public record SkinQuery(String words, String arms, String category, String sort, boolean eyes, List<String> tags,
		boolean tagsAll) {

	public static final List<String> CATEGORIES = List.of("girls", "boys", "characters", "creatures");
	public static final List<String> SORTS = List.of("new", "popular", "downloads");

	/** The most tags the site takes at once. */
	public static final int TAGS_MOST = 10;

	/** Skins per page: two or three rows of the grid. */
	public static final int PER = 24;

	public static final SkinQuery NONE = new SkinQuery("", "any", "any", "new", false, List.of(), false);

	public SkinQuery {
		words = words == null ? "" : words.strip();
		if (words.length() > 100) words = words.substring(0, 100);
		if (!"classic".equals(arms) && !"slim".equals(arms)) arms = "any";
		if (!CATEGORIES.contains(category)) category = "any";
		if (!SORTS.contains(sort)) sort = "new";
		TreeSet<String> sorted = new TreeSet<>();
		if (tags != null) for (String tag : tags) if (tag != null && !tag.isBlank()) sorted.add(tag.strip());
		List<String> kept = new ArrayList<>(sorted);
		tags = List.copyOf(kept.size() > TAGS_MOST ? kept.subList(0, TAGS_MOST) : kept);
		tagsAll = tagsAll && tags.size() > 1;
	}

	public SkinQuery withWords(String value) {
		return new SkinQuery(value, arms, category, sort, eyes, tags, tagsAll);
	}

	public SkinQuery withArms(String value) {
		return new SkinQuery(words, value, category, sort, eyes, tags, tagsAll);
	}

	public SkinQuery withCategory(String value) {
		return new SkinQuery(words, arms, value, sort, eyes, tags, tagsAll);
	}

	public SkinQuery withSort(String value) {
		return new SkinQuery(words, arms, category, value, eyes, tags, tagsAll);
	}

	public SkinQuery withEyes(boolean value) {
		return new SkinQuery(words, arms, category, sort, value, tags, tagsAll);
	}

	public SkinQuery withTags(List<String> value, boolean all) {
		return new SkinQuery(words, arms, category, sort, eyes, value, all);
	}

	/** Whether anything narrows the list, for the "nothing found" wording. */
	public boolean narrowed() {
		return !words.isEmpty() || !arms.equals("any") || !category.equals("any") || eyes || !tags.isEmpty();
	}

	/** The address of one page of this query, from the site's root. */
	public String path(int page) {
		StringBuilder out = new StringBuilder("/api/skins?size=64&per=").append(PER).append("&page=").append(Math.max(1, page));
		if (!words.isEmpty()) out.append("&q=").append(encode(words));
		if (!arms.equals("any")) out.append("&arms=").append(arms);
		if (!category.equals("any")) out.append("&category=").append(category);
		if (!sort.equals("new")) out.append("&sort=").append(sort);
		if (eyes) out.append("&eyes=1");
		if (!tags.isEmpty()) {
			out.append("&tag=").append(encode(String.join(",", tags)));
			if (tagsAll) out.append("&tags=all");
		}
		return out.toString();
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
	}

	/** One page of the list, as the site answered it. */
	public record Page(List<SkinCard> skins, int page, int pages, int total) { }

	/** Reads a page; rows that are not usable skins are left out rather than failing the page. */
	public static Page page(String body, String base) {
		JsonObject json = JsonParser.parseString(body).getAsJsonObject();
		List<SkinCard> skins = new ArrayList<>();
		if (json.get("skins") instanceof JsonArray array) {
			for (JsonElement one : array) {
				if (!(one instanceof JsonObject row)) continue;
				SkinCard card = SkinCard.from(row, base);
				if (card != null) skins.add(card);
			}
		}
		int page = intOf(json, "page", 1);
		int pages = intOf(json, "pages", page);
		int total = intOf(json, "total", skins.size());
		return new Page(List.copyOf(skins), page, pages, total);
	}

	/** A tag the listed skins carry, and how many carry it. */
	public record Tag(String tag, int count) { }

	/** Reads {@code /api/skins/tags}: the commonest first; anything not shaped like a tag is left out. */
	public static List<Tag> tags(String body) {
		JsonObject json = JsonParser.parseString(body).getAsJsonObject();
		List<Tag> out = new ArrayList<>();
		if (json.get("tags") instanceof JsonArray array) {
			for (JsonElement one : array) {
				if (!(one instanceof JsonObject row)) continue;
				String tag = row.get("tag") != null && row.get("tag").isJsonPrimitive() ? row.get("tag").getAsString().strip() : "";
				int count = intOf(row, "count", 0);
				if (tag.isEmpty() || tag.length() > 60 || count <= 0) continue;
				out.add(new Tag(tag, count));
			}
		}
		return List.copyOf(out);
	}

	private static int intOf(JsonObject json, String key, int otherwise) {
		try {
			return json.get(key) != null && json.get(key).isJsonPrimitive() ? json.get(key).getAsInt() : otherwise;
		} catch (NumberFormatException odd) {
			return otherwise;
		}
	}
}

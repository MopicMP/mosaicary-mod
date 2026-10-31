package com.mopicmp.mosaicary.client.skins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** The catalogue's query, as an address, and the reading of the site's answer. */
class SkinQueryTest {

	private static final String BASE = "https://mosaicary.com";

	@Test
	void anEmptyQueryAsksForSixtyFourPixelSkinsOnly() {
		assertEquals("/api/skins?size=64&per=24&page=1", SkinQuery.NONE.path(1));
		assertFalse(SkinQuery.NONE.narrowed());
	}

	@Test
	void everyFilterGoesIntoTheAddress() {
		SkinQuery query = SkinQuery.NONE.withWords("blue hoodie").withArms("slim").withCategory("girls").withSort("popular")
			.withEyes(true).withTags(List.of("red", "anime"), true);
		assertEquals("/api/skins?size=64&per=24&page=3&q=blue%20hoodie&arms=slim&category=girls&sort=popular&eyes=1"
			+ "&tag=anime%2Cred&tags=all", query.path(3));
		assertTrue(query.narrowed());
	}

	@Test
	void tagsInAnotherOrderAreTheSameQuery() {
		SkinQuery one = SkinQuery.NONE.withTags(List.of("b", "a", "a", " "), false);
		SkinQuery two = SkinQuery.NONE.withTags(List.of("a", "b"), false);
		assertEquals(one, two);
		assertEquals(List.of("a", "b"), one.tags());
	}

	@Test
	void everyTagAtOnceNeedsTwoTagsAndAtMostTenAreKept() {
		assertFalse(SkinQuery.NONE.withTags(List.of("one"), true).tagsAll());
		List<String> many = List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l");
		assertEquals(10, SkinQuery.NONE.withTags(many, false).tags().size());
	}

	@Test
	void unknownValuesFallBackToTheDefaults() {
		SkinQuery odd = new SkinQuery(null, "wide", "dragons", "random", false, null, false);
		assertEquals(SkinQuery.NONE, odd);
	}

	private static JsonObject row(String id, String png, String render) {
		JsonObject json = JsonParser.parseString("""
			{ "id": "%s", "name": "Blue hoodie", "arms": "slim", "width": 64, "height": 64,
			  "png": "%s", "render": "%s", "front": "https://mosaicary.com/api/skins/%s/front.png?v=1",
			  "page": "https://mosaicary.com/models/%s", "author": null,
			  "publisher": { "name": "MopicMP", "page": "https://mosaicary.com/u/AbCdEf123456" },
			  "license": "cc-by", "category": "girls", "tags": ["hoodie", "blue"], "likes": 12, "downloads": 340,
			  "published": "2026-10-21T16:20:00.000Z", "found": false, "wearer": null, "eyes": true }
			""".formatted(id, png, render, id, id)).getAsJsonObject();
		return json;
	}

	@Test
	void aRowFromTheSiteIsRead() {
		SkinCard card = SkinCard.from(row("9kPQ3TKUhrI8", BASE + "/api/skins/9kPQ3TKUhrI8.png",
			BASE + "/api/skins/9kPQ3TKUhrI8/render.png?v=1.1"), BASE);
		assertEquals("9kPQ3TKUhrI8", card.id());
		assertTrue(card.slim());
		assertEquals("MopicMP", card.credit());
		assertEquals("https://mosaicary.com/u/AbCdEf123456", card.creditUrl());
		assertFalse(card.wearer());
		assertTrue(card.eyes());
		assertEquals("1.1", card.pictureKey());
		assertEquals(List.of("hoodie", "blue"), card.tags());
		assertTrue(card.matches("blue mopic"));
		assertFalse(card.matches("red"));
	}

	@Test
	void anAddressOffTheSiteDropsTheRow() {
		// The file goes to Mojang as it comes: an answer pointing elsewhere is never shown.
		assertNull(SkinCard.from(row("9kPQ3TKUhrI8", "https://evil.example/skin.png",
			BASE + "/api/skins/9kPQ3TKUhrI8/render.png?v=1"), BASE));
		assertNull(SkinCard.from(row("9kPQ3TKUhrI8", "https://mosaicary.com.evil.example/a.png",
			BASE + "/api/skins/9kPQ3TKUhrI8/render.png?v=1"), BASE));
	}

	@Test
	void aRenderOffTheSiteFallsBackToTheFront() {
		SkinCard card = SkinCard.from(row("9kPQ3TKUhrI8", BASE + "/api/skins/9kPQ3TKUhrI8.png", "https://elsewhere/x.png"), BASE);
		assertTrue(card.picture().startsWith(BASE + "/api/skins/9kPQ3TKUhrI8/front.png"));
	}

	@Test
	void sizesMojangRefusesAreLeftOut() {
		JsonObject hd = row("9kPQ3TKUhrI8", BASE + "/api/skins/9kPQ3TKUhrI8.png", BASE + "/r.png");
		hd.addProperty("width", 128);
		hd.addProperty("height", 128);
		assertNull(SkinCard.from(hd, BASE));
		JsonObject legacy = row("9kPQ3TKUhrI8", BASE + "/api/skins/9kPQ3TKUhrI8.png", BASE + "/r.png");
		legacy.addProperty("height", 32);
		assertEquals(32, SkinCard.from(legacy, BASE).height());
	}

	@Test
	void aSkinFoundOnPlayersNamesTheWearerNotTheSite() {
		JsonObject found = row("9kPQ3TKUhrI8", BASE + "/api/skins/9kPQ3TKUhrI8.png", BASE + "/r.png");
		found.addProperty("found", true);
		found.addProperty("wearer", "Notch");
		found.add("license", null);
		found.add("publisher", JsonParser.parseString("{\"name\":\"Mosaicary Skins\",\"page\":\"https://mosaicary.com/u/x\"}"));
		SkinCard card = SkinCard.from(found, BASE);
		assertEquals("Notch", card.credit());
		assertTrue(card.wearer());
		assertEquals("", card.license());
	}

	@Test
	void aPageIsReadAndBadRowsAreSkipped() {
		String body = "{\"skins\":[" + row("9kPQ3TKUhrI8", BASE + "/api/skins/9kPQ3TKUhrI8.png", BASE + "/r.png")
			+ ",{\"id\":\"bad\"},7],\"page\":2,\"per\":24,\"total\":49,\"pages\":3}";
		SkinQuery.Page page = SkinQuery.page(body, BASE);
		assertEquals(1, page.skins().size());
		assertEquals(2, page.page());
		assertEquals(3, page.pages());
		assertEquals(49, page.total());
	}

	@Test
	void tagsAreRead() {
		List<SkinQuery.Tag> tags = SkinQuery.tags("{\"tags\":[{\"tag\":\"hoodie\",\"count\":41},{\"tag\":\"\",\"count\":3},"
			+ "{\"tag\":\"blue\",\"count\":0},{\"tag\":\"red\",\"count\":2}]}");
		assertEquals(List.of(new SkinQuery.Tag("hoodie", 41), new SkinQuery.Tag("red", 2)), tags);
	}
}

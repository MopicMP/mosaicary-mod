package com.mopicmp.mosaicary.wardrobe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The wardrobe: copies brought together, removals remembered, what travels and what stays. */
class WardrobeTest {

	private static final String HASH = "3b60a1f6d562f52aaebbf1434f1de147933a3affe0e764fa49ea057536623cd3";
	private static final String SHA1 = "0123456789abcdef0123456789abcdef01234567";
	private static final long DAY = 86_400_000L;

	private static Wardrobe.Entry site(String id, long changed) {
		Wardrobe.Entry entry = Wardrobe.Entry.ofSite(id, "Skin " + id, "classic", null, changed);
		entry.changed = changed;
		return entry;
	}

	private static Wardrobe.Entry find(List<Wardrobe.Entry> entries, String key) {
		for (Wardrobe.Entry entry : entries) if (entry.key.equals(key)) return entry;
		return null;
	}

	@Test
	void bothSidesEntriesAreKept() {
		List<Wardrobe.Entry> merged = Wardrobe.merge(List.of(site("aaaaaaaaaaaa", 1)), List.of(site("bbbbbbbbbbbb", 2)), 10);
		assertEquals(2, merged.size());
	}

	@Test
	void theLaterChangeWinsARemovalIncluded() {
		Wardrobe.Entry kept = site("aaaaaaaaaaaa", 100);
		Wardrobe.Entry removed = site("aaaaaaaaaaaa", 200);
		removed.gone = true;
		assertTrue(find(Wardrobe.merge(List.of(kept), List.of(removed), 300), "site:aaaaaaaaaaaa").gone);
		// Added back after the removal: the later one wins again.
		Wardrobe.Entry back = site("aaaaaaaaaaaa", 250);
		assertFalse(find(Wardrobe.merge(List.of(removed), List.of(back), 300), "site:aaaaaaaaaaaa").gone);
	}

	@Test
	void atTheSameMomentAKeptEntryBeatsARemoval() {
		Wardrobe.Entry kept = site("aaaaaaaaaaaa", 100);
		Wardrobe.Entry removed = site("aaaaaaaaaaaa", 100);
		removed.gone = true;
		assertFalse(find(Wardrobe.merge(List.of(removed), List.of(kept), 300), "site:aaaaaaaaaaaa").gone);
	}

	@Test
	void whatEitherSideLearntIsKeptWhicheverWins() {
		Wardrobe.Entry older = site("aaaaaaaaaaaa", 100);
		older.texture = HASH;
		older.worn = 90;
		Wardrobe.Entry newer = site("aaaaaaaaaaaa", 200);
		newer.name = "Renamed";
		Wardrobe.Entry merged = find(Wardrobe.merge(List.of(older), List.of(newer), 300), "site:aaaaaaaaaaaa");
		assertEquals("Renamed", merged.name);
		assertEquals(HASH, merged.texture);
		assertEquals(90, merged.worn);
	}

	@Test
	void oldRemovalsAreForgotten() {
		Wardrobe.Entry removed = site("aaaaaaaaaaaa", 0);
		removed.gone = true;
		assertTrue(Wardrobe.merge(List.of(removed), List.of(), 91 * DAY).isEmpty());
		assertEquals(1, Wardrobe.merge(List.of(removed), List.of(), 89 * DAY).size());
	}

	@Test
	void malformedEntriesAreDropped() {
		Wardrobe.Entry wrongKey = site("aaaaaaaaaaaa", 1);
		wrongKey.key = "site:bbbbbbbbbbbb";
		Wardrobe.Entry badTexture = Wardrobe.Entry.ofTexture("../../etc", "x", "classic", 1);
		Wardrobe.Entry badArms = site("cccccccccccc", 1);
		badArms.arms = "wide";
		Wardrobe.Entry unknownKind = site("dddddddddddd", 1);
		unknownKind.key = "http:dddddddddddd";
		assertTrue(Wardrobe.merge(List.of(wrongKey, badTexture, badArms, unknownKind), List.of(), 2).isEmpty());
	}

	@Test
	void aFileTravelsOnlyOnceItHasATextureAndWithoutItsFile(@TempDir Path folder) {
		Wardrobe wardrobe = new Wardrobe(folder, "0f2b3c9a-7e3d-4b8e-9a1c-2d3e4f5a6b7c");
		Wardrobe.Entry file = Wardrobe.Entry.ofFile(SHA1, "Mine", "slim", 10);
		wardrobe.add(file);
		assertTrue(wardrobe.travelling().isEmpty());
		wardrobe.worn(file, HASH, "slim", 20);
		List<Wardrobe.Entry> out = wardrobe.travelling();
		assertEquals(1, out.size());
		assertNull(out.get(0).file);
		assertEquals(HASH, out.get(0).texture);
		// And another copy takes it in without the file.
		Wardrobe elsewhere = new Wardrobe(folder.resolve("other"), "0f2b3c9a-7e3d-4b8e-9a1c-2d3e4f5a6b7c");
		assertTrue(elsewhere.mergeIn(Wardrobe.fromJson(wardrobe.travellingJson()), 30));
		assertEquals(HASH, elsewhere.get("file:" + SHA1).texture);
		assertFalse(elsewhere.mergeIn(Wardrobe.fromJson(wardrobe.travellingJson()), 30));
	}

	@Test
	void theWardrobeIsKeptOnDiskPerAccount(@TempDir Path folder) throws Exception {
		// Real times: reading the file drops removals older than ninety days by the clock.
		long now = System.currentTimeMillis();
		Wardrobe one = new Wardrobe(folder, "0f2b3c9a-7e3d-4b8e-9a1c-2d3e4f5a6b7c");
		one.add(site("aaaaaaaaaaaa", now - 3000));
		one.remove("site:aaaaaaaaaaaa", now - 2000);
		one.add(site("bbbbbbbbbbbb", now - 1000));
		Wardrobe again = new Wardrobe(folder, "0f2b3c9a-7e3d-4b8e-9a1c-2d3e4f5a6b7c");
		assertEquals(1, again.shown().size());
		assertTrue(again.has("site:bbbbbbbbbbbb"));
		assertFalse(again.has("site:aaaaaaaaaaaa"));
		// The removal is remembered, so a copy that has not heard of it does not bring it back.
		assertFalse(again.mergeIn(List.of(site("aaaaaaaaaaaa", now - 3000)), now));
		assertFalse(again.has("site:aaaaaaaaaaaa"));
		Wardrobe other = new Wardrobe(folder, "11111111-2222-4333-8444-555555555555");
		assertTrue(other.shown().isEmpty());
		assertTrue(Files.exists(folder.resolve("0f2b3c9a-7e3d-4b8e-9a1c-2d3e4f5a6b7c.json")));
	}

	@Test
	void unreadableJsonIsAnEmptyList() {
		assertTrue(Wardrobe.fromJson("not json").isEmpty());
		assertTrue(Wardrobe.fromJson("{\"entries\":1}").isEmpty());
	}

	@Test
	void theMostRecentlyWornComeFirst(@TempDir Path folder) {
		Wardrobe wardrobe = new Wardrobe(folder, "0f2b3c9a-7e3d-4b8e-9a1c-2d3e4f5a6b7c");
		wardrobe.add(site("aaaaaaaaaaaa", 1));
		wardrobe.add(site("bbbbbbbbbbbb", 2));
		wardrobe.worn(site("aaaaaaaaaaaa", 1), HASH, "classic", 5);
		assertEquals("site:aaaaaaaaaaaa", wardrobe.shown().get(0).key);
	}

	private static JsonObject body(String gender, double bust) {
		JsonObject body = new JsonObject();
		body.addProperty("gender", gender);
		body.addProperty("bust_size", bust);
		return body;
	}

	@Test
	void aBodyIsKeptWithItsSkinAndTravels(@TempDir Path folder) {
		long now = System.currentTimeMillis();
		Wardrobe wardrobe = new Wardrobe(folder, "0f2b3c9a-7e3d-4b8e-9a1c-2d3e4f5a6b7c");
		wardrobe.add(site("aaaaaaaaaaaa", now - 1000));
		wardrobe.bodyOf("site:aaaaaaaaaaaa", body("female", 0.6), now);
		assertEquals(now, wardrobe.get("site:aaaaaaaaaaaa").changed);
		// The same body again is no change, so nothing is sent for it; also as the float the mod holds.
		JsonObject asFloat = new JsonObject();
		asFloat.addProperty("gender", "female");
		asFloat.addProperty("bust_size", 0.6f);
		wardrobe.bodyOf("site:aaaaaaaaaaaa", asFloat, now + 5);
		assertEquals(now, wardrobe.get("site:aaaaaaaaaaaa").changed);
		// Not kept, no body.
		wardrobe.bodyOf("site:bbbbbbbbbbbb", body("male", 0), now);
		assertNull(wardrobe.get("site:bbbbbbbbbbbb"));
		Wardrobe again = new Wardrobe(folder, "0f2b3c9a-7e3d-4b8e-9a1c-2d3e4f5a6b7c");
		assertEquals(body("female", 0.6), again.get("site:aaaaaaaaaaaa").body);
		again.bodyOf("site:aaaaaaaaaaaa", asFloat, now + 10);
		assertEquals(now, again.get("site:aaaaaaaaaaaa").changed);
		Wardrobe.Entry sent = find(Wardrobe.fromJson(again.travellingJson()), "site:aaaaaaaaaaaa");
		assertEquals(body("female", 0.6), sent.body);
	}

	@Test
	void aCopyThatDoesNotKnowBodiesDoesNotLoseOne() {
		Wardrobe.Entry withBody = site("aaaaaaaaaaaa", 100);
		withBody.body = body("female", 0.6);
		// Worn again later in an older mod, which sends the entry without a body.
		Wardrobe.Entry later = site("aaaaaaaaaaaa", 200);
		assertEquals(body("female", 0.6), find(Wardrobe.merge(List.of(withBody), List.of(later), 300), "site:aaaaaaaaaaaa").body);
		assertEquals(body("female", 0.6), find(Wardrobe.merge(List.of(later), List.of(withBody), 300), "site:aaaaaaaaaaaa").body);
		// A later body wins over an earlier one.
		Wardrobe.Entry changed = site("aaaaaaaaaaaa", 300);
		changed.body = body("male", 0);
		assertEquals(body("male", 0), find(Wardrobe.merge(List.of(withBody), List.of(changed), 400), "site:aaaaaaaaaaaa").body);
	}

	@Test
	void aBodyKeepsOnlyWhatIsAboutTheBody() {
		List<Wardrobe.Entry> sent = Wardrobe.fromJson("[{\"key\":\"site:aaaaaaaaaaaa\",\"id\":\"aaaaaaaaaaaa\",\"changed\":1,\"body\":"
			+ "{\"gender\":\"FEMALE\",\"bust_size\":7,\"breast_physics\":\"yes\",\"breasts_uniboob\":false,"
			+ "\"hurt_sounds\":true,\"voice_pitch\":1.1}}]");
		JsonObject kept = find(Wardrobe.merge(List.of(), sent, 2), "site:aaaaaaaaaaaa").body;
		JsonObject expected = new JsonObject();
		expected.addProperty("gender", "female");
		// Past the mod's own limits: kept, as mods such as Over The Limits lift them.
		expected.addProperty("bust_size", 7.0);
		expected.addProperty("voice_pitch", 1.1);
		expected.addProperty("breasts_uniboob", false);
		assertEquals(expected, kept);
		// A number that is no setting is dropped.
		JsonObject broken = new JsonObject();
		broken.addProperty("bust_size", 1e9);
		assertNull(Body.clean(broken));
		// Nothing of a body left: none at all.
		JsonObject nothing = new JsonObject();
		nothing.addProperty("hurt_sounds", true);
		assertNull(Body.clean(nothing));
	}
}

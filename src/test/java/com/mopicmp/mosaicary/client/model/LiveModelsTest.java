package com.mopicmp.mosaicary.client.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The reader against the models on the live site, checked against the counts the
 * site itself keeps for each of them.
 *
 * The files are downloaded by hand into tmp/mosaicary-live (list.json from
 * /api/models and each model's /file as {id}.bbmodel), outside the repository.
 * Without that folder the test skips itself.
 */
class LiveModelsTest {

	private static Path folder() {
		String where = System.getProperty("mosaicary.liveModels");
		return where == null ? null : Path.of(where);
	}

	@Test
	void everyLiveModelReadsAndMatchesTheSiteCounts() throws Exception {
		Path folder = folder();
		assumeTrue(folder != null && Files.exists(folder.resolve("list.json")), "no live models downloaded");
		JsonArray list = JsonParser.parseString(Files.readString(folder.resolve("list.json"))).getAsJsonArray();
		int checked = 0;
		for (JsonElement one : list) {
			JsonObject card = one.getAsJsonObject();
			String id = card.get("id").getAsString();
			Path file = folder.resolve(id + ".bbmodel");
			if (!Files.exists(file)) continue;
			BbModel model = BbParser.parse(Files.readAllBytes(file));

			assertEquals(card.get("format").getAsString(), model.format(), id + " format");
			assertEquals(card.get("version").getAsString(), model.version(), id + " version");
			assertEquals(card.get("cubes").getAsInt(), model.cubes(), id + " cubes");
			assertEquals(card.get("meshes").getAsInt(), model.meshes(), id + " meshes");
			assertEquals(card.get("bones").getAsInt(), model.bones().size(), id + " bones");
			assertEquals(card.get("animations").getAsInt(), model.animations().size(), id + " animations");
			assertEquals(card.get("textures").getAsInt(), model.textures().size(), id + " textures");
			for (Texture texture : model.textures()) {
				assertTrue(texture.embedded(), id + " texture " + texture.index() + " embedded");
				assertTrue(texture.width() > 0 && texture.height() > 0, id + " texture size");
			}
			float[] bounds = model.bounds();
			assertNotNull(bounds, id + " draws something");
			assertTrue(model.quadCount() > 0, id + " has faces");
			BbParserTest.assertCounterClockwise(model);
			System.out.printf("%s: %s %s, %d quads, %d bones, bounds %.1f..%.1f x %.1f..%.1f x %.1f..%.1f%n",
				id, model.format(), model.version(), model.quadCount(), model.bones().size(),
				bounds[0], bounds[3], bounds[1], bounds[4], bounds[2], bounds[5]);
			checked++;
		}
		assertTrue(checked > 0, "no model files beside list.json");
	}

	@Test
	void everyBbmodelInTheFolderParses() throws IOException {
		Path folder = folder();
		assumeTrue(folder != null && Files.isDirectory(folder), "no live models downloaded");
		List<Path> files = new ArrayList<>();
		try (Stream<Path> found = Files.list(folder)) {
			found.filter(p -> p.toString().endsWith(".bbmodel")).forEach(files::add);
		}
		for (Path file : files) {
			try {
				BbModel model = BbParser.parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
				assertTrue(model.quadCount() > 0, file.getFileName() + " has faces");
			} catch (BbParser.NotAModel refused) {
				throw new AssertionError(file.getFileName() + " refused: " + refused.getMessage(), refused);
			}
		}
	}
}

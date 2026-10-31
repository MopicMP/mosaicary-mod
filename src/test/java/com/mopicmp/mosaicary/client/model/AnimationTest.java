package com.mopicmp.mosaicary.client.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Animation sampling: hand-checked cases, and the live models compared with the
 * website's own viewer code.
 */
class AnimationTest {

	private static String project(String version, String keyframes, String loop) {
		return """
			{"meta":{"format_version":"VERSION","model_format":"free"},
			 "elements":[{"type":"cube","uuid":"c","from":[0,0,0],"to":[1,1,1],"faces":{"up":{"uv":[0,0,1,1],"texture":0}}}],
			 "groups":[{"name":"arm","uuid":"g","origin":[0,0,0],"rotation":[10,0,0]}],
			 "outliner":[{"uuid":"g","children":["c"]}],
			 "textures":[{"uuid":"t"}],
			 "animations":[{"name":"wave","uuid":"a","loop":"LOOP","length":2,
			   "animators":{"g":{"name":"arm","type":"bone","keyframes":[KEYS]}}}]}
			""".replace("VERSION", version).replace("LOOP", loop).replace("KEYS", keyframes);
	}

	private static Animation.Change at(BbModel model, double seconds) {
		Map<Bone, Animation.Change> sample = model.animations().get(0).sample(seconds, new HashMap<>(), null);
		return sample.get(model.bone("arm"));
	}

	@Test
	void linearHalfwayAndStepHolds() throws Exception {
		BbModel model = BbParser.parse(project("5.0", """
			{"channel":"rotation","time":0,"interpolation":"linear","data_points":[{"x":0,"y":0,"z":0}]},
			{"channel":"rotation","time":1,"interpolation":"linear","data_points":[{"x":"90","y":"0","z":"30"}]},
			{"channel":"position","time":0,"interpolation":"step","data_points":[{"x":1,"y":2,"z":3}]},
			{"channel":"position","time":1,"interpolation":"step","data_points":[{"x":4,"y":5,"z":6}]}
			""", "loop"));
		Animation.Change half = at(model, 0.5);
		assertNotNull(half);
		assertEquals(45, half.rotation[0], 1e-4);
		assertEquals(15, half.rotation[2], 1e-4);
		// Step holds the earlier key until the next one.
		assertEquals(1, half.position[0], 1e-4);
		assertEquals(4, at(model, 1.5).position[0], 1e-4);
	}

	@Test
	void olderFilesTurnAxesRound() throws Exception {
		String keys = """
			{"channel":"rotation","time":0,"data_points":[{"x":10,"y":20,"z":30}]},
			{"channel":"position","time":0,"data_points":[{"x":1,"y":2,"z":3}]}
			""";
		Animation.Change now = at(BbParser.parse(project("5.0", keys, "loop")), 0);
		Animation.Change old = at(BbParser.parse(project("4.9", keys, "loop")), 0);
		assertEquals(10, now.rotation[0], 1e-4);
		assertEquals(-10, old.rotation[0], 1e-4);
		assertEquals(-20, old.rotation[1], 1e-4);
		assertEquals(30, old.rotation[2], 1e-4);
		assertEquals(-1, old.position[0], 1e-4);
		assertEquals(2, old.position[1], 1e-4);
	}

	@Test
	void molangValuesUseTheAnimationTime() throws Exception {
		BbModel model = BbParser.parse(project("5.0", """
			{"channel":"rotation","time":0,"data_points":[{"x":"math.sin(q.anim_time*90)*10","y":"v.unknown","z":"0"}]}
			""", "loop"));
		Animation.Change change = at(model, 1);
		assertEquals(10, change.rotation[0], 1e-4);
		assertEquals(0, change.rotation[1], 1e-4);
	}

	@Test
	void catmullromMatchesThreeJsSpline() {
		// Four keys, the middle segment at its midpoint: a uniform Catmull-Rom through
		// (0,0) (1,10) (2,10) (3,0) gives 11.25 at the middle of the second segment.
		java.util.List<double[]> points = java.util.List.of(new double[] { 0, 0 }, new double[] { 1, 10 },
			new double[] { 2, 10 }, new double[] { 3, 0 });
		assertEquals(11.25, Animation.splineY(points, 0.5), 1e-9);
		assertEquals(10, Animation.splineY(points, 1 / 3.0), 1e-9);
	}

	@Test
	void loopsOnceAndHold() throws Exception {
		BbModel model = BbParser.parse(project("5.0", """
			{"channel":"rotation","time":0,"data_points":[{"x":0,"y":0,"z":0}]}
			""", "once"));
		Animation clip = model.animations().get(0);
		assertEquals(Animation.Loop.ONCE, clip.loop());
		assertEquals(2, clip.timeInPlay(5), 1e-9);
		// In a preview a clip that does not loop is held for a second, then starts again.
		assertEquals(0.5, clip.timeInPreview(3.5), 1e-9);
	}

	/**
	 * The three live models posed by the mod and by the site's viewer
	 * (tmp/anim-golden.mts writes tmp/mosaicary-live/anim-golden.json): every bone's
	 * local position, rotation and scale at six moments of every animation.
	 */
	@Test
	void liveModelsPoseLikeTheSite() throws Exception {
		String where = System.getProperty("mosaicary.liveModels");
		assumeTrue(where != null && Files.exists(Path.of(where, "anim-golden.json")), "no golden poses");
		JsonObject golden = JsonParser.parseString(Files.readString(Path.of(where, "anim-golden.json"))).getAsJsonObject();
		int compared = 0;
		double worst = 0;
		for (Map.Entry<String, JsonElement> entry : golden.entrySet()) {
			Path file = entry.getKey().startsWith("seed:")
				? Path.of(where).resolveSibling("seed").resolve("files").resolve(entry.getKey().substring(5))
				: Path.of(where, entry.getKey() + ".bbmodel");
			BbModel model = BbParser.parse(Files.readAllBytes(file));
			Map<String, Bone> byUuid = new HashMap<>();
			for (Bone bone : model.bones()) byUuid.put(bone.uuid(), bone);
			JsonArray clips = entry.getValue().getAsJsonArray();
			assertEquals(model.animations().size(), clips.size(), entry.getKey() + " clip count");
			for (int c = 0; c < clips.size(); c++) {
				JsonObject clip = clips.get(c).getAsJsonObject();
				Animation animation = model.animations().get(c);
				assertEquals(clip.get("name").getAsString(), animation.name());
				for (JsonElement s : clip.getAsJsonArray("samples")) {
					JsonObject sample = s.getAsJsonObject();
					double seconds = sample.get("seconds").getAsDouble();
					Map<Bone, Animation.Change> mine = animation.sample(seconds, model.startingValues(), null);
					for (Map.Entry<String, JsonElement> b : sample.getAsJsonObject("bones").entrySet()) {
						Bone bone = byUuid.get(b.getKey());
						assertNotNull(bone, "bone " + b.getKey());
						JsonArray site = b.getValue().getAsJsonArray();
						Animation.Change change = mine.getOrDefault(bone, new Animation.Change());
						float[] offset = bone.offset();
						float[] rotation = bone.rotation();
						float[] scale = bone.scale();
						double[] expected = new double[9];
						for (int i = 0; i < 9; i++) expected[i] = site.get(i).getAsDouble();
						for (int i = 0; i < 3; i++) {
							double position = offset[i] + change.position[i];
							double turn = Math.toRadians(rotation[i] + change.rotation[i]);
							double size = scale[i] * change.scale[i];
							String where2 = entry.getKey() + " " + animation.name() + " @" + seconds + " " + bone.name() + " axis " + i;
							assertEquals(expected[i], position, 1e-3, where2 + " position");
							assertEquals(expected[3 + i], turn, 1e-4, where2 + " rotation");
							assertEquals(expected[6 + i], size, 1e-4, where2 + " scale");
							worst = Math.max(worst, Math.abs(expected[3 + i] - turn));
						}
						compared++;
					}
				}
			}
		}
		assertTrue(compared > 0);
		System.out.printf("compared %d bone poses with the site's viewer, largest rotation gap %.2e rad%n", compared, worst);
	}
}

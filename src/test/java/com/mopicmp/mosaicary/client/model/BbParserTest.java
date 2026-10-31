package com.mopicmp.mosaicary.client.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.imageio.ImageIO;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * The model reader against small hand-made projects whose right answers can be
 * worked out on paper.
 */
class BbParserTest {

	private static final float EPS = 1e-4f;

	static String fixture(String name) throws IOException {
		try (InputStream in = BbParserTest.class.getResourceAsStream("/fixtures/" + name)) {
			assertNotNull(in, "missing fixture " + name);
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	/** A tiny PNG made on the spot, as a data URL. */
	static String png(int width, int height) throws IOException {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) image.setRGB(x, y, 0xFF000000 | (x * 40) << 16 | (y * 40) << 8);
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
	}

	/** Every corner of every quad is in front of its own normal's turning direction. */
	static void assertCounterClockwise(BbModel model) {
		for (BbModel.Placed placed : model.restQuads()) {
			float[] p = placed.positions();
			Vector3f a = new Vector3f(p[0], p[1], p[2]);
			Vector3f b = new Vector3f(p[3], p[4], p[5]);
			Vector3f c = new Vector3f(p[6], p[7], p[8]);
			Vector3f turn = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));
			Vector3f normal = new Vector3f(placed.normal()[0], placed.normal()[1], placed.normal()[2]);
			assertTrue(turn.dot(normal) > 0, "quad runs clockwise against its normal in " + placed.bone());
		}
	}

	static Quad face(Bone bone, float nx, float ny, float nz) {
		for (Quad quad : bone.quads()) {
			if (Math.abs(quad.normal()[0] - nx) < EPS && Math.abs(quad.normal()[1] - ny) < EPS
					&& Math.abs(quad.normal()[2] - nz) < EPS) {
				return quad;
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ cubes

	@Test
	void oneCubeHasSixFacesInPlace() throws Exception {
		BbModel model = BbParser.parse(fixture("one-cube.bbmodel"));
		assertEquals("free", model.format());
		// Six faces, one of them hidden with "texture": null.
		assertEquals(5, model.quadCount());
		assertArrayEquals(new float[] { -4, 0, -4, 4, 8, 4 }, model.bounds(), EPS);
		assertCounterClockwise(model);

		// The top face: corners at y = 8, texture laid out over [0,0]-[8,8] of a 16 px UV space.
		Quad up = face(model.root(), 0, 1, 0);
		assertNotNull(up);
		for (int i = 0; i < 4; i++) assertEquals(8, up.y(i), EPS);
		// Top-left of the UV rectangle sits at the north-west corner of the top face.
		assertEquals(-4, up.x(0), EPS);
		assertEquals(-4, up.z(0), EPS);
		assertEquals(0, up.u(0), EPS);
		assertEquals(0, up.v(0), EPS);
		// The opposite corner of the drawing order is the south-east one at [8,8].
		assertEquals(4, up.x(2), EPS);
		assertEquals(4, up.z(2), EPS);
		assertEquals(0.5f, up.u(2), EPS);
		assertEquals(0.5f, up.v(2), EPS);
	}

	@Test
	void faceRotationTurnsTheUvCorners() throws Exception {
		BbModel model = BbParser.parse(fixture("one-cube.bbmodel"));
		// North is drawn with rotation 90: the corner that showed the top-left of the
		// rectangle now shows its bottom-left.
		Quad north = face(model.root(), 0, 0, -1);
		assertNotNull(north);
		// Corner 0 is the face's top-left seen from outside (x = 4 on the north side).
		assertEquals(4, north.x(0), EPS);
		assertEquals(8, north.y(0), EPS);
		assertEquals(8f / 16, north.u(0), EPS);
		assertEquals(16f / 16, north.v(0), EPS);
	}

	@Test
	void hiddenFaceIsLeftOutAndMissingTextureIsMarked() throws Exception {
		BbModel model = BbParser.parse(fixture("one-cube.bbmodel"));
		// The down face has "texture": null and is not drawn at all.
		assertNull(face(model.root(), 0, -1, 0));
		// East has no texture field: drawn in its marker colour (colour number 2).
		Quad east = face(model.root(), 1, 0, 0);
		assertNotNull(east);
		assertEquals(-1, east.texture());
		assertEquals(0xE8BD7B, east.marker());
	}

	@Test
	void rotatedCubeTurnsAboutItsOrigin() throws Exception {
		String json = """
			{"meta":{"format_version":"4.5","model_format":"free","box_uv":false},
			 "resolution":{"width":16,"height":16},
			 "elements":[{"type":"cube","uuid":"c1","from":[0,0,0],"to":[2,2,2],"origin":[0,0,0],
			   "rotation":[0,90,0],"faces":{"up":{"uv":[0,0,2,2],"texture":0}}}],
			 "outliner":["c1"],
			 "textures":[{"uuid":"t0","width":16,"height":16}]}
			""";
		BbModel model = BbParser.parse(json);
		// A quarter turn about Y takes +X to -Z: the cube now spans x 0..2, z -2..0.
		// Faces missing from the file are drawn in their marker colour, as on the
		// site, so all six count towards the bounds.
		assertEquals(6, model.quadCount());
		assertArrayEquals(new float[] { 0, 0, -2, 2, 2, 0 }, model.bounds(), EPS);
		assertCounterClockwise(model);
	}

	@Test
	void inflateGrowsEverySide() throws Exception {
		String json = """
			{"meta":{"format_version":"5.0","model_format":"free"},
			 "elements":[{"type":"cube","uuid":"c1","from":[0,0,0],"to":[2,4,6],"origin":[0,0,0],"inflate":0.5,
			   "faces":{"north":{"uv":[0,0,1,1],"texture":0},"south":{"uv":[0,0,1,1],"texture":0}}}],
			 "outliner":["c1"],"textures":[{"uuid":"t0"}]}
			""";
		assertArrayEquals(new float[] { -0.5f, -0.5f, -0.5f, 2.5f, 4.5f, 6.5f }, BbParser.parse(json).bounds(), EPS);
	}

	@Test
	void boxUvIsLaidOutFromTheSizeWithOffset() throws Exception {
		BbModel model = BbParser.parse(fixture("box-uv.bbmodel"));
		// A 4 x 6 x 2 cube at uv_offset [10, 20] on a 64 x 32 texture (bedrock: project resolution).
		// Box layout: north starts at [d, d] = [2, 2] and is w x h = 4 x 6 big.
		Quad north = face(model.root().children().get(0), 0, 0, -1);
		assertNotNull(north);
		float margin = 1f / 64;
		float[] us = { north.u(0), north.u(1), north.u(2), north.u(3) };
		float[] vs = { north.v(0), north.v(1), north.v(2), north.v(3) };
		assertEquals((12 + margin) / 64, min(us), EPS);
		assertEquals((16 - margin) / 64, max(us), EPS);
		assertEquals((22 + margin) / 32, min(vs), EPS);
		assertEquals((28 - margin) / 32, max(vs), EPS);
		// Up starts at [d + w, d] = [6, 2] and runs backwards by w and d.
		Quad up = face(model.root().children().get(0), 0, 1, 0);
		assertNotNull(up);
		float[] upU = { up.u(0), up.u(1), up.u(2), up.u(3) };
		float[] upV = { up.v(0), up.v(1), up.v(2), up.v(3) };
		assertEquals((12 + margin) / 64, min(upU), EPS);
		assertEquals((16 - margin) / 64, max(upU), EPS);
		assertEquals((20 + margin) / 32, min(upV), EPS);
		assertEquals((22 - margin) / 32, max(upV), EPS);
	}

	@Test
	void mirroredBoxUvSwapsEastAndWest() throws Exception {
		BbModel model = BbParser.parse(fixture("box-uv.bbmodel"));
		Bone mirrored = model.bone("mirrored");
		assertNotNull(mirrored);
		Quad east = face(mirrored, 1, 0, 0);
		assertNotNull(east);
		// Unmirrored, east sits at [0, d] with size [d, h]; mirrored it takes west's
		// place at [d + w, d] with the width running backwards: u from 8 down to 6.
		float margin = 1f / 64;
		assertEquals((8 - margin) / 64, east.u(0), EPS);
		assertEquals((6 + margin) / 64, east.u(3), EPS);
	}

	// ------------------------------------------------------------------ bones

	@Test
	void nestedGroupsBecomeABoneTree() throws Exception {
		BbModel model = BbParser.parse(fixture("bones.bbmodel"));
		Bone body = model.bone("body");
		Bone arm = model.bone("arm");
		assertNotNull(body);
		assertNotNull(arm);
		assertSame(model.root(), body.parent());
		assertSame(body, arm.parent());
		assertEquals(List.of("body", "arm", "hand"), model.bones().stream().map(Bone::name).toList());
		assertArrayEquals(new float[] { 0, 12, 0 }, body.offset(), EPS);
		assertArrayEquals(new float[] { 4, 10, 0 }, arm.offset(), EPS);
		// Quads are kept in the bone's own space, measured from its pivot.
		Quad armTop = face(arm, 0, 1, 0);
		assertNotNull(armTop);
		for (int i = 0; i < 4; i++) assertEquals(0, armTop.y(i), EPS);
		assertCounterClockwise(model);
	}

	@Test
	void boneRotationMovesItsChildren() throws Exception {
		BbModel model = BbParser.parse(fixture("bones.bbmodel"));
		// The arm (pivot [4, 22, 0], cube from [4, 12, -1] to [6, 22, 1]) is turned
		// 90 degrees about Z, counter-clockwise seen from +Z: the arm that hung down
		// from the pivot now points out to +X, and its old +X side faces up.
		// Relative to the pivot (x, y) becomes (-y, x): x 0..10, y 0..2.
		float maxX = Float.NEGATIVE_INFINITY;
		float minY = Float.POSITIVE_INFINITY;
		for (BbModel.Placed placed : model.restQuads()) {
			if (placed.bone() != model.bone("arm")) continue;
			for (int i = 0; i < 4; i++) {
				maxX = Math.max(maxX, placed.positions()[i * 3]);
				minY = Math.min(minY, placed.positions()[i * 3 + 1]);
			}
		}
		assertEquals(14, maxX, 1e-3);
		assertEquals(22, minY, 1e-3);
	}

	@Test
	void invisibleGroupIsKeptButMarked() throws Exception {
		BbModel model = BbParser.parse(fixture("bones.bbmodel"));
		Bone hand = model.bone("hand");
		assertNotNull(hand);
		assertFalse(hand.visible());
		for (BbModel.Placed placed : model.restQuads()) assertTrue(placed.bone() != hand);
	}

	@Test
	void notExportedPartsAreLeftOutUnlessNothingIsExported() throws Exception {
		String some = """
			{"meta":{"format_version":"5.0","model_format":"free"},
			 "elements":[
			   {"type":"cube","uuid":"a","from":[0,0,0],"to":[1,1,1],"faces":{"up":{"uv":[0,0,1,1],"texture":0}}},
			   {"type":"cube","uuid":"b","export":false,"from":[5,5,5],"to":[6,6,6],"faces":{"up":{"uv":[0,0,1,1],"texture":0}}},
			   {"type":"cube","uuid":"c","visibility":false,"from":[9,9,9],"to":[10,10,10],"faces":{"up":{"uv":[0,0,1,1],"texture":0}}}],
			 "outliner":["a","b","c"],"textures":[{"uuid":"t"}]}
			""";
		// One cube of six faces (five of them missing from the file and so marked).
		assertEquals(6, BbParser.parse(some).quadCount());
		String none = some.replace("\"uuid\":\"a\",", "\"uuid\":\"a\",\"export\":false,");
		// Nothing exported: everything visible is drawn, two cubes.
		assertEquals(12, BbParser.parse(none).quadCount());
	}

	@Test
	void oldLayoutWithWholeGroupsAndContent() throws Exception {
		// Format 3.0: elements under "cubes", groups whole in the outliner, children
		// under "content", and the Z turn of a group written the other way round.
		String json = """
			{"meta":{"format_version":"3.0","bone_rig":true},
			 "resolution":{"width":16,"height":16},
			 "cubes":[{"uuid":"k","from":[0,0,0],"to":[2,2,2],"origin":[0,0,0],
			   "faces":{"up":{"uv":[0,0,2,2],"texture":0}}}],
			 "outliner":[{"name":"old","origin":[0,0,0],"rotation":[0,0,90],"content":["k"]}],
			 "textures":[{"uuid":"t"}]}
			""";
		BbModel model = BbParser.parse(json);
		assertEquals("bedrock_old", model.format());
		Bone old = model.bone("old");
		assertNotNull(old);
		assertEquals(6, old.quads().size());
		assertEquals(-90, old.rotation()[2], EPS);
	}

	// ------------------------------------------------------------------ meshes

	@Test
	void meshTrianglesAndQuads() throws Exception {
		BbModel model = BbParser.parse(fixture("mesh.bbmodel"));
		List<Quad> quads = model.root().children().get(0).quads();
		assertEquals(2, quads.size());
		Quad triangle = quads.stream().filter(q -> q.x(2) == q.x(3) && q.y(2) == q.y(3) && q.z(2) == q.z(3))
			.findFirst().orElse(null);
		assertNotNull(triangle, "a triangle is a quad with its last corner repeated");
		assertCounterClockwise(model);
	}

	@Test
	void bowTieQuadIsReordered() throws Exception {
		BbModel model = BbParser.parse(fixture("mesh.bbmodel"));
		Quad square = null;
		for (Quad quad : model.root().children().get(0).quads()) {
			if (!(quad.x(2) == quad.x(3) && quad.y(2) == quad.y(3) && quad.z(2) == quad.z(3))) square = quad;
		}
		assertNotNull(square);
		// The file lists the square's corners as a bow tie. Drawn in the right order,
		// consecutive corners are always neighbours, never diagonal: each edge is 4 long.
		for (int i = 0; i < 4; i++) {
			int j = (i + 1) % 4;
			float dx = square.x(i) - square.x(j);
			float dz = square.z(i) - square.z(j);
			assertEquals(4, Math.sqrt(dx * dx + dz * dz), 1e-3, "edge " + i);
		}
		// UVs follow their corners: the corner at (4, 0, 4) carries uv [4, 4] of 16.
		for (int i = 0; i < 4; i++) {
			if (Math.abs(square.x(i) - 4) < EPS && Math.abs(square.z(i) - 4) < EPS) {
				assertEquals(0.25f, square.u(i), EPS);
				assertEquals(0.25f, square.v(i), EPS);
			}
		}
	}

	// ------------------------------------------------------------------ textures

	@Test
	void embeddedTexturesAreDecodedAndSized() throws Exception {
		String json = """
			{"meta":{"format_version":"5.0","model_format":"free"},
			 "resolution":{"width":16,"height":16},
			 "elements":[{"type":"cube","uuid":"a","from":[0,0,0],"to":[1,1,1],
			   "faces":{"up":{"uv":[0,0,8,8],"texture":1},"down":{"uv":[0,0,8,8],"texture":0}}}],
			 "outliner":["a"],
			 "textures":[{"uuid":"t0","source":"SRC0"},
			             {"uuid":"t1","source":"SRC1","width":32,"height":32,"uv_width":32,"uv_height":32}]}
			""".replace("SRC0", png(4, 2)).replace("SRC1", png(32, 32));
		BbModel model = BbParser.parse(json);
		assertEquals(2, model.textures().size());
		Texture first = model.textures().get(0);
		assertTrue(first.embedded());
		assertEquals("image/png", first.mime());
		// No size fields: read from the PNG header.
		assertEquals(4, first.width());
		assertEquals(2, first.height());
		assertEquals(16, first.uvWidth(), EPS);
		Texture second = model.textures().get(1);
		assertEquals(32, second.uvWidth(), EPS);
		// The up face uses the second texture, laid out against its own 32 px UV space.
		Quad up = face(model.root(), 0, 1, 0);
		assertNotNull(up);
		assertEquals(1, up.texture());
		assertEquals(0.25f, Math.max(Math.max(up.u(0), up.u(1)), Math.max(up.u(2), up.u(3))), EPS);
	}

	@Test
	void animatedTextureDrawsTheFirstFrame() throws Exception {
		// A 16 x 64 picture laid out as 16 x 16: four frames stacked; v is divided by four.
		String json = """
			{"meta":{"format_version":"5.0","model_format":"free"},
			 "resolution":{"width":16,"height":16},
			 "elements":[{"type":"cube","uuid":"a","from":[0,0,0],"to":[1,1,1],
			   "faces":{"up":{"uv":[0,0,16,16],"texture":0}}}],
			 "outliner":["a"],
			 "textures":[{"uuid":"t0","width":16,"height":64,"uv_width":16,"uv_height":16}]}
			""";
		BbModel model = BbParser.parse(json);
		assertEquals(4, model.textures().get(0).frames());
		Quad up = face(model.root(), 0, 1, 0);
		assertNotNull(up);
		float maxV = Math.max(Math.max(up.v(0), up.v(1)), Math.max(up.v(2), up.v(3)));
		assertEquals(0.25f, maxV, EPS);
	}

	@Test
	void singleTextureFormatPaintsEveryFaceWithOne() throws Exception {
		String json = """
			{"meta":{"format_version":"5.0","model_format":"bedrock"},
			 "resolution":{"width":16,"height":16},
			 "elements":[{"type":"cube","uuid":"a","from":[0,0,0],"to":[1,1,1],
			   "faces":{"up":{"uv":[0,0,1,1],"texture":1},"down":{"uv":[0,0,1,1],"texture":1},
			            "north":{"uv":[0,0,1,1]}}}],
			 "outliner":["a"],
			 "textures":[{"uuid":"t0"},{"uuid":"t1"}]}
			""";
		BbModel model = BbParser.parse(json);
		// Bedrock shows the default texture everywhere. None is marked default, so the
		// one the faces name most often (texture 1) is it, and the face naming none
		// gets it too rather than a marker colour.
		for (Quad quad : model.root().quads()) assertEquals(1, quad.texture());
		assertEquals(6, model.root().quads().size());
	}

	// ------------------------------------------------------------------ refusals

	@Test
	void refusalsSayWhy() {
		assertEquals(BbParser.Why.COMPRESSED,
			assertThrows(BbParser.NotAModel.class, () -> BbParser.parse("<lz>abc")).why());
		assertEquals(BbParser.Why.NOT_JSON,
			assertThrows(BbParser.NotAModel.class, () -> BbParser.parse("{ not json")).why());
		assertEquals(BbParser.Why.NOT_A_PROJECT,
			assertThrows(BbParser.NotAModel.class, () -> BbParser.parse("[1,2]")).why());
		assertEquals(BbParser.Why.NO_META,
			assertThrows(BbParser.NotAModel.class, () -> BbParser.parse("{\"elements\":[]}")).why());
		assertEquals(BbParser.Why.NOTHING_IN_IT,
			assertThrows(BbParser.NotAModel.class, () -> BbParser.parse("{\"meta\":{},\"elements\":[]}")).why());
	}

	static float min(float[] values) {
		float out = Float.POSITIVE_INFINITY;
		for (float value : values) out = Math.min(out, value);
		return out;
	}

	static float max(float[] values) {
		float out = Float.NEGATIVE_INFINITY;
		for (float value : values) out = Math.max(out, value);
		return out;
	}

	static List<Float> list(float[] values) {
		List<Float> out = new ArrayList<>();
		for (float value : values) out.add(value);
		return out;
	}
}

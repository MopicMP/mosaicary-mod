package com.mopicmp.mosaicary.client.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Figura avatars: which groups follow which part, several files as one model, archives. */
class FiguraTest {

	@TempDir
	Path temp;

	@Test
	void groupNamesFollowFigurasOrder() {
		assertEquals(Figura.Attach.HEAD, Figura.attach("Head"));
		assertEquals(Figura.Attach.HEAD, Figura.attach("HEAD_hat"));
		assertEquals(Figura.Attach.LEFT_ARM, Figura.attach("LeftArmLower"));
		// Pivot names are checked before the leg names they begin with.
		assertEquals(Figura.Attach.PIVOT, Figura.attach("LeftLeggingPivot"));
		assertEquals(Figura.Attach.LEFT_LEG, Figura.attach("LeftLegLower"));
		// Case matters, as in Figura.
		assertEquals(Figura.Attach.NONE, Figura.attach("head"));
		assertEquals(Figura.Attach.NONE, Figura.attach("ModelRoot"));
		assertEquals(Figura.Attach.ELSEWHERE, Figura.attach("Hud"));
		assertEquals(Figura.Attach.ELSEWHERE, Figura.attach("?skull"));
		assertEquals(Figura.Attach.LAYER, Figura.attach("Cape"));
		// "Head" comes before "Hud" and its spellings, so this one follows the head.
		assertEquals(Figura.Attach.HEAD, Figura.attach("HeadsUpDisplay"));
		assertEquals(Figura.Attach.NONE, Figura.attach("Camera"));
		assertFalse(Figura.onBody("WorldThing"));
		assertTrue(Figura.onBody("RightItemPivot"));
	}

	private static String group(String name, String uuid, String child, float y) {
		return "{\"name\":\"" + name + "\",\"uuid\":\"" + uuid + "\",\"origin\":[0," + y + ",0],\"children\":[\"" + child + "\"]}";
	}

	private static String cube(String uuid) {
		return "{\"type\":\"cube\",\"uuid\":\"" + uuid
			+ "\",\"from\":[0,0,0],\"to\":[1,1,1],\"faces\":{\"up\":{\"uv\":[0,0,1,1],\"texture\":0}}}";
	}

	/** A free-format file with one cube per named group, a texture kept beside it, and an idle animation. */
	private static String file(String... names) {
		StringBuilder outliner = new StringBuilder();
		StringBuilder elements = new StringBuilder();
		for (int i = 0; i < names.length; i++) {
			String comma = i > 0 ? "," : "";
			outliner.append(comma).append(group(names[i], names[i] + "-g", names[i] + "-c", i));
			elements.append(comma).append(cube(names[i] + "-c"));
		}
		String idle = "{\"name\":\"idle\",\"loop\":\"loop\",\"length\":1,\"animators\":{\"" + names[0]
			+ "-g\":{\"name\":\"" + names[0] + "\",\"type\":\"bone\",\"keyframes\":[{\"channel\":\"rotation\",\"time\":0,"
			+ "\"data_points\":[{\"x\":10,\"y\":0,\"z\":0}]}]}}}";
		return "{\"meta\":{\"format_version\":\"4.10\",\"model_format\":\"free\"},\"resolution\":{\"width\":2,\"height\":2},"
			+ "\"elements\":[" + elements + "],\"outliner\":[" + outliner + "],"
			+ "\"textures\":[{\"uuid\":\"t\",\"name\":\"skin\",\"relative_path\":\"../textures/skin.png\"}],"
			+ "\"animations\":[" + idle + "]}";
	}

	private static byte[] png() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB), "png", out);
		return out.toByteArray();
	}

	private Path avatar() throws IOException {
		Path root = temp.resolve("Avatar");
		Files.createDirectories(root.resolve("models/sub"));
		Files.createDirectories(root.resolve("textures"));
		Files.createDirectories(root.resolve("scripts"));
		Files.writeString(root.resolve("avatar.json"), "{\"name\":\"Test avatar\",\"authors\":[\"Ann\",\"Bo\"],"
			+ "\"description\":\"A test\",\"customizations\":{\"models.models.player.Hat\":{\"visible\":false}}}");
		Files.writeString(root.resolve("models/player.bbmodel"),
			file("Head", "Body", "LeftArm", "RightArm", "LeftLeg", "RightLeg", "LeftLeggingPivot", "Hud", "Cape", "Hat"));
		Files.writeString(root.resolve("models/sub/extra.bbmodel"), file("HEADwear"));
		Files.write(root.resolve("textures/skin.png"), png());
		Files.writeString(root.resolve("script.lua"), "print('hi')");
		Files.writeString(root.resolve("scripts/other.lua"), "-- nothing");
		return root;
	}

	private static Bone bone(BbModel model, String name) {
		Bone found = model.bone(name);
		assertNotNull(found, name);
		return found;
	}

	@Test
	void anAvatarIsAllItsFilesWithFigurasRules() throws Exception {
		Figura.Avatar avatar = Figura.read(avatar());
		BbModel model = avatar.model();
		assertEquals("Test avatar", avatar.name());
		assertEquals(List.of("Ann", "Bo"), avatar.authors());
		assertEquals(2, avatar.scripts());
		assertEquals("figura", model.format());

		// Each file is a group of its own under the root, named after the file.
		List<String> files = model.root().children().stream().map(Bone::name).toList();
		assertEquals(List.of("player", "extra"), files);

		// Drawn on the body or not, by the name and by avatar.json.
		assertTrue(bone(model, "Head").visible());
		assertTrue(bone(model, "LeftLeggingPivot").visible());
		assertFalse(bone(model, "Hud").visible());
		assertFalse(bone(model, "Cape").visible());
		assertFalse(bone(model, "Hat").visible());

		// The texture is not inside the file: it is read from beside it.
		assertTrue(model.textures().get(0).embedded());
		assertEquals(2, model.textures().size());
		assertEquals(1, model.textures().get(1).index());

		// Every group that starts with a part's name follows it, across files.
		Rig rig = Rig.of(model);
		assertEquals(List.of(bone(model, "Head"), bone(model, "HEADwear")), rig.all(Rig.Part.HEAD));
		assertEquals(List.of(bone(model, "LeftLeg")), rig.all(Rig.Part.LEFT_LEG));

		// Two files with an animation of the same name: the second is renamed by its file.
		assertNotNull(model.animation("idle"));
		assertNotNull(model.animation("extra.idle"));
	}

	@Test
	void anArchiveIsUnpackedInsideItsFolderOnly() throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			zip.putNextEntry(new ZipEntry("My Avatar/avatar.json"));
			zip.write("{\"name\":\"Zipped\"}".getBytes(StandardCharsets.UTF_8));
			zip.putNextEntry(new ZipEntry("My Avatar/model.bbmodel"));
			zip.write(file("Body").getBytes(StandardCharsets.UTF_8));
			zip.putNextEntry(new ZipEntry("../escaped.txt"));
			zip.write("no".getBytes(StandardCharsets.UTF_8));
		}
		byte[] zip = bytes.toByteArray();
		assertTrue(Figura.isZip(zip));
		Path into = temp.resolve("cache/one");
		Path found = Figura.unzip(zip, into);
		assertEquals(into.resolve("My Avatar"), found);
		assertFalse(Files.exists(temp.resolve("cache/escaped.txt")));
		assertEquals("Zipped", Figura.read(found).name());
		// A second unpack of a complete folder reads what is there.
		assertEquals(found, Figura.unzip(zip, into));
	}

	@Test
	void anArchiveWithoutAnAvatarIsRefused() throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			zip.putNextEntry(new ZipEntry("model.bbmodel"));
			zip.write(file("Body").getBytes(StandardCharsets.UTF_8));
		}
		assertThrows(IOException.class, () -> Figura.unzip(bytes.toByteArray(), temp.resolve("cache/two")));
	}
}

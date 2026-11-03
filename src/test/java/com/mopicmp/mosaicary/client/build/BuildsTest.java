//#if MC>=12111
// Builds are read and written from 1.21.11 on (client/build/Builds).
package com.mopicmp.mosaicary.client.build;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.mopicmp.mosaicary.client.build.Builds.Format;
import com.mopicmp.mosaicary.client.build.Builds.Structure;

/**
 * The mod's reader and writers of builds against the site's (site/shared/structure.ts).
 *
 * The samples are files the mods themselves saved (tmp/structure-samples: Litematica 5 and
 * 6, Sponge 1, 2 and 3) and a few licensed pack blueprints (tmp/axiom/licensed). The site's
 * writers turn each into all three formats with a list of its blocks beside it
 * ({@code STRUCTURE_OUT=../tmp/structure-out npx vitest run test/write-structures.test.ts}
 * in site/). What is held here: the mod reads every source into the same list the site
 * did, reads every file the site wrote into it too, and its own three writers give it back
 * again. The files it writes are left in tmp/structure-java for the site to read in turn.
 * Without those folders, outside the repository, the tests that need them skip themselves.
 */
class BuildsTest {

	private static Path tmp() {
		String where = System.getProperty("mosaicary.tmp");
		return where == null ? null : Path.of(where);
	}

	/** Every block as "x,y,z state", sorted: the list the site writes beside each sample. */
	static List<String> lines(Structure structure) {
		List<String> out = new ArrayList<>();
		int w = structure.width();
		int l = structure.length();
		int[] blocks = structure.blocks();
		for (int i = 0; i < blocks.length; i++) {
			var parsed = Builds.parseState(structure.palette().get(blocks[i]));
			String name = parsed.getKey();
			String state = Builds.stateText(name, parsed.getValue());
			if (name.equals("minecraft:air") || name.equals("minecraft:cave_air") || name.equals("minecraft:void_air")
				|| name.equals("minecraft:structure_void")) continue;
			out.add((i % w) + "," + (i / (w * l)) + "," + ((i / w) % l) + " " + state);
		}
		out.sort(null);
		return out;
	}

	private static List<Path> sources(Path tmp) throws IOException {
		List<Path> out = new ArrayList<>();
		try (Stream<Path> files = Files.list(tmp.resolve("structure-samples"))) {
			files.filter(one -> one.toString().matches(".*\\.(litematic|schem)$")).sorted().forEach(out::add);
		}
		Path licensed = tmp.resolve("axiom/licensed");
		if (Files.isDirectory(licensed)) {
			try (Stream<Path> files = Files.list(licensed)) {
				files.filter(one -> one.toString().endsWith(".bp")).sorted().limit(3).forEach(out::add);
			}
		}
		return out;
	}

	/** The site's name for a source's outputs: its file name with every run of non-word characters as one underscore. */
	private static String base(Path source) {
		return source.getFileName().toString().replaceAll("\\W+", "_");
	}

	@Test
	void readsEverySampleAsTheSiteDoesAndWritesItBackInAllThree() throws Exception {
		Path tmp = tmp();
		assumeTrue(tmp != null && Files.isDirectory(tmp.resolve("structure-samples")), "no samples in tmp/structure-samples");
		Path siteOut = tmp.resolve("structure-out");
		Path mine = tmp.resolve("structure-java");
		Files.createDirectories(mine);
		int compared = 0;
		for (Path source : sources(tmp)) {
			byte[] bytes = Files.readAllBytes(source);
			Structure structure = Builds.read(bytes);
			List<String> expected = lines(structure);
			assertTrue(!expected.isEmpty(), source + " has blocks");

			Path listed = siteOut.resolve(base(source) + ".expected.txt");
			if (Files.exists(listed)) {
				List<String> site = Files.readAllLines(listed, StandardCharsets.UTF_8).stream().filter(one -> !one.isEmpty()).sorted().toList();
				assertEquals(site, expected, source.getFileName() + ": the mod reads the same blocks as the site");
				compared++;
				// And every file the site wrote from it reads back to the same blocks here.
				for (String extension : List.of("bp", "litematic", "schem", "atroot.schem")) {
					Path written = siteOut.resolve(base(source) + "." + extension);
					assertEquals(expected, lines(Builds.read(Files.readAllBytes(written))), written.getFileName() + " written by the site");
				}
			}

			for (Format format : Format.values()) {
				byte[] out = Builds.write(structure, format, null, 0);
				assertEquals(format, Builds.formatOf(out), source.getFileName() + " as " + format + " is known as one");
				Structure back = Builds.read(out);
				assertEquals(expected, lines(back), source.getFileName() + " through " + format);
				assertEquals(structure.blockEntities().size(), back.blockEntities().size(), source.getFileName() + " block entities through " + format);
				assertEquals(structure.entities().size(), back.entities().size(), source.getFileName() + " entities through " + format);
				for (int i = 0; i < structure.blockEntities().size(); i++) {
					Builds.PlacedBlockEntity a = structure.blockEntities().get(i);
					Builds.PlacedBlockEntity b = back.blockEntities().get(i);
					assertEquals(a.id() + "@" + a.x() + "," + a.y() + "," + a.z() + " " + a.data(),
						b.id() + "@" + b.x() + "," + b.y() + "," + b.z() + " " + b.data(), source.getFileName() + " a block entity through " + format);
				}
				for (int i = 0; i < structure.entities().size(); i++) {
					Builds.PlacedEntity a = structure.entities().get(i);
					Builds.PlacedEntity b = back.entities().get(i);
					assertEquals(a.id() + "@" + a.x() + "," + a.y() + "," + a.z() + " " + a.data(),
						b.id() + "@" + b.x() + "," + b.y() + "," + b.z() + " " + b.data(), source.getFileName() + " an entity through " + format);
				}
				Files.write(mine.resolve(base(source) + "." + format.extension), out);
			}
			Files.writeString(mine.resolve(base(source) + ".expected.txt"), String.join("\n", expected) + "\n");
		}
		System.out.println("builds: " + sources(tmp).size() + " sources, " + compared + " held to the site's lists");
	}

	@Test
	void litematicaPackingRunsAcrossTheEdgesOfLongs() {
		Random random = new Random(7);
		for (int bits = 2; bits <= 13; bits++) {
			int[] indices = new int[1000];
			for (int i = 0; i < indices.length; i++) indices[i] = random.nextInt(1 << bits);
			assertArrayEquals(indices, Builds.unpackLitematic(Builds.packLitematic(indices, bits), bits, indices.length), bits + " bits");
		}
	}

	@Test
	void varintsRoundTrip() throws Exception {
		int[] values = { 0, 1, 127, 128, 255, 300, 16383, 16384, 2_000_000, Integer.MAX_VALUE };
		assertArrayEquals(values, Builds.readVarints(Builds.writeVarints(values), values.length));
		assertThrows(Builds.NotABuild.class, () -> Builds.readVarints(new byte[] { (byte) 0x80 }, 1));
	}

	@Test
	void statesAreWrittenAsTheGameWritesThem() {
		var parsed = Builds.parseState("oak_stairs[waterlogged=false, facing=east,half=bottom]");
		assertEquals("minecraft:oak_stairs", parsed.getKey());
		assertEquals("minecraft:oak_stairs[facing=east,half=bottom,waterlogged=false]", Builds.stateText(parsed.getKey(), parsed.getValue()));
		assertEquals("mod:thing", Builds.stateText("mod:thing", java.util.Map.of()));
	}

	@Test
	void somethingElseIsNotABuild() {
		assertNull(Builds.formatOf(new byte[] { 1, 2, 3, 4 }));
		assertNull(Builds.formatOf(new byte[] { (byte) 0x1F, (byte) 0x8B, 0, 0 }));
		assertNull(Builds.formatOf("{\"meta\":{}}".getBytes(StandardCharsets.UTF_8)));
		assertThrows(Builds.NotABuild.class, () -> Builds.read(new byte[] { 1, 2, 3 }));
	}

	@Test
	void aBlueprintCutShortIsRefusedRatherThanReadHalf() throws Exception {
		byte[] whole = Builds.write(new Structure("one", "", List.of(), 4000, 1, 1, 1,
			List.of("minecraft:air", "minecraft:stone"), new int[] { 1 }, List.of(), List.of()), Format.AXIOM, null, 0);
		assertEquals(1, lines(Builds.read(whole)).size());
		byte[] cut = java.util.Arrays.copyOf(whole, whole.length - 5);
		assertThrows(Builds.NotABuild.class, () -> Builds.read(cut));
	}
}
//#endif

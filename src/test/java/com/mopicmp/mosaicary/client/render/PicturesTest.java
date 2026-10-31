package com.mopicmp.mosaicary.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

/** The shrinking of card pictures, which averages with alpha as the weight. */
class PicturesTest {

	@Test
	void halvesBySquaresOfFour() {
		BufferedImage image = new BufferedImage(4, 2, BufferedImage.TYPE_INT_ARGB);
		// Left square: red and blue, all opaque. Right square: one opaque white pixel, rest clear.
		image.setRGB(0, 0, 0xFFFF0000);
		image.setRGB(1, 0, 0xFFFF0000);
		image.setRGB(0, 1, 0xFF0000FF);
		image.setRGB(1, 1, 0xFF0000FF);
		image.setRGB(2, 0, 0xFFFFFFFF);
		BufferedImage out = Pictures.shrink(image, 2);
		assertEquals(2, out.getWidth());
		assertEquals(1, out.getHeight());
		assertEquals(0xFF7F007F, out.getRGB(0, 0));
		// Clear pixels thin the alpha but do not darken the colour.
		assertEquals(0x3FFFFFFF, out.getRGB(1, 0));
	}

	@Test
	void smallPicturesAreLeftAtTheirSize() {
		BufferedImage image = new BufferedImage(3, 5, BufferedImage.TYPE_INT_ARGB);
		BufferedImage out = Pictures.shrink(image, 256);
		assertEquals(3, out.getWidth());
		assertEquals(5, out.getHeight());
	}

	/**
	 * A card picture as the site serves it (WebP), made into the small PNG an Axiom
	 * blueprint carries when one is written from a schematic. Uses a picture downloaded
	 * into tmp/mosaicary-live; skips without one.
	 */
	@Test
	void aCardPictureBecomesASmallPng() throws Exception {
		String where = System.getProperty("mosaicary.liveModels");
		assumeTrue(where != null && Files.isDirectory(Path.of(where)), "no live models downloaded");
		Path webp;
		try (Stream<Path> files = Files.list(Path.of(where))) {
			webp = files.filter(one -> one.toString().endsWith(".webp")).findFirst().orElse(null);
		}
		assumeTrue(webp != null, "no card picture downloaded");
		byte[] png = Pictures.pngScaled(Files.readAllBytes(webp), 128);
		assertNotNull(png, webp + " did not become a PNG");
		assertEquals(0x89, png[0] & 0xFF);
		assertEquals('P', png[1]);
		BufferedImage back = ImageIO.read(new ByteArrayInputStream(png));
		assertTrue(back.getWidth() <= 128 && back.getHeight() <= 128 && Math.max(back.getWidth(), back.getHeight()) == 128,
			back.getWidth() + "x" + back.getHeight());
	}
}

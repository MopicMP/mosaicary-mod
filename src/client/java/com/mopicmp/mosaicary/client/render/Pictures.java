package com.mopicmp.mosaicary.client.render;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;
import javax.imageio.spi.IIORegistry;

import com.mojang.blaze3d.platform.NativeImage;
import com.mopicmp.mosaicary.Mosaicary;

/**
 * Turns picture bytes into something the game can draw.
 *
 * The game's own decoder (stb_image) reads PNG, which is what Blockbench embeds in
 * its files. The site's card pictures are WebP, which it does not read; those go
 * through ImageIO with the TwelveMonkeys WebP reader and come back as PNG.
 *
 * The WebP reader is registered by hand: ImageIO finds plugins with a service
 * loader over the application class path, and inside a mod loader the decoder's
 * jar is nested inside this mod's jar where that scan does not look.
 */
public final class Pictures {

	private static boolean ready;
	private static boolean broken;

	private Pictures() { }

	private static synchronized void prepare() {
		if (ready || broken) return;
		try {
			System.setProperty("java.awt.headless", "true");
			IIORegistry.getDefaultInstance().registerServiceProvider(
				new com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi());
			ready = true;
		} catch (Throwable missing) {
			broken = true;
			Mosaicary.LOGGER.warn("No WebP decoder available: {}", missing.toString());
		}
	}

	/**
	 * Decodes any picture the game or ImageIO can read. Safe to call off the client
	 * thread: a NativeImage is plain memory until it is uploaded. Returns null when
	 * nothing can read it. The caller owns the image and must close it.
	 */
	public static NativeImage decode(byte[] bytes) {
		if (bytes == null || bytes.length == 0) return null;
		NativeImage image = stb(bytes);
		if (image != null) return image;
		byte[] png = toPng(bytes);
		return png == null ? null : stb(png);
	}

	private static NativeImage stb(byte[] bytes) {
		try {
			return NativeImage.read(bytes);
		} catch (Exception unreadable) {
			// Not a warning: WebP is the ordinary case here and has a second decoder.
			return null;
		}
	}

	/**
	 * Decodes a picture and shrinks it to at most {@code largest} pixels on its
	 * longer side, averaging pixels rather than dropping them, so that a 512 px card
	 * picture shown in a 100-unit card does not shimmer under nearest-neighbour
	 * sampling. Safe off the client thread.
	 */
	public static NativeImage decodeScaled(byte[] bytes, int largest) {
		prepare();
		try {
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
			if (image == null) return decode(bytes);
			BufferedImage flat = shrink(image, largest);
			int w = flat.getWidth();
			int h = flat.getHeight();
			ByteArrayOutputStream out = new ByteArrayOutputStream(w * h);
			ImageIO.write(flat, "png", out);
			return stb(out.toByteArray());
		} catch (Exception unreadable) {
			return decode(bytes);
		}
	}

	/**
	 * A picture as PNG bytes, at most {@code largest} pixels on its longer side, or null
	 * when nothing can read it: the card picture an Axiom blueprint carries when one is
	 * written from a schematic. Safe off the client thread.
	 */
	public static byte[] pngScaled(byte[] bytes, int largest) {
		prepare();
		try {
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
			if (image == null) return null;
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			return ImageIO.write(shrink(image, largest), "png", out) ? out.toByteArray() : null;
		} catch (Exception unreadable) {
			return null;
		}
	}

	/**
	 * Shrinks a picture by averaging every source pixel that falls into each target
	 * pixel, weighted by alpha so that a transparent background does not darken the
	 * model's edges. Done by hand rather than with AWT's scaled instances, which are
	 * produced through the asynchronous image-producer machinery.
	 */
	static BufferedImage shrink(BufferedImage image, int largest) {
		int width = image.getWidth();
		int height = image.getHeight();
		float factor = Math.min(1f, (float) largest / Math.max(width, height));
		int w = Math.max(1, Math.round(width * factor));
		int h = Math.max(1, Math.round(height * factor));
		int[] source = image.getRGB(0, 0, width, height, null, 0, width);
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < h; y++) {
			int y0 = y * height / h;
			int y1 = Math.max(y0 + 1, (y + 1) * height / h);
			for (int x = 0; x < w; x++) {
				int x0 = x * width / w;
				int x1 = Math.max(x0 + 1, (x + 1) * width / w);
				long a = 0;
				long r = 0;
				long g = 0;
				long b = 0;
				for (int sy = y0; sy < y1; sy++) {
					for (int sx = x0; sx < x1; sx++) {
						int argb = source[sy * width + sx];
						int alpha = argb >>> 24;
						a += alpha;
						r += (long) ((argb >> 16) & 255) * alpha;
						g += (long) ((argb >> 8) & 255) * alpha;
						b += (long) (argb & 255) * alpha;
					}
				}
				int count = (y1 - y0) * (x1 - x0);
				int argb = 0;
				if (a > 0) {
					argb = (int) (a / count) << 24 | (int) (r / a) << 16 | (int) (g / a) << 8 | (int) (b / a);
				}
				out.setRGB(x, y, argb);
			}
		}
		return out;
	}

	/** The same picture as a PNG, with its alpha, or null. */
	static byte[] toPng(byte[] bytes) {
		prepare();
		try {
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
			if (image == null) return null;
			BufferedImage flat = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
			flat.getGraphics().drawImage(image, 0, 0, null);
			ByteArrayOutputStream out = new ByteArrayOutputStream(bytes.length * 2);
			ImageIO.write(flat, "png", out);
			return out.toByteArray();
		} catch (Exception unreadable) {
			Mosaicary.LOGGER.debug("Could not decode a picture: {}", unreadable.toString());
			return null;
		}
	}
}

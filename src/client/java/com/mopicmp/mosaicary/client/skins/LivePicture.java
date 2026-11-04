package com.mopicmp.mosaicary.client.skins;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.NativeImage;
import com.mopicmp.mosaicary.Mosaicary;
import com.mopicmp.mosaicary.client.render.Textures;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * A card's picture with the eyes moving: the site's live render (an animated PNG), played on one
 * texture. Each frame is drawn over the last, and the texture is sent to the graphics card only
 * when the moment drawn has changed, which is a few times in a round of about fifteen seconds.
 */
final class LivePicture implements AutoCloseable {

	private static int made;

	/** A frame decoded, off the client thread. */
	record Decoded(Apng.Frame frame, NativeImage image) { }

	private final List<Decoded> frames;
	private final NativeImage canvas;
	private final DynamicTexture texture;
	private final Identifier id;
	private final long started = System.currentTimeMillis();
	private final long round;
	private int shown;

	private LivePicture(List<Decoded> frames) {
		this.frames = frames;
		Decoded first = frames.get(0);
		this.canvas = new NativeImage(first.image().getWidth(), first.image().getHeight(), false);
		this.canvas.copyFrom(first.image());
		this.texture = Textures.of(() -> "Mosaicary live picture", canvas);
		this.id = Mosaicary.id("skins/live-picture/" + (made++));
		Minecraft.getInstance().getTextureManager().register(id, texture);
		long sum = 0;
		for (Decoded one : frames) sum += Math.max(1, one.frame().millis());
		this.round = sum;
	}

	/** The frames of a file, ready to be played; null when there is nothing to move. */
	static List<Decoded> decode(byte[] file) {
		List<Decoded> out = new ArrayList<>();
		try {
			for (Apng.Frame frame : Apng.frames(file)) {
				// Through a local, as the pattern for 1.19.2 misses a call reading a record's component (ClientPatterns).
				byte[] png = frame.png();
				out.add(new Decoded(frame, NativeImage.read(png)));
			}
		} catch (IOException | RuntimeException unreadable) {
			for (Decoded one : out) one.image().close();
			return null;
		}
		if (out.size() < 2) {
			for (Decoded one : out) one.image().close();
			return null;
		}
		return out;
	}

	/** Made on the client thread, as it registers a texture. */
	static LivePicture of(List<Decoded> frames) {
		return new LivePicture(frames);
	}

	int width() {
		return canvas.getWidth();
	}

	int height() {
		return canvas.getHeight();
	}

	/** The texture as it is now; call on the client thread when it is about to be drawn. */
	Identifier frame() {
		long time = (System.currentTimeMillis() - started) % round;
		int now = 0;
		for (long passed = 0; now < frames.size() - 1; now++) {
			passed += Math.max(1, frames.get(now).frame().millis());
			if (time < passed) break;
		}
		if (now != shown) {
			if (now < shown) {
				canvas.copyFrom(frames.get(0).image());
				shown = 0;
			}
			for (int i = shown + 1; i <= now; i++) draw(frames.get(i));
			shown = now;
			texture.upload();
		}
		return id;
	}

	private void draw(Decoded one) {
		int x0 = one.frame().x();
		int y0 = one.frame().y();
		// A local, not one.image(): the preprocessor does not resolve calls on a nested record.
		NativeImage image = one.image();
		for (int y = 0; y < image.getHeight() && y0 + y < canvas.getHeight(); y++) {
			for (int x = 0; x < image.getWidth() && x0 + x < canvas.getWidth(); x++) {
				// Apart: the preprocessor rewrites a call inside another it rewrites as well only once.
				int pixel = image.getPixel(x, y);
				canvas.setPixel(x0 + x, y0 + y, pixel);
			}
		}
	}

	@Override
	public void close() {
		Minecraft.getInstance().getTextureManager().release(id);
		for (Decoded one : frames) one.image().close();
	}
}

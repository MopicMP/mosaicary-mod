package com.mopicmp.mosaicary.client.skins;

import java.util.List;
import java.util.Random;

import com.mojang.blaze3d.platform.NativeImage;
import com.mopicmp.mosaicary.Mosaicary;
import com.mopicmp.mosaicary.client.render.Textures;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * A skin's texture with its eyes moving, for one preview at a time.
 *
 * The texture is the skin enlarged eight times, so a pupil can slide by an eighth of a pixel
 * as on the site rather than jump a whole one; the model's texture coordinates do not care
 * about the size. Only the face is painted again, and only when the moment drawn would look
 * different from the last one.
 */
public final class LiveEyes implements AutoCloseable {

	private static final int SCALE = FreshEyes.STEPS;
	private static final int SIZE = 64 * SCALE;
	private static int made;

	private final FreshEyes.Eyes eyes;
	private final int[] base;
	private final NativeImage image;
	private final DynamicTexture texture;
	private final Identifier id;
	private final Random random = new Random();
	private List<FreshEyes.Key> move;
	private long moveStart;
	private long last = Long.MIN_VALUE;
	private int painted;

	private LiveEyes(FreshEyes.Eyes eyes, int[] pixels) {
		this.eyes = eyes;
		this.base = new int[SIZE * SIZE];
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) base[y * SIZE + x] = pixels[(y / SCALE) * 64 + x / SCALE];
		}
		this.image = new NativeImage(SIZE, SIZE, false);
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) image.setPixel(x, y, base[y * SIZE + x]);
		}
		this.texture = Textures.of(() -> "Mosaicary live eyes", image);
		this.id = Mosaicary.id("skins/live-eyes/" + (made++));
		Minecraft.getInstance().getTextureManager().register(id, texture);
		this.moveStart = System.currentTimeMillis() + gap();
	}

	/** A moving copy of a skin, or null when it carries no copy of its eyes for Fresh Moves. */
	public static LiveEyes of(int[] pixels) {
		FreshEyes.Eyes eyes = FreshEyes.read(pixels);
		return eyes == null ? null : new LiveEyes(eyes, pixels);
	}

	private long gap() {
		return (long) (FreshEyes.GAP_LEAST + random.nextDouble() * FreshEyes.GAP_MORE);
	}

	/** The texture as it is now; call once a frame, on the client thread. */
	public Identifier frame() {
		long now = System.currentTimeMillis();
		FreshEyes.Pose pose = FreshEyes.Pose.REST;
		if (now >= moveStart) {
			if (move == null) move = FreshEyes.move(eyes.rows().length, random);
			double time = now - moveStart;
			if (time >= FreshEyes.length(move)) {
				move = null;
				moveStart = now + gap();
			} else {
				pose = FreshEyes.at(move, time);
			}
		}
		long key = FreshEyes.keyOf(pose, eyes.rows().length);
		if (key != last) {
			last = key;
			paint(pose);
			texture.upload();
			painted++;
		}
		return id;
	}

	/** The face as drawn, then the eyes at this moment over it. */
	private void paint(FreshEyes.Pose pose) {
		int x0 = 8 * SCALE;
		int y0 = 8 * SCALE;
		for (int y = y0; y < y0 + 8 * SCALE; y++) {
			for (int x = x0; x < x0 + 8 * SCALE; x++) image.setPixel(x, y, base[y * SIZE + x]);
		}
		for (FreshEyes.Patch patch : FreshEyes.patches(eyes, pose)) {
			int left = x0 + (int) Math.round(patch.x() * SCALE);
			int top = y0 + (int) Math.round(patch.y() * SCALE);
			int right = x0 + (int) Math.round((patch.x() + patch.w()) * SCALE);
			int bottom = y0 + (int) Math.round((patch.y() + patch.h()) * SCALE);
			int colour = patch.colour() | 0xFF000000;
			for (int y = top; y < bottom; y++) {
				for (int x = left; x < right; x++) image.setPixel(x, y, colour);
			}
		}
	}

	/** How many times the eyes have been painted anew: the first time, then every change. */
	public int painted() {
		return painted;
	}

	@Override
	public void close() {
		Minecraft.getInstance().getTextureManager().release(id);
	}
}

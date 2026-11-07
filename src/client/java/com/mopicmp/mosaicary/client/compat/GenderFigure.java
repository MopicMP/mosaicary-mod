package com.mopicmp.mosaicary.client.compat;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import com.mopicmp.mosaicary.Mosaicary;
import com.mopicmp.mosaicary.client.render.Textures;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Female Gender Mod's breasts on the figures this mod draws itself: the preview of a skin, the
 * wardrobe, the figures beside the menus. They are parts of the figure's own model, under its body,
 * drawn from a picture that is the skin with the faces of the breasts laid out under it, so that
 * they turn, walk and take 3D Skin Layers' cubes with the rest of the figure.
 *
 * Where the breasts sit, how they are tilted for their size and how they bounce is Female Gender
 * Mod's own: adapted from its GenderLayer (setupRender, setupTransformations) and BreastPhysics
 * (update, tickMovement, finishTick) of 5.0.0 for 26.3, which are under the GNU LGPL 3.0, as this
 * mod is. Female Gender Mod: WildfireRomeo, celeste, pupnewfster and its contributors,
 * https://github.com/FemaleGenderMod/FemaleGenderMod . What a figure in a screen does not have
 * (armour, a vehicle, being a baby, swinging an arm) is left out; its walk stands in for moving.
 */
public final class GenderFigure {

	private GenderFigure() { }

	/** The breasts' box, as Female Gender Mod makes it: 4 pixels wide, 5 high and 3 deep. */
	public static final int WIDTH = 4;
	public static final int HEIGHT = 5;
	public static final int DEPTH = 3;

	/** Where the faces of each box lie in the picture: under the skin, the breasts, then their outer layer. */
	public static final int SKIN_V = 64;
	public static final int OVERLAY_V = 80;
	public static final int LEFT_U = 0;
	public static final int RIGHT_U = 16;
	/** The picture's size: the skin over the faces of the breasts. */
	public static final int PICTURE_HEIGHT = 128;

	/**
	 * A body's breasts, from the names a body has (wardrobe.Body), the mod's own defaults where one is
	 * missing; null where the body has none: a gender without them, or too small to draw.
	 */
	public record Shape(float size, float x, float y, float z, boolean uniboob, float cleavage, boolean physics,
			float bounce, float floppiness) {

		public static Shape of(JsonObject body) {
			if (body == null) return null;
			String gender = text(body, "gender", "male");
			if (!gender.equals("female") && !gender.equals("other")) return null;
			Shape shape = new Shape(number(body, "bust_size", 0.6f), number(body, "breasts_xOffset", 0),
				number(body, "breasts_yOffset", 0), number(body, "breasts_zOffset", 0), flag(body, "breasts_uniboob", true),
				number(body, "breasts_cleavage", 0), flag(body, "breast_physics", true), number(body, "bounce_multiplier", 0.333f),
				number(body, "floppy_multiplier", 0.75f));
			return drawn(shape.size()) < 0.02f ? null : shape;
		}
	}

	private static String text(JsonObject body, String name, String otherwise) {
		JsonElement value = body.get(name);
		return value != null && value.isJsonPrimitive() ? value.getAsString().toLowerCase(java.util.Locale.ROOT) : otherwise;
	}

	private static float number(JsonObject body, String name, float otherwise) {
		JsonElement value = body.get(name);
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsFloat() : otherwise;
	}

	private static boolean flag(JsonObject body, String name, boolean otherwise) {
		JsonElement value = body.get(name);
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() ? value.getAsBoolean() : otherwise;
	}

	/** The size the breasts are drawn at for a size set, as Female Gender Mod reckons it. */
	private static float drawn(float size) {
		float drawn = Math.min(size * 1.5f, 0.7f);
		if (size > 0.7f) drawn = size;
		return drawn;
	}

	/**
	 * The bounce of both breasts, ticked twenty times a second as the game ticks an entity, with the
	 * figure's walk for its movement. One for each figure on a screen.
	 */
	public static final class Physics {

		private final Side left = new Side();
		private final Side right = new Side();
		private final Random random = new Random();
		private float lastSeconds = Float.NaN;
		private float ticked;
		private float lastYaw = Float.NaN;
		/** Between the last two ticks, for drawing in between. */
		private float partial;

		/**
		 * Brings the bounce up to {@code seconds}: walking or not, the walk where it is (the game's
		 * limb position, as the figure's walk swings them) and how far the figure has turned, in degrees.
		 */
		public void update(Shape shape, float seconds, boolean walking, float walkPosition, float yaw) {
			if (Float.isNaN(lastSeconds) || seconds < lastSeconds || seconds - lastSeconds > 1) {
				lastSeconds = seconds;
				ticked = seconds;
				lastYaw = yaw;
				left.settle(shape);
				right.settle(shape);
			}
			lastSeconds = seconds;
			while (seconds - ticked >= TICK) {
				ticked += TICK;
				float turned = Float.isNaN(lastYaw) ? 0 : yaw - lastYaw;
				lastYaw = yaw;
				left.tick(shape, random, walking, walkPosition, turned);
				if (shape.uniboob()) right.copy(left);
				else right.tick(shape, random, walking, walkPosition, turned);
			}
			partial = Math.max(0, Math.min(1, (seconds - ticked) / TICK));
		}

		private static final float TICK = 0.05f;
	}

	private static final class Side {
		float bounceVelX;
		float targetBounceX;
		float velocityX;
		float positionX;
		float prePositionX;
		float bounceVel;
		float targetBounceY;
		float velocity;
		float positionY;
		float prePositionY;
		float bounceRotVel;
		float targetRotVel;
		float rotVelocity;
		float bounceRotation;
		float preBounceRotation;
		float breastSize;
		float preBreastSize;

		/** At rest at the shape's size, as when a figure first comes on screen. */
		void settle(Shape shape) {
			breastSize = preBreastSize = shape.size();
		}

		void copy(Side other) {
			bounceVelX = other.bounceVelX;
			targetBounceX = other.targetBounceX;
			velocityX = other.velocityX;
			positionX = other.positionX;
			prePositionX = other.prePositionX;
			bounceVel = other.bounceVel;
			targetBounceY = other.targetBounceY;
			velocity = other.velocity;
			positionY = other.positionY;
			prePositionY = other.prePositionY;
			bounceRotVel = other.bounceRotVel;
			targetRotVel = other.targetRotVel;
			rotVelocity = other.rotVelocity;
			bounceRotation = other.bounceRotation;
			preBounceRotation = other.preBounceRotation;
			breastSize = other.breastSize;
			preBreastSize = other.preBreastSize;
		}

		/** One tick of Female Gender Mod's BreastPhysics.update for a figure standing in place. */
		void tick(Shape shape, Random random, boolean walking, float walkPosition, float turned) {
			prePositionY = positionY;
			prePositionX = positionX;
			preBounceRotation = bounceRotation;
			preBreastSize = breastSize;
			float target = shape.size();
			float weight = target * 1.25f;
			breastSize += breastSize < target ? Math.abs(breastSize - target) / 2 : -Math.abs(breastSize - target) / 2;
			float intensity = target * 3 * Math.round(shape.bounce() * 3 * 100) / 100f;
			if (!shape.uniboob()) intensity *= 0.5f + random.nextFloat() * 2;
			// tickMovement: no movement up or down, the turn of the body for its yaw, and the walk.
			float rotation = -(turned / 15) * intensity;
			targetBounceY = weight;
			targetRotVel = rotation;
			targetBounceX = -rotation / 10;
			if (walking) targetBounceY += (float) Math.cos(walkPosition * 0.6662f + Math.PI) * 0.5f * WALK_SPEED * 0.5f;
			finish(shape);
		}

		private void finish(Shape shape) {
			float percent = shape.floppiness();
			float bounceAmount = Math.max(0.15f, Math.min(0.6f, 0.45f * (1 - percent) + 0.15f));
			float delta = 2.25f - bounceAmount;
			float distanceFromMin = Math.abs(bounceVel + 1.5f) * 0.5f;
			float distanceFromMax = Math.abs(bounceVel - 2.65f) * 0.5f;
			if (bounceVel < -0.5f) targetBounceY += distanceFromMin;
			if (bounceVel > 2.5f) targetBounceY -= distanceFromMax;
			targetBounceY = Math.max(-1.5f, Math.min(2.5f, targetBounceY));
			targetRotVel = Math.max(-25, Math.min(25, targetRotVel));
			velocity = lerp(bounceAmount, velocity, (targetBounceY - bounceVel) * delta);
			bounceVel += velocity * percent * 1.1625f;
			velocityX = lerp(bounceAmount, velocityX, (targetBounceX - bounceVelX) * delta);
			bounceVelX += velocityX * percent;
			rotVelocity = lerp(bounceAmount, rotVelocity, (targetRotVel - bounceRotVel) * delta);
			bounceRotVel += rotVelocity * percent;
			bounceRotation = bounceRotVel;
			positionX = bounceVelX;
			positionY = bounceVel;
			if (positionY < -0.5f) positionY = -0.5f;
			if (positionY > 1.5f) {
				positionY = 1.5f;
				velocity = 0;
			}
		}
	}

	/** How hard the figure's walk swings its limbs, as the game's limb speed: the walk's own (PlayerPose.walk). */
	private static final float WALK_SPEED = 0.7f;

	private static float lerp(float amount, float from, float to) {
		return from + amount * (to - from);
	}

	/**
	 * Poses the breasts, parts of the body, for a shape: Female Gender Mod's setupRender and
	 * setupTransformations, the bounce from {@code physics} where the body has it switched on, and
	 * the slight rise and fall of breathing, {@code seconds} from when the figure came on screen.
	 * Their outer layer sits on them as the mod draws it flat, or, with {@code cubes}, on the box
	 * itself, where 3D Skin Layers' cubes go.
	 */
	public static void pose(ModelPart leftBreast, ModelPart rightBreast, ModelPart leftWear, ModelPart rightWear, Shape shape,
			Physics physics, float seconds, boolean cubes) {
		float partial = physics.partial;
		apply(placed(true, physics.left, shape, partial, seconds, cubes), leftBreast, leftWear);
		apply(placed(false, shape.uniboob() ? physics.left : physics.right, shape, partial, seconds, cubes), rightBreast, rightWear);
	}

	/**
	 * Where one breast sits, in the body's own pixels as the game's model has them (down and to the
	 * back positive), and how it is turned, in radians; its outer layer's shift back and size. Not
	 * visible when too small to draw.
	 */
	public record Placement(boolean visible, float x, float y, float z, float xRot, float yRot, float wearZ, float wearScale) { }

	/** Both breasts of a figure standing still, as on a card of the catalogue: left, then right. */
	public static Placement[] still(Shape shape, boolean cubes) {
		Side side = new Side();
		side.settle(shape);
		return new Placement[] { placed(true, side, shape, 0, 0, cubes), placed(false, side, shape, 0, 0, cubes) };
	}

	private static void apply(Placement placed, ModelPart breast, ModelPart wear) {
		breast.visible = placed.visible();
		if (!placed.visible()) return;
		breast.x = placed.x();
		breast.y = placed.y();
		breast.z = placed.z();
		breast.xRot = placed.xRot();
		breast.yRot = placed.yRot();
		breast.zRot = 0;
		wear.x = 0;
		wear.y = 0;
		wear.z = placed.wearZ();
		//#if MC>=11900
		wear.xScale = placed.wearScale();
		wear.yScale = placed.wearScale();
		wear.zScale = placed.wearScale();
		//#endif
	}

	private static Placement placed(boolean isLeft, Side side, Shape shape, float partial, float seconds, boolean cubes) {
		float sign = isLeft ? 1 : -1;
		float bSize = shape.physics() ? lerp(partial, side.preBreastSize, side.breastSize) : shape.size();
		float positionX = lerp(partial, side.prePositionX, side.positionX);
		float positionY = lerp(partial, side.prePositionY, side.positionY);
		float bounceRotation = lerp(partial, side.preBounceRotation, side.bounceRotation);
		float offsetX = round(shape.x());
		float offsetY = -round(shape.y());
		float offsetZ = -round(shape.z());
		float outward = Math.min(Math.round(shape.cleavage() * 100), 10);
		float breastSize = drawn(bSize);
		if (breastSize < 0.02f) return new Placement(false, 0, 0, 0, 0, 0, 0, 1);
		float zOffset = 0.0625f - bSize * 0.0625f;
		breastSize += 0.5f * Math.abs(bSize - 0.7f) * 2;
		boolean bounce = shape.physics();

		// In blocks, from the body's own place and turn, as Female Gender Mod's pose stack.
		double x = 0;
		double y = 0;
		double z = 0;
		if (bounce) {
			x += positionX / 32;
			y += positionY / 32;
		}
		x += sign * offsetX * 0.0625f;
		y += 0.05625f + offsetY * 0.0625f;
		z += zOffset - 0.125f + offsetZ * 0.0425f;
		double yaw = 0;
		if (bounce) {
			yaw = Math.toRadians(bounceRotation);
			if (!shape.uniboob()) {
				// Turned about the middle of the chest rather than the breast's own corner.
				double pivot = sign * -0.125f;
				x += pivot - Math.cos(yaw) * pivot;
				z += Math.sin(yaw) * pivot;
			}
		}
		float rotation = breastSize;
		if (bounce) {
			y += -0.035f * breastSize;
			rotation -= positionY / 12;
		}
		rotation = Math.min(rotation, breastSize + 0.2f);
		rotation = Math.min(rotation, 1);
		double tilt = Math.toRadians(-35 * rotation);
		// Breathing: the breasts rise and fall a little, Female Gender Mod's ageInTicks * 0.09.
		float breath = -(float) Math.cos(seconds * 20 * 0.09f) * 0.45f + 0.45f;
		tilt += Math.toRadians(breath);
		// The outer layer: a little before the box and larger, as the mod draws it flat; on the box with cubes.
		return new Placement(true, (float) (x * 16), (float) (y * 16), (float) (z * 16), (float) tilt,
			(float) (yaw + Math.toRadians(sign * outward)), cubes ? 0 : -0.015f * 16, cubes ? 1 : 1.05f);
	}

	/** Female Gender Mod's rounding of an offset to one decimal place. */
	private static float round(float value) {
		return Math.round(value * 10) / 10f;
	}

	/**
	 * The picture a figure with breasts is drawn from: the skin, with the faces of both breasts and of
	 * their outer layer laid out under it, as boxes made from {@link #SKIN_V} and {@link #OVERLAY_V}
	 * read them; and for 3D Skin Layers, the same with the jacket under the breasts' fronts cleared,
	 * which its cubes are built from, so that the jacket has none where the breasts are. {@code image}
	 * is at a skin's size; the texture is at the size of the skin's own, and with {@code live} it
	 * follows that one's face.
	 */
	public record Picture(Identifier texture, int[] pixels, int[] cut, NativeImage image, Live live) { }

	/**
	 * For a skin drawn from a texture repainted as it is drawn, live eyes (skins/LiveEyes), which is
	 * eight times a skin so that a pupil can move by less than a pixel: the picture's texture at that
	 * size, and the face as last copied from it, the only part that is repainted.
	 */
	private record Live(DynamicTexture shown, int scale, int[] face) { }

	/** A picture for a skin's texture laid out by a set of layouts. */
	private record Key(Identifier texture, String layouts) { }

	private static final Map<Key, Picture> pictures = new HashMap<>();
	private static final Map<Key, Long> used = new HashMap<>();
	private static final long KEPT_MILLIS = 60_000;
	private static int made;

	/**
	 * The picture for a skin's texture: its pixels read from the texture itself where it is one this
	 * game can read at a skin's size, else from {@code pixels}; the faces laid out by {@code layouts}
	 * (GenderMod.layouts), or the mod's defaults without them. A texture larger than a skin, live eyes,
	 * is followed: the picture's face is copied from it whenever it has changed. Null while neither is there.
	 */
	public static Picture picture(Identifier texture, int[] pixels, int[][][] layouts) {
		long now = System.currentTimeMillis();
		sweep(now);
		layouts = layoutsOr(layouts);
		Key key = new Key(texture, java.util.Arrays.deepToString(layouts));
		NativeImage drawn = imageOf(texture);
		Picture picture = pictures.get(key);
		if (picture == null) {
			int[] skin = pixelsOf(drawn, pixels);
			if (skin == null) return null;
			picture = build(skin, layouts, drawn);
			if (picture == null) return null;
			pictures.put(key, picture);
		}
		if (picture.live() != null) follow(picture.live(), drawn);
		used.put(key, now);
		return picture;
	}

	/** The picture behind a texture this game can read, one made in memory; null for any other. */
	private static NativeImage imageOf(Identifier texture) {
		AbstractTexture drawn = texture == null ? null : Minecraft.getInstance().getTextureManager().getTexture(texture);
		return drawn instanceof DynamicTexture dynamic ? dynamic.getPixels() : null;
	}

	/** How many times a skin's size a texture's picture is: 1 for a skin, 8 for live eyes; 0 for any other shape. */
	private static int scaleOf(NativeImage image) {
		if (image == null || image.getWidth() != image.getHeight() || image.getWidth() % 64 != 0) return 0;
		return image.getWidth() / 64;
	}

	private static int[] pixelsOf(NativeImage drawn, int[] pixels) {
		if (scaleOf(drawn) == 1) {
			int[] out = new int[64 * 64];
			for (int y = 0; y < 64; y++) {
				for (int x = 0; x < 64; x++) out[y * 64 + x] = drawn.getPixel(x, y);
			}
			return out;
		}
		return pixels != null && pixels.length == 64 * 64 ? pixels : null;
	}

	/** The face of a skin, from (8, 8) to (16, 16): all that live eyes repaint. */
	private static final int FACE = 8;

	/** Copies the face of the texture followed into the picture's, and sends it on, when it has changed. */
	private static void follow(Live live, NativeImage drawn) {
		NativeImage shown = live.shown().getPixels();
		int scale = live.scale();
		if (shown == null || scaleOf(drawn) != scale) return;
		boolean changed = false;
		int i = 0;
		for (int y = FACE * scale; y < 2 * FACE * scale; y++) {
			for (int x = FACE * scale; x < 2 * FACE * scale; x++, i++) {
				int colour = drawn.getPixel(x, y);
				if (live.face()[i] == colour) continue;
				live.face()[i] = colour;
				shown.setPixel(x, y, colour);
				changed = true;
			}
		}
		if (changed) live.shown().upload();
	}

	/**
	 * The picture's texture, at a skin's size; or, for a texture larger than a skin, at that size:
	 * the texture as it is drawn now over the faces of the breasts enlarged as much.
	 */
	private static DynamicTexture shown(NativeImage image, NativeImage drawn) {
		int scale = scaleOf(drawn);
		NativeImage shown;
		if (scale > 1) {
			shown = new NativeImage(64 * scale, PICTURE_HEIGHT * scale, true);
			for (int y = 0; y < PICTURE_HEIGHT * scale; y++) {
				for (int x = 0; x < 64 * scale; x++) {
					shown.setPixel(x, y, y < 64 * scale ? drawn.getPixel(x, y) : image.getPixel(x / scale, y / scale));
				}
			}
		} else {
			shown = new NativeImage(64, PICTURE_HEIGHT, true);
			shown.copyFrom(image);
		}
		// The texture keeps this copy and closes it when let go.
		return Textures.of(() -> "Figure with breasts", shown);
	}

	/** The layouts given, or the mod's defaults without them. */
	private static int[][][] layoutsOr(int[][][] layouts) {
		if (layouts != null) return layouts;
		int[][][] overlay = GenderBreasts.overlay();
		return new int[][][] { GenderBreasts.SKIN_LEFT, GenderBreasts.SKIN_RIGHT, overlay[0], overlay[1] };
	}

	/** A skin with the faces of the breasts laid out under it: the picture, its pixels, and the same with the jacket cut. */
	private record Laid(NativeImage image, int[] pixels, int[] cut) { }

	private static Laid lay(int[] skin, int[][][] layouts) {
		try (NativeImage source = new NativeImage(64, 64, true)) {
			for (int y = 0; y < 64; y++) {
				for (int x = 0; x < 64; x++) source.setPixel(x, y, skin[y * 64 + x]);
			}
			NativeImage image = new NativeImage(64, PICTURE_HEIGHT, true);
			image.copyFrom(source);
			int[][][] overlay = { layouts[2], layouts[3] };
			GenderBreasts.lay(source, image, layouts[0], LEFT_U, SKIN_V);
			GenderBreasts.lay(source, image, layouts[1], RIGHT_U, SKIN_V);
			GenderBreasts.lay(source, image, overlay[0], LEFT_U, OVERLAY_V);
			GenderBreasts.lay(source, image, overlay[1], RIGHT_U, OVERLAY_V);
			int[] pixels = new int[64 * PICTURE_HEIGHT];
			for (int y = 0; y < PICTURE_HEIGHT; y++) {
				for (int x = 0; x < 64; x++) pixels[y * 64 + x] = image.getPixel(x, y);
			}
			int[] cut = pixels.clone();
			try (NativeImage cleared = new NativeImage(64, 64, true)) {
				cleared.copyFrom(source);
				GenderBreasts.clearFront(cleared, overlay[0]);
				GenderBreasts.clearFront(cleared, overlay[1]);
				for (int y = 0; y < 64; y++) {
					for (int x = 0; x < 64; x++) cut[y * 64 + x] = cleared.getPixel(x, y);
				}
			}
			return new Laid(image, pixels, cut);
		}
	}

	/**
	 * The pixels of a skin with the faces of the breasts laid out under it, 64 by {@link #PICTURE_HEIGHT},
	 * by {@code layouts} or the mod's defaults: as they are, and with the jacket under the breasts'
	 * fronts cleared, for 3D Skin Layers' cubes. Made off the client thread, for a card's picture.
	 */
	public static int[][] laidOut(int[] skin, int[][][] layouts) {
		Laid laid = lay(skin, layoutsOr(layouts));
		laid.image().close();
		return new int[][] { laid.pixels(), laid.cut() };
	}

	private static Picture build(int[] skin, int[][][] layouts, NativeImage drawn) {
		try {
			Laid laid = lay(skin, layouts);
			NativeImage image = laid.image();
			int[] pixels = laid.pixels();
			int[] cut = laid.cut();
			Identifier texture = Mosaicary.id("gender/figure/" + made++);
			// This image stays for 3D Skin Layers' cubes; the texture has a copy of its own.
			DynamicTexture shown = shown(image, drawn);
			Minecraft.getInstance().getTextureManager().register(texture, shown);
			int scale = scaleOf(drawn);
			Live live = null;
			if (scale > 1) {
				live = new Live(shown, scale, new int[FACE * scale * FACE * scale]);
				int i = 0;
				for (int y = FACE * scale; y < 2 * FACE * scale; y++) {
					for (int x = FACE * scale; x < 2 * FACE * scale; x++) live.face()[i++] = drawn.getPixel(x, y);
				}
			}
			return new Picture(texture, pixels, cut, image, live);
		} catch (RuntimeException failed) {
			Mosaicary.LOGGER.warn("Could not lay out the breasts of a figure: {}", failed.toString());
			return null;
		}
	}

	/** 3D Skin Layers' cubes for the outer layer of both breasts, built once for a picture; null without them. */
	public static Object[] meshes(Picture picture) {
		Object[] both = meshes.get(picture);
		if (both == null) {
			Object left = SkinLayers3d.mesh(picture.image(), WIDTH, HEIGHT, DEPTH, LEFT_U, OVERLAY_V);
			Object right = SkinLayers3d.mesh(picture.image(), WIDTH, HEIGHT, DEPTH, RIGHT_U, OVERLAY_V);
			both = left == null || right == null ? new Object[0] : new Object[] { left, right };
			meshes.put(picture, both);
		}
		return both.length == 0 ? null : both;
	}

	private static final Map<Picture, Object[]> meshes = new java.util.IdentityHashMap<>();

	/** Lets go of the pictures not drawn for a minute. */
	private static void sweep(long now) {
		for (Iterator<Map.Entry<Key, Long>> entries = used.entrySet().iterator(); entries.hasNext();) {
			Map.Entry<Key, Long> entry = entries.next();
			if (now - entry.getValue() < KEPT_MILLIS) continue;
			Picture picture = pictures.remove(entry.getKey());
			if (picture != null) {
				meshes.remove(picture);
				Minecraft.getInstance().getTextureManager().release(picture.texture());
				picture.image().close();
			}
			entries.remove();
		}
	}
}

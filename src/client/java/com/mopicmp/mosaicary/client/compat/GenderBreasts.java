package com.mopicmp.mosaicary.client.compat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mopicmp.mosaicary.Mosaicary;
import com.mopicmp.mosaicary.client.render.Textures;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Female Gender Mod's breasts with 3D Skin Layers' outer layer, which neither mod does on its own:
 * the breasts are boxes of Female Gender Mod's own, not parts of the game's model, so 3D Skin
 * Layers leaves their outer layer flat, and its three-dimensional jacket stands behind them, its
 * cubes sticking out around the breasts and their picture shown twice.
 *
 * So the outer layer of each breast becomes a mesh of cubes built by 3D Skin Layers itself, from a
 * picture of the breast's faces laid out as the faces of a box in a skin, and the jacket is built
 * again from the skin with the fronts of the breasts cut out of it. Both follow the faces the
 * player's own layouts in Female Gender Mod give the breasts. The mixins of each line of Female
 * Gender Mod (client/mixin/GenderLayerMixin) say where to draw them; the breasts' shape, size,
 * physics and offsets stay its own.
 */
public final class GenderBreasts {

	private GenderBreasts() { }

	/** A breast box: 4 pixels wide, 5 high and 3 deep, as Female Gender Mod makes it. */
	private static final int WIDTH = 4;
	private static final int HEIGHT = 5;
	private static final int DEPTH = 3;

	/**
	 * Where each face of a breast box lies in a skin's layout of a box from (0, 0), in the order
	 * Female Gender Mod gives faces to the quads of a layout: east, west, down (the top, as y
	 * points down in a model), up (the underside) and north (the front); x, y, width, height.
	 */
	private static final int[][] FACES = {
		{ DEPTH + WIDTH, DEPTH, DEPTH, HEIGHT },
		{ 0, DEPTH, DEPTH, HEIGHT },
		{ DEPTH, 0, WIDTH, DEPTH },
		{ DEPTH + WIDTH, 0, WIDTH, DEPTH },
		{ DEPTH, DEPTH, WIDTH, HEIGHT },
	};
	private static final int UP = 3;
	private static final int NORTH = 4;

	/** Where the right breast's box is laid out in the picture of both; the left one's is at (0, 0). */
	private static final int RIGHT_U = 16;

	/**
	 * The quads of the outer layer of each breast up to 1.21.1 (Female Gender Mod 2.9 to 3.2), which
	 * has no layouts and takes the faces of its default one: the jacket's front over the chest, with
	 * the side towards the other breast left out (all zero), in the order of {@link #FACES}. The
	 * underside is the jacket's two rows under the front, which those lines draw a pixel higher.
	 */
	private static final int[][] FIXED_LEFT = {
		{ 0, 0, 0, 0 }, { 17, 37, 20, 42 }, { 20, 34, 24, 37 }, { 20, 42, 24, 44 }, { 20, 37, 24, 42 },
	};
	private static final int[][] FIXED_RIGHT = {
		{ 28, 37, 31, 42 }, { 0, 0, 0, 0 }, { 24, 34, 28, 37 }, { 24, 42, 28, 44 }, { 24, 37, 28, 42 },
	};

	/**
	 * The default layouts of 5.0 (Female Gender Mod's UVs) in the same order: the breasts themselves
	 * from the body's front and its sides, and their outer layer, which differs from the older lines'
	 * only in its underside, a pixel lower.
	 */
	public static final int[][] SKIN_LEFT = {
		{ 24, 21, 27, 26 }, { 16, 21, 20, 26 }, { 20, 17, 24, 21 }, { 20, 25, 24, 27 }, { 20, 21, 24, 26 },
	};
	public static final int[][] SKIN_RIGHT = {
		{ 28, 21, 32, 26 }, { 21, 21, 24, 26 }, { 24, 17, 28, 21 }, { 24, 25, 28, 27 }, { 24, 21, 28, 26 },
	};
	public static final int[][] OVERLAY_LEFT = {
		{ 0, 0, 0, 0 }, { 17, 37, 20, 42 }, { 20, 34, 24, 37 }, { 20, 41, 24, 44 }, { 20, 37, 24, 42 },
	};
	public static final int[][] OVERLAY_RIGHT = {
		{ 28, 37, 31, 42 }, { 0, 0, 0, 0 }, { 24, 34, 28, 37 }, { 24, 41, 28, 44 }, { 24, 37, 28, 42 },
	};

	/** The outer layer's layouts the line installed draws with by default: 5.0's, or the older ones' up to 1.21.1. */
	public static int[][][] overlay() {
		return GenderMod.windowInMenus() ? new int[][][] { OVERLAY_LEFT, OVERLAY_RIGHT } : new int[][][] { FIXED_LEFT, FIXED_RIGHT };
	}

	/**
	 * A render state carrying the jacket cut for the player's breasts, from where the breasts are
	 * submitted to where the player model is set up; null in the states of other players. From
	 * 1.21.11 on (mixin/EntityRenderStateMixin).
	 */
	public interface Carrier {
		Object mosaicary$jacket();

		void mosaicary$jacket(Object mesh);
	}

	/** The meshes of both breasts, and the picture they are drawn with. */
	public record Breasts(Identifier texture, Object left, Object right) { }

	private record Key(Identifier skin, int image, String layouts) { }

	private static final class Built {
		final Breasts breasts;
		final Object jacket;
		long used;

		Built(Breasts breasts, Object jacket) {
			this.breasts = breasts;
			this.jacket = jacket;
		}
	}

	/** Kept while used: a picture and three meshes a skin; let go a minute after the skin was last drawn. */
	private static final Map<Key, Built> built = new HashMap<>();
	private static final long KEPT_MILLIS = 60_000;
	private static long swept;
	private static int made;

	/**
	 * The meshes for the outer layer of a skin's breasts, given the layouts of their outer layer:
	 * five quads (x1, y1, x2, y2) of the skin each, in Female Gender Mod's order of faces, each
	 * null or all zero where the face is not drawn. Null without 3D Skin Layers, with its jacket
	 * turned off, or while the skin's pixels are not there.
	 */
	public static Breasts breasts(Identifier skin, int[][] left, int[][] right) {
		Built entry = entry(skin, left, right);
		return entry == null ? null : entry.breasts;
	}

	/** The jacket's mesh for the skin with the fronts of its breasts cut out; null as for {@link #breasts}. */
	public static Object jacket(Identifier skin, int[][] left, int[][] right) {
		Built entry = entry(skin, left, right);
		return entry == null ? null : entry.jacket;
	}

	/**
	 * Up to 1.21.1: the meshes for the outer layer of a player's breasts, from the skin 3D Skin Layers
	 * built the player's own meshes from, and the jacket with them cut out given to it for the player
	 * ({@link #cutTorso}). Null as for {@link #breasts}, or before 3D Skin Layers has built them.
	 */
	public static Breasts fixed(Object player) {
		Identifier skin = SkinLayers3d.builtSkin(player);
		Breasts breasts = breasts(skin, FIXED_LEFT, FIXED_RIGHT);
		if (breasts != null) cutTorso(player, jacket(skin, FIXED_LEFT, FIXED_RIGHT));
		return breasts;
	}

	/** A player's whole jacket as 3D Skin Layers built it, the cut one given in its place, and when the breasts were last drawn. */
	private static final class Cut {
		Object whole;
		Object cut;
		long drawn;
	}

	/** Up to 1.21.1: the players given a cut jacket, until they have the whole one back. */
	private static final Map<Object, Cut> cuts = new WeakHashMap<>();
	private static final long CUT_MILLIS = 250;

	/**
	 * Up to 1.21.1, where 3D Skin Layers keeps a player's jacket on the player and hangs it on the
	 * model for each frame: gives it the jacket with the breasts cut out as they are drawn, so that it
	 * hangs that one from the next frame on. {@link #tick} gives the whole one back once they have not
	 * been drawn for a moment: the body made flat, the jacket hidden or the player out of sight.
	 */
	private static void cutTorso(Object player, Object cut) {
		Object now = SkinLayers3d.torso(player);
		if (cut == null || now == null) return;
		Cut entry = cuts.get(player);
		if (entry == null) {
			entry = new Cut();
			entry.whole = now;
			cuts.put(player, entry);
		} else if (now != entry.cut) {
			// Built again since, from another skin.
			entry.whole = now;
		}
		if (now != cut) SkinLayers3d.torso(player, cut);
		entry.cut = cut;
		entry.drawn = System.currentTimeMillis();
	}

	/** Each tick: gives the whole jacket back to the players whose breasts are no longer drawn ({@link #cutTorso}). */
	public static void tick() {
		if (cuts.isEmpty()) return;
		long now = System.currentTimeMillis();
		for (Iterator<Map.Entry<Object, Cut>> entries = cuts.entrySet().iterator(); entries.hasNext(); ) {
			Map.Entry<Object, Cut> entry = entries.next();
			Cut cut = entry.getValue();
			if (now - cut.drawn <= CUT_MILLIS) continue;
			// Unless 3D Skin Layers has built it again since.
			if (SkinLayers3d.torso(entry.getKey()) == cut.cut) SkinLayers3d.torso(entry.getKey(), cut.whole);
			entries.remove();
		}
	}

	/**
	 * Draws a breast's mesh with the pose at the corner of the breast's box (x, y and z at their
	 * least, in pixels from there to the box's other corner, as Female Gender Mod draws the box
	 * itself), made as 3D Skin Layers makes the jacket's.
	 */
	public static void draw(Object mesh, boolean left, PoseStack pose, VertexConsumer buffer, int light, int overlay, int color) {
		pose.pushPose();
		// The left breast's box reaches from x = -4 to 0, the right one's from 0 to 4. Made wider from
		// x = 0, where they meet, as the jacket is from the middle: from their own middles the two
		// would overlap there, and their fronts flicker in each other.
		pose.translate(0, 0, DEPTH / 2f / 16);
		pose.scale(SkinLayers3d.setting("bodyVoxelWidthSize", 1.05f), 1.035f, SkinLayers3d.setting("baseVoxelSize", 1.15f));
		// The mesh is centred on x and z.
		pose.translate((left ? -WIDTH / 2f : WIDTH / 2f) / 16, 0, 0);
		SkinLayers3d.render(mesh, pose, buffer, light, overlay, color);
		pose.popPose();
	}

	/** Whether 3D Skin Layers draws the player's outer layer this far from the camera ({@code distance} squared, in blocks). */
	public static boolean near(double distanceSquared) {
		float most = SkinLayers3d.setting("renderDistanceLOD", 14);
		return distanceSquared <= most * most;
	}

	private static Built entry(Identifier skin, int[][] left, int[][] right) {
		if (skin == null || !SkinLayers3d.jacket()) return null;
		NativeImage image = SkinLayers3d.skin(skin);
		if (image == null || image.getWidth() != 64 || image.getHeight() != 64) return null;
		long now = System.currentTimeMillis();
		sweep(now);
		Key key = new Key(skin, System.identityHashCode(image), Arrays.deepToString(left) + Arrays.deepToString(right));
		Built entry = built.get(key);
		if (entry == null) {
			entry = build(image, left, right);
			built.put(key, entry);
		}
		entry.used = now;
		return entry;
	}

	private static Built build(NativeImage skin, int[][] left, int[][] right) {
		Breasts breasts = null;
		try {
			NativeImage faces = new NativeImage(64, 64, true);
			lay(skin, faces, left, 0);
			lay(skin, faces, right, RIGHT_U);
			Object leftMesh = SkinLayers3d.mesh(faces, WIDTH, HEIGHT, DEPTH, 0, 0);
			Object rightMesh = SkinLayers3d.mesh(faces, WIDTH, HEIGHT, DEPTH, RIGHT_U, 0);
			Identifier texture = Mosaicary.id("gender/breasts/" + made++);
			// The picture now belongs to the texture, which closes it when let go.
			Minecraft.getInstance().getTextureManager().register(texture, Textures.of(() -> "Breasts' outer layer", faces));
			breasts = leftMesh == null || rightMesh == null ? null : new Breasts(texture, leftMesh, rightMesh);
		} catch (RuntimeException failed) {
			Mosaicary.LOGGER.warn("Could not lay out the breasts' outer layer: {}", failed.toString());
		}
		Object jacket = null;
		try (NativeImage cut = new NativeImage(64, 64, true)) {
			cut.copyFrom(skin);
			clearFront(cut, left);
			clearFront(cut, right);
			// As 3D Skin Layers builds the jacket.
			jacket = SkinLayers3d.mesh(cut, 8, 12, 4, 16, 32);
		} catch (RuntimeException failed) {
			Mosaicary.LOGGER.warn("Could not cut the breasts out of the jacket: {}", failed.toString());
		}
		return new Built(breasts, jacket);
	}

	/** Lays a breast's faces out from {@code u} as the faces of a box in a skin. */
	private static void lay(NativeImage skin, NativeImage faces, int[][] layout, int u) {
		lay(skin, faces, layout, u, 0);
	}

	/**
	 * Lays a breast's faces out from ({@code u}, {@code v}) as the faces of a box in a skin: a box of
	 * four by five by three made there with a texture offset reads them as Female Gender Mod's does.
	 */
	public static void lay(NativeImage skin, NativeImage faces, int[][] layout, int u, int v) {
		if (layout == null) return;
		// Female Gender Mod gives the faces in order to the quads that are there, so a missing one moves the rest up.
		int face = 0;
		for (int[] quad : layout) {
			if (quad == null) continue;
			int[] to = FACES[face];
			// Its underside runs from the front to the back down the quad, a skin's from the back.
			copy(skin, quad, faces, u + to[0], v + to[1], to[2], to[3], face == UP);
			face++;
		}
	}

	/** Copies a quad of the skin onto a rectangle, flipped (a quad given right to left or bottom to top) as the quad says. */
	private static void copy(NativeImage skin, int[] quad, NativeImage faces, int x, int y, int width, int height, boolean upsideDown) {
		int x1 = quad[0];
		int y1 = quad[1];
		int x2 = quad[2];
		int y2 = quad[3];
		if (x1 == x2 || y1 == y2) return;
		for (int j = 0; j < height; j++) {
			int row = upsideDown ? height - 1 - j : j;
			int fromY = along(y1, y2, row, height);
			for (int i = 0; i < width; i++) {
				int fromX = along(x1, x2, i, width);
				if (fromX < 0 || fromY < 0 || fromX >= 64 || fromY >= 64) continue;
				faces.setPixel(x + i, y + j, skin.getPixel(fromX, fromY));
			}
		}
	}

	/** The pixel of a quad's side from {@code from} to {@code to} at step {@code i} of {@code steps}. */
	private static int along(int from, int to, int i, int steps) {
		return from < to ? from + i * (to - from) / steps : from - 1 - i * (from - to) / steps;
	}

	/** Clears the pixels of the skin under a breast's front (the quad of its layout's front face). */
	public static void clearFront(NativeImage skin, int[][] layout) {
		if (layout == null) return;
		int face = 0;
		for (int[] quad : layout) {
			if (quad == null) continue;
			if (face++ != NORTH) continue;
			for (int y = Math.min(quad[1], quad[3]); y < Math.max(quad[1], quad[3]); y++) {
				for (int x = Math.min(quad[0], quad[2]); x < Math.max(quad[0], quad[2]); x++) {
					if (x >= 0 && y >= 0 && x < 64 && y < 64) skin.setPixel(x, y, 0);
				}
			}
		}
	}

	private static void sweep(long now) {
		if (now - swept < KEPT_MILLIS / 4) return;
		swept = now;
		for (Iterator<Built> entries = built.values().iterator(); entries.hasNext(); ) {
			Built entry = entries.next();
			if (now - entry.used <= KEPT_MILLIS) continue;
			if (entry.breasts != null) Minecraft.getInstance().getTextureManager().release(entry.breasts.texture());
			entries.remove();
		}
	}
}

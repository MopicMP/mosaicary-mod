package com.mopicmp.mosaicary.client.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mopicmp.mosaicary.Mosaicary;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.Identifier;

/**
 * The skin's outer layer in three dimensions on the figures the mod draws itself (the wardrobe's
 * preview, the figure beside the menus) when 3D Skin Layers is installed, as that mod shows it on
 * players in the world. Through its API: a mesh of small cubes built from the skin's pixels for
 * each outer part, hung on the part, and the game draws the mesh in the part's place wherever it
 * draws the part. Without the mod, or with one whose API is not this one, the layer stays flat.
 *
 * Reached by reflection, so the mod is no dependency and one set of names serves every version and
 * loader. Its own settings for which parts are made three-dimensional are followed, read as the
 * figure is drawn, so a change there shows at once.
 */
public final class SkinLayers3d {

	private SkinLayers3d() { }

	/**
	 * Each outer part as 3D Skin Layers builds it for a player: the part's name, the box of the
	 * skin it is made from (width, height, depth, u, v), whether it hangs from its top, how far it
	 * is turned, its setting and its place on the part (wide arms, then slim ones).
	 */
	private record Layer(String part, int width, int height, int depth, int u, int v, boolean topPivot, float turn,
			String setting, String offset, String slimOffset) { }

	private static final Layer[] LAYERS = {
		new Layer("hat", 8, 8, 8, 32, 0, false, 0.6f, "enableHat", "HEAD", "HEAD"),
		new Layer("jacket", 8, 12, 4, 16, 32, true, 0, "enableJacket", "BODY", "BODY"),
		new Layer("left_sleeve", 4, 12, 4, 48, 48, true, -2, "enableLeftSleeve", "LEFT_ARM", "LEFT_ARM_SLIM"),
		new Layer("right_sleeve", 4, 12, 4, 40, 32, true, -2, "enableRightSleeve", "RIGHT_ARM", "RIGHT_ARM_SLIM"),
		new Layer("left_pants", 4, 12, 4, 0, 48, true, 0, "enableLeftPants", "LEFT_LEG", "LEFT_LEG"),
		new Layer("right_pants", 4, 12, 4, 0, 32, true, 0, "enableRightPants", "RIGHT_LEG", "RIGHT_LEG"),
	};

	private static boolean looked;
	private static Object meshHelper;
	private static Method create;
	private static Method inject;
	private static Method injected;
	private static Method render;
	private static Method skin;
	private static Field config;
	private static final Map<String, Object> offsets = new java.util.HashMap<>();
	private static final Map<String, Field> settings = new java.util.HashMap<>();

	/** The meshes built for a skin's pixels, wide and slim; dropped with the pixels. */
	private static final Map<int[], Object[][]> built = new WeakHashMap<>();

	/** Whether 3D Skin Layers is there with the API this uses; looked up once. */
	public static boolean available() {
		if (!looked) {
			looked = true;
			try {
				Class<?> api = Class.forName("dev.tr7zw.skinlayers.api.SkinLayersAPI");
				Class<?> mesh = Class.forName("dev.tr7zw.skinlayers.api.Mesh");
				Class<?> offset = Class.forName("dev.tr7zw.skinlayers.api.OffsetProvider");
				meshHelper = api.getMethod("getMeshHelper").invoke(null);
				create = Class.forName("dev.tr7zw.skinlayers.api.MeshHelper").getMethod("create3DMesh", NativeImage.class,
					int.class, int.class, int.class, int.class, int.class, boolean.class, float.class);
				Class<?> injector = Class.forName("dev.tr7zw.skinlayers.accessor.ModelPartInjector");
				inject = injector.getMethod("setInjectedMesh", mesh, offset);
				injected = injector.getMethod("getInjectedMesh");
				render = mesh.getMethod("render", ModelPart.class, PoseStack.class, VertexConsumer.class, int.class, int.class, int.class);
				// How it reads a player's skin; not part of its API, so without it only the breasts stay flat.
				try {
					for (Method method : Class.forName("dev.tr7zw.skinlayers.SkinUtil").getMethods()) {
						if (method.getName().equals("getTexture") && method.getParameterCount() == 2
								&& method.getParameterTypes()[0] == Identifier.class) skin = method;
					}
				} catch (ClassNotFoundException none) {
					skin = null;
				}
				for (Layer layer : LAYERS) {
					offsets.put(layer.offset(), offset.getField(layer.offset()).get(null));
					offsets.put(layer.slimOffset(), offset.getField(layer.slimOffset()).get(null));
				}
				try {
					config = Class.forName("dev.tr7zw.skinlayers.versionless.ModBase").getField("config");
					for (Layer layer : LAYERS) settings.put(layer.setting(), config.getType().getField(layer.setting()));
				} catch (ReflectiveOperationException none) {
					// Settings by other names: every part is made three-dimensional.
					config = null;
				}
			} catch (ClassNotFoundException absent) {
				meshHelper = null;
			} catch (ReflectiveOperationException | RuntimeException | LinkageError other) {
				Mosaicary.LOGGER.warn("3D Skin Layers is installed, but not with the API the wardrobe knows; its figures stay flat: {}",
					other.toString());
				meshHelper = null;
			}
		}
		return meshHelper != null;
	}

	/**
	 * Hangs the skin's three-dimensional outer layer on a figure's outer parts, found by name, or takes
	 * it off: with the pixels not known yet, a part switched off in 3D Skin Layers' settings, or the
	 * mod not there (where nothing is done at all). Done before every frame of the figure is drawn.
	 */
	public static void dress(Function<String, ModelPart> parts, int[] pixels, boolean slim) {
		if (!available()) return;
		// A skin, or a figure's picture with more under it (GenderFigure): 64 wide either way.
		Object[] meshes = pixels == null || (pixels.length != 64 * 64 && pixels.length != 64 * GenderFigure.PICTURE_HEIGHT) ? null
			: meshes(pixels, slim);
		hung = 0;
		for (int i = 0; i < LAYERS.length; i++) {
			Layer layer = LAYERS[i];
			ModelPart part = parts.apply(layer.part());
			if (part == null) continue;
			boolean on = meshes != null && meshes[i] != null && enabled(layer);
			if (on) hung++;
			try {
				inject.invoke(part, on ? meshes[i] : null, on ? offsets.get(slim ? layer.slimOffset() : layer.offset()) : null);
			} catch (ReflectiveOperationException | RuntimeException failed) {
				Mosaicary.LOGGER.warn("3D Skin Layers did not take a layer of the wardrobe's figure; its figures stay flat: {}",
					failed.toString());
				meshHelper = null;
				return;
			}
		}
	}

	private static int hung;

	/** How many outer parts were given a mesh when a figure was last dressed; for the game test. */
	public static int hung() {
		return hung;
	}

	/** Whether the jacket is made three-dimensional in 3D Skin Layers' settings. */
	public static boolean jacket() {
		return available() && enabled(LAYERS[1]);
	}

	/** One of 3D Skin Layers' number settings (the size of its cubes, how far it draws them), or this. */
	public static float setting(String name, float otherwise) {
		if (!available() || config == null) return otherwise;
		try {
			Object values = config.get(null);
			return values == null ? otherwise : config.getType().getField(name).getFloat(values);
		} catch (ReflectiveOperationException | RuntimeException unreadable) {
			return otherwise;
		}
	}

	/**
	 * The mesh 3D Skin Layers builds from a box of a picture as it does for a part of the player:
	 * {@code width} by {@code height} by {@code depth} pixels with the box's faces laid out from
	 * ({@code u}, {@code v}) as in a skin, hanging from its top. Null without the mod or if it fails.
	 */
	public static Object mesh(NativeImage image, int width, int height, int depth, int u, int v) {
		if (!available()) return null;
		try {
			return create.invoke(meshHelper, image, width, height, depth, u, v, true, 0f);
		} catch (ReflectiveOperationException | RuntimeException failed) {
			Mosaicary.LOGGER.warn("3D Skin Layers could not build a mesh: {}", failed.toString());
			return null;
		}
	}

	/** Draws a mesh at the pose: its box centred on x and z, its top at y = 0, in pixels. */
	public static void render(Object mesh, PoseStack pose, VertexConsumer buffer, int light, int overlay, int color) {
		try {
			render.invoke(mesh, null, pose, buffer, light, overlay, color);
		} catch (ReflectiveOperationException | RuntimeException failed) {
			Mosaicary.LOGGER.warn("3D Skin Layers could not draw a mesh: {}", failed.toString());
		}
	}

	/**
	 * A skin's pixels as 3D Skin Layers reads them for its meshes, or null while they are not there
	 * yet or it reads them some other way. The picture is its own and stays open.
	 */
	public static NativeImage skin(Identifier texture) {
		if (!available() || skin == null) return null;
		try {
			return (NativeImage) skin.invoke(null, texture, null);
		} catch (ReflectiveOperationException | RuntimeException unreadable) {
			return null;
		}
	}

	/** Whether 3D Skin Layers has hung a mesh on this part for the frame being drawn. */
	public static boolean hasMesh(ModelPart part) {
		if (!available()) return false;
		try {
			return injected.invoke(part) != null;
		} catch (ReflectiveOperationException | RuntimeException unreadable) {
			return false;
		}
	}

	/** The places of the breasts' meshes on their parts, left and right; made once. */
	private static Object[] breastOffsets;

	/**
	 * Hangs a breast's mesh (GenderFigure) on the part that is its outer layer, or takes it off with
	 * null. On the part's own place and turn, the box's corner, the mesh is put as Female Gender
	 * Mod's own box is drawn in the world with 3D Skin Layers' cubes (GenderBreasts.draw): made wider
	 * from where the two breasts meet, as the jacket is from the middle.
	 */
	public static void hangBreast(ModelPart part, Object mesh, boolean left) {
		if (!available()) return;
		try {
			if (breastOffsets == null) {
				Class<?> provider = Class.forName("dev.tr7zw.skinlayers.api.OffsetProvider");
				breastOffsets = new Object[2];
				for (int side = 0; side < 2; side++) {
					float x = (side == 0 ? -GenderFigure.WIDTH : GenderFigure.WIDTH) / 2f;
					breastOffsets[side] = java.lang.reflect.Proxy.newProxyInstance(provider.getClassLoader(), new Class<?>[] { provider },
						(proxy, method, args) -> {
							if (method.getName().equals("applyOffset")) {
								PoseStack stack = (PoseStack) args[0];
								stack.translate(0, 0, GenderFigure.DEPTH / 2f / 16);
								stack.scale(setting("bodyVoxelWidthSize", 1.05f), 1.035f, setting("baseVoxelSize", 1.15f));
								args[1].getClass().getMethod("setPosition", float.class, float.class, float.class).invoke(args[1], x, 0f, 0f);
								return null;
							}
							return switch (method.getName()) {
								case "equals" -> proxy == args[0];
								case "hashCode" -> System.identityHashCode(proxy);
								default -> "breast offset";
							};
						});
				}
			}
			inject.invoke(part, mesh, mesh == null ? null : breastOffsets[left ? 0 : 1]);
		} catch (ReflectiveOperationException | RuntimeException failed) {
			Mosaicary.LOGGER.warn("3D Skin Layers did not take a breast's layer: {}", failed.toString());
		}
	}

	/** Hangs a mesh made like the jacket's on the part in the jacket's place. */
	public static void hangJacket(ModelPart part, Object mesh) {
		if (!available()) return;
		try {
			inject.invoke(part, mesh, offsets.get(LAYERS[1].offset()));
		} catch (ReflectiveOperationException | RuntimeException failed) {
			Mosaicary.LOGGER.warn("3D Skin Layers did not take a jacket: {}", failed.toString());
		}
	}

	private static boolean lookedAtPlayers;
	private static Class<?> playerSettings;
	private static Method torso;
	private static Method setTorso;
	private static Method builtSkin;

	/**
	 * Whether 3D Skin Layers keeps the meshes of a player's outer layer on the player, as it does up
	 * to 1.21.1, and hangs them on the model for each frame it is drawn. Not part of its API.
	 */
	private static boolean keepsPlayers() {
		if (!lookedAtPlayers) {
			lookedAtPlayers = true;
			if (available()) {
				try {
					Class<?> settings = Class.forName("dev.tr7zw.skinlayers.accessor.PlayerSettings");
					torso = settings.getMethod("getTorsoMesh");
					setTorso = settings.getMethod("setTorsoMesh", Class.forName("dev.tr7zw.skinlayers.api.Mesh"));
					builtSkin = settings.getMethod("getCurrentSkin");
					playerSettings = settings;
				} catch (ReflectiveOperationException | LinkageError none) {
					playerSettings = null;
				}
			}
		}
		return playerSettings != null;
	}

	/** The jacket's mesh 3D Skin Layers keeps for a player; null where it keeps none (see {@link #keepsPlayers}). */
	public static Object torso(Object player) {
		if (!keepsPlayers() || !playerSettings.isInstance(player)) return null;
		try {
			return torso.invoke(player);
		} catch (ReflectiveOperationException | RuntimeException unreadable) {
			return null;
		}
	}

	/** Gives a player another mesh for the jacket, which 3D Skin Layers hangs on the model from the next frame on. */
	public static void torso(Object player, Object mesh) {
		if (!keepsPlayers() || !playerSettings.isInstance(player)) return;
		try {
			setTorso.invoke(player, mesh);
		} catch (ReflectiveOperationException | RuntimeException failed) {
			Mosaicary.LOGGER.warn("3D Skin Layers did not take a jacket: {}", failed.toString());
		}
	}

	/** The skin 3D Skin Layers built a player's meshes from; null before it has, or where it keeps none. */
	public static Identifier builtSkin(Object player) {
		if (!keepsPlayers() || !playerSettings.isInstance(player)) return null;
		try {
			return (Identifier) builtSkin.invoke(player);
		} catch (ReflectiveOperationException | RuntimeException unreadable) {
			return null;
		}
	}

	private static boolean enabled(Layer layer) {
		if (config == null) return true;
		try {
			Object values = config.get(null);
			return values == null || settings.get(layer.setting()).getBoolean(values);
		} catch (ReflectiveOperationException | RuntimeException unreadable) {
			return true;
		}
	}

	/** The six meshes for a skin, built once for each of its arm widths. */
	private static Object[] meshes(int[] pixels, boolean slim) {
		Object[][] both = built.computeIfAbsent(pixels, key -> new Object[2][]);
		int which = slim ? 1 : 0;
		if (both[which] == null) both[which] = build(pixels, slim);
		return both[which];
	}

	private static Object[] build(int[] pixels, boolean slim) {
		Object[] meshes = new Object[LAYERS.length];
		int height = pixels.length / 64;
		try (NativeImage image = new NativeImage(64, height, false)) {
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < 64; x++) image.setPixel(x, y, pixels[y * 64 + x]);
			}
			for (int i = 0; i < LAYERS.length; i++) {
				Layer layer = LAYERS[i];
				// A slim arm is a pixel narrower.
				int width = slim && layer.part().endsWith("_sleeve") ? 3 : layer.width();
				meshes[i] = create.invoke(meshHelper, image, width, layer.height(), layer.depth(), layer.u(), layer.v(),
					layer.topPivot(), layer.turn());
			}
		} catch (ReflectiveOperationException | RuntimeException failed) {
			Mosaicary.LOGGER.warn("3D Skin Layers could not build the layer of a skin in the wardrobe: {}", failed.toString());
			return new Object[LAYERS.length];
		}
		return meshes;
	}
}

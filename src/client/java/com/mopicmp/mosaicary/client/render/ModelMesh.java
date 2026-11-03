//#if MC>=12111
// Worn models and their catalogue are built from 1.21.11 on (docs/mod-versions-plan.md).
package com.mopicmp.mosaicary.client.render;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.joml.Matrix4f;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mopicmp.mosaicary.Mosaicary;
import com.mopicmp.mosaicary.client.model.BbModel;
import com.mopicmp.mosaicary.client.model.Bone;
import com.mopicmp.mosaicary.client.model.Posing;
import com.mopicmp.mosaicary.client.model.Quad;
import com.mopicmp.mosaicary.client.model.Texture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * A parsed model made drawable: its textures uploaded, its faces grouped by bone
 * and by texture, ready to be handed to the game's renderer.
 *
 * <h2>One path for the screen and the world</h2>
 *
 * Drawing goes through {@link SubmitNodeCollector#submitCustomGeometry}, which is
 * what the game's own entity renderers submit to. A picture-in-picture preview in
 * a screen gets a collector, and so does an entity renderer in the world, so the
 * same mesh serves the catalogue preview now and a worn model later.
 *
 * <h2>Bones stay separate</h2>
 *
 * Each bone is submitted with its own transform on the pose stack, built from its
 * rest pivot and rotation plus whatever a {@link Posing} adds this frame. An
 * animation therefore turns a bone by changing a few numbers; no vertex is rebuilt.
 *
 * Create and close on the client thread: textures are registered with the game.
 */
public final class ModelMesh implements AutoCloseable {

	/** Faces of one bone that share one texture and so one draw. */
	private record Batch(RenderType type, List<Quad> quads, boolean marker) { }

	private static Identifier white;

	private final BbModel model;
	private final List<Identifier> registered = new ArrayList<>();
	private final Map<Bone, List<Batch>> batches = new IdentityHashMap<>();
	private final float[] bounds;
	private boolean closed;

	private ModelMesh(BbModel model) {
		this.model = model;
		float[] box = model.bounds();
		this.bounds = box != null ? box : new float[] { -8, 0, -8, 8, 16, 8 };
	}

	/**
	 * The model's pictures decoded and waiting to be uploaded. Made off the client
	 * thread, since decoding is the slow part; {@link #upload} then only registers.
	 */
	public static final class Prepared {
		private final BbModel model;
		private final List<NativeImage> images = new ArrayList<>();
		private final List<Boolean> translucent = new ArrayList<>();

		private Prepared(BbModel model) {
			this.model = model;
		}

		public BbModel model() {
			return model;
		}

		/**
		 * Registers the textures and groups the faces. Client thread only.
		 *
		 * @param key a short name unique among the models alive at the same time,
		 *            used in the texture ids
		 */
		public ModelMesh upload(String key) {
			ModelMesh mesh = new ModelMesh(model);
			String safe = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
			List<Identifier> ids = new ArrayList<>();
			for (int i = 0; i < images.size(); i++) {
				NativeImage image = images.get(i);
				if (image == null) {
					ids.add(null);
					continue;
				}
				Identifier id = Mosaicary.id("model/" + safe + "/" + i);
				Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "Mosaicary " + safe, image));
				mesh.registered.add(id);
				ids.add(id);
			}
			images.clear();
			mesh.group(model.root(), ids, translucent);
			for (Bone bone : model.bones()) mesh.group(bone, ids, translucent);
			return mesh;
		}

		/** Frees the decoded pictures when the upload is not going to happen. */
		public void discard() {
			for (NativeImage image : images) if (image != null) image.close();
			images.clear();
		}
	}

	/** Decodes the model's embedded pictures. Safe off the client thread. */
	public static Prepared prepare(BbModel model) {
		Prepared prepared = new Prepared(model);
		for (Texture texture : model.textures()) {
			NativeImage image = texture.embedded() ? Pictures.decode(texture.image()) : null;
			prepared.images.add(image);
			prepared.translucent.add(image != null && hasPartialAlpha(image));
		}
		return prepared;
	}

	private void group(Bone bone, List<Identifier> ids, List<Boolean> translucent) {
		Map<String, Batch> byKey = new LinkedHashMap<>();
		for (Quad quad : bone.quads()) {
			int index = quad.texture();
			Identifier id = index >= 0 && index < ids.size() ? ids.get(index) : null;
			boolean marker = id == null;
			if (marker) id = white();
			boolean clear = !marker && translucent.get(index);
			String key = id + "|" + quad.doubleSided() + "|" + clear;
			Identifier texture = id;
			Batch batch = byKey.computeIfAbsent(key, k -> new Batch(typeOf(texture, quad.doubleSided(), clear),
				new ArrayList<>(), marker));
			batch.quads().add(quad);
		}
		if (!byKey.isEmpty()) batches.put(bone, List.copyOf(byKey.values()));
	}

	private static RenderType typeOf(Identifier texture, boolean doubleSided, boolean translucent) {
		if (translucent) return doubleSided ? RenderTypes.entityTranslucent(texture) : RenderTypes.entityTranslucentCull(texture);
		return doubleSided ? RenderTypes.entityCutout(texture) : RenderTypes.entityCutoutCull(texture);
	}

	/** Whether any pixel is neither fully clear nor fully solid, which needs blending. */
	private static boolean hasPartialAlpha(NativeImage image) {
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int alpha = image.getPixel(x, y) >>> 24;
				if (alpha > 0 && alpha < 255) return true;
			}
		}
		return false;
	}

	/** A one-pixel white texture for faces painted in a flat marker colour. */
	private static Identifier white() {
		if (white == null) {
			NativeImage image = new NativeImage(1, 1, false);
			image.setPixel(0, 0, 0xFFFFFFFF);
			white = Mosaicary.id("model/white");
			Minecraft.getInstance().getTextureManager().register(white, new DynamicTexture(() -> "Mosaicary white", image));
		}
		return white;
	}

	public BbModel model() {
		return model;
	}

	/** The box around the model at rest, in model pixels: min x, y, z, max x, y, z. */
	public float[] bounds() {
		return bounds.clone();
	}

	/**
	 * Submits the whole model with the pose stack as it stands, in model pixels.
	 * Scale the stack by 1/16 first to draw it at block size.
	 */
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, Posing posing) {
		submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, posing);
	}

	/**
	 * The same with an overlay, which is how the game tints an entity red when it is
	 * hurt; pass {@link OverlayTexture#NO_OVERLAY} for none.
	 */
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, Posing posing) {
		if (closed) return;
		submitBone(model.root(), poseStack, collector, light, overlay, posing);
	}

	private void submitBone(Bone bone, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay,
			Posing posing) {
		if (!bone.visible()) return;
		poseStack.pushPose();
		if (!bone.isRoot()) {
			float[] rotation = new float[3];
			float[] offset = new float[3];
			float[] scale = { 1, 1, 1 };
			if (posing != null) posing.pose(bone, rotation, offset, scale);
			poseStack.mulPose(bone.local(rotation, offset, scale, new Matrix4f()));
		}
		List<Batch> mine = batches.get(bone);
		if (mine != null) {
			for (Batch batch : mine) {
				collector.submitCustomGeometry(poseStack, batch.type(), (pose, buffer) -> emit(batch, pose, buffer, light, overlay));
			}
		}
		for (Bone child : bone.children()) submitBone(child, poseStack, collector, light, overlay, posing);
		poseStack.popPose();
	}

	private static void emit(Batch batch, PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay) {
		for (Quad quad : batch.quads()) {
			int colour = batch.marker() ? 0xFF000000 | quad.marker() : 0xFFFFFFFF;
			float[] n = quad.normal();
			for (int i = 0; i < 4; i++) {
				buffer.addVertex(pose, quad.x(i), quad.y(i), quad.z(i))
					.setColor(colour)
					.setUv(batch.marker() ? 0.5f : quad.u(i), batch.marker() ? 0.5f : quad.v(i))
					.setOverlay(overlay)
					.setLight(light)
					.setNormal(pose, n[0], n[1], n[2]);
			}
		}
	}

	@Override
	public void close() {
		if (closed) return;
		closed = true;
		for (Identifier id : registered) Minecraft.getInstance().getTextureManager().release(id);
		registered.clear();
	}
}
//#endif

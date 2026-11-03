//#if MC>=12111
// Worn models and their catalogue are built from 1.21.11 on (docs/mod-versions-plan.md).
package com.mopicmp.mosaicary.client.render;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
//#if MC<260100
//$$ import net.minecraft.client.renderer.MultiBufferSource;
//$$ import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
//#endif

/**
 * Draws a model preview into its own texture, which the screen then shows.
 *
 * This is the game's picture-in-picture mechanism, the one that draws the player
 * in the inventory: a small offscreen target with its own depth buffer, so the
 * model is drawn in real 3D and sorted correctly, and then blitted into the GUI.
 *
 * The camera is placed the way the site places it for a card picture, so a model
 * opened at its card's angle can be compared with the card.
 */
public final class PreviewRenderer extends PictureInPictureRenderer<PreviewState> {

	/** Full brightness, the packed light value the inventory draws entities with. */
	private static final int FULL_BRIGHT = 0xF000F0;

	//#if MC<260100
	//$$ public PreviewRenderer(MultiBufferSource.BufferSource buffers) {
	//$$ 	super(buffers);
	//$$ }
	//#endif

	@Override
	public Class<PreviewState> getRenderStateClass() {
		return PreviewState.class;
	}

	@Override
	//#if MC>=260100
	protected void renderToTexture(PreviewState state, PoseStack poseStack, SubmitNodeCollector collector) {
	//#else
	//$$ protected void renderToTexture(PreviewState state, PoseStack poseStack) {
	//$$ 	// Before 26.1 the renderer collects the model's parts itself and draws them at the end.
	//$$ 	FeatureRenderDispatcher features = Minecraft.getInstance().gameRenderer.getFeatureRenderDispatcher();
	//$$ 	SubmitNodeCollector collector = features.getSubmitNodeStorage();
	//#endif
		Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
		// The stack arrives scaled to GUI units with the origin in the middle of the
		// box and y pointing down the screen. A half turn about Z stands the model
		// up the right way, and leaves the eye looking from -Z towards +Z.
		poseStack.rotate(Axis.ZP.rotation((float) Math.PI));
		// Then the model is turned so that the direction the site's camera would
		// stand in (spin around Y, tilt above) points at the eye.
		poseStack.rotate(Axis.XP.rotation(-state.tilt()));
		poseStack.rotate(Axis.YP.rotation((float) Math.PI - state.spin()));
		float[] center = state.center();
		poseStack.translate(-center[0], -center[1], -center[2]);
		state.mesh().submit(poseStack, collector, FULL_BRIGHT, state.posing());
		//#if MC<260100
		//$$ features.renderAllFeatures();
		//#endif
	}

	@Override
	protected float getTranslateY(int height, int guiScale) {
		return height / 2.0f;
	}

	@Override
	protected String getTextureLabel() {
		return "mosaicary model preview";
	}
}
//#endif

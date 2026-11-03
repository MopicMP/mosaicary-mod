package com.mopicmp.mosaicary.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mopicmp.mosaicary.client.model.Motion;
import com.mopicmp.mosaicary.client.wear.Wearing;

import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.UvMapping;

/**
 * Draws a worn model in place of the player model.
 *
 * Only the one call that submits the game's model is replaced. Everything around it
 * stays the game's: the body's turn towards where the player faces, lying in a bed,
 * swimming and elytra flight, the death tilt, invisibility (no model is submitted for
 * an invisible player, so neither is the worn one), the hurt tint, the shadow and the
 * name tag.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

	/** The player model is drawn at fifteen sixteenths; a worn model keeps its own size. */
	private static final float PLAYER_SCALE = 0.9375f;

	// The method itself and not the bridge the compiler adds for the generic state, which has the
	// same name; the preprocessor refuses a name that could be either.
	@WrapOperation(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;"
		+ "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;"
		+ "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V"))
	private void mosaicary$drawWorn(SubmitNodeCollector collector, Model<?> model, Object state, PoseStack poseStack,
			RenderType renderType, int light, int overlay, int tint, UvMapping uvMapping, int outline,
			Operation<Void> original) {
		Wearing.Worn worn = state instanceof EntityRenderState entity ? Wearing.wornOf(entity) : null;
		if (worn == null) {
			original.call(collector, model, state, poseStack, renderType, light, overlay, tint, uvMapping, outline);
			return;
		}
		poseStack.pushPose();
		// Undo what the game did to reach its model's space (feet 1.5 blocks down,
		// x and y pointing the other way, fifteen sixteenths), then go from model
		// pixels to blocks. What is left is the player's position and facing.
		poseStack.translate(0.0f, 1.501f, 0.0f);
		poseStack.scale(1 / PLAYER_SCALE, 1 / PLAYER_SCALE, 1 / PLAYER_SCALE);
		poseStack.scale(-1.0f, -1.0f, 1.0f);
		poseStack.scale(1 / 16.0f, 1 / 16.0f, 1 / 16.0f);
		if (worn.crouching()) poseStack.translate(0.0f, -Motion.CROUCH_DROP, 0.0f);
		// The picked size, about the feet; the hitbox and the eyes stay the game's.
		poseStack.scale(worn.scale(), worn.scale(), worn.scale());
		worn.mesh().submit(poseStack, collector, light, overlay, worn.posing());
		poseStack.popPose();
	}
}

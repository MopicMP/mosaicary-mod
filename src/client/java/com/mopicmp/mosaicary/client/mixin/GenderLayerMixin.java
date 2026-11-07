//#if MC>=260100 && GENDER>=1
// Female Gender Mod's line for 26.3 (5.0), on Fabric and NeoForge.
package com.mopicmp.mosaicary.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mopicmp.mosaicary.client.compat.GenderBreasts;
import com.wildfire.api.uvs.UVLayout;
import com.wildfire.api.uvs.UVQuad;
import com.wildfire.client.render.BreastSide;
import com.wildfire.client.render.GenderLayer;
import com.wildfire.client.render.GenderRenderState;

import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.resources.Identifier;

/**
 * Joins in Female Gender Mod's drawing of the breasts (GenderBreasts): the outer layer of each
 * breast drawn as 3D Skin Layers' cubes in place of the mod's flat one, with the fronts of the
 * breasts cut out of the jacket's cubes behind them. Their shape, size, physics, offsets and
 * everything else the mod does stay its own. Added only when both mods are there (ClientMixins),
 * and left out by Mixin where the line installed draws the breasts otherwise, which then keep the
 * flat layer: a mod one depends on for nothing should not stop the game.
 */
@Mixin(value = GenderLayer.class, remap = false)
public abstract class GenderLayerMixin {

	// The model the breast submits in the second order is its outer layer, drawn only with the jacket showing.
	@WrapOperation(method = "renderBreast", require = 0, at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V"))
	private void mosaicary$outerLayer(OrderedSubmitNodeCollector collector, Model<?> model, Object state, PoseStack pose, RenderType type,
			int light, int overlay, int color, UvMapping uv, int outline, Operation<Void> original,
			@Local(argsOnly = true) GenderRenderState gender, @Local(argsOnly = true) BreastSide side) {
		GenderBreasts.Breasts breasts = null;
		// Only on a body drawn as it is: one seen through keeps the flat layer.
		if (state instanceof AvatarRenderState avatar && !avatar.isInvisible && GenderBreasts.near(avatar.distanceToCameraSq)) {
			Identifier skin = avatar.skin.body().texturePath();
			int[][] left = mosaicary$quads(gender.leftBreastOverlayUVLayout);
			int[][] right = mosaicary$quads(gender.rightBreastOverlayUVLayout);
			breasts = GenderBreasts.breasts(skin, left, right);
			if (breasts != null) ((GenderBreasts.Carrier) avatar).mosaicary$jacket(GenderBreasts.jacket(skin, left, right));
		}
		if (breasts == null) {
			original.call(collector, model, state, pose, type, light, overlay, color, uv, outline);
			return;
		}
		boolean left = side == BreastSide.LEFT;
		Object mesh = left ? breasts.left() : breasts.right();
		pose.pushPose();
		// Back from where the flat layer is drawn, a little before the box and bigger, to the box itself.
		pose.scale(1 / 1.05f, 1 / 1.05f, 1 / 1.05f);
		pose.translate(0, 0, 0.015f);
		collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(breasts.texture()), (at, buffer) -> {
			PoseStack drawn = new PoseStack();
			drawn.last().pose().set(at.pose());
			drawn.last().normal().set(at.normal());
			GenderBreasts.draw(mesh, left, drawn, buffer, light, overlay, color);
		});
		pose.popPose();
	}

	/** A layout's quads in the order the mod gives them to the box's faces; null where it has none. */
	@Unique
	private static int[][] mosaicary$quads(UVLayout layout) {
		int[][] quads = new int[layout.getAllSides().size()][];
		int i = 0;
		for (UVQuad quad : layout.getAllSides().values()) {
			quads[i++] = quad == null ? null : new int[] { quad.x1(), quad.y1(), quad.x2(), quad.y2() };
		}
		return quads;
	}
}
//#elseif MC>=12111 && GENDER>=1
//$$ // Female Gender Mod's line for 1.21.11 (its 5.0 betas), which is for Fabric only.
//$$ package com.mopicmp.mosaicary.client.mixin;
//$$
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.Unique;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$
//$$ import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
//$$ import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
//$$ import com.llamalad7.mixinextras.sugar.Local;
//$$ import com.mojang.blaze3d.vertex.PoseStack;
//$$ import com.mopicmp.mosaicary.client.compat.GenderBreasts;
//$$ import com.wildfire.main.uvs.UVLayout;
//$$ import com.wildfire.main.uvs.UVQuad;
//$$ import com.wildfire.render.BreastRenderCommand;
//$$ import com.wildfire.render.BreastSide;
//$$ import com.wildfire.render.GenderLayer;
//$$ import com.wildfire.render.GenderRenderState;
//$$
//$$ import net.minecraft.client.renderer.SubmitNodeCollector;
//$$ import net.minecraft.client.renderer.entity.state.AvatarRenderState;
//$$ import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
//$$ import net.minecraft.client.renderer.rendertype.RenderType;
//$$ import net.minecraft.client.renderer.rendertype.RenderTypes;
//$$ import net.minecraft.resources.Identifier;
//$$
//$$ /** As in the line for 26.3 above. */
//$$ @Mixin(value = GenderLayer.class, remap = false)
//$$ public abstract class GenderLayerMixin {
//$$
	//$$ // The second geometry the breast submits is its outer layer, drawn only with the jacket showing.
	//$$ @WrapOperation(method = "renderBreast", remap = false, require = 0, at = @At(value = "INVOKE", ordinal = 1, remap = true,
		//$$ target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitCustomGeometry(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/SubmitNodeCollector$CustomGeometryRenderer;)V"))
	//$$ private void mosaicary$outerLayer(SubmitNodeCollector collector, PoseStack pose, RenderType type,
			//$$ SubmitNodeCollector.CustomGeometryRenderer drawing, Operation<Void> original,
			//$$ @Local(argsOnly = true) HumanoidRenderState state, @Local(argsOnly = true) BreastSide side) {
		//$$ GenderBreasts.Breasts breasts = null;
		//$$ GenderRenderState gender = GenderRenderState.get(state);
		//$$ // Only on a body drawn as it is: one seen through, or only its outline, keeps the flat layer.
		//$$ if (state instanceof AvatarRenderState avatar && !avatar.isInvisible && gender != null
				//$$ && drawing instanceof BreastRenderCommand && GenderBreasts.near(avatar.distanceToCameraSq)) {
			//$$ Identifier skin = avatar.skin.body().texturePath();
			//$$ int[][] left = mosaicary$quads(gender.leftBreastOverlayUVLayout);
			//$$ int[][] right = mosaicary$quads(gender.rightBreastOverlayUVLayout);
			//$$ breasts = GenderBreasts.breasts(skin, left, right);
			//$$ if (breasts != null) ((GenderBreasts.Carrier) avatar).mosaicary$jacket(GenderBreasts.jacket(skin, left, right));
		//$$ }
		//$$ if (breasts == null) {
			//$$ original.call(collector, pose, type, drawing);
			//$$ return;
		//$$ }
		//$$ BreastRenderCommand flat = (BreastRenderCommand) drawing;
		//$$ boolean left = side.isLeft;
		//$$ Object mesh = left ? breasts.left() : breasts.right();
		//$$ pose.pushPose();
		//$$ // Back from where the flat layer is drawn, a little before the box and bigger, to the box itself.
		//$$ pose.scale(1 / 1.05f, 1 / 1.05f, 1 / 1.05f);
		//$$ pose.translate(0, 0, 0.015f);
		//$$ original.call(collector, pose, RenderTypes.entityTranslucent(breasts.texture()),
			//$$ (SubmitNodeCollector.CustomGeometryRenderer) (at, buffer) -> {
				//$$ PoseStack drawn = new PoseStack();
				//$$ drawn.last().pose().set(at.pose());
				//$$ drawn.last().normal().set(at.normal());
				//$$ GenderBreasts.draw(mesh, left, drawn, buffer, flat.light(), flat.overlay(), flat.color());
			//$$ });
		//$$ pose.popPose();
	//$$ }
//$$
	//$$ /** A layout's quads in the order the mod gives them to the box's faces; null where it has none. */
	//$$ @Unique
	//$$ private static int[][] mosaicary$quads(UVLayout layout) {
		//$$ int[][] quads = new int[layout.getAllSides().size()][];
		//$$ int i = 0;
		//$$ for (UVQuad quad : layout.getAllSides().values()) {
			//$$ quads[i++] = quad == null ? null : new int[] { quad.x1(), quad.y1(), quad.x2(), quad.y2() };
		//$$ }
		//$$ return quads;
	//$$ }
//$$ }
//#elseif MC>=12100 && GENDER>=1
//$$ // Female Gender Mod's lines for 1.21.1 (3.2.1 on Fabric, 3.2.2 on NeoForge), which draw the
//$$ // breasts straight into the buffer, the outer layer in the same one as the rest of the body.
//$$ package com.mopicmp.mosaicary.client.mixin;
//$$
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$
//$$ import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
//$$ import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
//$$ import com.llamalad7.mixinextras.sugar.Local;
//$$ import com.mojang.blaze3d.vertex.PoseStack;
//$$ import com.mojang.blaze3d.vertex.VertexConsumer;
//$$ import com.mopicmp.mosaicary.client.compat.GenderBreasts;
//$$ import com.wildfire.render.GenderLayer;
//$$ import com.wildfire.render.WildfireModelRenderer;
//$$
//$$ import net.minecraft.client.Minecraft;
//$$ import net.minecraft.client.renderer.MultiBufferSource;
//$$ import net.minecraft.client.renderer.RenderType;
//$$ import net.minecraft.world.entity.LivingEntity;
//$$
//$$ /** As in the line for 26.3 above, with the jacket cut where 3D Skin Layers keeps it for the player (GenderBreasts.fixed). */
//$$ @Mixin(value = GenderLayer.class, remap = false)
//$$ public abstract class GenderLayerMixin {
//$$
	//$$ // The second box the breast draws is its outer layer, drawn only with the jacket showing. Named
	//$$ // without its descriptor, which has the game's types, named otherwise on each loader.
	//$$ @WrapOperation(method = "renderBreast", require = 0, at = @At(value = "INVOKE", ordinal = 1, target = "Lcom/wildfire/render/GenderLayer;renderBox"))
	//$$ private void mosaicary$outerLayer(WildfireModelRenderer.ModelBox box, PoseStack pose, VertexConsumer buffer, int light, int overlay, int color,
			//$$ Operation<Void> original, @Local(argsOnly = true) LivingEntity entity, @Local(argsOnly = true) MultiBufferSource buffers) {
		//$$ GenderBreasts.Breasts breasts = entity.isInvisible()
			//$$ || !GenderBreasts.near(Minecraft.getInstance().getEntityRenderDispatcher().distanceToSqr(entity)) ? null : GenderBreasts.fixed(entity);
		//$$ if (breasts == null) {
			//$$ original.call(box, pose, buffer, light, overlay, color);
			//$$ return;
		//$$ }
		//$$ // The left breast's box reaches from x = -4 to 0.
		//$$ boolean left = box.posX1 < 0;
		//$$ pose.pushPose();
		//$$ // Back from where the flat layer is drawn, a little before the box and bigger, to the box itself.
		//$$ pose.scale(1 / 1.05f, 1 / 1.05f, 1 / 1.05f);
		//$$ pose.translate(0, 0, 0.015f);
		//$$ GenderBreasts.draw(left ? breasts.left() : breasts.right(), left, pose, buffers.getBuffer(RenderType.entityTranslucent(breasts.texture())),
			//$$ light, overlay, color);
		//$$ pose.popPose();
	//$$ }
//$$ }
//#elseif GENDER>=1
//$$ // Female Gender Mod's lines for 1.18.2 to 1.20.1 (2.9 to 3.1), on Fabric and Forge, which draw
//$$ // the breasts straight into the buffer, the outer layer in the same one as the rest of the body.
//$$ // Redirected rather than wrapped: Forge has no MixinExtras before 1.20.2.
//$$ package com.mopicmp.mosaicary.client.mixin;
//$$
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.Shadow;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Redirect;
//$$
//$$ import com.mojang.blaze3d.vertex.PoseStack;
//$$ import com.mojang.blaze3d.vertex.VertexConsumer;
//$$ import com.mopicmp.mosaicary.client.compat.GenderBreasts;
//$$ import com.wildfire.render.GenderLayer;
//$$ import com.wildfire.render.WildfireModelRenderer;
//$$
//$$ import net.minecraft.client.Minecraft;
//$$ import net.minecraft.client.player.AbstractClientPlayer;
//$$ import net.minecraft.client.renderer.MultiBufferSource;
//$$ import net.minecraft.client.renderer.RenderType;
//$$ import net.minecraft.world.item.ItemStack;
//$$
//$$ /** As in the line for 26.3 above, with the jacket cut where 3D Skin Layers keeps it for the player (GenderBreasts.fixed). */
//$$ @Mixin(value = GenderLayer.class, remap = false)
//$$ public abstract class GenderLayerMixin {
//$$
	//$$ @Shadow
	//$$ private static void renderBox(WildfireModelRenderer.ModelBox box, PoseStack pose, VertexConsumer buffer, int light, int overlay,
			//$$ float red, float green, float blue, float alpha) {
		//$$ throw new AssertionError();
	//$$ }
//$$
	//$$ // The second box the breast draws is its outer layer, drawn only with the jacket showing. Named
	//$$ // without its descriptor, which has the game's types, named otherwise on each loader. After the
	//$$ // call's own arguments, the first four of the breast's, the same in each of these lines.
	//$$ @Redirect(method = "renderBreast", require = 0, at = @At(value = "INVOKE", ordinal = 1, target = "Lcom/wildfire/render/GenderLayer;renderBox"))
	//$$ private void mosaicary$outerLayer(WildfireModelRenderer.ModelBox box, PoseStack pose, VertexConsumer buffer, int light, int overlay,
			//$$ float red, float green, float blue, float alpha, AbstractClientPlayer player, ItemStack armor, PoseStack breastPose,
			//$$ MultiBufferSource buffers) {
		//$$ GenderBreasts.Breasts breasts = player.isInvisible()
			//$$ || !GenderBreasts.near(Minecraft.getInstance().getEntityRenderDispatcher().distanceToSqr(player)) ? null : GenderBreasts.fixed(player);
		//$$ if (breasts == null) {
			//$$ renderBox(box, pose, buffer, light, overlay, red, green, blue, alpha);
			//$$ return;
		//$$ }
		//$$ // The left breast's box reaches from x = -4 to 0.
		//$$ boolean left = box.posX1 < 0;
		//$$ int color = Math.round(alpha * 255) << 24 | Math.round(red * 255) << 16 | Math.round(green * 255) << 8 | Math.round(blue * 255);
		//$$ pose.pushPose();
		//$$ // Back from where the flat layer is drawn, a little before the box and bigger, to the box itself.
		//$$ pose.scale(1 / 1.05f, 1 / 1.05f, 1 / 1.05f);
		//$$ pose.translate(0, 0, 0.015f);
		//$$ GenderBreasts.draw(left ? breasts.left() : breasts.right(), left, pose, buffers.getBuffer(RenderType.entityTranslucent(breasts.texture())),
			//$$ light, overlay, color);
		//$$ pose.popPose();
	//$$ }
//$$ }
//#endif

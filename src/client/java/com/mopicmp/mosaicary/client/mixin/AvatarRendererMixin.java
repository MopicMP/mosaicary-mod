package com.mopicmp.mosaicary.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mopicmp.mosaicary.client.MosaicaryClient;
import com.mopicmp.mosaicary.client.wear.Wearing;

import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;

/**
 * Hands a worn model to the player's render state, and turns the layers off while
 * one is worn.
 *
 * The layers are everything drawn on top of the player model: armour, held items,
 * the cape and elytra, parrots, stuck arrows and bee stings. They are fitted to the
 * game's own body, and on a model of another shape they would float in the air, so
 * with a worn model none of them is drawn. The name tag is not a layer and stays.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("TAIL"))
	private void mosaicary$extractWorn(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo info) {
		MosaicaryClient client = MosaicaryClient.instance();
		// With the model shelf hidden, no model is drawn on anyone.
		if (client != null && MosaicaryClient.MODELS) client.wearing().extract(entity, state, partialTicks);
	}

	@Inject(method = "shouldRenderLayers(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)Z",
		at = @At("HEAD"), cancellable = true)
	private void mosaicary$noLayersWhenWorn(AvatarRenderState state, CallbackInfoReturnable<Boolean> info) {
		if (state.getData(Wearing.KEY) != null) info.setReturnValue(false);
	}
}

//#if MC>=12111
// Where 3D Skin Layers hangs its meshes on the player model as it is set up for each player drawn
// (from 1.21.2 on); added with Female Gender Mod's mixins only (ClientMixins).
package com.mopicmp.mosaicary.client.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mopicmp.mosaicary.client.compat.GenderBreasts;
import com.mopicmp.mosaicary.client.compat.SkinLayers3d;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

/**
 * Puts the jacket with the breasts cut out of it (GenderBreasts) in place of the whole one 3D Skin
 * Layers has just hung on the jacket, for a player whose breasts Female Gender Mod draws with their
 * outer layer in cubes. Applied after 3D Skin Layers' own mixin here, so that it comes after it.
 */
@Mixin(value = PlayerModel.class, priority = 1100)
public abstract class PlayerModelMixin {

	@Shadow
	@Final
	public ModelPart jacket;

	// The method itself and not the bridge for the generic state, which has the same name.
	//#disable-remap
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
	//#enable-remap
	private void mosaicary$cutJacket(AvatarRenderState state, CallbackInfo info) {
		Object cut = ((GenderBreasts.Carrier) state).mosaicary$jacket();
		if (cut != null && SkinLayers3d.hasMesh(jacket)) SkinLayers3d.hangJacket(jacket, cut);
	}
}
//#endif

package com.mopicmp.mosaicary.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.authlib.GameProfile;
import com.mopicmp.mosaicary.client.MosaicaryClient;
import com.mopicmp.mosaicary.client.skins.Skins;

import net.minecraft.client.multiplayer.PlayerInfo;
//#if MC>=12002
import net.minecraft.world.entity.player.PlayerSkin;
//#else
//$$ import net.minecraft.resources.ResourceLocation;
//#endif
//#if MC>=12111
import net.minecraft.core.ClientAsset;
//#endif

/**
 * Draws a skin just put on in place of the one the server handed out.
 *
 * A server learns a player's skin when they join and keeps it until they join again. After
 * a skin has been put on here, the local player sees it at once, and so does every player
 * whose new skin came through the site's relay. Everything that draws a player's skin asks
 * this one method (the player in the world, the hand in first person, the head in the
 * player list), so all of them change together. The cape and elytra stay the server's.
 */
@Mixin(PlayerInfo.class)
public abstract class PlayerInfoMixin {

	@Shadow
	public abstract GameProfile getProfile();

	//#if MC>=12002
	@Inject(method = "getSkin()Lnet/minecraft/world/entity/player/PlayerSkin;", at = @At("RETURN"), cancellable = true)
	private void mosaicary$shownSkin(CallbackInfoReturnable<PlayerSkin> info) {
		MosaicaryClient client = MosaicaryClient.instance();
		if (client == null) return;
		Skins.Shown shown = client.skins().shownOn(getProfile().id());
		if (shown == null) return;
		PlayerSkin given = info.getReturnValue();
		// Before 1.21.9 a skin names its texture and the address it came from rather than one asset.
		//#if MC>=12111
		info.setReturnValue(new PlayerSkin(new ClientAsset.ResourceTexture(shown.texture(), shown.texture()), given.cape(),
			given.elytra(), shown.model(), given.secure()));
		//#else
		//$$ info.setReturnValue(new PlayerSkin(shown.texture(), null, given.capeTexture(), given.elytraTexture(), shown.model(),
		//$$ 	given.secure()));
		//#endif
	}
	//#else
	//$$ // Before 1.20.2 the texture and the arms are asked for apart.
	//$$ @Inject(method = "getSkinLocation()Lnet/minecraft/resources/ResourceLocation;", at = @At("RETURN"), cancellable = true)
	//$$ private void mosaicary$shownTexture(CallbackInfoReturnable<ResourceLocation> info) {
	//$$ 	Skins.Shown shown = shown();
	//$$ 	if (shown != null) info.setReturnValue(shown.texture());
	//$$ }
	//$$
	//$$ @Inject(method = "getModelName()Ljava/lang/String;", at = @At("RETURN"), cancellable = true)
	//$$ private void mosaicary$shownModel(CallbackInfoReturnable<String> info) {
	//$$ 	Skins.Shown shown = shown();
	//$$ 	if (shown != null) info.setReturnValue(shown.model().id());
	//$$ }
	//$$
	//$$ private Skins.Shown shown() {
	//$$ 	MosaicaryClient client = MosaicaryClient.instance();
	//$$ 	return client == null ? null : client.skins().shownOn(getProfile().getId());
	//$$ }
	//#endif
}

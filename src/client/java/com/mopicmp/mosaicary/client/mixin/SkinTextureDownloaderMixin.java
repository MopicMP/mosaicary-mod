package com.mopicmp.mosaicary.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.platform.NativeImage;
import com.mopicmp.mosaicary.client.skins.SkinPixels;

//#if MC>=12111
import net.minecraft.client.renderer.texture.SkinTextureDownloader;
import net.minecraft.core.ClientAsset;
//#else
//$$ import net.minecraft.client.renderer.texture.HttpTexture;
//#endif

/**
 * Makes every skin the game downloads, the player's own and everyone else's, what the wardrobe
 * shows ({@link SkinPixels}). Its see-through places stay: the game makes a skin's base layer
 * solid, which is what turns them black, and the player model is drawn with alpha, so nothing else
 * is needed. And a corner filled with a background is emptied, or the Fresh Moves pack draws that
 * background over the face.
 *
 * Only this game sees the difference. A player without the mod still sees those places black,
 * which the wardrobe warns about ({@code SkinPixels.seeThrough}).
 */
// Before 1.21.9 skins are downloaded by HttpTexture, which has the same setNoAlpha.
//#if MC>=12111
@Mixin(SkinTextureDownloader.class)
//#else
//$$ @Mixin(HttpTexture.class)
//#endif
abstract class SkinTextureDownloaderMixin {

	@Inject(method = "setNoAlpha", at = @At("HEAD"), cancellable = true)
	private static void mosaicary$keepAlpha(NativeImage image, int x0, int y0, int x1, int y1, CallbackInfo info) {
		info.cancel();
	}

	/**
	 * Where every picture passes on its way to the graphics card, whatever path it came by: Entity
	 * Texture Features ends the processing of a skin early. A cape is 64 by 32 and not touched.
	 */
	//#if MC>=12111
	@Inject(method = "registerTextureInManager", at = @At("HEAD"))
	private void mosaicary$clearCorners(ClientAsset.Texture texture, NativeImage image, CallbackInfoReturnable<?> info) {
		clearCorners(image);
	}
	//#else
	//$$ @Inject(method = "upload", at = @At("HEAD"))
	//$$ private void mosaicary$clearCorners(NativeImage image, CallbackInfo info) {
	//$$ 	clearCorners(image);
	//$$ }
	//#endif

	private static void clearCorners(NativeImage image) {
		if (image.getWidth() != 64 || image.getHeight() != 64) return;
		for (int[] corner : SkinPixels.CORNERS) {
			int solid = 0;
			for (int y = corner[1]; y < corner[1] + 8; y++) {
				for (int x = corner[0]; x < corner[0] + 8; x++) if ((image.getPixel(x, y) >>> 24) >= 128) solid++;
			}
			if (solid <= SkinPixels.CORNER_DRAWN_MOST) continue;
			for (int y = corner[1]; y < corner[1] + 8; y++) {
				for (int x = corner[0]; x < corner[0] + 8; x++) image.setPixel(x, y, 0);
			}
		}
	}
}

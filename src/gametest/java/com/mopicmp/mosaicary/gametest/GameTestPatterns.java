package com.mopicmp.mosaicary.gametest;

import com.mojang.blaze3d.platform.NativeImage;
import com.mopicmp.mosaicary.client.platform.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Screen;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * The game tests' share of client/platform/ClientPatterns: the preprocessor applies patterns
 * within one source set. Never called.
 */
@SuppressWarnings("unused")
final class GameTestPatterns {

	private GameTestPatterns() {
	}

	@Pattern
	private static void setScreen(Minecraft minecraft, Screen screen) {
		//#if MC>=260100
		minecraft.gui.setScreen(screen);
		//#else
		//$$ minecraft.setScreen(screen);
		//#endif
	}

	@Pattern
	private static Screen screen(Minecraft minecraft) {
		//#if MC>=260100
		return minecraft.gui.screen();
		//#else
		//$$ return minecraft.screen;
		//#endif
	}

	@Pattern
	private static ToastManager toastManager(Minecraft minecraft) {
		//#if MC>=260100
		return minecraft.gui.toastManager();
		//#elseif MC>=12111
		//$$ return minecraft.getToastManager();
		//#else
		//$$ return minecraft.getToasts();
		//#endif
	}

	/** The camera turned to a yaw and pitch, in degrees. Fabric's test input does it since 26.1. */
	@Pattern
	private static void lookAt(ClientGameTestContext context, float yaw, float pitch) {
		//#if MC>=260100
		context.getInput().lookAt(yaw, pitch);
		//#else
		//$$ context.runOnClient((Minecraft minecraft) -> { minecraft.player.setYRot(yaw); minecraft.player.setXRot(pitch); });
		//#endif
	}

	@Pattern
	private static int getPixel(NativeImage image, int x, int y) {
		//#if MC>=12111
		return image.getPixel(x, y);
		//#else
		//$$ return net.minecraft.util.FastColor.ABGR32.fromArgb32(image.getPixelRGBA(x, y));
		//#endif
	}

	@Pattern
	private static void setPixel(NativeImage image, int x, int y, int argb) {
		//#if MC>=12111
		image.setPixel(x, y, argb);
		//#else
		//$$ image.setPixelRGBA(x, y, net.minecraft.util.FastColor.ABGR32.fromArgb32(argb));
		//#endif
	}
}

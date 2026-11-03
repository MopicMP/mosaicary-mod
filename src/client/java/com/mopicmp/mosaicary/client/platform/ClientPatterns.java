package com.mopicmp.mosaicary.client.platform;

import java.net.URI;
import java.nio.file.Path;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.SessionService;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.chat.ChatListener;
//#if MC>=260100
import com.mojang.blaze3d.Blaze3D;
//#endif

/**
 * Calls that moved between game versions to another object, which a rename cannot follow.
 * Never called: each method is a pattern ({@link Pattern}) the preprocessor applies to the
 * whole client source.
 *
 * Since 26.1 the screens, toasts and chat belong to {@code Minecraft.gui}, and the in-game
 * overlay that used to be {@code Gui} is {@code Gui.hud}; before, all of them were on
 * {@code Minecraft} itself. Before 1.21.11 (as far as the versions built here go) a picture's
 * pixels are read and written as ABGR, and a player's profile is a class with getters.
 */
@SuppressWarnings("unused")
final class ClientPatterns {

	private ClientPatterns() {
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

	@Pattern
	private static ChatListener chatListener(Minecraft minecraft) {
		//#if MC>=260100
		return minecraft.gui.chatListener();
		//#else
		//$$ return minecraft.getChatListener();
		//#endif
	}

	@Pattern
	private static Hud hud(Minecraft minecraft) {
		//#if MC>=260100
		return minecraft.gui.hud;
		//#else
		//$$ return minecraft.gui;
		//#endif
	}

	@Pattern
	private static Overlay overlay(Minecraft minecraft) {
		//#if MC>=260100
		return minecraft.gui.overlay();
		//#else
		//$$ return minecraft.getOverlay();
		//#endif
	}

	@Pattern
	private static RenderTarget mainRenderTarget(Minecraft minecraft) {
		//#if MC>=260100
		return minecraft.gameRenderer.mainRenderTarget();
		//#else
		//$$ return minecraft.getMainRenderTarget();
		//#endif
	}

	@Pattern
	private static void openUri(URI uri) {
		//#if MC>=260100
		Blaze3D.openUri(uri);
		//#else
		//$$ net.minecraft.util.Util.getPlatform().openUri(uri);
		//#endif
	}

	@Pattern
	private static void openPath(Path path) {
		//#if MC>=260100
		Blaze3D.openPath(path);
		//#else
		//$$ net.minecraft.util.Util.getPlatform().openPath(path);
		//#endif
	}

	/** A pixel as ARGB. */
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

	@Pattern
	private static UUID id(GameProfile profile) {
		//#if MC>=12111
		return profile.id();
		//#else
		//$$ return profile.getId();
		//#endif
	}

	@Pattern
	private static String name(GameProfile profile) {
		//#if MC>=12111
		return profile.name();
		//#else
		//$$ return profile.getName();
		//#endif
	}

	@Pattern
	private static PropertyMap properties(GameProfile profile) {
		//#if MC>=12111
		return profile.properties();
		//#else
		//$$ return profile.getProperties();
		//#endif
	}

	@Pattern
	private static SessionService sessionService(Minecraft minecraft) {
		//#if MC>=12111
		return minecraft.services().sessionService();
		//#else
		//$$ return minecraft.getMinecraftSessionService();
		//#endif
	}
}

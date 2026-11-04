package com.mopicmp.mosaicary.client.platform;

import java.net.URI;
import java.nio.file.Path;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.SessionService;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
		//#elseif MC>=12002
		//$$ net.minecraft.util.Util.getPlatform().openPath(path);
		//#else
		//$$ net.minecraft.Util.getPlatform().openFile(path.toFile());
		//#endif
	}

	/** A pixel as ARGB. */
	@Pattern
	private static int getPixel(NativeImage image, int x, int y) {
		//#if MC>=12111
		return image.getPixel(x, y);
		//#elseif MC>=12002
		//$$ return net.minecraft.util.FastColor.ABGR32.fromArgb32(image.getPixelRGBA(x, y));
		//#else
		//$$ return com.mopicmp.mosaicary.client.skins.SkinPixels.swapRedBlue(image.getPixelRGBA(x, y));
		//#endif
	}

	@Pattern
	private static void setPixel(NativeImage image, int x, int y, int argb) {
		//#if MC>=12111
		image.setPixel(x, y, argb);
		//#elseif MC>=12002
		//$$ image.setPixelRGBA(x, y, net.minecraft.util.FastColor.ABGR32.fromArgb32(argb));
		//#else
		//$$ image.setPixelRGBA(x, y, com.mopicmp.mosaicary.client.skins.SkinPixels.swapRedBlue(argb));
		//#endif
	}

	/** A widget's right and bottom edges, which 1.20.1 does not give, only its place and size. */
	@Pattern
	private static int right(AbstractWidget widget) {
		//#if MC>=12002
		return widget.getRight();
		//#else
		//$$ return com.mopicmp.mosaicary.client.ui.ScreenWidgets.right(widget);
		//#endif
	}

	@Pattern
	private static int bottom(AbstractWidget widget) {
		//#if MC>=12002
		return widget.getBottom();
		//#else
		//$$ return com.mopicmp.mosaicary.client.ui.ScreenWidgets.bottom(widget);
		//#endif
	}

	// Texts are made by Component's own factories from 1.19 on, and before by constructing their kinds.
	@Pattern
	private static MutableComponent literal(String text) {
		//#if MC>=11900
		return Component.literal(text);
		//#else
		//$$ return new net.minecraft.network.chat.TextComponent(text);
		//#endif
	}

	@Pattern
	private static MutableComponent empty() {
		//#if MC>=11900
		return Component.empty();
		//#else
		//$$ return new net.minecraft.network.chat.TextComponent("");
		//#endif
	}

	@Pattern
	private static MutableComponent translatable(String key) {
		//#if MC>=11900
		return Component.translatable(key);
		//#else
		//$$ return new net.minecraft.network.chat.TranslatableComponent(key);
		//#endif
	}

	@Pattern
	private static MutableComponent translatable(String key, Object... args) {
		//#if MC>=11900
		return Component.translatable(key, args);
		//#else
		//$$ return new net.minecraft.network.chat.TranslatableComponent(key, args);
		//#endif
	}

	// Keys and mouse buttons have names on InputConstants from 1.17 on, and before only GLFW's.
	@Pattern
	private static int keyEscape() {
		//#if MC>=11700
		return InputConstants.KEY_ESCAPE;
		//#else
		//$$ return org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
		//#endif
	}

	@Pattern
	private static int keyM() {
		//#if MC>=11700
		return InputConstants.KEY_M;
		//#else
		//$$ return org.lwjgl.glfw.GLFW.GLFW_KEY_M;
		//#endif
	}

	@Pattern
	private static int keyBackspace() {
		//#if MC>=11700
		return InputConstants.KEY_BACKSPACE;
		//#else
		//$$ return org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE;
		//#endif
	}

	@Pattern
	private static int keyReturn() {
		//#if MC>=11700
		return InputConstants.KEY_RETURN;
		//#else
		//$$ return org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
		//#endif
	}

	@Pattern
	private static int keyR() {
		//#if MC>=11700
		return InputConstants.KEY_R;
		//#else
		//$$ return org.lwjgl.glfw.GLFW.GLFW_KEY_R;
		//#endif
	}

	@Pattern
	private static int keyF5() {
		//#if MC>=11700
		return InputConstants.KEY_F5;
		//#else
		//$$ return org.lwjgl.glfw.GLFW.GLFW_KEY_F5;
		//#endif
	}

	@Pattern
	private static int key1() {
		//#if MC>=11700
		return InputConstants.KEY_1;
		//#else
		//$$ return org.lwjgl.glfw.GLFW.GLFW_KEY_1;
		//#endif
	}

	@Pattern
	private static int mouseButtonLeft() {
		//#if MC>=11700
		return InputConstants.MOUSE_BUTTON_LEFT;
		//#else
		//$$ return org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;
		//#endif
	}

	@Pattern
	private static int mouseButtonRight() {
		//#if MC>=11700
		return InputConstants.MOUSE_BUTTON_RIGHT;
		//#else
		//$$ return org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT;
		//#endif
	}

	/**
	 * JSON read from a string; Gson does it by a static method from 2.8.6 on, and 1.16.5 has 2.8.0. Written
	 * with the class's short name, as the calls are: a pattern matches the call as it is written.
	 */
	@Pattern
	private static JsonElement parseString(String json) {
		//#if MC>=11700
		return JsonParser.parseString(json);
		//#else
		//$$ return new JsonParser().parse(json);
		//#endif
	}

	/** A copy of a JSON object; Gson's own is public from 2.8.2 on, and 1.16.5 has 2.8.0. */
	@Pattern
	private static com.google.gson.JsonObject deepCopy(com.google.gson.JsonObject object) {
		//#if MC>=11700
		return object.deepCopy();
		//#else
		//$$ return new JsonParser().parse(object.toString()).getAsJsonObject();
		//#endif
	}

	/** The player's account id; before 1.19 only by way of the profile. */
	@Pattern
	private static UUID profileId(User user) {
		//#if MC>=11900
		return user.getProfileId();
		//#else
		//$$ return user.getGameProfile().getId();
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

	//#if MC<12002
	//$$ // GuiGraphics came with 1.20. Before, versions/1.20.1-1.19.2.txt makes it the pose stack the game
	//$$ // draws with, and each call on it goes to Gfx.
	//$$
	//$$ @Pattern
	//$$ private static void fill(net.minecraft.client.gui.GuiGraphics graphics, int x0, int y0, int x1, int y1, int colour) {
	//#if MC>=12000
	//$$ 	graphics.fill(x0, y0, x1, y1, colour);
	//#else
	//$$ 	net.minecraft.client.gui.GuiComponent.fill(graphics, x0, y0, x1, y1, colour);
	//#endif
	//$$ }
	//$$
	//$$ @Pattern
	//$$ private static int drawString(net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.client.gui.Font font, String text, int x, int y, int colour) {
	//#if MC>=12000
	//$$ 	return graphics.drawString(font, text, x, y, colour);
	//#else
	//$$ 	return font.drawShadow(graphics, text, x, y, colour);
	//#endif
	//$$ }
	//$$
	//$$ @Pattern
	//$$ private static int drawString(net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.client.gui.Font font, String text, int x, int y, int colour, boolean shadow) {
	//#if MC>=12000
	//$$ 	return graphics.drawString(font, text, x, y, colour, shadow);
	//#else
	//$$ 	return com.mopicmp.mosaicary.client.platform.Gfx.text(graphics, font, text, x, y, colour, shadow);
	//#endif
	//$$ }
	//$$
	//$$ @Pattern
	//$$ private static void drawCenteredString(net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.client.gui.Font font, String text, int x, int y, int colour) {
	//#if MC>=12000
	//$$ 	graphics.drawCenteredString(font, text, x, y, colour);
	//#else
	//$$ 	net.minecraft.client.gui.GuiComponent.drawCenteredString(graphics, font, text, x, y, colour);
	//#endif
	//$$ }
	//$$
	//$$ @Pattern
	//$$ private static int drawString(net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.client.gui.Font font, net.minecraft.network.chat.Component text, int x, int y, int colour) {
	//#if MC>=12000
	//$$ 	return graphics.drawString(font, text, x, y, colour);
	//#else
	//$$ 	return font.drawShadow(graphics, text, x, y, colour);
	//#endif
	//$$ }
	//$$
	//$$ @Pattern
	//$$ private static int drawString(net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.client.gui.Font font, net.minecraft.network.chat.Component text, int x, int y, int colour, boolean shadow) {
	//#if MC>=12000
	//$$ 	return graphics.drawString(font, text, x, y, colour, shadow);
	//#else
	//$$ 	return com.mopicmp.mosaicary.client.platform.Gfx.text(graphics, font, text, x, y, colour, shadow);
	//#endif
	//$$ }
	//$$
	//$$ @Pattern
	//$$ private static void drawCenteredString(net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.client.gui.Font font, net.minecraft.network.chat.Component text, int x, int y, int colour) {
	//#if MC>=12000
	//$$ 	graphics.drawCenteredString(font, text, x, y, colour);
	//#else
	//$$ 	net.minecraft.client.gui.GuiComponent.drawCenteredString(graphics, font, text, x, y, colour);
	//#endif
	//$$ }
	//$$
	//$$ @Pattern
	//$$ private static int drawString(net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.client.gui.Font font, net.minecraft.util.FormattedCharSequence text, int x, int y, int colour) {
	//#if MC>=12000
	//$$ 	return graphics.drawString(font, text, x, y, colour);
	//#else
	//$$ 	return font.drawShadow(graphics, text, x, y, colour);
	//#endif
	//$$ }
	//$$
	//$$ @Pattern
	//$$ private static int drawString(net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.client.gui.Font font, net.minecraft.util.FormattedCharSequence text, int x, int y, int colour, boolean shadow) {
	//#if MC>=12000
	//$$ 	return graphics.drawString(font, text, x, y, colour, shadow);
	//#else
	//$$ 	return com.mopicmp.mosaicary.client.platform.Gfx.text(graphics, font, text, x, y, colour, shadow);
	//#endif
	//$$ }
	//$$
	//$$ @Pattern
	//$$ private static void drawCenteredString(net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.client.gui.Font font, net.minecraft.util.FormattedCharSequence text, int x, int y, int colour) {
	//#if MC>=12000
	//$$ 	graphics.drawCenteredString(font, text, x, y, colour);
	//#else
	//$$ 	com.mopicmp.mosaicary.client.platform.Gfx.centered(graphics, font, text, x, y, colour);
	//#endif
	//$$ }
	//$$
	//$$ @Pattern
	//$$ private static void blit(net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.resources.ResourceLocation texture, int x, int y, int width, int height, float u, float v, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
	//#if MC>=12000
	//$$ 	graphics.blit(texture, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight);
	//#else
	//$$ 	com.mopicmp.mosaicary.client.platform.Gfx.blit(graphics, texture, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight);
	//#endif
	//$$ }
	//$$
	//$$ @Pattern
	//$$ private static void blitNineSliced(net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.resources.ResourceLocation texture, int x, int y, int width, int height, int left, int top, int right, int bottom, int uWidth, int vHeight, int u, int v) {
	//#if MC>=12000
	//$$ 	graphics.blitNineSliced(texture, x, y, width, height, left, top, right, bottom, uWidth, vHeight, u, v);
	//#else
	//$$ 	com.mopicmp.mosaicary.client.platform.Gfx.blitNineSliced(graphics, texture, x, y, width, height, left, top, right, bottom, uWidth, vHeight, u, v);
	//#endif
	//$$ }
	//$$
	//$$ @Pattern
	//$$ private static void enableScissor(net.minecraft.client.gui.GuiGraphics graphics, int x0, int y0, int x1, int y1) {
	//#if MC>=12000
	//$$ 	graphics.enableScissor(x0, y0, x1, y1);
	//#else
	//$$ 	com.mopicmp.mosaicary.client.platform.Gfx.enableScissor(graphics, x0, y0, x1, y1);
	//#endif
	//$$ }
	//$$
	//$$ @Pattern
	//$$ private static void disableScissor(net.minecraft.client.gui.GuiGraphics graphics) {
	//#if MC>=12000
	//$$ 	graphics.disableScissor();
	//#else
	//$$ 	com.mopicmp.mosaicary.client.platform.Gfx.disableScissor(graphics);
	//#endif
	//$$ }
	//$$
	//$$ @Pattern
	//$$ private static void setColor(net.minecraft.client.gui.GuiGraphics graphics, float red, float green, float blue, float alpha) {
	//#if MC>=12000
	//$$ 	graphics.setColor(red, green, blue, alpha);
	//#else
	//$$ 	com.mopicmp.mosaicary.client.platform.Gfx.setColor(graphics, red, green, blue, alpha);
	//#endif
	//$$ }
	//$$
	//$$ @Pattern
	//$$ private static void flush(net.minecraft.client.gui.GuiGraphics graphics) {
	//#if MC>=12000
	//$$ 	graphics.flush();
	//#else
	//$$ 	com.mopicmp.mosaicary.client.platform.Gfx.flush(graphics);
	//#endif
	//$$ }
	//$$
	//$$ @Pattern
	//$$ private static com.mojang.blaze3d.vertex.PoseStack pose(net.minecraft.client.gui.GuiGraphics graphics) {
	//#if MC>=12000
	//$$ 	return graphics.pose();
	//#else
	//$$ 	return com.mopicmp.mosaicary.client.platform.Gfx.pose(graphics);
	//#endif
	//$$ }
	//$$
	//$$ @Pattern
	//$$ private static net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource(net.minecraft.client.gui.GuiGraphics graphics) {
	//#if MC>=12000
	//$$ 	return graphics.bufferSource();
	//#else
	//$$ 	return com.mopicmp.mosaicary.client.platform.Gfx.bufferSource(graphics);
	//#endif
	//$$ }
	//$$
	//$$ @Pattern
	//$$ private static void renderTooltip(net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.client.gui.Font font, java.util.List<net.minecraft.util.FormattedCharSequence> lines, int x, int y) {
	//#if MC>=12000
	//$$ 	graphics.renderTooltip(font, lines, x, y);
	//#else
	//$$ 	com.mopicmp.mosaicary.client.platform.Gfx.tooltip(graphics, font, lines, x, y);
	//#endif
	//$$ }
	//$$
	//$$ @Pattern
	//$$ private static void renderTooltip(net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.client.gui.Font font, net.minecraft.network.chat.Component text, int x, int y) {
	//#if MC>=12000
	//$$ 	graphics.renderTooltip(font, text, x, y);
	//#else
	//$$ 	com.mopicmp.mosaicary.client.platform.Gfx.tooltip(graphics, font, text, x, y);
	//#endif
	//$$ }
	//$$
	//$$ // A widget's place has getters and setters from 1.19.3 on, and before is two fields. A picture is
	//$$ // read from bytes from 1.19.3 on, and before from a stream.
	//$$
	//$$ @Pattern
	//$$ private static int getX(AbstractWidget widget) {
	//#if MC>=11903
	//$$ 	return widget.getX();
	//#else
	//$$ 	return widget.x;
	//#endif
	//$$ }
	//$$
	//$$ @Pattern
	//$$ private static int getY(AbstractWidget widget) {
	//#if MC>=11903
	//$$ 	return widget.getY();
	//#else
	//$$ 	return widget.y;
	//#endif
	//$$ }
	//$$
	//$$ @Pattern
	//$$ private static void setX(AbstractWidget widget, int x) {
	//#if MC>=11903
	//$$ 	widget.setX(x);
	//#else
	//$$ 	widget.x = x;
	//#endif
	//$$ }
	//$$
	//$$ @Pattern
	//$$ private static void setY(AbstractWidget widget, int y) {
	//#if MC>=11903
	//$$ 	widget.setY(y);
	//#else
	//$$ 	widget.y = y;
	//#endif
	//$$ }
	//$$
	//$$ @Pattern
	//$$ private static NativeImage read(byte[] bytes) throws java.io.IOException {
	//#if MC>=11903
	//$$ 	return NativeImage.read(bytes);
	//#else
	//$$ 	return NativeImage.read(new java.io.ByteArrayInputStream(bytes));
	//#endif
	//$$ }
	//#endif
}

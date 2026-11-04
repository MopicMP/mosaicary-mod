//#if MC<12000
//$$ package com.mopicmp.mosaicary.client.platform;
//$$
//$$ import java.util.List;
//$$
//$$ import com.mojang.blaze3d.systems.RenderSystem;
//$$ import com.mojang.blaze3d.vertex.PoseStack;
//$$
//$$ import net.minecraft.client.Minecraft;
//$$ import net.minecraft.client.gui.Font;
//$$ import net.minecraft.client.gui.GuiComponent;
//$$ import net.minecraft.client.gui.screens.Screen;
//$$ import net.minecraft.client.renderer.MultiBufferSource;
//$$ import net.minecraft.network.chat.Component;
//$$ import net.minecraft.resources.ResourceLocation;
//$$ import net.minecraft.util.FormattedCharSequence;
//$$
//$$ /**
//$$  * What 1.20's GuiGraphics does, for the versions before it, where a screen draws with a pose stack
//$$  * and GuiComponent's static methods. versions/1.20.1-1.19.2.txt makes every GuiGraphics a pose
//$$  * stack, so that the render methods the mod overrides keep the game's own signatures, and the
//$$  * patterns in ClientPatterns turn each call on it into a call here. The pose stack is taken by
//$$  * every method, used or not, as a pattern has to keep its receiver.
//$$  */
//$$ public final class Gfx {
//$$
//$$ 	/** The size of the textures blitNineSliced reads from, as on 1.20. */
//$$ 	private static final int ATLAS = 256;
//$$
//$$ 	private Gfx() {
//$$ 	}
//$$
//$$ 	public static int text(PoseStack pose, Font font, String text, int x, int y, int colour, boolean shadow) {
//$$ 		return shadow ? font.drawShadow(pose, text, x, y, colour) : font.draw(pose, text, x, y, colour);
//$$ 	}
//$$
//$$ 	public static int text(PoseStack pose, Font font, Component text, int x, int y, int colour, boolean shadow) {
//$$ 		return shadow ? font.drawShadow(pose, text, x, y, colour) : font.draw(pose, text, x, y, colour);
//$$ 	}
//$$
//$$ 	public static int text(PoseStack pose, Font font, FormattedCharSequence text, int x, int y, int colour, boolean shadow) {
//$$ 		return shadow ? font.drawShadow(pose, text, x, y, colour) : font.draw(pose, text, x, y, colour);
//$$ 	}
//$$
//$$ 	/** Text centred on x, as GuiComponent draws it; it does so for a FormattedCharSequence from 1.17 on only. */
//$$ 	public static void centered(PoseStack pose, Font font, FormattedCharSequence text, int x, int y, int colour) {
//$$ 		font.drawShadow(pose, text, x - font.width(text) / 2f, y, colour);
//$$ 	}
//$$
//$$ 	/** The texture the next blits read; before 1.17 the game binds it rather than handing it to a shader. */
//$$ 	public static void bind(ResourceLocation texture) {
//#if MC>=11700
//$$ 		RenderSystem.setShaderTexture(0, texture);
//#else
//$$ 		Minecraft.getInstance().getTextureManager().bind(texture);
//#endif
//$$ 	}
//$$
//$$ 	public static void blit(PoseStack pose, ResourceLocation texture, int x, int y, int width, int height, float u, float v,
//$$ 			int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
//$$ 		bind(texture);
//$$ 		GuiComponent.blit(pose, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight);
//$$ 	}
//$$
//$$ 	/**
//$$ 	 * A frame from a 256-pixel texture, as 1.20's: the corners as they are, the edges and the middle
//$$ 	 * repeated to fill the size asked for.
//$$ 	 */
//$$ 	public static void blitNineSliced(PoseStack pose, ResourceLocation texture, int x, int y, int width, int height,
//$$ 			int left, int top, int right, int bottom, int uWidth, int vHeight, int u, int v) {
//$$ 		left = Math.min(left, width / 2);
//$$ 		right = Math.min(right, width / 2);
//$$ 		top = Math.min(top, height / 2);
//$$ 		bottom = Math.min(bottom, height / 2);
//$$ 		bind(texture);
//$$ 		int middleU = uWidth - left - right;
//$$ 		int middleV = vHeight - top - bottom;
//$$ 		int middleWidth = width - left - right;
//$$ 		int middleHeight = height - top - bottom;
//$$ 		repeat(pose, x, y, left, top, u, v, left, top);
//$$ 		repeat(pose, x + left, y, middleWidth, top, u + left, v, middleU, top);
//$$ 		repeat(pose, x + width - right, y, right, top, u + uWidth - right, v, right, top);
//$$ 		repeat(pose, x, y + top, left, middleHeight, u, v + top, left, middleV);
//$$ 		repeat(pose, x + left, y + top, middleWidth, middleHeight, u + left, v + top, middleU, middleV);
//$$ 		repeat(pose, x + width - right, y + top, right, middleHeight, u + uWidth - right, v + top, right, middleV);
//$$ 		repeat(pose, x, y + height - bottom, left, bottom, u, v + vHeight - bottom, left, bottom);
//$$ 		repeat(pose, x + left, y + height - bottom, middleWidth, bottom, u + left, v + vHeight - bottom, middleU, bottom);
//$$ 		repeat(pose, x + width - right, y + height - bottom, right, bottom, u + uWidth - right, v + vHeight - bottom, right, bottom);
//$$ 	}
//$$
//$$ 	/** A width by height area filled with copies of one tile of the bound texture, the last ones cut short. */
//$$ 	private static void repeat(PoseStack pose, int x, int y, int width, int height, int u, int v, int tileWidth, int tileHeight) {
//$$ 		if (width <= 0 || height <= 0 || tileWidth <= 0 || tileHeight <= 0) return;
//$$ 		for (int dx = 0; dx < width; dx += tileWidth) {
//$$ 			int w = Math.min(tileWidth, width - dx);
//$$ 			for (int dy = 0; dy < height; dy += tileHeight) {
//$$ 				int h = Math.min(tileHeight, height - dy);
//$$ 				GuiComponent.blit(pose, x + dx, y + dy, u, v, w, h, ATLAS, ATLAS);
//$$ 			}
//$$ 		}
//$$ 	}
//$$
//$$ 	/** The pose stack itself, which is what GuiGraphics.pose() gives on 1.20. */
//$$ 	public static PoseStack pose(PoseStack pose) {
//$$ 		return pose;
//$$ 	}
//$$
//$$ 	/** Draws only inside the box from here on, in the screen's units, as 1.19's GuiComponent does (1.18.2 has none). */
//$$ 	public static void enableScissor(PoseStack pose, int x0, int y0, int x1, int y1) {
//$$ 		com.mojang.blaze3d.platform.Window window = Minecraft.getInstance().getWindow();
//$$ 		double scale = window.getGuiScale();
//$$ 		RenderSystem.enableScissor((int) (x0 * scale), (int) (window.getHeight() - y1 * scale),
//$$ 			Math.max(0, (int) ((x1 - x0) * scale)), Math.max(0, (int) ((y1 - y0) * scale)));
//$$ 	}
//$$
//$$ 	public static void disableScissor(PoseStack pose) {
//$$ 		RenderSystem.disableScissor();
//$$ 	}
//$$
//$$ 	public static void setColor(PoseStack pose, float red, float green, float blue, float alpha) {
//#if MC>=11700
//$$ 		RenderSystem.setShaderColor(red, green, blue, alpha);
//#else
//$$ 		RenderSystem.color4f(red, green, blue, alpha);
//#endif
//$$ 	}
//$$
//$$ 	public static MultiBufferSource.BufferSource bufferSource(PoseStack pose) {
//$$ 		return Minecraft.getInstance().renderBuffers().bufferSource();
//$$ 	}
//$$
//$$ 	/** Draws what was put in the buffers so far, as 1.20's does before something drawn directly. */
//$$ 	public static void flush(PoseStack pose) {
//$$ 		RenderSystem.disableDepthTest();
//$$ 		bufferSource(pose).endBatch();
//$$ 		RenderSystem.enableDepthTest();
//$$ 	}
//$$
//$$ 	/** Over the open screen, as 1.20's; nothing when there is none to measure the room by. */
//$$ 	public static void tooltip(PoseStack pose, Font font, List<? extends FormattedCharSequence> lines, int x, int y) {
//$$ 		Screen screen = Minecraft.getInstance().screen;
//$$ 		if (screen != null) screen.renderTooltip(pose, lines, x, y);
//$$ 	}
//$$
//$$ 	public static void tooltip(PoseStack pose, Font font, Component text, int x, int y) {
//$$ 		Screen screen = Minecraft.getInstance().screen;
//$$ 		if (screen != null) screen.renderTooltip(pose, text, x, y);
//$$ 	}
//$$ }
//#endif

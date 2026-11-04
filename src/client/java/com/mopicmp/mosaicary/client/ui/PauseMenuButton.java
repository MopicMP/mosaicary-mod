package com.mopicmp.mosaicary.client.ui;

//#if MC<12111
//$$ import net.minecraft.client.gui.Font;
//#endif
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
//#if MC>=11700
import net.minecraft.client.gui.narration.NarrationElementOutput;
//#endif
//#if MC>=12111
import net.minecraft.client.input.InputWithModifiers;
//#endif
import net.minecraft.network.chat.Component;

/**
 * The catalogue's button on the pause menu: a game-style 20 x 20 button with the
 * catalogue icon, set in the row of small icon buttons the menu already has.
 */
public final class PauseMenuButton extends AbstractButton {

	private final Runnable onPress;

	public PauseMenuButton(int x, int y, Component tooltip, Runnable onPress) {
		super(x, y, 20, 20, tooltip);
		this.onPress = onPress;
		setTooltip(Tooltip.create(tooltip));
	}

	// Before 1.21.9 a press comes without the keys held, and the button draws its background itself,
	// and its label over it; the label here is the tooltip and what is narrated, so it is left out.
	//#if MC>=12111
	@Override
	public void onPress(InputWithModifiers input) {
		onPress.run();
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		extractDefaultSprite(graphics);
		drawIcon(graphics);
	}
	//#elseif MC>=11903
	//$$ @Override
	//$$ public void onPress() {
	//$$ 	onPress.run();
	//$$ }
	//$$
	//$$ @Override
	//$$ protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float a) {
	//$$ 	super.renderWidget(graphics, mouseX, mouseY, a);
	//$$ 	drawIcon(graphics);
	//$$ }
	//$$
	//$$ @Override
	//$$ public void renderString(GuiGraphics graphics, Font font, int colour) {
	//$$ }
	//#else
	//$$ // Before 1.19.3 the game draws a button's background and label in one method, so the background
	//$$ // is drawn here as it draws it, without the label. Nor has a widget a place by getters or a
	//$$ // tooltip of its own: see PlacedWidget.
	//$$ private Tooltip tooltip;
	//$$
	//$$ @Override
	//$$ public void onPress() {
	//$$ 	onPress.run();
	//$$ }
	//$$
	//$$ @Override
	//$$ public void renderButton(com.mojang.blaze3d.vertex.PoseStack pose, int mouseX, int mouseY, float a) {
	//#if MC>=11700
	//$$ 	com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
	//$$ 	com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, WIDGETS_LOCATION);
	//$$ 	com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1, 1, 1, alpha);
	//#else
	//$$ 	net.minecraft.client.Minecraft.getInstance().getTextureManager().bind(WIDGETS_LOCATION);
	//$$ 	com.mojang.blaze3d.systems.RenderSystem.color4f(1, 1, 1, alpha);
	//#endif
	//$$ 	com.mojang.blaze3d.systems.RenderSystem.enableBlend();
	//$$ 	com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
	//$$ 	com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
	//$$ 	int v = 46 + getYImage(isHoveredOrFocused()) * 20;
	//$$ 	blit(pose, x, y, 0, v, width / 2, height);
	//$$ 	blit(pose, x + width / 2, y, 200 - width / 2, v, width / 2, height);
	//$$ 	drawIcon(pose);
	//$$ 	if (isHovered) Tooltip.hover(tooltip);
	//$$ }
	//$$
	//$$ private void setTooltip(Tooltip tooltip) {
	//$$ 	this.tooltip = tooltip;
	//$$ }
	//$$
	//$$ private int getX() {
	//$$ 	return x;
	//$$ }
	//$$
	//$$ private int getY() {
	//$$ 	return y;
	//$$ }
	//#endif

	private void drawIcon(GuiGraphicsExtractor graphics) {
		Icons.draw(graphics, Icon.WARDROBE, getX() + 2, getY() + 2, active ? 0xFFFFFFFF : 0xFFA0A0A0);
	}

	//#if MC>=11700
	@Override
	public void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
	//#endif
}

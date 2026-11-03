package com.mopicmp.mosaicary.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
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

	// Before 1.21.9 a press comes without the keys held, and the button draws its background itself.
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
	//#else
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
	//#endif

	private void drawIcon(GuiGraphicsExtractor graphics) {
		Icons.draw(graphics, Icon.WARDROBE, getX() + 2, getY() + 2, active ? 0xFFFFFFFF : 0xFFA0A0A0);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}

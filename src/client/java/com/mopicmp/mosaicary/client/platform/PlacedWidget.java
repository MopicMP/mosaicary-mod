//#if MC<11903
//$$ package com.mopicmp.mosaicary.client.platform;
//$$
//$$ import com.mojang.blaze3d.vertex.PoseStack;
//$$
//$$ import net.minecraft.client.gui.components.AbstractWidget;
//$$ import net.minecraft.network.chat.Component;
//$$
//$$ /**
//$$  * What a widget has from 1.19.3 on and not before, for the mod's own widgets on the versions before:
//$$  * its place through getters and setters rather than two fields, whether the pointer is over it,
//$$  * and a tooltip of its own, handed over while the pointer is over it (see {@link Tooltip}). Calls
//$$  * on other widgets go to the fields through the patterns in ClientPatterns.
//$$  */
//$$ public abstract class PlacedWidget extends AbstractWidget {
//$$
//$$ 	private Tooltip tooltip;
//$$
//$$ 	protected PlacedWidget(int x, int y, int width, int height, Component message) {
//$$ 		super(x, y, width, height, message);
//$$ 	}
//$$
//$$ 	public int getX() {
//$$ 		return x;
//$$ 	}
//$$
//$$ 	public int getY() {
//$$ 		return y;
//$$ 	}
//$$
//$$ 	public void setX(int x) {
//$$ 		this.x = x;
//$$ 	}
//$$
//$$ 	public void setY(int y) {
//$$ 		this.y = y;
//$$ 	}
//$$
//$$ 	public boolean isHovered() {
//$$ 		return isHovered;
//$$ 	}
//$$
//$$ 	public void setTooltip(Tooltip tooltip) {
//$$ 		this.tooltip = tooltip;
//$$ 	}
//$$
//#if MC<11700
//$$ 	/**
//$$ 	 * Whether the widget takes the pointer, as from 1.17 on; before, the game reads the field itself,
//$$ 	 * so a widget that says otherwise is asked here before a click or a hover.
//$$ 	 */
//$$ 	public boolean isActive() {
//$$ 		return visible && active;
//$$ 	}
//$$
//$$ 	@Override
//$$ 	public boolean mouseClicked(double mouseX, double mouseY, int button) {
//$$ 		return isActive() && super.mouseClicked(mouseX, mouseY, button);
//$$ 	}
//$$
//$$ 	@Override
//$$ 	public boolean isMouseOver(double mouseX, double mouseY) {
//$$ 		return isActive() && super.isMouseOver(mouseX, mouseY);
//$$ 	}
//$$
//#endif
//$$ 	@Override
//$$ 	public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
//$$ 		super.render(pose, mouseX, mouseY, partialTick);
//$$ 		if (visible && isHovered) Tooltip.hover(tooltip);
//$$ 	}
//$$ }
//#endif

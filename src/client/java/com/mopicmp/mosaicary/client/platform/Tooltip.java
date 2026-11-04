//#if MC<11903
//$$ package com.mopicmp.mosaicary.client.platform;
//$$
//$$ import java.util.List;
//$$
//$$ import com.mojang.blaze3d.vertex.PoseStack;
//$$
//$$ import net.minecraft.client.Minecraft;
//$$ import net.minecraft.client.gui.screens.Screen;
//$$ import net.minecraft.network.chat.Component;
//$$ import net.minecraft.util.FormattedCharSequence;
//$$
//$$ /**
//$$  * The game's Tooltip of 1.19.3, for the versions before it, where a widget has no tooltip of its
//$$  * own. The mod's widgets keep theirs and, under the pointer, hand it over for the frame
//$$  * ({@link #hover}); it is drawn once the whole screen is (mixin/ScreenTooltipMixin), so that no
//$$  * widget drawn later covers it, as 1.19.3 does. versions/1.20.1-1.19.2.txt points the game's
//$$  * name here.
//$$  */
//$$ public final class Tooltip {
//$$
//$$ 	/** As wide as 1.19.3 lets a tooltip be before it wraps it. */
//$$ 	private static final int WIDTH = 170;
//$$
//$$ 	private static List<FormattedCharSequence> next;
//$$
//$$ 	private final Component message;
//$$
//$$ 	private Tooltip(Component message) {
//$$ 		this.message = message;
//$$ 	}
//$$
//$$ 	public static Tooltip create(Component message) {
//$$ 		return new Tooltip(message);
//$$ 	}
//$$
//$$ 	/** Shows the tooltip once the screen drawn now is; null shows none. */
//$$ 	public static void hover(Tooltip tooltip) {
//$$ 		if (tooltip != null) next = Minecraft.getInstance().font.split(tooltip.message, WIDTH);
//$$ 	}
//$$
//$$ 	/** Shows these lines once the screen drawn now is. */
//$$ 	public static void hover(List<FormattedCharSequence> lines) {
//$$ 		next = lines;
//$$ 	}
//$$
//$$ 	/** Draws the tooltip handed over this frame, if any, over the screen, and forgets it. */
//$$ 	public static void drawOver(Screen screen, PoseStack pose, int mouseX, int mouseY) {
//$$ 		List<FormattedCharSequence> lines = next;
//$$ 		next = null;
//$$ 		if (lines != null) screen.renderTooltip(pose, lines, mouseX, mouseY);
//$$ 	}
//$$ }
//#endif

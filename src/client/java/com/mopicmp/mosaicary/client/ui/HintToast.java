package com.mopicmp.mosaicary.client.ui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
//#if MC>=12111
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
//#else
//$$ import net.minecraft.client.gui.components.toasts.ToastComponent;
//#endif
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/**
 * A hint in the corner, in the style of the game's own tutorial hints: the catalogue
 * icon, a title and a line, gone by itself after a few seconds.
 *
 * Toasts never take input, so a hint cannot get in the way of play.
 */
public final class HintToast implements Toast {

	//#if MC>=12002
	private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("toast/tutorial");
	//#endif
	private static final int TEXT_LEFT = 30;
	private static final int TEXT_WIDTH = 126;
	/** The game's colour for the title line of its tutorial hints. */
	private static final int TITLE_COLOUR = 0xFF500050;

	private final List<FormattedCharSequence> lines = new ArrayList<>();
	private final int titleLines;
	private final long showForMs;
	//#if MC>=12111
	private Visibility visibility = Visibility.SHOW;
	//#endif

	public HintToast(Font font, Component title, Component message, long showForMs) {
		lines.addAll(font.split(title, TEXT_WIDTH));
		titleLines = lines.size();
		if (message != null) lines.addAll(font.split(message, TEXT_WIDTH));
		this.showForMs = showForMs;
	}

	// Before 1.21.2 a toast is drawn and says whether it stays in one call.
	//#if MC>=12111
	@Override
	public Visibility getWantedVisibility() {
		return visibility;
	}

	@Override
	public void update(ToastManager manager, long fullyVisibleForMs) {
		if (fullyVisibleForMs > showForMs) visibility = Visibility.HIDE;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long fullyVisibleForMs) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND, 0, 0, width(), height());
		draw(graphics, font);
	}
	//#else
	//$$ @Override
	//$$ public Visibility render(GuiGraphics graphics, ToastComponent toasts, long fullyVisibleForMs) {
	//#if MC>=12002
	//$$ 	graphics.blitSprite(BACKGROUND, 0, 0, width(), height());
	//#else
	//$$ 	// Before 1.20.2 the hint's frame is a part of the one sheet of toasts, 160 x 32 at (0, 96).
	//$$ 	graphics.blitNineSliced(TEXTURE, 0, 0, width(), height(), 4, 4, 4, 4, 160, 32, 0, 96);
	//#endif
	//$$ 	draw(graphics, toasts.getMinecraft().font);
	//$$ 	return fullyVisibleForMs > showForMs ? Visibility.HIDE : Visibility.SHOW;
	//$$ }
	//#endif

	@Override
	public int height() {
		return 7 + Math.max(lines.size(), 2) * 11 + 3;
	}

	private void draw(GuiGraphicsExtractor graphics, Font font) {
		Icons.draw(graphics, Icon.CATALOGUE, 8, 8, TITLE_COLOUR);
		int textTop = 7 + (Math.max(lines.size(), 2) * 11 - lines.size() * 11) / 2;
		for (int i = 0; i < lines.size(); i++) {
			graphics.text(font, lines.get(i), TEXT_LEFT, textTop + i * 11, i < titleLines ? TITLE_COLOUR : 0xFF000000, false);
		}
	}
}

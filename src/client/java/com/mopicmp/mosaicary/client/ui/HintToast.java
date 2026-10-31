package com.mopicmp.mosaicary.client.ui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
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

	private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("toast/tutorial");
	private static final int TEXT_LEFT = 30;
	private static final int TEXT_WIDTH = 126;
	/** The game's colour for the title line of its tutorial hints. */
	private static final int TITLE_COLOUR = 0xFF500050;

	private final List<FormattedCharSequence> lines = new ArrayList<>();
	private final int titleLines;
	private final long showForMs;
	private Visibility visibility = Visibility.SHOW;

	public HintToast(Font font, Component title, Component message, long showForMs) {
		lines.addAll(font.split(title, TEXT_WIDTH));
		titleLines = lines.size();
		if (message != null) lines.addAll(font.split(message, TEXT_WIDTH));
		this.showForMs = showForMs;
	}

	@Override
	public Visibility getWantedVisibility() {
		return visibility;
	}

	@Override
	public void update(ToastManager manager, long fullyVisibleForMs) {
		if (fullyVisibleForMs > showForMs) visibility = Visibility.HIDE;
	}

	@Override
	public int height() {
		return 7 + Math.max(lines.size(), 2) * 11 + 3;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long fullyVisibleForMs) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND, 0, 0, width(), height());
		Icons.draw(graphics, Icon.CATALOGUE, 8, 8, TITLE_COLOUR);
		int textTop = 7 + (Math.max(lines.size(), 2) * 11 - lines.size() * 11) / 2;
		for (int i = 0; i < lines.size(); i++) {
			graphics.text(font, lines.get(i), TEXT_LEFT, textTop + i * 11, i < titleLines ? TITLE_COLOUR : 0xFF000000, false);
		}
	}
}

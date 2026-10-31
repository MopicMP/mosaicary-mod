package com.mopicmp.mosaicary.client.ui;

import java.util.List;

import com.mopicmp.mosaicary.client.MosaicaryClient;
import com.mopicmp.mosaicary.client.site.Settings;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Asked once, on the first Wear: whether to share the worn model with other players
 * through mosaicary.com and see theirs.
 *
 * A small dialog over the catalogue: two buttons and nothing else to do. Whatever
 * the answer, the model is then put on; the answer only decides whether the site is
 * told about it and asked about others. Escape counts as not now and does not ask
 * again. The answer can be changed later in the catalogue's settings.
 */
public final class ConsentScreen extends Screen {

	private static final int WIDTH = 280;
	private static final int ROW = 22;

	private final Screen parent;
	private final Runnable then;

	/**
	 * @param parent the screen to go back to
	 * @param then   what to do after the answer, on the parent screen: the wear itself
	 */
	public ConsentScreen(Screen parent, Runnable then) {
		super(Component.translatable("mosaicary.consent.title"));
		this.parent = parent;
		this.then = then;
	}

	private int top() {
		return height / 2 - 60;
	}

	/** Wide enough for the question on its one line, in whatever language it is. */
	private int panelWidth() {
		return Math.min(width - 16, Math.max(WIDTH, 10 + Icons.SIZE + 6 + font.width(title) + 12));
	}

	private List<FormattedCharSequence> lines() {
		return font.split(Component.translatable("mosaicary.consent.text"), panelWidth() - 24);
	}

	@Override
	protected void init() {
		int left = (width - panelWidth()) / 2;
		int buttonsY = top() + 34 + lines().size() * 10 + 8;
		Component yes = Component.translatable("mosaicary.consent.yes");
		Component no = Component.translatable("mosaicary.consent.no");
		int yesWidth = IconButton.widthFor(yes);
		int noWidth = IconButton.widthFor(no);
		int x = left + (panelWidth() - yesWidth - noWidth - 6) / 2;
		yesButton = new IconButton(x, buttonsY, yesWidth, ROW, Icon.SHARING, yes,
			Component.translatable("mosaicary.consent.yes.tooltip"), () -> answer(Settings.YES)).primary(true);
		addRenderableWidget(yesButton);
		noButton = new IconButton(x + yesWidth + 6, buttonsY, noWidth, ROW, Icon.CLOSE, no,
			Component.translatable("mosaicary.consent.no.tooltip"), () -> answer(Settings.NO));
		addRenderableWidget(noButton);
	}

	private IconButton yesButton;
	private IconButton noButton;

	/** The two answers; for the game test. */
	public IconButton yesButton() {
		return yesButton;
	}

	public IconButton noButton() {
		return noButton;
	}

	private void answer(String answer) {
		MosaicaryClient client = MosaicaryClient.instance();
		client.settings().sharing = answer;
		client.saveSettings();
		minecraft.gui.setScreen(parent);
		if (then != null) then.run();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		// The catalogue stays in view behind the dialog, dimmed.
		if (parent != null) parent.extractRenderState(graphics, -10000, -10000, a);
		graphics.nextStratum();
		graphics.fill(0, 0, width, height, 0xB0000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		if (CatalogueScreen.hideMouse) {
			mouseX = -10000;
			mouseY = -10000;
		}
		int left = (width - panelWidth()) / 2;
		List<FormattedCharSequence> lines = lines();
		int bottom = top() + 34 + lines.size() * 10 + 8 + ROW + 10;
		Theme.card(graphics, left, top(), left + panelWidth(), bottom);
		Icons.draw(graphics, Icon.SHARING, left + 10, top() + 9, Theme.ACCENT);
		graphics.text(font, title, left + 10 + Icons.SIZE + 6, top() + 13, Theme.TEXT, false);
		int y = top() + 34;
		for (FormattedCharSequence line : lines) {
			graphics.text(font, line, left + 12, y, Theme.TEXT_DIM, false);
			y += 10;
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	@Override
	public void onClose() {
		answer(Settings.NO);
	}
}

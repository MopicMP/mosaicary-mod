package com.mopicmp.mosaicary.client.ui;

import com.mopicmp.mosaicary.client.MosaicaryClient;
import com.mopicmp.mosaicary.client.site.Settings;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Where the skin beside the main menu or the pause menu stands and how large it is, with the
 * figure itself on the screen as it will stand on the menu: the menu's buttons are drawn as
 * outlines in their places, so the figure is placed against them.
 *
 * The place is an offset from the figure's own spot, X to the right and Y down, in GUI units,
 * typed or stepped with the buttons beside the fields; the size is a slider, in percent of its
 * own height; under it, whether the player's name stands over the figure. Every change is saved
 * and shown at once, here and on the menu.
 */
public final class MenuSkinScreen extends Screen {

	private static final int ROW = 20;
	private static final int PANEL = 160;
	private static final int STEP = 4;

	private final Screen parent;
	private final MenuSkin.Beside beside;
	private EditBox xField;
	private EditBox yField;
	private SizeSlider sizeSlider;
	private IconButton nameOn;
	private IconButton nameOff;
	private int nameY;
	private boolean nameCut;

	public MenuSkinScreen(Screen parent, MenuSkin.Beside beside) {
		super(Component.translatable(beside == MenuSkin.Beside.MAIN ? "mosaicary.menu_skin.title" : "mosaicary.menu_skin.title.pause"));
		this.parent = parent;
		this.beside = beside;
	}

	private Settings settings() {
		return MosaicaryClient.instance().settings();
	}

	private int panelTop() {
		return Math.max(4, height / 2 - 83);
	}

	@Override
	protected void init() {
		int x = 8;
		int y = panelTop() + 26;
		xField = coordinate(x, y, beside.x(settings()), value -> {
			if (beside == MenuSkin.Beside.MAIN) settings().menuSkinX = value;
			else settings().pauseSkinX = value;
		});
		y += ROW + 6;
		yField = coordinate(x, y, beside.y(settings()), value -> {
			if (beside == MenuSkin.Beside.MAIN) settings().menuSkinY = value;
			else settings().pauseSkinY = value;
		});
		y += ROW + 10;
		sizeSlider = new SizeSlider(x + 4, y, PANEL - 8, ROW, beside.size(settings()));
		addRenderableWidget(sizeSlider);
		y += ROW + 6;
		// The name over the figure: two icon buttons, the current one lit.
		nameY = y;
		int right = x + PANEL - 4;
		nameOn = IconButton.square(right - 2 * ROW - 2, y, ROW, Icon.SHARING, Component.translatable("mosaicary.menu_skin.name.on"),
			() -> setNamed(true));
		nameOff = IconButton.square(right - ROW, y, ROW, Icon.CLOSE, Component.translatable("mosaicary.menu_skin.name.off"),
			() -> setNamed(false));
		addRenderableWidget(nameOn);
		addRenderableWidget(nameOff);
		showNamed();
		y += ROW + 10;
		int half = (PANEL - 8 - 4) / 2;
		addRenderableWidget(new IconButton(x + 4, y, half, ROW, Icon.RESET_VIEW, Component.translatable("mosaicary.menu_skin.reset"),
			Component.translatable("mosaicary.menu_skin.reset.tooltip"), this::reset));
		addRenderableWidget(new IconButton(x + 4 + half + 4, y, half, ROW, Icon.DOWNLOADED, Component.translatable("mosaicary.menu_skin.done"),
			null, this::onClose).primary(true));
		// The figure itself, where the menu will show it.
		addRenderableWidget(new MenuSkin(this, beside, true));
	}

	/** One coordinate: its letter, minus, the typed number, plus. */
	private EditBox coordinate(int x, int y, int value, java.util.function.IntConsumer set) {
		int field = PANEL - 8 - 14 - 2 * (ROW + 2);
		int fieldX = x + 4 + 14 + ROW + 2;
		EditBox box = new EditBox(font, fieldX + 4, y + (ROW - 8) / 2, field - 8, 10, Component.empty());
		box.setBordered(false);
		box.setMaxLength(5);
		box.setValue(Integer.toString(value));
		box.setTextColor(Theme.TEXT);
		box.setResponder(text -> {
			// Digits and a leading minus only; anything else typed is taken out again.
			String clean = text.replaceAll("[^0-9-]", "").replaceAll("(?<!^)-", "");
			if (!clean.equals(text)) {
				box.setValue(clean);
				return;
			}
			try {
				set.accept(Integer.parseInt(text));
				save();
			} catch (NumberFormatException halfTyped) {
				// "-" or nothing yet: the figure waits for a number.
			}
		});
		addRenderableWidget(box);
		addRenderableWidget(IconButton.square(x + 4 + 14, y, ROW, Icon.LESS, Component.translatable("mosaicary.menu_skin.less"),
			() -> box.setValue(Integer.toString(parse(box) - STEP))));
		addRenderableWidget(IconButton.square(fieldX + field + 2, y, ROW, Icon.MORE, Component.translatable("mosaicary.menu_skin.more"),
			() -> box.setValue(Integer.toString(parse(box) + STEP))));
		return box;
	}

	private void setNamed(boolean on) {
		if (beside == MenuSkin.Beside.MAIN) settings().menuSkinName = on;
		else settings().pauseSkinName = on;
		save();
		showNamed();
	}

	private void showNamed() {
		boolean on = beside.named(settings());
		nameOn.primary(on);
		nameOff.primary(!on);
	}

	/** The button that hides the name over the figure; for the game test. */
	public IconButton nameOffButton() {
		return nameOff;
	}

	/** Whether the name row's words were too wide and were cut short when last drawn; for the game test. */
	public boolean nameLabelCut() {
		return nameCut;
	}

	private static int parse(EditBox box) {
		try {
			return Integer.parseInt(box.getValue());
		} catch (NumberFormatException none) {
			return 0;
		}
	}

	private void save() {
		settings().normalise();
		MosaicaryClient.instance().saveSettings();
	}

	private void reset() {
		if (beside == MenuSkin.Beside.MAIN) {
			settings().menuSkinX = 0;
			settings().menuSkinY = 0;
			settings().menuSkinSize = 100;
		} else {
			settings().pauseSkinX = 0;
			settings().pauseSkinY = 0;
			settings().pauseSkinSize = 100;
		}
		save();
		rebuildWidgets();
	}

	private final class SizeSlider extends AbstractSliderButton {
		SizeSlider(int x, int y, int width, int height, int percent) {
			super(x, y, width, height, Component.empty(), toValue(percent));
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.translatable("mosaicary.menu_skin.size", percent()));
		}

		@Override
		protected void applyValue() {
			if (beside == MenuSkin.Beside.MAIN) settings().menuSkinSize = percent();
			else settings().pauseSkinSize = percent();
			save();
		}

		private int percent() {
			int span = Settings.MENU_SKIN_LARGEST - Settings.MENU_SKIN_SMALLEST;
			return (int) Math.round((Settings.MENU_SKIN_SMALLEST + value * span) / 5.0) * 5;
		}
	}

	private static double toValue(int percent) {
		return (percent - Settings.MENU_SKIN_SMALLEST) / (double) (Settings.MENU_SKIN_LARGEST - Settings.MENU_SKIN_SMALLEST);
	}

	@Override
	//#if MC>=12002
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
	//#else
	//$$ public void renderBackground(GuiGraphics graphics) {
	//$$ 	super.renderBackground(graphics);
	//#endif
		graphics.fill(0, 0, width, height, Theme.SCREEN);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		ghostMenu(graphics);
		int x = 8;
		int top = panelTop();
		int bottom = top + 26 + 2 * (ROW + 6) + 2 * (ROW + 10) + ROW + 6;
		Theme.card(graphics, x, top, x + PANEL, bottom);
		Icons.draw(graphics, Icon.OWN_SKIN, x + 6, top + 6, Theme.ACCENT);
		graphics.text(font, ellipsis(title.getString(), PANEL - Icons.SIZE - 16), x + Icons.SIZE + 10, top + 10, Theme.TEXT, false);
		label(graphics, xField, "X", Component.translatable("mosaicary.menu_skin.x.tooltip"), mouseX, mouseY);
		label(graphics, yField, "Y", Component.translatable("mosaicary.menu_skin.y.tooltip"), mouseX, mouseY);
		nameLabel(graphics, mouseX, mouseY);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	/** The name row's words, left of its two buttons, with what they do in the tooltip. */
	private void nameLabel(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		String words = Component.translatable("mosaicary.menu_skin.name").getString();
		String text = ellipsis(words, nameOn.getX() - 4 - 14);
		nameCut = !text.equals(words);
		graphics.text(font, text, 14, nameY + (ROW - 8) / 2, Theme.TEXT_DIM, false);
		if (mouseX >= 8 && mouseX < nameOn.getX() && mouseY >= nameY && mouseY < nameY + ROW) {
			Theme.tooltip(graphics, font, Component.translatable("mosaicary.menu_skin.name.tooltip"), mouseX, mouseY);
		}
	}

	/** A coordinate's letter and the field-coloured card under its number. */
	private void label(GuiGraphicsExtractor graphics, EditBox box, String letter, Component tooltip, int mouseX, int mouseY) {
		int y = box.getY() - (ROW - 8) / 2;
		graphics.text(font, letter, 14, y + (ROW - 8) / 2, Theme.TEXT_DIM, false);
		Theme.card(graphics, box.getX() - 4, y, box.getX() + box.getWidth() + 4, y + ROW, Theme.FIELD,
			box.isFocused() ? Theme.ACCENT : Theme.EDGE);
		if (mouseX >= 8 && mouseX < box.getX() && mouseY >= y && mouseY < y + ROW) {
			Theme.tooltip(graphics, font, tooltip, mouseX, mouseY);
		}
	}

	/** The main menu's buttons as outlines in their places, to set the figure against. */
	private void ghostMenu(GuiGraphicsExtractor graphics) {
		if (beside == MenuSkin.Beside.PAUSE) {
			ghostPauseMenu(graphics);
			return;
		}
		int top = MenuSkin.mainButtonsTop(height);
		int left = width / 2 - 100;
		int ghost = 0x40FFFFFF;
		for (int row = 0; row < 3; row++) outline(graphics, left, top + row * 24, left + 200, top + row * 24 + 20, ghost);
		for (int i = 0; i < 3; i++) outline(graphics, width / 2 - 34 + i * 24, top + 72, width / 2 - 14 + i * 24, top + 92, ghost);
		outline(graphics, left, top + 96, left + 98, top + 116, ghost);
		outline(graphics, left + 102, top + 96, left + 200, top + 116, ghost);
	}

	/** The pause menu: a wide row, two halves, the small buttons, two halves and a wide row. */
	private void ghostPauseMenu(GuiGraphicsExtractor graphics) {
		int top = MenuSkin.pauseButtonsTop(height);
		int half = MenuSkin.PAUSE_BUTTONS_HALF;
		int left = width / 2 - half;
		int right = width / 2 + half;
		int ghost = 0x40FFFFFF;
		outline(graphics, left, top, right, top + 20, ghost);
		for (int row : new int[] { 1, 3 }) {
			outline(graphics, left, top + row * 24, left + 98, top + row * 24 + 20, ghost);
			outline(graphics, right - 98, top + row * 24, right, top + row * 24 + 20, ghost);
		}
		for (int i = 0; i < 6; i++) outline(graphics, width / 2 - 70 + i * 24, top + 48, width / 2 - 50 + i * 24, top + 68, ghost);
		outline(graphics, left, top + 96, right, top + 116, ghost);
	}

	private static void outline(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int colour) {
		graphics.fill(x0, y0, x1, y0 + 1, colour);
		graphics.fill(x0, y1 - 1, x1, y1, colour);
		graphics.fill(x0, y0, x0 + 1, y1, colour);
		graphics.fill(x1 - 1, y0, x1, y1, colour);
	}

	private String ellipsis(String text, int room) {
		if (font.width(text) <= room) return text;
		return font.plainSubstrByWidth(text, Math.max(0, room - font.width("..."))) + "...";
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	//#if MC<11900
	//$$ /** Lays the screen out anew, as rebuildWidgets does from 1.19 on. */
	//$$ private void rebuildWidgets() {
	//$$ 	init(minecraft, width, height);
	//$$ }
	//#endif
}

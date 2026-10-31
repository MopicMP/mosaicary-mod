package com.mopicmp.mosaicary.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mopicmp.mosaicary.client.Catalogue;
import com.mopicmp.mosaicary.client.MosaicaryClient;
import com.mopicmp.mosaicary.client.site.ModelCard;
import com.mopicmp.mosaicary.client.site.Settings;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

/**
 * Chooses which of the ring's slots a model goes into.
 *
 * Eight slots in two rows of four, each a card with the picture of the model it
 * holds. A click puts the model into that slot; a click on the slot that already
 * holds this model empties it. The screen returns to the catalogue after a choice.
 */
public final class SlotScreen extends Screen {

	private static final int SLOT = 64;
	private static final int GAP = 6;

	private final Screen parent;
	private final ModelCard card;

	public SlotScreen(Screen parent, ModelCard card) {
		super(Component.translatable("mosaicary.slots.title"));
		this.parent = parent;
		this.card = card;
	}

	@Override
	protected void init() {
		int panelWidth = 4 * SLOT + 3 * GAP + 16;
		int left = (width - panelWidth) / 2;
		int top = (height - (2 * SLOT + GAP + 52)) / 2;
		addRenderableWidget(IconButton.square(left + panelWidth - 22 - 4, top + 4, 22, Icon.CLOSE,
			Component.translatable("mosaicary.close"), this::onClose));
	}

	private int[] slotAt(int index) {
		int panelWidth = 4 * SLOT + 3 * GAP + 16;
		int left = (width - panelWidth) / 2 + 8;
		int top = (height - (2 * SLOT + GAP + 52)) / 2 + 36;
		return new int[] { left + (index % 4) * (SLOT + GAP), top + (index / 4) * (SLOT + GAP) };
	}

	/** The middle of a slot, in GUI units; for the game test. */
	public int[] slotCentre(int index) {
		int[] at = slotAt(index);
		return new int[] { at[0] + SLOT / 2, at[1] + SLOT / 2 };
	}

	private int slotUnder(double mouseX, double mouseY) {
		for (int i = 0; i < Settings.SLOTS; i++) {
			int[] at = slotAt(i);
			if (mouseX >= at[0] && mouseX < at[0] + SLOT && mouseY >= at[1] && mouseY < at[1] + SLOT) return i;
		}
		return -1;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		graphics.fill(0, 0, width, height, Theme.SCREEN);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		if (CatalogueScreen.hideMouse) {
			mouseX = -10000;
			mouseY = -10000;
		}
		int panelWidth = 4 * SLOT + 3 * GAP + 16;
		int left = (width - panelWidth) / 2;
		int top = (height - (2 * SLOT + GAP + 52)) / 2;
		Theme.card(graphics, left, top, left + panelWidth, top + 2 * SLOT + GAP + 52);
		Icons.draw(graphics, Icon.BIND, left + 8, top + 7, Theme.ACCENT);
		graphics.text(font, Component.translatable("mosaicary.slots.title", card.name()), left + 8 + Icons.SIZE + 6,
			top + 11, Theme.TEXT, false);

		Settings settings = MosaicaryClient.instance().settings();
		Catalogue catalogue = MosaicaryClient.instance().catalogue();
		int hovered = slotUnder(mouseX, mouseY);
		for (int i = 0; i < Settings.SLOTS; i++) {
			int[] at = slotAt(i);
			Settings.Saved held = settings.slots.get(i);
			boolean mine = held != null && held.id.equals(card.id());
			Theme.card(graphics, at[0], at[1], at[0] + SLOT, at[1] + SLOT, i == hovered ? Theme.CARD_HOVER : Theme.FIELD,
				mine ? Theme.ACCENT : i == hovered ? Theme.EDGE_HOVER : Theme.EDGE);
			if (held == null) {
				Icons.draw(graphics, Icon.BIND, at[0] + (SLOT - Icons.SIZE) / 2, at[1] + (SLOT - Icons.SIZE) / 2, Theme.TEXT_OFF);
			} else {
				Catalogue.Picture picture = catalogue.picture(held.card());
				if (picture.state() == Catalogue.State.READY) {
					graphics.blit(RenderPipelines.GUI_TEXTURED, picture.texture(), at[0] + 4, at[1] + 4, 0, 0, SLOT - 8,
						SLOT - 8, picture.width(), picture.height(), picture.width(), picture.height());
				} else {
					Icons.draw(graphics, Icon.CUBES, at[0] + (SLOT - Icons.SIZE) / 2, at[1] + (SLOT - Icons.SIZE) / 2, Theme.TEXT_DIM);
				}
			}
			// The slot's number, on a small card of its own in the corner.
			String number = Integer.toString(i + 1);
			Theme.card(graphics, at[0] + 2, at[1] + 2, at[0] + 4 + font.width(number) + 4, at[1] + 14, Theme.CARD, Theme.EDGE);
			graphics.text(font, number, at[0] + 5, at[1] + 4, Theme.TEXT, false);
			if (i == hovered) {
				Component said = mine ? Component.translatable("mosaicary.slots.clear")
					: held == null ? Component.translatable("mosaicary.slots.put")
					: Component.translatable("mosaicary.slots.replace", held.name);
				Theme.tooltip(graphics, font, said, mouseX, mouseY);
			}
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;
		int slot = slotUnder(event.x(), event.y());
		if (slot < 0) return false;
		choose(slot);
		return true;
	}

	/** Puts the model into a slot, or empties the slot if it already holds this model. */
	public void choose(int slot) {
		Settings settings = MosaicaryClient.instance().settings();
		Settings.Saved held = settings.slots.get(slot);
		if (held != null && held.id.equals(card.id())) {
			settings.slots.set(slot, null);
		} else {
			settings.slots.set(slot, Settings.Saved.of(card));
		}
		MosaicaryClient.instance().saveSettings();
		onClose();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		// The number keys pick a slot directly.
		int key = event.key();
		if (key >= InputConstants.KEY_1 && key < InputConstants.KEY_1 + Settings.SLOTS) {
			choose(key - InputConstants.KEY_1);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}

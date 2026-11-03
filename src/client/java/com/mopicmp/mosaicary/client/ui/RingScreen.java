//#if MC>=12111
// Worn models and their catalogue are built from 1.21.11 on (docs/mod-versions-plan.md).
package com.mopicmp.mosaicary.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mopicmp.mosaicary.client.Catalogue;
import com.mopicmp.mosaicary.client.MosaicaryClient;
import com.mopicmp.mosaicary.client.site.ModelCard;
import com.mopicmp.mosaicary.client.site.Settings;
import com.mopicmp.mosaicary.client.wear.Wearing;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

/**
 * The quick-select ring: eight slots around the middle of the screen, the player's
 * own skin in the centre.
 *
 * Opened by holding the ring key. The pointer chooses by direction: whichever slot
 * lies in the direction of the pointer from the centre is the chosen one, and close
 * to the centre the own skin is. Releasing the key applies the choice and closes the
 * ring; so does a click. Escape closes it without a change.
 *
 * Each slot shows its model's card picture; the name of the chosen one is shown on a
 * card below the centre.
 */
public final class RingScreen extends Screen {

	private static final int SLOT = 36;
	/** Within this distance of the centre, in GUI units, the centre is chosen. */
	private static final int CENTRE = 26;

	private final KeyMapping key;
	/** The slot under the pointer: 0 to 7, -1 for the centre. */
	private int chosen = -1;

	public RingScreen(KeyMapping key) {
		super(Component.translatable("mosaicary.ring.title"));
		this.key = key;
	}

	/**
	 * How far the slots sit from the centre. At least far enough that the diagonal
	 * slots clear the name card under the centre; more on a larger screen.
	 */
	private int radius() {
		int clear = (int) Math.ceil((CENTRE + 2 + 16 + 4 + SLOT / 2.0) / Math.sqrt(0.5));
		return Math.max(clear, Math.min(width, height) / 2 - SLOT / 2 - 18);
	}

	/** Where slot {@code index} sits: clockwise from the top. */
	private int[] slotCentre(int index) {
		double angle = -Math.PI / 2 + index * 2 * Math.PI / Settings.SLOTS;
		return new int[] { width / 2 + (int) Math.round(Math.cos(angle) * radius()),
			height / 2 + (int) Math.round(Math.sin(angle) * radius()) };
	}

	/** The middle of a slot, in GUI units; for the game test. */
	public int[] slotPoint(int index) {
		return slotCentre(index);
	}

	/** The slot in the direction of a point, or -1 for the centre. */
	int slotToward(double x, double y) {
		double dx = x - width / 2.0;
		double dy = y - height / 2.0;
		if (dx * dx + dy * dy < CENTRE * CENTRE) return -1;
		double angle = Math.atan2(dy, dx) + Math.PI / 2;
		double step = 2 * Math.PI / Settings.SLOTS;
		int index = (int) Math.floor((angle + step / 2) / step);
		return ((index % Settings.SLOTS) + Settings.SLOTS) % Settings.SLOTS;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		// The world stays visible behind the ring, only dimmed.
		graphics.fill(0, 0, width, height, 0x88000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		if (!Theme.hideMouse) chosen = slotToward(mouseX, mouseY);
		Settings settings = MosaicaryClient.instance().settings();
		Catalogue catalogue = MosaicaryClient.instance().catalogue();
		Wearing wearing = MosaicaryClient.instance().wearing();
		ModelCard worn = wearing.worn();

		for (int i = 0; i < Settings.SLOTS; i++) {
			int[] at = slotCentre(i);
			int x0 = at[0] - SLOT / 2;
			int y0 = at[1] - SLOT / 2;
			Settings.Saved held = settings.slots.get(i);
			boolean wornHere = held != null && worn != null && held.id.equals(worn.id());
			Theme.card(graphics, x0, y0, x0 + SLOT, y0 + SLOT, i == chosen ? Theme.CARD_HOVER : Theme.CARD,
				i == chosen ? Theme.ACCENT : wornHere ? Theme.ACCENT_DARK : Theme.EDGE);
			if (held == null) {
				Icons.draw(graphics, Icon.BIND, at[0] - Icons.SIZE / 2, at[1] - Icons.SIZE / 2, Theme.TEXT_OFF);
			} else {
				Catalogue.Picture picture = catalogue.picture(held.card());
				if (picture.state() == Catalogue.State.READY) {
					graphics.blit(RenderPipelines.GUI_TEXTURED, picture.texture(), x0 + 3, y0 + 3, 0, 0, SLOT - 6, SLOT - 6,
						picture.width(), picture.height(), picture.width(), picture.height());
				} else {
					Icons.draw(graphics, Icon.CUBES, at[0] - Icons.SIZE / 2, at[1] - Icons.SIZE / 2, Theme.TEXT_DIM);
				}
			}
			String number = Integer.toString(i + 1);
			Theme.card(graphics, x0 + 1, y0 + 1, x0 + 3 + font.width(number) + 4, y0 + 13, Theme.CARD, Theme.EDGE);
			graphics.text(font, number, x0 + 4, y0 + 3, Theme.TEXT, false);
		}

		// The centre: the player's own skin.
		int c = CENTRE - 4;
		Theme.card(graphics, width / 2 - c, height / 2 - c, width / 2 + c, height / 2 + c,
			chosen == -1 ? Theme.CARD_HOVER : Theme.CARD, chosen == -1 ? Theme.ACCENT : worn == null ? Theme.ACCENT_DARK : Theme.EDGE);
		Icons.draw(graphics, Icon.OWN_SKIN, width / 2 - Icons.SIZE / 2, height / 2 - Icons.SIZE / 2,
			chosen == -1 ? Theme.ACCENT : Theme.TEXT);

		// The name of what is chosen, on a card below the centre.
		Component name;
		if (chosen == -1) {
			name = Component.translatable("mosaicary.ring.own_skin");
		} else {
			Settings.Saved held = settings.slots.get(chosen);
			name = held == null ? Component.translatable("mosaicary.ring.empty", chosen + 1) : Component.literal(held.name);
		}
		int nameWidth = font.width(name) + 12;
		int nameTop = height / 2 + CENTRE + 4;
		Theme.card(graphics, width / 2 - nameWidth / 2, nameTop, width / 2 + nameWidth / 2, nameTop + 16);
		graphics.centeredText(font, name, width / 2, nameTop + 4, Theme.TEXT);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	/** Picks a slot directly; for the screenshot run, which has no pointer to move. */
	public void choose(int slot) {
		chosen = slot;
	}

	/** Applies the chosen slot (or the own skin) and closes the ring. */
	public void apply() {
		Wearing wearing = MosaicaryClient.instance().wearing();
		if (chosen == -1) {
			wearing.takeOff();
		} else {
			Settings.Saved held = MosaicaryClient.instance().settings().slots.get(chosen);
			if (held != null && !held.adult) wearing.wear(held.card());
		}
		onClose();
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (key.matches(event)) {
			apply();
			return true;
		}
		return super.keyReleased(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			chosen = slotToward(event.x(), event.y());
			apply();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
//#endif

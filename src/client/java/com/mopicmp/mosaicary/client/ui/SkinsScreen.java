package com.mopicmp.mosaicary.client.ui;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

import com.mojang.blaze3d.Blaze3D;
import com.mojang.blaze3d.platform.InputConstants;
import com.mopicmp.mosaicary.client.MosaicaryClient;
import com.mopicmp.mosaicary.client.site.Settings;
import com.mopicmp.mosaicary.client.site.SiteClient;
import com.mopicmp.mosaicary.client.skins.FreshMoves;
import com.mopicmp.mosaicary.client.skins.LiveEyes;
import com.mopicmp.mosaicary.client.skins.MojangSkins;
import com.mopicmp.mosaicary.client.skins.SkinCard;
import com.mopicmp.mosaicary.client.skins.SkinQuery;
import com.mopicmp.mosaicary.client.skins.Skins;
import com.mopicmp.mosaicary.client.skins.WardrobeSync;
import com.mopicmp.mosaicary.wardrobe.Wardrobe;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
//#if MC>=12111
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//#endif
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.PlayerModelType;

/**
 * The skin wardrobe: the catalogue of mosaicary.com's skins, and "My skins".
 *
 * Laid out like the model catalogue: a row of controls on top, a body, and a bottom row
 * that is always there with the status line and the links. One screen with a grid and a
 * detail view, so going back from a skin keeps the search, the filters and the scroll
 * position as they were.
 *
 * <h2>The catalogue</h2>
 *
 * The same search and filters as the launcher catalogue, each a dropdown with an icon:
 * arms, category, live eyes, order, and tags (a list with a search line and ticks).
 * Pages of 24 load by themselves as the list nears its end; a page that failed says so in
 * a line at the bottom, which tries again when clicked. There is no "load more" button.
 *
 * <h2>The detail view</h2>
 *
 * The skin on the game's own player model, turned by dragging, moved by dragging with the
 * right button and zoomed with the wheel, turning by itself and walking when those are
 * switched on. Wear puts it on the account.
 *
 * <h2>Live eyes</h2>
 *
 * A skin whose eyes come alive with the Fresh Moves resource pack moves them in the preview
 * as the pack moves them in the world (client/skins/LiveEyes), when the pack and the two mods
 * it needs are there and switched on. When they are not, a yellow card in the panel with the
 * skin's description, above its buttons, says what is missing: the one thing by name with a link to it, several with a link
 * each, a pack that is there but off with a button that switches it on. The same card is a
 * strip over the grid while the catalogue shows only skins with live eyes.
 */
public final class SkinsScreen extends Screen {

	public static final String SOURCE_URL = "https://github.com/MopicMP/mosaicary-mod";

	private static final int MARGIN = 6;
	private static final int GAP = 4;
	private static final int ROW = 22;
	private static final int CARD_TARGET = 96;

	public enum Tab { CATALOGUE, WARDROBE }

	// What the screen shows, kept between openings.
	private static Tab tab = Tab.CATALOGUE;
	private static SkinQuery query = SkinQuery.NONE;
	private static String wardrobeWords = "";
	private static final double[] scrolls = new double[2];
	private static boolean spinning;
	private static boolean walking;

	private final Screen parent;
	private final Skins skins;

	private SkinCard openCard;
	private Wardrobe.Entry openEntry;
	private float rotationX = -5;
	private float rotationY = 30;
	private float zoom = 1;
	private float panX;
	private float panY;
	private boolean turning;
	private boolean panning;
	private long lastFrame = System.nanoTime();
	private final long opened = System.nanoTime();

	private EditBox search;
	private final List<Dropdown<?>> dropdowns = new ArrayList<>();
	private TagPicker tagPicker;
	private IconButton wearButton;
	private IconButton keepButton;
	private IconButton saveButton;
	private Component notice;
	private long noticeUntil;
	private int noticeTint = Theme.ACCENT;

	private PlayerPose.Figure wideModel;
	private PlayerPose.Figure slimModel;

	public SkinsScreen(Screen parent, Skins skins) {
		super(Component.translatable("mosaicary.skins.title"));
		this.parent = parent;
		this.skins = skins;
		// A pack put in the folder while the game runs is seen from this opening on.
		FreshMoves.rescan();
	}

	// ------------------------------------------------------------------ live eyes and what they need

	/** The moving copy of the open skin's texture, and which texture it was made from. */
	private LiveEyes liveEyes;
	private Object liveEyesOf;
	/** Whether the open skin carries eyes for Fresh Moves, once its texture is read; null until then. */
	private Boolean openHasEyes;

	/** What live eyes need, as it stood when the widgets were made; a change remakes them. */
	private FreshMoves.Status freshShown;

	private void forgetLiveEyes() {
		if (liveEyes != null) liveEyes.close();
		liveEyes = null;
		liveEyesOf = null;
		openHasEyes = null;
	}

	/** Whether the yellow card is due here: a skin with live eyes open, or the catalogue showing only those. */
	private boolean eyesShown() {
		if (detail()) return openCard != null ? openCard.eyes() : Boolean.TRUE.equals(openHasEyes);
		return tab == Tab.CATALOGUE && query.eyes();
	}

	private FreshMoves.Status freshNeeded() {
		if (!eyesShown()) return null;
		FreshMoves.Status status = FreshMoves.status();
		return status.ready() ? null : status;
	}

	/** One action on the yellow card. */
	private record FreshAction(Icon icon, Component label, Component tooltip, Runnable run) { }

	private List<Component> freshLines(FreshMoves.Status status) {
		List<Component> lines = new ArrayList<>();
		if (status.missing().isEmpty()) {
			lines.add(Component.translatable("mosaicary.eyes.off"));
			return lines;
		}
		if (status.missing().size() == 1) {
			FreshMoves.Part part = status.missing().get(0);
			lines.add(Component.translatable(part == FreshMoves.Part.PACK ? "mosaicary.eyes.missing.pack" : "mosaicary.eyes.missing.mod",
				part.title));
		} else {
			List<String> names = new ArrayList<>();
			for (FreshMoves.Part part : status.missing()) names.add(part.title);
			lines.add(Component.translatable("mosaicary.eyes.missing.several", String.join(", ", names)));
		}
		boolean mod = status.missing().stream().anyMatch(part -> part != FreshMoves.Part.PACK);
		if (mod) lines.add(Component.translatable("mosaicary.eyes.restart"));
		if (status.packOff() != null) lines.add(Component.translatable("mosaicary.eyes.off"));
		return lines;
	}

	private List<FreshAction> freshActions(FreshMoves.Status status) {
		List<FreshAction> out = new ArrayList<>();
		for (FreshMoves.Part part : status.missing()) {
			out.add(new FreshAction(Icon.OPEN_SITE, Component.literal(part.title),
				Component.translatable("mosaicary.eyes.get.tooltip", part.title), () -> openUri(part.page)));
		}
		// A pack that is there but off is offered to be switched on, whatever else is missing.
		if (status.packOff() != null) {
			out.add(new FreshAction(Icon.EYES, Component.translatable("mosaicary.eyes.switch_on"),
				Component.translatable("mosaicary.eyes.switch_on.tooltip"), () -> FreshMoves.switchOn(status.packOff())));
		}
		return out;
	}

	/**
	 * A card's place and the lines it shows. {@code whole} is all of its text, for the tooltip of a
	 * card cut short ({@code cut}) because the panel has no room for all of it.
	 */
	private record Notice(int x0, int y0, int x1, int y1, List<FormattedCharSequence> lines, List<Component> whole, boolean cut) { }

	/** The cards in the description panel, in the order {@link #cardLines} counts them. */
	private static final int FRESH_CARD = 0;
	private static final int SEE_THROUGH_CARD = 1;
	private static final int ACCOUNT_CARD = 2;

	/** The panel's top for the cards: under the author and licence, which stay in sight. */
	private int cardsCeiling(Detail layout) {
		int rows = openCard != null ? 2 : 1;
		return layout.infoY0 + 6 + rows * (Icons.SIZE + 3) + 5;
	}

	/** The width the words of a card in the description panel have, beside its icon. */
	private int cardTextWidth(Detail layout) {
		return layout.infoX1 - layout.infoX0 - 12 - 12 - Icons.SIZE - 4;
	}

	/**
	 * How many lines of words each card in the description panel shows: all of them when the panel
	 * has the room, and otherwise the card with the most lines gives one up at a time, down to one
	 * line each, until the cards fit between the author and licence and the buttons.
	 */
	private int[] cardLines() {
		Detail layout = detailLayout();
		int width = cardTextWidth(layout);
		int[] lines = new int[3];
		int[] fixed = new int[3];
		boolean[] shown = new boolean[3];
		if (freshShown != null) {
			shown[FRESH_CARD] = true;
			for (Component line : freshLines(freshShown)) lines[FRESH_CARD] += font.split(line, width).size();
			fixed[FRESH_CARD] = 6 + 6 + freshActions(freshShown).size() * (ROW + 3) + 3;
		}
		Skins.Look look = skins.look(entry());
		if (look != null && look.seeThrough()) {
			shown[SEE_THROUGH_CARD] = true;
			lines[SEE_THROUGH_CARD] = font.split(Component.translatable("mosaicary.skins.see_through"), width).size();
			fixed[SEE_THROUGH_CARD] = 6 + 6;
		}
		Component said = accountSaid();
		if (said != null) {
			shown[ACCOUNT_CARD] = true;
			lines[ACCOUNT_CARD] = font.split(said, width).size();
			fixed[ACCOUNT_CARD] = 6 + 4 + ROW + 4;
		}
		int room = layout.infoY1 - 6 - ROW - 6 - cardsCeiling(layout);
		while (stackHeight(lines, fixed, shown) > room) {
			int longest = -1;
			for (int i = 0; i < 3; i++) {
				if (shown[i] && lines[i] > 1 && (longest < 0 || lines[i] > lines[longest])) longest = i;
			}
			if (longest < 0) break;
			lines[longest]--;
		}
		return lines;
	}

	private static int stackHeight(int[] lines, int[] fixed, boolean[] shown) {
		int total = 0;
		int cards = 0;
		for (int i = 0; i < 3; i++) {
			if (!shown[i]) continue;
			total += fixed[i] + Math.max(Icons.SIZE, lines[i] * 10);
			cards++;
		}
		return total + Math.max(0, cards - 1) * 3;
	}

	/** The lines of a card's words, at most {@code keep} of them; when cut, the last one ends in "...". */
	private List<FormattedCharSequence> fitted(List<Component> words, int width, int keep) {
		List<FormattedCharSequence> all = new ArrayList<>();
		for (Component line : words) all.addAll(font.split(line, width));
		if (all.size() <= keep) return all;
		List<FormattedCharSequence> narrower = new ArrayList<>();
		for (Component line : words) narrower.addAll(font.split(line, width - font.width("...")));
		List<FormattedCharSequence> cut = new ArrayList<>(narrower.subList(0, Math.max(1, keep)));
		int last = cut.size() - 1;
		cut.set(last, FormattedCharSequence.composite(cut.get(last), FormattedCharSequence.forward("...", Style.EMPTY)));
		return cut;
	}

	/** The whole of a card's words over it while the pointer is on its words, when they were cut. */
	private void cutTooltip(GuiGraphicsExtractor graphics, Notice card, int mouseX, int mouseY) {
		int textBottom = card.y0() + 7 + Math.max(Icons.SIZE, card.lines().size() * 10);
		if (!card.cut() || mouseX < card.x0() || mouseX >= card.x1() || mouseY < card.y0() || mouseY >= textBottom) return;
		List<FormattedCharSequence> all = new ArrayList<>();
		for (Component line : card.whole()) all.addAll(font.split(line, 200));
		graphics.setTooltipForNextFrame(font, all, mouseX, mouseY);
	}

	private static final int NOTICE_STRIP = ROW + 8;

	private Notice notice(FreshMoves.Status status) {
		if (status == null) return null;
		List<Component> said = freshLines(status);
		if (detail()) {
			// In the card with the skin's description, just above its buttons: never over the figure.
			Detail layout = detailLayout();
			int x0 = layout.infoX0 + 6;
			int x1 = layout.infoX1 - 6;
			int keep = cardLines()[FRESH_CARD];
			List<FormattedCharSequence> lines = fitted(said, cardTextWidth(layout), keep);
			boolean cut = fitted(said, cardTextWidth(layout), Integer.MAX_VALUE).size() > keep;
			int buttons = freshActions(status).size();
			int y1 = layout.infoY1 - 6 - ROW - 6;
			int y0 = y1 - (6 + Math.max(Icons.SIZE, lines.size() * 10) + 6 + buttons * (ROW + 3) + 3);
			return new Notice(x0, y0, x1, y1, lines, said, cut);
		}
		int y0 = secondRowY() + ROW + GAP;
		List<FormattedCharSequence> lines = new ArrayList<>();
		for (Component line : said) lines.add(line.getVisualOrderText());
		return new Notice(MARGIN, y0, width - MARGIN, y0 + NOTICE_STRIP, lines, said, false);
	}

	/** The buttons of the yellow card, made with the other widgets. */
	private void initFresh() {
		freshShown = freshNeeded();
		Notice place = notice(freshShown);
		if (place == null) return;
		List<FreshAction> actions = freshActions(freshShown);
		if (detail()) {
			int y = place.y1() - 3 - actions.size() * (ROW + 3);
			for (FreshAction action : actions) {
				addRenderableWidget(new IconButton(place.x0() + 6, y, place.x1() - place.x0() - 12, ROW, action.icon(), action.label(),
					action.tooltip(), action.run()).accent(Theme.WARN));
				y += ROW + 3;
			}
		} else {
			int x = place.x1() - 4;
			for (int i = actions.size() - 1; i >= 0; i--) {
				FreshAction action = actions.get(i);
				int w = Math.min(IconButton.widthFor(action.label()), 140);
				x -= w;
				addRenderableWidget(new IconButton(x, place.y0() + 4, w, ROW, action.icon(), action.label(), action.tooltip(), action.run())
					.accent(Theme.WARN));
				x -= GAP;
			}
			freshStripTextRight = x;
		}
	}

	private int freshStripTextRight;

	/** Whether the pack and its mods are there and on: eyes move only then, as in the game. */
	private boolean freshReady;

	private void drawFresh(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Notice place = notice(freshShown);
		if (place == null) return;
		Theme.card(graphics, place.x0(), place.y0(), place.x1(), place.y1(), 0xF0302814, Theme.WARN);
		Icons.draw(graphics, Icon.EYES, place.x0() + 5, place.y0() + (detail() ? 6 : (place.y1() - place.y0() - Icons.SIZE) / 2), Theme.WARN);
		int textX = place.x0() + 5 + Icons.SIZE + 4;
		if (detail()) {
			int y = place.y0() + 7;
			for (FormattedCharSequence line : place.lines()) {
				graphics.text(font, line, textX, y, Theme.WARN, false);
				y += 10;
			}
			cutTooltip(graphics, place, mouseX, mouseY);
			return;
		}
		// The strip: the first line as fits, the whole of it in the tooltip.
		int room = freshStripTextRight - textX - 4;
		String text = place.lines().isEmpty() ? "" : freshLines(freshShown).get(0).getString();
		int textY = place.y0() + (NOTICE_STRIP - 8) / 2;
		graphics.text(font, ellipsis(text, room), textX, textY, Theme.WARN, false);
		if (mouseX >= place.x0() && mouseX < freshStripTextRight && mouseY >= place.y0() && mouseY < place.y1()) {
			List<FormattedCharSequence> all = new ArrayList<>();
			for (Component line : freshLines(freshShown)) all.addAll(font.split(line, 200));
			graphics.setTooltipForNextFrame(font, all, mouseX, mouseY);
		}
	}

	/**
	 * The warning about a skin players without the mod see otherwise, in the panel with the skin's
	 * description, above the card about live eyes when there is one; null for any other skin.
	 */
	private Notice seeThroughCard() {
		if (!detail()) return null;
		Skins.Look look = skins.look(entry());
		if (look == null || !look.seeThrough()) return null;
		Detail layout = detailLayout();
		Notice below = notice(freshShown);
		int x0 = layout.infoX0 + 6;
		int x1 = layout.infoX1 - 6;
		List<Component> words = List.of(Component.translatable("mosaicary.skins.see_through"));
		int keep = cardLines()[SEE_THROUGH_CARD];
		List<FormattedCharSequence> lines = fitted(words, cardTextWidth(layout), keep);
		boolean cut = fitted(words, cardTextWidth(layout), Integer.MAX_VALUE).size() > keep;
		int y1 = below != null ? below.y0() - 3 : layout.infoY1 - 6 - ROW - 6;
		int y0 = y1 - (6 + Math.max(Icons.SIZE, lines.size() * 10) + 6);
		return new Notice(x0, y0, x1, y1, lines, words, cut);
	}

	private void drawSeeThrough(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Notice place = seeThroughCard();
		if (place == null) return;
		Theme.card(graphics, place.x0(), place.y0(), place.x1(), place.y1(), 0xF0302814, Theme.WARN);
		Icons.draw(graphics, Icon.ERROR, place.x0() + 5, place.y0() + 6, Theme.WARN);
		int y = place.y0() + 7;
		int wordsX = place.x0() + 5 + Icons.SIZE + 4;
		for (FormattedCharSequence line : place.lines()) {
			graphics.text(font, line, wordsX, y, Theme.WARN, false);
			y += 10;
		}
		cutTooltip(graphics, place, mouseX, mouseY);
	}

	// ------------------------------------------------------------------ the account on the site

	private WardrobeSync sync() {
		return MosaicaryClient.instance().wardrobeSync();
	}

	/**
	 * What to say about signing in to an account on the site, or null. Always on My skins, whose
	 * whole point it is; and everywhere for a game Mojang will not take a skin from, for which
	 * that account is the only way to wear one.
	 */
	private Component accountSaid() {
		WardrobeSync sync = sync();
		if (sync == null || sync.on(WardrobeSync.Where.ACCOUNT)) return null;
		boolean noMojang = !Skins.canUpload();
		if (detail() ? !noMojang : tab != Tab.WARDROBE && !noMojang) return null;
		WardrobeSync.Link link = sync.link();
		if (link != null && "waiting".equals(link.state())) return Component.translatable("mosaicary.account.waiting", link.code());
		if (link != null && !"starting".equals(link.state()) && !"taken".equals(link.state())) {
			return Component.translatable("mosaicary.settings.account." + link.state());
		}
		return Component.translatable(noMojang ? "mosaicary.account.offer.no_mojang" : "mosaicary.account.offer");
	}

	private Component accountButtonLabel() {
		WardrobeSync.Link link = sync().link();
		if (link != null && "waiting".equals(link.state())) return Component.translatable("mosaicary.account.again");
		return Component.translatable(Skins.canUpload() ? "mosaicary.account.in" : "mosaicary.account.in.no_mojang");
	}

	private void accountPressed() {
		WardrobeSync.Link link = sync().link();
		if (link != null && "waiting".equals(link.state())) openUri(link.url());
		else sync().startLink();
	}

	/** The room the words have beside the button in the strip over the grid. */
	private int accountStripRoom() {
		return width - 2 * MARGIN - Icons.SIZE - 14 - IconButton.widthFor(accountButtonLabel()) - 8;
	}

	/** How tall the strip over the grid is for these words, at most three lines of them; 0 without any. */
	private int accountStripHeight() {
		Component said = accountSaid();
		if (said == null || detail()) return 0;
		return Math.max(ROW + 8, Math.min(3, font.split(said, accountStripRoom()).size()) * 10 + 8);
	}

	private int accountStripTop() {
		return secondRowY() + ROW + GAP + (freshShown != null ? NOTICE_STRIP + GAP : 0);
	}

	/** The card in the panel with the skin's description, above any others there; null without one. */
	private Notice accountCard() {
		Component said = detail() ? accountSaid() : null;
		if (said == null) return null;
		Detail layout = detailLayout();
		Notice below = seeThroughCard();
		if (below == null) below = notice(freshShown);
		int x0 = layout.infoX0 + 6;
		int x1 = layout.infoX1 - 6;
		List<Component> words = List.of(said);
		int keep = cardLines()[ACCOUNT_CARD];
		List<FormattedCharSequence> lines = fitted(words, cardTextWidth(layout), keep);
		boolean cut = fitted(words, cardTextWidth(layout), Integer.MAX_VALUE).size() > keep;
		int y1 = below != null ? below.y0() - 3 : layout.infoY1 - 6 - ROW - 6;
		int y0 = y1 - (6 + Math.max(Icons.SIZE, lines.size() * 10) + 4 + ROW + 4);
		return new Notice(x0, y0, x1, y1, lines, words, cut);
	}

	private void initAccount() {
		shownAccount = accountKey();
		Component said = accountSaid();
		if (said == null) return;
		Component label = accountButtonLabel();
		Component tooltip = Component.translatable("mosaicary.account.in.tooltip");
		if (detail()) {
			Notice card = accountCard();
			int w = card.x1() - card.x0() - 12;
			// A button that cannot hold the words shows only its icon, and this one has to say what it does.
			if (IconButton.widthFor(label) > w) label = Component.translatable("mosaicary.account.in");
			addRenderableWidget(new IconButton(card.x0() + 6, card.y1() - 4 - ROW, w, ROW, Icon.LINK, label, tooltip,
				this::accountPressed).primary(true));
		} else {
			int w = IconButton.widthFor(label);
			int h = accountStripHeight();
			addRenderableWidget(new IconButton(width - MARGIN - 4 - w, accountStripTop() + (h - ROW) / 2, w, ROW, Icon.LINK, label, tooltip,
				this::accountPressed).primary(true));
		}
	}

	private String shownAccount;

	/** What the widgets above are made from: when it changes, they are made again. */
	private String accountKey() {
		Component said = accountSaid();
		Skins.Look look = detail() ? skins.look(entry()) : null;
		return (said == null ? "" : said.getString()) + "|" + (look != null && look.seeThrough());
	}

	private void drawAccount(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Component said = accountSaid();
		if (said == null) return;
		int textColour = Theme.TEXT;
		if (detail()) {
			Notice card = accountCard();
			Theme.card(graphics, card.x0(), card.y0(), card.x1(), card.y1(), Theme.CARD, Theme.ACCENT);
			Icons.draw(graphics, Icon.SITE, card.x0() + 5, card.y0() + 6, Theme.ACCENT);
			int y = card.y0() + 7;
			int wordsX = card.x0() + 5 + Icons.SIZE + 4;
			for (FormattedCharSequence line : card.lines()) {
				graphics.text(font, line, wordsX, y, textColour, false);
				y += 10;
			}
			cutTooltip(graphics, card, mouseX, mouseY);
			return;
		}
		int y0 = accountStripTop();
		int h = accountStripHeight();
		Theme.card(graphics, MARGIN, y0, width - MARGIN, y0 + h, Theme.CARD, Theme.ACCENT);
		Icons.draw(graphics, Icon.SITE, MARGIN + 5, y0 + (h - Icons.SIZE) / 2, Theme.ACCENT);
		List<FormattedCharSequence> lines = font.split(said, accountStripRoom());
		int shown = Math.min(3, lines.size());
		boolean cut = lines.size() > shown;
		// Words that did not fit are marked as cut, and all in the tooltip.
		if (cut) lines = font.split(said, accountStripRoom() - font.width("..."));
		int y = y0 + (h - shown * 10) / 2 + 1;
		for (int i = 0; i < shown; i++) {
			FormattedCharSequence line = lines.get(i);
			if (cut && i == shown - 1) line = FormattedCharSequence.composite(line, FormattedCharSequence.forward("...", Style.EMPTY));
			graphics.text(font, line, MARGIN + 5 + Icons.SIZE + 4, y, textColour, false);
			y += 10;
		}
		if (cut && mouseX >= MARGIN && mouseX < MARGIN + accountStripRoom() && mouseY >= y0 && mouseY < y0 + h) {
			Theme.tooltip(graphics, font, said, mouseX, mouseY);
		}
	}

	private double scroll() {
		return scrolls[tab.ordinal()];
	}

	private void scroll(double value) {
		scrolls[tab.ordinal()] = value;
	}

	// ------------------------------------------------------------------ layout

	private int topRowY() {
		return MARGIN;
	}

	private int secondRowY() {
		return topRowY() + ROW + GAP;
	}

	private int bottomRowY() {
		return height - MARGIN - ROW;
	}

	private boolean detail() {
		return openCard != null || openEntry != null;
	}

	private int bodyTop() {
		if (detail()) return secondRowY();
		// The yellow strip, while the catalogue shows only skins with live eyes the game cannot move.
		int strip = accountStripHeight();
		return secondRowY() + ROW + GAP + (freshShown != null ? NOTICE_STRIP + GAP : 0) + (strip > 0 ? strip + GAP : 0);
	}

	private int bodyBottom() {
		return bottomRowY() - GAP;
	}

	private void openSettings() {
		minecraft.gui.setScreen(new SettingsScreen(this));
	}

	@Override
	protected void init() {
		dropdowns.clear();
		tagPicker = null;
		search = null;
		wearButton = null;
		keepButton = null;
		saveButton = null;
		if (wideModel == null) {
			wideModel = PlayerPose.model(false);
			slimModel = PlayerPose.model(true);
		}
		if (tab == Tab.CATALOGUE) skins.shelf(query);
		skins.tags();

		int right = width - MARGIN;
		addRenderableWidget(IconButton.square(right - ROW, topRowY(), ROW, Icon.CLOSE, Component.translatable("mosaicary.close"),
			this::onClose));
		right -= ROW + GAP;
		addRenderableWidget(IconButton.square(right - ROW, topRowY(), ROW, Icon.SETTINGS,
			Component.translatable("mosaicary.settings.title"), this::openSettings));
		right -= ROW + GAP;

		if (detail()) initDetail(right);
		else initGrid(right);
		initFresh();
		initAccount();
		initBottom();
	}

	private void initGrid(int right) {
		if (tab == Tab.CATALOGUE) {
			addRenderableWidget(IconButton.square(right - ROW, topRowY(), ROW, Icon.REFRESH, Component.translatable("mosaicary.refresh"),
				() -> {
					skins.refresh(query);
					scroll(0);
				}));
			right -= ROW + GAP;
		}

		// The two tabs, labelled when there is room beside a search field of a useful width.
		Component catalogue = Component.translatable("mosaicary.skins.tab.catalogue");
		Component mine = Component.translatable("mosaicary.skins.tab.mine");
		int labelled = IconButton.widthFor(catalogue) + IconButton.widthFor(mine) + GAP;
		boolean roomy = right - MARGIN - labelled - GAP >= 170;
		int catalogueWidth = roomy ? IconButton.widthFor(catalogue) : ROW;
		int mineWidth = roomy ? IconButton.widthFor(mine) : ROW;
		int x = MARGIN;
		addRenderableWidget(new IconButton(x, topRowY(), catalogueWidth, ROW, Icon.CATALOGUE, roomy ? catalogue : null,
			Component.translatable("mosaicary.skins.tab.catalogue.tooltip"), () -> switchTab(Tab.CATALOGUE))
			.primary(tab == Tab.CATALOGUE));
		x += catalogueWidth + GAP;
		addRenderableWidget(new IconButton(x, topRowY(), mineWidth, ROW, Icon.WARDROBE, roomy ? mine : null,
			Component.translatable("mosaicary.skins.tab.mine.tooltip"), () -> switchTab(Tab.WARDROBE))
			.primary(tab == Tab.WARDROBE));
		x += mineWidth + GAP;

		searchLeft = x;
		int fieldLeft = x + Icons.SIZE + 8;
		search = new EditBox(font, fieldLeft, topRowY() + (ROW - 9) / 2 + 1, Math.max(20, right - fieldLeft - 4), 10,
			Component.translatable("mosaicary.skins.search"));
		search.setBordered(false);
		search.setMaxLength(100);
		Component hint = Component.translatable(tab == Tab.CATALOGUE ? "mosaicary.skins.search" : "mosaicary.skins.search.mine");
		if (font.width(hint) > search.getWidth() - 4) hint = Component.translatable("mosaicary.search.short");
		search.setHint(hint.copy().withColor(Theme.TEXT_OFF));
		search.setTextColor(Theme.TEXT);
		search.setValue(tab == Tab.CATALOGUE ? query.words() : wardrobeWords);
		search.setResponder(value -> {
			if (tab == Tab.CATALOGUE) setQuery(query.withWords(value));
			else wardrobeWords = value;
			scroll(0);
		});
		searchRight = right;
		addRenderableWidget(search);

		if (tab == Tab.CATALOGUE) initFilters();
		else initWardrobeRow();
	}

	private int searchLeft;
	private int searchRight;

	/** The filters, each a dropdown, sharing the second row evenly. */
	private void initFilters() {
		List<Dropdown.Option<String>> arms = List.of(
			new Dropdown.Option<>("any", Component.translatable("mosaicary.skins.arms.any")),
			new Dropdown.Option<>("classic", Component.translatable("mosaicary.skins.arms.classic")),
			new Dropdown.Option<>("slim", Component.translatable("mosaicary.skins.arms.slim")));
		List<Dropdown.Option<String>> categories = new ArrayList<>();
		categories.add(new Dropdown.Option<>("any", Component.translatable("mosaicary.skins.category.any")));
		for (String one : SkinQuery.CATEGORIES) categories.add(new Dropdown.Option<>(one, Component.translatable("mosaicary.skins.category." + one)));
		List<Dropdown.Option<Boolean>> eyes = List.of(
			new Dropdown.Option<>(false, Component.translatable("mosaicary.skins.eyes.any")),
			new Dropdown.Option<>(true, Component.translatable("mosaicary.skins.eyes.live")));
		List<Dropdown.Option<String>> sorts = new ArrayList<>();
		for (String one : SkinQuery.SORTS) sorts.add(new Dropdown.Option<>(one, Component.translatable("mosaicary.skins.sort." + one)));

		int y = secondRowY();
		int available = width - 2 * MARGIN - 4 * GAP;
		int each = available / 5;
		int x = MARGIN;
		Dropdown<String> armsBox = new Dropdown<>(x, y, each, ROW, Icon.ARMS, Component.translatable("mosaicary.skins.filter.arms"),
			arms, query.arms(), value -> setQuery(query.withArms(value)));
		x += each + GAP;
		Dropdown<String> categoryBox = new Dropdown<>(x, y, each, ROW, Icon.CATEGORY,
			Component.translatable("mosaicary.skins.filter.category"), categories, query.category(),
			value -> setQuery(query.withCategory(value)));
		x += each + GAP;
		Dropdown<Boolean> eyesBox = new Dropdown<>(x, y, each, ROW, Icon.EYES, Component.translatable("mosaicary.skins.filter.eyes"),
			eyes, query.eyes(), value -> setQuery(query.withEyes(value)));
		x += each + GAP;
		Dropdown<String> sortBox = new Dropdown<>(x, y, each, ROW, Icon.SORT, Component.translatable("mosaicary.skins.filter.sort"),
			sorts, query.sort(), value -> setQuery(query.withSort(value)));
		x += each + GAP;
		tagPicker = new TagPicker(x, y, width - MARGIN - x, ROW, skins::tags, query.tags(), query.tagsAll(),
			(tags, all) -> setQuery(query.withTags(tags, all)));
		for (Dropdown<?> box : List.of(armsBox, categoryBox, eyesBox, sortBox)) {
			dropdowns.add(box);
			addRenderableWidget(box);
		}
		addRenderableWidget(tagPicker);
	}

	/** The wardrobe's second row: adding a file of one's own, and where the wardrobe is kept. */
	private void initWardrobeRow() {
		Component add = Component.translatable("mosaicary.skins.mine.add");
		int addWidth = IconButton.widthFor(add);
		addRenderableWidget(new IconButton(MARGIN, secondRowY(), addWidth, ROW, Icon.UPLOAD, add,
			Component.translatable("mosaicary.skins.mine.add.tooltip"), this::chooseFile));
		syncLeft = MARGIN + addWidth + GAP;
	}

	private int syncLeft;

	private void setQuery(SkinQuery next) {
		if (next.equals(query)) return;
		query = next;
		scroll(0);
		skins.shelf(query);
	}

	private void switchTab(Tab next) {
		if (tab == next) return;
		tab = next;
		for (Dropdown<?> box : dropdowns) box.close();
		rebuildWidgets();
	}

	private void initDetail(int right) {
		addRenderableWidget(new IconButton(MARGIN, topRowY(), IconButton.widthFor(Component.translatable("mosaicary.back.short")),
			ROW, Icon.BACK, Component.translatable("mosaicary.back.short"), Component.translatable("mosaicary.skins.back"),
			this::closeDetail));
		Detail layout = detailLayout();

		// On the preview: turning by itself, walking, and back to the opening view.
		int px = layout.previewX0 + 3;
		int py = layout.previewY0 + 3;
		addRenderableWidget(IconButton.square(px, py, ROW, Icon.SPIN, Component.translatable("mosaicary.skins.preview.spin"),
			() -> {
				spinning = !spinning;
				rebuildWidgets();
			}).primary(spinning));
		addRenderableWidget(IconButton.square(px + ROW + GAP, py, ROW, Icon.ROLE_WALK,
			Component.translatable("mosaicary.skins.preview.walk"), () -> {
				walking = !walking;
				rebuildWidgets();
			}).primary(walking));
		addRenderableWidget(IconButton.square(layout.previewX1 - ROW - 3, py, ROW, Icon.RESET_VIEW,
			Component.translatable("mosaicary.skins.preview.reset"), this::resetView));

		// The actions along the bottom of the info card; Wear first and filled.
		int y = layout.infoY1 - 6 - ROW;
		int x = layout.infoX0 + 6;
		int available = layout.infoX1 - 6 - x;
		Component wear = Component.translatable("mosaicary.skins.action.wear");
		int wearWidth = Math.max(IconButton.widthFor(wear), IconButton.widthFor(Component.translatable("mosaicary.skins.action.wearing")));
		boolean labelled = wearWidth + 3 * (ROW + GAP) <= available;
		if (!labelled) wearWidth = ROW;
		wearButton = new IconButton(x, y, wearWidth, ROW, Icon.WEAR, labelled ? wear : null,
			Component.translatable("mosaicary.skins.action.wear.tooltip"), this::wear).primary(true);
		wearLabelled = labelled;
		addRenderableWidget(wearButton);
		x += wearWidth + GAP;
		keepButton = IconButton.square(x, y, ROW, Icon.KEEP, Component.translatable("mosaicary.skins.action.keep.tooltip"), this::keep);
		addRenderableWidget(keepButton);
		x += ROW + GAP;
		saveButton = IconButton.square(x, y, ROW, Icon.DOWNLOAD, Component.translatable("mosaicary.skins.action.save.tooltip"),
			this::savePng);
		addRenderableWidget(saveButton);
		x += ROW + GAP;
		String page = pageOf();
		if (page != null) {
			addRenderableWidget(IconButton.square(x, y, ROW, Icon.OPEN_SITE, Component.translatable("mosaicary.skins.action.site.tooltip"),
				() -> openUri(page)));
		}
		shownWear = null;
		shownKept = null;
		refreshButtons();
	}

	private boolean wearLabelled;

	private void initBottom() {
		int right = width - MARGIN;
		Component sourceLabel = Component.translatable("mosaicary.skins.source");
		Component siteLabel = Component.translatable("mosaicary.site.button");
		int sourceWidth = IconButton.widthFor(sourceLabel);
		int siteWidth = IconButton.widthFor(siteLabel);
		boolean labels = width - 2 * MARGIN - sourceWidth - siteWidth - 2 * GAP >= 140;
		if (!labels) {
			sourceWidth = ROW;
			siteWidth = ROW;
		}
		addRenderableWidget(new IconButton(right - sourceWidth, bottomRowY(), sourceWidth, ROW, Icon.SOURCE,
			labels ? sourceLabel : null, Component.translatable("mosaicary.skins.source.tooltip"), () -> openUri(SOURCE_URL)));
		right -= sourceWidth + GAP;
		addRenderableWidget(new IconButton(right - siteWidth, bottomRowY(), siteWidth, ROW, Icon.SITE, labels ? siteLabel : null,
			Component.translatable("mosaicary.skins.site.tooltip"), () -> openUri(skins.site().base() + "/skins")));
		statusRight = right - siteWidth - GAP;
	}

	private int statusRight;

	// ------------------------------------------------------------------ what is open

	/** The wardrobe entry of what the detail view shows: the kept one when it is kept, else a fresh one. */
	private Wardrobe.Entry entry() {
		if (openEntry != null) return openEntry;
		Wardrobe.Entry kept = skins.wardrobe().get("site:" + openCard.id());
		return kept != null ? kept : openCard.entry(System.currentTimeMillis());
	}

	private String pageOf() {
		if (openCard != null) return openCard.page();
		if (openEntry != null && openEntry.id != null) return skins.site().base() + "/skins/" + openEntry.id;
		return null;
	}

	public void openCard(SkinCard card) {
		forgetLiveEyes();
		openCard = card;
		openEntry = null;
		resetView();
		skins.texture(card);
		rebuildWidgets();
	}

	public void openEntry(Wardrobe.Entry entry) {
		forgetLiveEyes();
		openEntry = entry;
		openCard = null;
		resetView();
		skins.texture(entry);
		rebuildWidgets();
	}

	public void closeDetail() {
		forgetLiveEyes();
		openCard = null;
		openEntry = null;
		rebuildWidgets();
	}

	private void resetView() {
		rotationX = -5;
		rotationY = 30;
		zoom = 1;
		panX = 0;
		panY = 0;
	}

	// ------------------------------------------------------------------ actions

	private void wear() {
		Wardrobe.Entry entry = entry();
		// The first Wear asks whether to share it through the site; the skin goes on either way.
		if (Settings.NOT_ASKED.equals(MosaicaryClient.instance().settings().sharing)) {
			minecraft.gui.setScreen(new ConsentScreen(this, () -> skins.wear(entry)));
		} else {
			skins.wear(entry);
		}
	}

	private void keep() {
		Wardrobe wardrobe = skins.wardrobe();
		Wardrobe.Entry entry = entry();
		if (wardrobe.has(entry.key)) {
			wardrobe.remove(entry.key, System.currentTimeMillis());
			say(Component.translatable("mosaicary.skins.mine.removed", entry.name), Theme.TEXT_DIM);
			// A file of one's own taken out of the wardrobe has nowhere to be shown from.
			if (openEntry != null) closeDetail();
		} else {
			entry.added = System.currentTimeMillis();
			entry.changed = entry.added;
			wardrobe.add(entry);
			say(Component.translatable("mosaicary.skins.mine.kept", entry.name), Theme.ACCENT);
		}
		shownKept = null;
		refreshButtons();
	}

	private void savePng() {
		Wardrobe.Entry entry = entry();
		saveButton.active = false;
		skins.savePng(entry).whenComplete((path, problem) -> minecraft.execute(() -> {
			if (saveButton != null) saveButton.active = true;
			if (problem != null) {
				say(Component.translatable("mosaicary.skins.saved.failed"), Theme.WARN);
				return;
			}
			say(Component.translatable("mosaicary.skins.saved", path.getFileName().toString()), Theme.ACCENT);
			Blaze3D.openPath(path.getParent());
		}));
	}

	/** Choosing a file of one's own, in a window of the mod's; dropping it onto the window works too. */
	private void chooseFile() {
		minecraft.gui.setScreen(new SkinFileScreen(this, skins, file -> importFiles(List.of(file))));
	}


	@Override
	public void tick() {
		// Remade when what live eyes need changes: a pack switched on, the open skin read.
		if (!java.util.Objects.equals(freshNeeded(), freshShown) || !accountKey().equals(shownAccount)) rebuildWidgets();
		if (MosaicaryClient.instance().relay().nameTaken() && !nameTakenSaid) {
			nameTakenSaid = true;
			say(Component.translatable("mosaicary.account.name_taken"), Theme.WARN);
		}
		freshReady = FreshMoves.status().ready();
	}

	@Override
	public void onFilesDrop(List<Path> files) {
		importFiles(files);
	}

	private void importFiles(List<Path> files) {
		int taken = 0;
		for (Path file : files) {
			if (!file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png") || taken >= 20) continue;
			taken++;
			skins.importFile(file, entry -> {
				say(Component.translatable("mosaicary.skins.mine.added", entry.name), Theme.ACCENT);
				if (tab != Tab.WARDROBE) switchTab(Tab.WARDROBE);
			}, why -> say(Component.translatable("mosaicary.skins.mine.refused." + why, file.getFileName().toString()), Theme.WARN));
		}
		if (taken == 0 && !files.isEmpty()) say(Component.translatable("mosaicary.skins.mine.refused.png", files.get(0).getFileName().toString()), Theme.WARN);
	}

	private boolean nameTakenSaid;

	private void say(Component said, int tint) {
		notice = said;
		noticeTint = tint;
		noticeUntil = System.currentTimeMillis() + 8000;
	}

	private static void openUri(String address) {
		try {
			Blaze3D.openUri(URI.create(address));
		} catch (IllegalArgumentException bad) {
			// A malformed address; nothing to open.
		}
	}

	private Skins.Wear shownWear;
	private Boolean shownKept;

	/** The Wear and keep buttons follow what has happened, changing only when it changes. */
	private void refreshButtons() {
		if (wearButton == null || !detail()) return;
		Wardrobe.Entry entry = entry();
		Skins.Wear state = skins.wearState(entry.key);
		boolean can = skins.canWear();
		if (state != shownWear || shownWear == null) {
			shownWear = state;
			if (!can) {
				wearButton.active = false;
				wearButton.icon(() -> Icon.WEAR).tooltip(Component.translatable("mosaicary.skins.action.wear.offline"));
			} else if (state == null) {
				wearButton.active = !skins.uploading();
				wearButton.icon(() -> Icon.WEAR).tooltip(Component.translatable("mosaicary.skins.action.wear.tooltip"));
				if (wearLabelled) wearButton.label(Component.translatable("mosaicary.skins.action.wear"));
			} else if (state.state() == Skins.State.LOADING) {
				wearButton.active = false;
				wearButton.icon(() -> Icon.LOADING).tooltip(Component.translatable("mosaicary.skins.action.wearing.tooltip"));
				if (wearLabelled) wearButton.label(Component.translatable("mosaicary.skins.action.wearing"));
			} else if (state.state() == Skins.State.READY) {
				wearButton.active = true;
				wearButton.icon(() -> Icon.DOWNLOADED).tooltip(Component.translatable(Skins.canUpload()
					? "mosaicary.skins.action.worn.tooltip" : "mosaicary.skins.action.worn.tooltip.no_mojang"));
				if (wearLabelled) wearButton.label(Component.translatable("mosaicary.skins.action.worn"));
			} else {
				wearButton.active = true;
				String why = state.failure() == null ? "no_answer" : state.failure().name().toLowerCase(Locale.ROOT);
				wearButton.icon(() -> Icon.ERROR).tooltip(Component.translatable("mosaicary.skins.wear.failed." + why));
				if (wearLabelled) wearButton.label(Component.translatable("mosaicary.skins.action.wear"));
			}
		}
		boolean kept = skins.wardrobe().has(entry.key);
		if (shownKept == null || shownKept != kept) {
			shownKept = kept;
			keepButton.icon(() -> kept ? Icon.KEPT : Icon.KEEP).primary(kept);
			keepButton.tooltip(Component.translatable(kept ? "mosaicary.skins.action.unkeep.tooltip" : "mosaicary.skins.action.keep.tooltip"));
		}
	}

	// ------------------------------------------------------------------ the grid

	private record Grid(int x0, int y0, int x1, int y1, int columns, int cardWidth, int pictureHeight, int cardHeight) {
		int contentHeight(int count) {
			int rows = (count + columns - 1) / columns;
			return rows == 0 ? 0 : rows * cardHeight + (rows - 1) * GAP;
		}
	}

	private Grid grid() {
		int x0 = MARGIN;
		int x1 = width - MARGIN;
		int y0 = bodyTop();
		int y1 = bodyBottom();
		int available = x1 - x0 - 6;
		int columns = Math.max(1, (available + GAP) / (CARD_TARGET + GAP));
		int cardWidth = (available - (columns - 1) * GAP) / columns;
		int pictureHeight = Math.max(40, cardWidth - 2);
		return new Grid(x0, y0, x1, y1, columns, cardWidth, pictureHeight, pictureHeight + 32);
	}

	/** The wardrobe entries the search lets through. */
	private List<Wardrobe.Entry> mine() {
		List<Wardrobe.Entry> out = new ArrayList<>();
		String needle = wardrobeWords.toLowerCase(Locale.ROOT).strip();
		for (Wardrobe.Entry entry : skins.wardrobe().shown()) {
			if (needle.isEmpty() || (entry.name != null && entry.name.toLowerCase(Locale.ROOT).contains(needle))) out.add(entry);
		}
		return out;
	}

	private int count() {
		return tab == Tab.CATALOGUE ? skins.shelf(query).cards.size() : mine().size();
	}

	/** Index of the card under the mouse, or -1. */
	private int cardAt(double mouseX, double mouseY) {
		if (detail()) return -1;
		Grid grid = grid();
		if (mouseX < grid.x0() || mouseX >= grid.x1() || mouseY < grid.y0() || mouseY >= grid.y1()) return -1;
		int column = (int) ((mouseX - grid.x0()) / (grid.cardWidth() + GAP));
		int row = (int) ((mouseY - grid.y0() + scroll()) / (grid.cardHeight() + GAP));
		if (column >= grid.columns()) return -1;
		int left = grid.x0() + column * (grid.cardWidth() + GAP);
		int top = (int) (grid.y0() - scroll()) + row * (grid.cardHeight() + GAP);
		if (mouseX >= left + grid.cardWidth() || mouseY >= top + grid.cardHeight()) return -1;
		int index = row * grid.columns() + column;
		return index >= 0 && index < count() ? index : -1;
	}

	/** The middle of a catalogue card, or null when it is not shown; for the game test. */
	public int[] cardCenter(int index) {
		if (detail()) return null;
		Grid grid = grid();
		int left = grid.x0() + (index % grid.columns()) * (grid.cardWidth() + GAP);
		int top = (int) (grid.y0() - scroll()) + (index / grid.columns()) * (grid.cardHeight() + GAP);
		return new int[] { left + grid.cardWidth() / 2, top + grid.pictureHeight() / 2 };
	}

	// ------------------------------------------------------------------ drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		graphics.fill(0, 0, width, height, Theme.SCREEN);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		if (Theme.hideMouse) {
			mouseX = -10000;
			mouseY = -10000;
		}
		pointerX = mouseX;
		pointerY = mouseY;
		long now = System.nanoTime();
		float seconds = (now - lastFrame) / 1e9f;
		lastFrame = now;
		if (detail()) {
			if (spinning && !turning) rotationY += seconds * 30;
			drawDetail(graphics, mouseX, mouseY);
			refreshButtons();
		} else {
			Theme.card(graphics, searchLeft, topRowY(), searchRight, topRowY() + ROW, Theme.FIELD,
				search != null && search.isFocused() ? Theme.ACCENT : Theme.EDGE);
			Icons.draw(graphics, Icon.SEARCH, searchLeft + 4, topRowY() + (ROW - Icons.SIZE) / 2, Theme.TEXT_DIM);
			if (tab == Tab.WARDROBE) drawSyncCard(graphics, mouseX, mouseY);
			if (tab == Tab.CATALOGUE) drawCatalogue(graphics, mouseX, mouseY);
			else drawWardrobe(graphics, mouseX, mouseY);
			drawFresh(graphics, mouseX, mouseY);
			drawAccount(graphics, mouseX, mouseY);
		}
		drawStatus(graphics);
		super.extractRenderState(graphics, mouseX, mouseY, a);
		Theme.nextLayer(graphics);
		for (Dropdown<?> box : dropdowns) box.extractList(graphics, mouseX, mouseY, width);
		if (tagPicker != null) tagPicker.extractList(graphics, mouseX, mouseY, width);
	}

	/** Where the wardrobe is kept: here, and the copies it is brought together with. */
	private void drawSyncCard(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int x0 = syncLeft;
		int x1 = width - MARGIN;
		int y = secondRowY();
		Theme.card(graphics, x0, y, x1, y + ROW);
		MosaicaryClient client = MosaicaryClient.instance();
		Component said = client.wardrobeSync() == null ? Component.translatable("mosaicary.skins.mine.here")
			: client.wardrobeSync().summary();
		Component more = client.wardrobeSync() == null ? Component.translatable("mosaicary.skins.mine.here.tooltip")
			: client.wardrobeSync().details();
		Icons.draw(graphics, Icon.WARDROBE, x0 + 4, y + (ROW - Icons.SIZE) / 2, Theme.TEXT_DIM);
		graphics.text(font, ellipsis(said.getString(), x1 - x0 - Icons.SIZE - 14), x0 + Icons.SIZE + 8, y + (ROW - 8) / 2,
			Theme.TEXT, false);
		if (mouseX >= x0 && mouseX < x1 && mouseY >= y && mouseY < y + ROW) Theme.tooltip(graphics, font, more, mouseX, mouseY);
	}

	private void drawCatalogue(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Grid grid = grid();
		Skins.Shelf shelf = skins.shelf(query);
		if (shelf.cards.isEmpty()) {
			if (shelf.loading || shelf.failure == null && shelf.pagesLoaded == 0) {
				message(graphics, grid, Icon.LOADING, Theme.ACCENT, Component.translatable("mosaicary.state.loading"), null);
			} else if (shelf.failure != null) {
				boolean offline = shelf.failure == SiteClient.Failure.OFFLINE;
				message(graphics, grid, offline ? Icon.OFFLINE : Icon.ERROR, Theme.WARN,
					Component.translatable(offline ? "mosaicary.state.offline" : "mosaicary.state.error"),
					Component.translatable(offline ? "mosaicary.state.offline.detail" : "mosaicary.state.error.detail"));
			} else if (query.narrowed()) {
				message(graphics, grid, Icon.EMPTY, Theme.TEXT_DIM, Component.translatable("mosaicary.state.nomatch"),
					Component.translatable("mosaicary.state.nomatch.detail"));
			} else {
				message(graphics, grid, Icon.EMPTY, Theme.TEXT_DIM, Component.translatable("mosaicary.skins.empty"),
					Component.translatable("mosaicary.skins.empty.detail"));
			}
			return;
		}
		int count = shelf.cards.size();
		// One more row at the end while a page is on its way or has failed.
		boolean tail = shelf.loading || shelf.failure != null;
		int content = grid.contentHeight(count) + (tail ? ROW + GAP : 0);
		int visible = grid.y1() - grid.y0();
		scroll(Math.max(0, Math.min(Math.max(0, content - visible), scroll())));
		// The next page is asked for while there is still a screen of cards to go.
		if (content - visible - scroll() < visible) skins.more(query);

		int hovered = cardAt(mouseX, mouseY);
		graphics.enableScissor(grid.x0(), grid.y0(), grid.x1(), grid.y1());
		for (int i = 0; i < count; i++) {
			int left = grid.x0() + (i % grid.columns()) * (grid.cardWidth() + GAP);
			int top = (int) (grid.y0() - scroll()) + (i / grid.columns()) * (grid.cardHeight() + GAP);
			if (top > grid.y1() || top + grid.cardHeight() < grid.y0()) continue;
			drawCatalogueCard(graphics, shelf.cards.get(i), left, top, grid, i == hovered);
		}
		if (tail) {
			int top = (int) (grid.y0() - scroll()) + grid.contentHeight(count) + GAP;
			drawTail(graphics, shelf, grid, top, mouseX, mouseY);
		}
		graphics.disableScissor();
		scrollbar(graphics, grid, content);
	}

	/** The line at the end of the list: loading, or the failure with a retry on click. */
	private void drawTail(GuiGraphicsExtractor graphics, Skins.Shelf shelf, Grid grid, int top, int mouseX, int mouseY) {
		boolean failed = shelf.failure != null && !shelf.loading;
		boolean over = failed && mouseX >= grid.x0() && mouseX < grid.x1() - 6 && mouseY >= top && mouseY < top + ROW;
		Theme.card(graphics, grid.x0(), top, grid.x1() - 6, top + ROW, over ? Theme.CARD_HOVER : Theme.CARD,
			failed ? Theme.WARN : Theme.EDGE);
		Component said = Component.translatable(failed ? "mosaicary.skins.more.failed" : "mosaicary.skins.more.loading");
		int textWidth = font.width(said) + Icons.SIZE + 4;
		int x = (grid.x0() + grid.x1() - 6 - textWidth) / 2;
		Icons.draw(graphics, failed ? Icon.REFRESH : Icon.LOADING, x, top + (ROW - Icons.SIZE) / 2, failed ? Theme.WARN : Theme.ACCENT);
		graphics.text(font, said, x + Icons.SIZE + 4, top + (ROW - 8) / 2, Theme.TEXT, false);
		tailTop = top;
	}

	private int tailTop = Integer.MIN_VALUE;

	private void scrollbar(GuiGraphicsExtractor graphics, Grid grid, int content) {
		int visible = grid.y1() - grid.y0();
		if (content <= visible) return;
		int thumb = Math.max(12, visible * visible / content);
		int at = (int) ((visible - thumb) * (scroll() / (content - visible)));
		graphics.fill(grid.x1() - 3, grid.y0(), grid.x1() - 1, grid.y1(), Theme.CARD);
		graphics.fill(grid.x1() - 3, grid.y0() + at, grid.x1() - 1, grid.y0() + at + thumb, Theme.EDGE_HOVER);
	}

	private void drawCatalogueCard(GuiGraphicsExtractor graphics, SkinCard card, int left, int top, Grid grid, boolean hovered) {
		int right = left + grid.cardWidth();
		Theme.card(graphics, left, top, right, top + grid.cardHeight(), hovered ? Theme.CARD_HOVER : Theme.CARD,
			hovered ? Theme.ACCENT : Theme.EDGE);
		int pictureBottom = top + 1 + grid.pictureHeight();
		graphics.fill(left + 1, top + 1, right - 1, pictureBottom, 0xFF273A49);
		Skins.Picture picture = skins.picture(card);
		if (picture.state() == Skins.State.READY) {
			int side = Math.min(grid.cardWidth() - 2, grid.pictureHeight());
			int x = left + 1 + (grid.cardWidth() - 2 - side) / 2;
			int y = top + 1 + (grid.pictureHeight() - side) / 2;
			Identifier moving = card.eyes() && freshReady ? skins.livePicture(card) : null;
			Theme.blit(graphics, moving != null ? moving : picture.texture(), x, y, 0, 0, side, side, picture.width(), picture.height(),
				picture.width(), picture.height());
		} else {
			Icons.draw(graphics, picture.state() == Skins.State.LOADING ? Icon.LOADING : Icon.OWN_SKIN,
				left + (grid.cardWidth() - Icons.SIZE) / 2, top + 1 + (grid.pictureHeight() - Icons.SIZE) / 2, 0xB0FFFFFF);
		}
		// Badges in the picture's corner: kept in the wardrobe, eyes that come alive.
		int badgeX = right - 2 - Icons.SIZE;
		if (skins.wardrobe().has("site:" + card.id())) {
			Icons.draw(graphics, Icon.KEPT, badgeX, top + 3, Theme.ACCENT);
			badgeX -= Icons.SIZE;
		}
		if (card.eyes()) {
			Icons.draw(graphics, Icon.EYES, badgeX, top + 3, 0xE0FFFFFF);
			badgeX -= Icons.SIZE;
		}
		seeThroughMark(graphics, skins.look(card), badgeX, top + 3);

		int textLeft = left + 5;
		int textWidth = grid.cardWidth() - 10;
		graphics.text(font, ellipsis(card.name(), textWidth), textLeft, pictureBottom + 4, Theme.TEXT, false);
		int creditY = pictureBottom + 14;
		Icons.draw(graphics, card.wearer() ? Icon.OWN_SKIN : Icon.AUTHOR, textLeft - 2, creditY, Theme.TEXT_DIM);
		graphics.text(font, ellipsis(card.credit(), textWidth - Icons.SIZE), textLeft + Icons.SIZE, creditY + 4, Theme.TEXT_DIM, false);
	}

	/** The warning on a card whose skin players without the mod see otherwise, its words on the pointer. */
	private void seeThroughMark(GuiGraphicsExtractor graphics, Skins.Look look, int x, int y) {
		if (look == null || !look.seeThrough()) return;
		Icons.draw(graphics, Icon.ERROR, x, y, Theme.WARN);
		if (pointerX >= x && pointerX < x + Icons.SIZE && pointerY >= y && pointerY < y + Icons.SIZE) {
			Theme.tooltip(graphics, font, Component.translatable("mosaicary.skins.see_through"), pointerX, pointerY);
		}
	}

	private int pointerX;
	private int pointerY;

	private void drawWardrobe(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Grid grid = grid();
		List<Wardrobe.Entry> entries = mine();
		if (entries.isEmpty()) {
			boolean searched = !wardrobeWords.isBlank() && !skins.wardrobe().shown().isEmpty();
			message(graphics, grid, Icon.WARDROBE, Theme.TEXT_DIM,
				Component.translatable(searched ? "mosaicary.state.nomatch" : "mosaicary.skins.mine.empty"),
				Component.translatable(searched ? "mosaicary.skins.mine.nomatch.detail" : "mosaicary.skins.mine.empty.detail"));
			return;
		}
		int content = grid.contentHeight(entries.size());
		int visible = grid.y1() - grid.y0();
		scroll(Math.max(0, Math.min(Math.max(0, content - visible), scroll())));
		int hovered = cardAt(mouseX, mouseY);
		long latest = 0;
		for (Wardrobe.Entry entry : entries) latest = Math.max(latest, entry.worn);
		graphics.enableScissor(grid.x0(), grid.y0(), grid.x1(), grid.y1());
		for (int i = 0; i < entries.size(); i++) {
			int left = grid.x0() + (i % grid.columns()) * (grid.cardWidth() + GAP);
			int top = (int) (grid.y0() - scroll()) + (i / grid.columns()) * (grid.cardHeight() + GAP);
			if (top > grid.y1() || top + grid.cardHeight() < grid.y0()) continue;
			Wardrobe.Entry entry = entries.get(i);
			drawWardrobeCard(graphics, entry, left, top, grid, i == hovered, latest > 0 && entry.worn == latest);
		}
		graphics.disableScissor();
		scrollbar(graphics, grid, content);
	}

	private void drawWardrobeCard(GuiGraphicsExtractor graphics, Wardrobe.Entry entry, int left, int top, Grid grid, boolean hovered,
			boolean lastWorn) {
		int right = left + grid.cardWidth();
		Theme.card(graphics, left, top, right, top + grid.cardHeight(), hovered ? Theme.CARD_HOVER : Theme.CARD,
			hovered ? Theme.ACCENT : lastWorn ? Theme.ACCENT_DARK : Theme.EDGE);
		int pictureBottom = top + 1 + grid.pictureHeight();
		graphics.fill(left + 1, top + 1, right - 1, pictureBottom, 0xFF273A49);
		// A skin from the site has the site's picture; anything else is drawn the same way here.
		boolean fromSite = entry.id != null && entry.picture != null;
		String version = fromSite && entry.picture.contains("?v=") ? entry.picture.substring(entry.picture.indexOf("?v=") + 3) : "0";
		Skins.Picture picture = fromSite ? skins.picture(entry.id, entry.picture, version.replaceAll("[^0-9A-Za-z._-]", "_")) : skins.figure(entry);
		if (picture.state() == Skins.State.READY) {
			int side = Math.min(grid.cardWidth() - 2, grid.pictureHeight());
			int x = left + 1 + (grid.cardWidth() - 2 - side) / 2;
			int y = top + 1 + (grid.pictureHeight() - side) / 2;
			Skins.Look seen = fromSite && freshReady ? skins.look(entry) : null;
			Identifier moving = seen != null && seen.eyes() ? skins.livePicture(entry) : null;
			Theme.blit(graphics, moving != null ? moving : picture.texture(), x, y, 0, 0, side, side, picture.width(), picture.height(),
				picture.width(), picture.height());
		} else {
			Icons.draw(graphics, picture.state() == Skins.State.LOADING ? Icon.LOADING : Icon.OWN_SKIN,
				left + (grid.cardWidth() - Icons.SIZE) / 2, top + 1 + (grid.pictureHeight() - Icons.SIZE) / 2, 0xB0FFFFFF);
		}
		int badgeX = right - 2 - Icons.SIZE;
		if (lastWorn) {
			Icons.draw(graphics, Icon.WEAR, badgeX, top + 3, Theme.ACCENT);
			badgeX -= Icons.SIZE;
		}
		seeThroughMark(graphics, skins.look(entry), badgeX, top + 3);
		int textLeft = left + 5;
		int textWidth = grid.cardWidth() - 10;
		String name = entry.name == null || entry.name.isBlank() ? Component.translatable("mosaicary.skins.mine.unnamed").getString() : entry.name;
		graphics.text(font, ellipsis(name, textWidth), textLeft, pictureBottom + 4, Theme.TEXT, false);
		int lineY = pictureBottom + 14;
		Icon source = entry.id != null ? Icon.SITE : entry.file != null ? Icon.UPLOAD : Icon.OWN_SKIN;
		Icons.draw(graphics, source, textLeft - 2, lineY, Theme.TEXT_DIM);
		String from = Component.translatable(entry.id != null ? "mosaicary.skins.mine.from.site"
			: entry.file != null ? "mosaicary.skins.mine.from.file" : "mosaicary.skins.mine.from.texture").getString();
		graphics.text(font, ellipsis(from, textWidth - Icons.SIZE), textLeft + Icons.SIZE, lineY + 4, Theme.TEXT_DIM, false);
	}

	private String ellipsis(String text, int width) {
		if (text == null) return "";
		if (font.width(text) <= width) return text;
		return font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))) + "...";
	}

	/** A centred card that says what is happening: an empty, loading or failed state. */
	private void message(GuiGraphicsExtractor graphics, Grid grid, Icon icon, int tint, Component title, Component detail) {
		int width = Math.min(grid.x1() - grid.x0() - 20, 280);
		List<FormattedCharSequence> lines = detail == null ? List.of() : font.split(detail, width - 16);
		int height = 16 + Icons.SIZE + 6 + 10 + lines.size() * 10 + 12;
		int x0 = (grid.x0() + grid.x1() - width) / 2;
		int y0 = (grid.y0() + grid.y1() - height) / 2;
		Theme.card(graphics, x0, y0, x0 + width, y0 + height);
		Icons.draw(graphics, icon, x0 + (width - Icons.SIZE) / 2, y0 + 10, tint);
		graphics.centeredText(font, title, x0 + width / 2, y0 + 10 + Icons.SIZE + 6, Theme.TEXT);
		int y = y0 + 10 + Icons.SIZE + 6 + 12;
		for (FormattedCharSequence line : lines) {
			graphics.centeredText(font, line, x0 + width / 2, y, Theme.TEXT_DIM);
			y += 10;
		}
	}

	private void drawStatus(GuiGraphicsExtractor graphics) {
		int y = bottomRowY();
		Theme.card(graphics, MARGIN, y, statusRight, y + ROW);
		Icon icon;
		int tint;
		Component said;
		if (notice != null && System.currentTimeMillis() < noticeUntil) {
			icon = noticeTint == Theme.WARN ? Icon.ERROR : Icon.DOWNLOADED;
			tint = noticeTint;
			said = notice;
		} else if (skins.uploading()) {
			icon = Icon.LOADING;
			tint = Theme.ACCENT;
			said = Component.translatable("mosaicary.skins.action.wearing.tooltip");
		} else if (tab == Tab.WARDROBE && !detail()) {
			icon = Icon.WARDROBE;
			tint = Theme.TEXT_DIM;
			said = Component.translatable("mosaicary.skins.mine.count", skins.wardrobe().shown().size());
		} else {
			Skins.Shelf shelf = skins.shelf(query);
			if (shelf.loading) {
				icon = Icon.LOADING;
				tint = Theme.ACCENT;
				said = Component.translatable("mosaicary.state.loading");
			} else if (shelf.failure != null) {
				icon = Icon.ERROR;
				tint = Theme.WARN;
				said = Component.translatable("mosaicary.skins.status.failed");
			} else {
				icon = Icon.CATALOGUE;
				tint = Theme.TEXT_DIM;
				said = Component.translatable(query.narrowed() ? "mosaicary.skins.status.found" : "mosaicary.skins.status.count", shelf.total);
			}
		}
		Icons.draw(graphics, icon, MARGIN + 4, y + (ROW - Icons.SIZE) / 2, tint);
		graphics.text(font, ellipsis(said.getString(), statusRight - MARGIN - Icons.SIZE - 12), MARGIN + Icons.SIZE + 8,
			y + (ROW - font.lineHeight) / 2 + 1, Theme.TEXT, false);
	}

	// ------------------------------------------------------------------ the detail view

	private record Detail(int previewX0, int previewY0, int previewX1, int previewY1, int infoX0, int infoY0, int infoX1, int infoY1) { }

	private Detail detailLayout() {
		int top = bodyTop();
		int bottom = bodyBottom();
		int left = MARGIN;
		int right = width - MARGIN;
		int infoWidth = Math.max(180, Math.min(240, (right - left) * 2 / 5));
		int split = right - infoWidth;
		return new Detail(left, top, split - GAP, bottom, split, top, right, bottom);
	}

	private void drawDetail(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Detail layout = detailLayout();
		Wardrobe.Entry entry = entry();
		String name = entry.name == null || entry.name.isBlank() ? Component.translatable("mosaicary.skins.mine.unnamed").getString() : entry.name;

		// The name, on a card of its own in the top row.
		int nameX0 = MARGIN + IconButton.widthFor(Component.translatable("mosaicary.back.short")) + GAP;
		int nameX1 = width - MARGIN - 2 * (ROW + GAP);
		Theme.card(graphics, nameX0, topRowY(), nameX1, topRowY() + ROW);
		Icons.draw(graphics, Icon.OWN_SKIN, nameX0 + 4, topRowY() + (ROW - Icons.SIZE) / 2, Theme.ACCENT);
		graphics.text(font, ellipsis(name, nameX1 - nameX0 - Icons.SIZE - 14), nameX0 + Icons.SIZE + 8,
			topRowY() + (ROW - font.lineHeight) / 2 + 1, Theme.TEXT, false);

		Theme.card(graphics, layout.previewX0, layout.previewY0, layout.previewX1, layout.previewY1, 0xFF273A49, Theme.EDGE);
		Skins.Texture texture = openCard != null ? skins.texture(openCard) : skins.texture(entry);
		if (texture.ready()) {
			PlayerPose.Figure model = texture.model() == PlayerModelType.SLIM ? slimModel : wideModel;
			pose(model, (System.nanoTime() - opened) / 1e9f);
			// The eyes move when the game would move them too: the pack and its mods there and on.
			if (liveEyesOf != texture) {
				forgetLiveEyes();
				liveEyesOf = texture;
				liveEyes = LiveEyes.of(texture.pixels());
				openHasEyes = liveEyes != null;
			}
			boolean moving = liveEyes != null && freshShown == null;
			// The figure stands in the room below the card's buttons, but is cut only at the
			// card's edges: turned or zoomed in, it goes on under the buttons. The game stands
			// the figure on the bottom of the box it is drawn in, so the box is placed for the
			// figure's middle to stay in the room's middle at any zoom, and is cut to the card.
			int roomTop = layout.previewY0 + ROW + 6;
			int roomBottom = layout.previewY1 - 4;
			float scale = 0.97f * (roomBottom - roomTop) / 2.125f * zoom;
			float middle = (roomTop + roomBottom) / 2f + panY;
			int boxBottom = Math.round(middle + FIGURE_MIDDLE * scale);
			int boxTop = Math.min(layout.previewY0 + 1, Math.round(middle - 1.6f * scale));
			// Sideways the figure stands in the middle of the box, and the box still covers the card.
			int cardX0 = layout.previewX0 + 1;
			int cardX1 = layout.previewX1 - 1;
			int centre = Math.round((cardX0 + cardX1) / 2f + panX);
			int half = Math.max(centre - cardX0, cardX1 - centre);
			// Tilted about its middle, as the game measures it from the box's top.
			float pivot = -((boxBottom - boxTop) / scale - FIGURE_MIDDLE);
			graphics.enableScissor(cardX0, layout.previewY0 + 1, cardX1, layout.previewY1 - 1);
			PlayerPose.draw(graphics, model, moving ? liveEyes.frame() : texture.texture(), scale, rotationX, rotationY, pivot,
				centre - half, boxTop, centre + half, boxBottom);
			graphics.disableScissor();
		} else {
			boolean loading = texture.state() == Skins.State.LOADING;
			Grid area = new Grid(layout.previewX0, layout.previewY0, layout.previewX1, layout.previewY1, 1, 0, 0, 0);
			message(graphics, area, loading ? Icon.LOADING : Icon.ERROR, loading ? Theme.ACCENT : Theme.WARN,
				Component.translatable(loading ? "mosaicary.detail.loading" : "mosaicary.skins.detail.failed"), null);
		}
		drawInfo(graphics, layout, entry, mouseX, mouseY);
		drawFresh(graphics, mouseX, mouseY);
		drawSeeThrough(graphics, mouseX, mouseY);
		drawAccount(graphics, mouseX, mouseY);
	}

	@Override
	public void removed() {
		forgetLiveEyes();
		super.removed();
	}

	/**
	 * How high the figure's middle stands above the bottom of the box the game draws it in, in
	 * blocks: the game lifts the model 1.601 above the bottom, and the model reaches 0.5 above
	 * that and 1.5 below.
	 */
	private static final float FIGURE_MIDDLE = 1.1f;

	/** At rest, the arms swaying a little; walking by the game's formulas when walking is on. */
	private static void pose(PlayerPose.Figure model, float seconds) {
		PlayerPose.rest(model);
		if (walking) PlayerPose.walk(model, seconds);
		else PlayerPose.idle(model, seconds);
	}

	private int creditRowY = Integer.MIN_VALUE;

	private void drawInfo(GuiGraphicsExtractor graphics, Detail layout, Wardrobe.Entry entry, int mouseX, int mouseY) {
		Theme.card(graphics, layout.infoX0, layout.infoY0, layout.infoX1, layout.infoY1);
		int x = layout.infoX0 + 6;
		int width = layout.infoX1 - layout.infoX0 - 12;
		int y = layout.infoY0 + 6;
		SkinCard card = openCard;
		creditRowY = Integer.MIN_VALUE;
		if (card != null) {
			// Who drew it, linking to their page when there is one.
			creditRowY = y;
			boolean link = !card.creditUrl().isEmpty();
			boolean over = link && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + Icons.SIZE;
			Icons.draw(graphics, card.wearer() ? Icon.OWN_SKIN : Icon.AUTHOR, x, y, Theme.ACCENT);
			String credit = card.credit().isEmpty() ? "-" : card.credit();
			graphics.text(font, ellipsis(credit, width - Icons.SIZE - 20), x + Icons.SIZE + 4, y + 4, over ? Theme.ACCENT : Theme.TEXT, false);
			if (link) Icons.draw(graphics, Icon.OPEN_SITE, x + width - Icons.SIZE, y, over ? Theme.ACCENT : Theme.TEXT_DIM);
			if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + Icons.SIZE) {
				Theme.tooltip(graphics, font, Component.translatable(card.wearer() ? "mosaicary.skins.detail.wearer"
					: "mosaicary.skins.detail.author"), mouseX, mouseY);
			}
			y += Icons.SIZE + 3;
			Icons.draw(graphics, Icon.LICENCE, x, y, Theme.ACCENT);
			String licence = card.license().isEmpty() ? Component.translatable("mosaicary.skins.licence.found").getString()
				: Component.translatableWithFallback("mosaicary.licence." + card.license(), card.license().toUpperCase(Locale.ROOT)).getString();
			graphics.text(font, ellipsis(licence, width - Icons.SIZE - 4), x + Icons.SIZE + 4, y + 4, Theme.TEXT, false);
			y += Icons.SIZE + 3;
		} else {
			Icon source = entry.id != null ? Icon.SITE : entry.file != null ? Icon.UPLOAD : Icon.OWN_SKIN;
			Icons.draw(graphics, source, x, y, Theme.ACCENT);
			String from = Component.translatable(entry.id != null ? "mosaicary.skins.mine.from.site"
				: entry.file != null ? "mosaicary.skins.mine.from.file" : "mosaicary.skins.mine.from.texture").getString();
			graphics.text(font, ellipsis(from, width - Icons.SIZE - 4), x + Icons.SIZE + 4, y + 4, Theme.TEXT, false);
			y += Icons.SIZE + 3;
		}
		graphics.fill(x, y, x + width, y + 1, Theme.EDGE);
		y += 5;

		List<Object[]> facts = new ArrayList<>();
		facts.add(new Object[] { Icon.ARMS, Component.translatable(entry.slim() ? "mosaicary.skins.arms.slim" : "mosaicary.skins.arms.classic")
			.getString(), Component.translatable("mosaicary.skins.fact.arms") });
		if (card != null) {
			facts.add(new Object[] { Icon.LIKES, Integer.toString(card.likes()), Component.translatable("mosaicary.fact.likes") });
			facts.add(new Object[] { Icon.DOWNLOADS, Integer.toString(card.downloads()), Component.translatable("mosaicary.fact.downloads") });
			facts.add(new Object[] { Icon.TAG, Integer.toString(card.tags().size()), card.tags().isEmpty()
				? Component.translatable("mosaicary.fact.tags.none") : Component.translatable("mosaicary.fact.tags", String.join(", ", card.tags())) });
			if (!card.category().isEmpty()) {
				facts.add(new Object[] { Icon.CATEGORY, Component.translatable("mosaicary.skins.category." + card.category()).getString(),
					Component.translatable("mosaicary.skins.filter.category") });
			}
			facts.add(new Object[] { Icon.DATE, card.published().length() >= 10 ? card.published().substring(0, 10) : card.published(),
				Component.translatable("mosaicary.fact.published") });
			if (card.eyes()) {
				facts.add(new Object[] { Icon.EYES, Component.translatable("mosaicary.skins.fact.eyes").getString(),
					Component.translatable("mosaicary.skins.fact.eyes.tooltip") });
			}
		} else {
			if (entry.worn > 0) {
				facts.add(new Object[] { Icon.WEAR, dateOf(entry.worn), Component.translatable("mosaicary.skins.fact.worn") });
			}
			facts.add(new Object[] { Icon.DATE, dateOf(entry.added), Component.translatable("mosaicary.skins.fact.added") });
		}
		// Rows stop above the buttons, and above the cards when there are any.
		Notice notice = accountCard();
		if (notice == null) notice = seeThroughCard();
		if (notice == null) notice = notice(freshShown);
		int factsBottom = notice != null ? notice.y0() - 3 : layout.infoY1 - 6 - ROW - 3;
		int half = width / 2;
		for (int i = 0; i < facts.size(); i++) {
			Object[] fact = facts.get(i);
			int fx = x + (i % 2) * half;
			int fy = y + (i / 2) * (Icons.SIZE + 1);
			if (fy + Icons.SIZE > factsBottom) break;
			Icons.draw(graphics, (Icon) fact[0], fx, fy, Theme.TEXT_DIM);
			graphics.text(font, ellipsis((String) fact[1], half - Icons.SIZE - 5), fx + Icons.SIZE + 3, fy + 4, Theme.TEXT, false);
			if (mouseX >= fx && mouseX < fx + half && mouseY >= fy && mouseY < fy + Icons.SIZE) {
				Theme.tooltip(graphics, font, (Component) fact[2], mouseX, mouseY);
			}
		}
		y += ((facts.size() + 1) / 2) * (Icons.SIZE + 1) + 3;

		// What Wear does, said once above the buttons while there is room.
		int limit = notice != null ? notice.y0() - 4 : layout.infoY1 - 6 - ROW - 4;
		if (y + 24 < limit) {
			graphics.fill(x, y, x + width, y + 1, Theme.EDGE);
			y += 5;
			for (FormattedCharSequence line : font.split(Component.translatable(Skins.canUpload() ? "mosaicary.skins.detail.how" : "mosaicary.skins.detail.how.no_mojang"), width)) {
				if (y + 9 > limit) break;
				graphics.text(font, line, x, y, Theme.TEXT_DIM, false);
				y += 10;
			}
		}
	}

	private static String dateOf(long millis) {
		return java.time.Instant.ofEpochMilli(millis).toString().substring(0, 10);
	}

	// ------------------------------------------------------------------ input

	// Before 1.21.9 the game hands the pointer and the button over as numbers, not as one event.
	//#if MC>=12111
	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return clicked(event.x(), event.y(), event.button(), () -> super.mouseClicked(event, doubleClick));
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		return dragged(dx, dy) || super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		released();
		return super.mouseReleased(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (tagPicker != null && tagPicker.isOpen()) return tagPicker.typed(event.codepoint());
		return super.charTyped(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		return pressed(event.key()) || super.keyPressed(event);
	}
	//#else
	//$$ @Override
	//$$ public boolean mouseClicked(double mouseX, double mouseY, int button) {
	//$$ 	return clicked(mouseX, mouseY, button, () -> super.mouseClicked(mouseX, mouseY, button));
	//$$ }
	//$$
	//$$ @Override
	//$$ public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
	//$$ 	return dragged(dx, dy) || super.mouseDragged(mouseX, mouseY, button, dx, dy);
	//$$ }
	//$$
	//$$ @Override
	//$$ public boolean mouseReleased(double mouseX, double mouseY, int button) {
	//$$ 	released();
	//$$ 	return super.mouseReleased(mouseX, mouseY, button);
	//$$ }
	//$$
	//$$ @Override
	//$$ public boolean charTyped(char typed, int modifiers) {
	//$$ 	if (tagPicker != null && tagPicker.isOpen()) return tagPicker.typed(typed);
	//$$ 	return super.charTyped(typed, modifiers);
	//$$ }
	//$$
	//$$ @Override
	//$$ public boolean keyPressed(int key, int scanCode, int modifiers) {
	//$$ 	return pressed(key) || super.keyPressed(key, scanCode, modifiers);
	//$$ }
	//#endif

	/** A click; {@code widgets} gives it to the screen's own buttons, after any open list has had it. */
	private boolean clicked(double x, double y, int button, BooleanSupplier widgets) {
		for (Dropdown<?> box : dropdowns) {
			if (box.isOpen() && box.clickList(x, y, width)) return true;
		}
		if (tagPicker != null && tagPicker.isOpen() && tagPicker.clickList(x, y, width)) return true;
		if (widgets.getAsBoolean()) return true;
		if (button == InputConstants.MOUSE_BUTTON_RIGHT && detail()) {
			Detail layout = detailLayout();
			if (x >= layout.previewX0 && x < layout.previewX1 && y >= layout.previewY0 && y < layout.previewY1) {
				panning = true;
				return true;
			}
		}
		if (button != InputConstants.MOUSE_BUTTON_LEFT) return false;

		if (!detail()) {
			int index = cardAt(x, y);
			if (index >= 0) {
				if (tab == Tab.CATALOGUE) openCard(skins.shelf(query).cards.get(index));
				else openEntry(mine().get(index));
				return true;
			}
			if (tab == Tab.CATALOGUE) {
				Skins.Shelf shelf = skins.shelf(query);
				if (shelf.failure != null && shelf.cards.isEmpty()) {
					skins.refresh(query);
					return true;
				}
				if (shelf.failure != null && y >= tailTop && y < tailTop + ROW) {
					skins.retry(query);
					return true;
				}
			}
			return false;
		}

		Detail layout = detailLayout();
		if (openCard != null && !openCard.creditUrl().isEmpty() && y >= creditRowY && y < creditRowY + Icons.SIZE
				&& x >= layout.infoX0 && x < layout.infoX1) {
			openUri(openCard.creditUrl());
			return true;
		}
		if (x >= layout.previewX0 && x < layout.previewX1 && y >= layout.previewY0 && y < layout.previewY1) {
			turning = true;
			return true;
		}
		return false;
	}

	/** A drag that turns or moves the figure; false for one the screen's own widgets take. */
	private boolean dragged(double dx, double dy) {
		if (panning) {
			// As far as keeps the figure's middle on the card.
			Detail layout = detailLayout();
			float mostX = (layout.previewX1 - layout.previewX0) / 2f;
			float mostY = (layout.previewY1 - layout.previewY0) / 2f;
			panX = Math.max(-mostX, Math.min(mostX, panX + (float) dx));
			panY = Math.max(-mostY, Math.min(mostY, panY + (float) dy));
			return true;
		}
		if (turning) {
			rotationY += (float) dx * 2.5f;
			rotationX = Math.max(-50, Math.min(50, rotationX - (float) dy * 2.5f));
			return true;
		}
		return false;
	}

	private void released() {
		turning = false;
		panning = false;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		for (Dropdown<?> box : dropdowns) {
			if (box.isOpen() && box.scrollList(x, y, scrollY, width)) return true;
		}
		if (tagPicker != null && tagPicker.scrollList(x, y, scrollY, width)) return true;
		if (!detail()) {
			scroll(scroll() - scrollY * 24);
			return true;
		}
		Detail layout = detailLayout();
		if (x >= layout.previewX0 && x < layout.previewX1 && y >= layout.previewY0 && y < layout.previewY1) {
			zoom = (float) Math.max(0.5, Math.min(3, zoom * Math.pow(1.15, scrollY)));
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	/** A key the screen takes itself (by its key code): Escape closes an open list or the skin. */
	private boolean pressed(int key) {
		if (tagPicker != null && tagPicker.isOpen() && tagPicker.key(key)) return true;
		if (key == InputConstants.KEY_ESCAPE) {
			for (Dropdown<?> box : dropdowns) {
				if (box.isOpen()) {
					box.close();
					return true;
				}
			}
			if (detail()) {
				closeDetail();
				return true;
			}
		}
		return false;
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}

	/** The open tab; for the game test. */
	/** Switches tabs as the tab's button does; for the screenshot run. */
	public void showTab(Tab next) {
		switchTab(next);
	}

	public static Tab tab() {
		return tab;
	}

	/** The Wear button of the detail view, or null; for the game test. */
	public IconButton wearButton() {
		return wearButton;
	}

	/** The keep button of the detail view, or null; for the game test. */
	public IconButton keepButton() {
		return keepButton;
	}

	public SkinCard openCard() {
		return openCard;
	}

	public Skins skins() {
		return skins;
	}

	/** How many times the open skin's eyes have been painted anew, 0 without live eyes; for the game test. */
	public int eyesPainted() {
		return liveEyes == null ? 0 : liveEyes.painted();
	}

	/** Sets the catalogue's query; for the game test, which shows only skins with live eyes. */
	public static void query(SkinQuery next) {
		query = next;
	}

	/** The catalogue's current query; for the screenshot run. */
	public static SkinQuery query() {
		return query;
	}

	/** The middle of the preview, where the wheel zooms; for the game test. */
	public int[] previewCenter() {
		Detail layout = detailLayout();
		return new int[] { (layout.previewX0 + layout.previewX1) / 2, (layout.previewY0 + layout.previewY1) / 2 };
	}

	/** Looks a skin of the open shelf over and says whether its cards are all read; for the game test. */
	public boolean cardsRead() {
		for (SkinCard card : skins.shelf(query).cards) if (skins.look(card) == null) return false;
		return true;
	}

	/** Turns the preview; for the screenshot run. */
	public void setView(float rotationX, float rotationY) {
		this.rotationX = rotationX;
		this.rotationY = rotationY;
		spinning = false;
	}

	/** Messages the status line shows for a moment; public for the sync, which reports there. */
	public void report(Component said, boolean problem) {
		say(said, problem ? Theme.WARN : Theme.ACCENT);
	}

	/** Pending skins wear button state; for the game test. */
	public MojangSkins.Failure lastFailure() {
		Skins.Wear state = detail() ? skins.wearState(entry().key) : null;
		return state == null ? null : state.failure();
	}
}

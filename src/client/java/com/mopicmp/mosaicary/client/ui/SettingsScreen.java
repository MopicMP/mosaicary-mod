package com.mopicmp.mosaicary.client.ui;

import com.mopicmp.mosaicary.client.MosaicaryClient;
import com.mopicmp.mosaicary.client.site.Settings;
import com.mopicmp.mosaicary.client.skins.WardrobeSync;
import com.mopicmp.mosaicary.client.wear.WearRelay;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The wardrobe's settings: sharing and how it goes right now, "My skins" on the site by
 * the Minecraft account, an account on mosaicary.com, the skin beside the main menu and the
 * pause menu, and deleting one's data on the site. The site's address is not here: it is for development
 * only, and is changed in the settings file.
 *
 * Each setting is a card: an icon and a short name on the left, the control on the
 * right, the explanation in the tooltip. A choice of two is a pair of icon buttons with
 * the current one lit, never a switch with words beside it. The status card says whether
 * skins travel through the site, or why not; an unavailable relay is said, not hidden.
 * Signing in to an account on the site shows its code on the card and opens the page
 * where it is agreed to. Every change takes effect at once.
 * Deleting one's data also switches sharing and the wardrobe on the site off.
 */
public final class SettingsScreen extends Screen {

	private static final int WIDTH = 320;
	private static final int ROW = 20;
	private static final int CARD = 24;
	private static final int SPACE = 3;
	private static final int ROWS = 7;
	private static final int SHARING = 0;
	private static final int STATUS = 1;
	private static final int ON_SITE = 2;
	private static final int ACCOUNT = 3;
	private static final int MENU_SKIN = 4;
	private static final int PAUSE_SKIN = 5;
	private static final int DATA = 6;

	private final Screen parent;
	private IconButton shareOn;
	private IconButton shareOff;
	private IconButton siteOn;
	private IconButton siteOff;
	private IconButton accountButton;
	private IconButton menuOn;
	private IconButton menuOff;
	private IconButton pauseOn;
	private IconButton pauseOff;
	private IconButton deleteButton;
	private WearRelay.Deleted deleted;
	private String shownLink;

	public SettingsScreen(Screen parent) {
		super(Component.translatable("mosaicary.settings.title"));
		this.parent = parent;
	}

	private int left() {
		return (width - WIDTH) / 2;
	}

	private int top() {
		return Math.max(2, height / 2 - (ROWS * (CARD + SPACE) + 28) / 2);
	}

	private int rowY(int row) {
		return top() + 28 + row * (CARD + SPACE);
	}

	private int controlY(int row) {
		return rowY(row) + (CARD - ROW) / 2;
	}

	@Override
	protected void init() {
		Settings settings = MosaicaryClient.instance().settings();
		WardrobeSync sync = MosaicaryClient.instance().wardrobeSync();
		int right = left() + WIDTH;
		addRenderableWidget(IconButton.square(right - ROW - 4, top() + 4, ROW, Icon.CLOSE,
			Component.translatable("mosaicary.close"), this::onClose));

		// Sharing: two icon buttons, the current one lit.
		IconButton[] sharing = pair(SHARING, "mosaicary.settings.sharing", () -> setSharing(Settings.YES), () -> setSharing(Settings.NO));
		shareOn = sharing[0];
		shareOff = sharing[1];
		showSharing(settings.sharing);

		// My skins on the site, by the Minecraft account.
		IconButton[] onSite = pair(ON_SITE, "mosaicary.settings.on_site", () -> setOnSite(true), () -> setOnSite(false));
		siteOn = onSite[0];
		siteOff = onSite[1];
		showOnSite(settings.wardrobeOnSite);
		if (!MosaicaryClient.instance().relay().canSignIn()) {
			siteOn.active = false;
			siteOn.tooltip(Component.translatable("mosaicary.settings.on_site.unavailable"));
		}

		// An account on mosaicary.com: sign in with a code, or out.
		Component in = Component.translatable("mosaicary.settings.account.in");
		Component out = Component.translatable("mosaicary.settings.account.out");
		int accountWidth = Math.max(IconButton.widthFor(in), IconButton.widthFor(out));
		accountButton = new IconButton(right - 6 - accountWidth, controlY(ACCOUNT), accountWidth, ROW, Icon.LINK, in,
			Component.translatable("mosaicary.settings.account.in.tooltip"), this::account);
		addRenderableWidget(accountButton);
		showAccount();

		// The player's skin beside the main menu.
		IconButton[] menu = pair(MENU_SKIN, "mosaicary.settings.menu_skin", () -> setMenuSkin(true), () -> setMenuSkin(false));
		menuOn = menu[0];
		menuOff = menu[1];
		showMenuSkin(settings.menuSkin);
		// Where it stands and how large: a screen of its own, with the figure on it.
		addRenderableWidget(IconButton.square(menuOn.getX() - 4 - ROW, controlY(MENU_SKIN), ROW, Icon.SIZE,
			Component.translatable("mosaicary.settings.menu_skin.place"),
			() -> minecraft.gui.setScreen(new MenuSkinScreen(this, MenuSkin.Beside.MAIN))));

		// The same beside the pause menu, placed on its own.
		IconButton[] pause = pair(PAUSE_SKIN, "mosaicary.settings.pause_skin", () -> setPauseSkin(true), () -> setPauseSkin(false));
		pauseOn = pause[0];
		pauseOff = pause[1];
		showPauseSkin(settings.pauseSkin);
		addRenderableWidget(IconButton.square(pauseOn.getX() - 4 - ROW, controlY(PAUSE_SKIN), ROW, Icon.SIZE,
			Component.translatable("mosaicary.settings.pause_skin.place"),
			() -> minecraft.gui.setScreen(new MenuSkinScreen(this, MenuSkin.Beside.PAUSE))));

		// Deleting one's data on the site, which also switches sharing and the wardrobe there off.
		Component delete = Component.translatable("mosaicary.settings.delete");
		int deleteWidth = Math.max(IconButton.widthFor(delete), IconButton.widthFor(Component.translatable("mosaicary.settings.deleted")));
		deleteButton = new IconButton(right - 6 - deleteWidth, controlY(DATA), deleteWidth, ROW,
			Icon.DELETE, delete, Component.translatable("mosaicary.settings.delete.tooltip"), this::deleteMyData);
		addRenderableWidget(deleteButton);
		showDeleted();
		if (sync.link() != null) shownLink = sync.link().state();
	}

	/** Two icon buttons, on and off, at the right of a row. */
	private IconButton[] pair(int row, String key, Runnable on, Runnable off) {
		int right = left() + WIDTH;
		Component onLabel = Component.translatable("mosaicary.settings.sharing.on");
		Component offLabel = Component.translatable("mosaicary.settings.sharing.off");
		int offWidth = IconButton.widthFor(offLabel);
		int onWidth = IconButton.widthFor(onLabel);
		IconButton offButton = new IconButton(right - 6 - offWidth, controlY(row), offWidth, ROW, Icon.CLOSE, offLabel,
			Component.translatable(key + ".off.tooltip"), off);
		IconButton onButton = new IconButton(right - 6 - offWidth - 4 - onWidth, controlY(row), onWidth, ROW, Icon.SHARING, onLabel,
			Component.translatable(key + ".on.tooltip"), on);
		addRenderableWidget(onButton);
		addRenderableWidget(offButton);
		return new IconButton[] { onButton, offButton };
	}

	private void deleteMyData() {
		deleteButton.active = false;
		MosaicaryClient.instance().wardrobeSync().setOnSite(false);
		showOnSite(false);
		MosaicaryClient.instance().relay().deleteMyData(result -> {
			deleted = result;
			// Signed in still, the game would send the wardrobe straight back to the site.
			if (result == WearRelay.Deleted.DONE) MosaicaryClient.instance().wardrobeSync().signOut();
			MosaicaryClient.instance().saveSettings();
			showAccount();
			showSharing(Settings.NO);
			showDeleted();
		});
	}

	/** The delete button after a try: what came of it, on the button and in its tooltip. */
	private void showDeleted() {
		WearRelay relay = MosaicaryClient.instance().relay();
		if (deleted == WearRelay.Deleted.DONE) {
			deleteButton.icon(() -> Icon.DOWNLOADED).label(Component.translatable("mosaicary.settings.deleted"));
			deleteButton.tooltip(Component.translatable("mosaicary.settings.deleted.tooltip"));
			deleteButton.active = false;
		} else if (!relay.canDelete() || deleted == WearRelay.Deleted.NOT_AVAILABLE) {
			deleteButton.tooltip(Component.translatable("mosaicary.settings.delete.unavailable"));
			deleteButton.active = false;
		} else if (deleted == WearRelay.Deleted.FAILED) {
			deleteButton.icon(() -> Icon.ERROR);
			deleteButton.tooltip(Component.translatable("mosaicary.settings.delete.failed"));
			deleteButton.active = true;
		} else {
			deleteButton.active = true;
		}
	}

	/** The deletion button; for the game test. */
	public IconButton deleteButton() {
		return deleteButton;
	}

	/** The button that switches sharing on; for the game test. */
	public IconButton shareOnButton() {
		return shareOn;
	}

	private void setSharing(String value) {
		MosaicaryClient.instance().settings().sharing = value;
		MosaicaryClient.instance().saveSettings();
		showSharing(value);
	}

	private void showSharing(String value) {
		shareOn.primary(Settings.YES.equals(value));
		shareOff.primary(Settings.NO.equals(value));
	}

	private void setOnSite(boolean on) {
		MosaicaryClient.instance().wardrobeSync().setOnSite(on);
		showOnSite(on);
	}

	private void showOnSite(boolean on) {
		siteOn.primary(on);
		siteOff.primary(!on);
	}

	private void setMenuSkin(boolean on) {
		MosaicaryClient.instance().settings().menuSkin = on;
		MosaicaryClient.instance().saveSettings();
		showMenuSkin(on);
	}

	private void showMenuSkin(boolean on) {
		menuOn.primary(on);
		menuOff.primary(!on);
	}

	private void setPauseSkin(boolean on) {
		MosaicaryClient.instance().settings().pauseSkin = on;
		MosaicaryClient.instance().saveSettings();
		showPauseSkin(on);
	}

	private void showPauseSkin(boolean on) {
		pauseOn.primary(on);
		pauseOff.primary(!on);
	}

	private void account() {
		WardrobeSync sync = MosaicaryClient.instance().wardrobeSync();
		if (sync.on(WardrobeSync.Where.ACCOUNT)) sync.signOut();
		else sync.startLink();
		showAccount();
	}

	private void showAccount() {
		WardrobeSync sync = MosaicaryClient.instance().wardrobeSync();
		boolean signedIn = sync.on(WardrobeSync.Where.ACCOUNT);
		accountButton.icon(() -> signedIn ? Icon.CLOSE : Icon.LINK);
		accountButton.label(Component.translatable(signedIn ? "mosaicary.settings.account.out" : "mosaicary.settings.account.in"));
		accountButton.tooltip(Component.translatable(signedIn ? "mosaicary.settings.account.out.tooltip"
			: "mosaicary.settings.account.in.tooltip"));
		WardrobeSync.Link link = sync.link();
		accountButton.active = signedIn || link == null || !"starting".equals(link.state()) && !"waiting".equals(link.state());
	}

	@Override
	public void tick() {
		WardrobeSync sync = MosaicaryClient.instance().wardrobeSync();
		sync.pollLink();
		String state = sync.link() == null ? null : sync.link().state();
		if (!java.util.Objects.equals(state, shownLink)) {
			shownLink = state;
			showAccount();
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		graphics.fill(0, 0, width, height, Theme.SCREEN);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		if (CatalogueScreen.hideMouse || SkinsScreen.hideMouse) {
			mouseX = -10000;
			mouseY = -10000;
		}
		int left = left();
		int right = left + WIDTH;
		Theme.card(graphics, left, top(), right, rowY(ROWS - 1) + CARD + 6);
		Icons.draw(graphics, Icon.SETTINGS, left + 8, top() + 6, Theme.ACCENT);
		graphics.text(font, title, left + 8 + Icons.SIZE + 6, top() + 10, Theme.TEXT, false);

		row(graphics, SHARING, Icon.SHARING, Component.translatable("mosaicary.settings.sharing"), mouseX, mouseY,
			Component.translatable("mosaicary.settings.sharing.tooltip"));
		statusRow(graphics, mouseX, mouseY);
		row(graphics, ON_SITE, Icon.WARDROBE, Component.translatable("mosaicary.settings.on_site"), mouseX, mouseY,
			Component.translatable("mosaicary.settings.on_site.tooltip"));
		accountRow(graphics, mouseX, mouseY);
		row(graphics, MENU_SKIN, Icon.OWN_SKIN, Component.translatable("mosaicary.settings.menu_skin"), mouseX, mouseY,
			Component.translatable("mosaicary.settings.menu_skin.tooltip"));
		row(graphics, PAUSE_SKIN, Icon.OWN_SKIN, Component.translatable("mosaicary.settings.pause_skin"), mouseX, mouseY,
			Component.translatable("mosaicary.settings.pause_skin.tooltip"));
		row(graphics, DATA, Icon.DELETE, Component.translatable("mosaicary.settings.data"), mouseX, mouseY,
			Component.translatable("mosaicary.settings.data.tooltip"));
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	/**
	 * The account on mosaicary.com: which one the game is in, or the code to agree to on
	 * the site while signing in, or what came of it.
	 */
	private void accountRow(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		WardrobeSync sync = MosaicaryClient.instance().wardrobeSync();
		Settings settings = MosaicaryClient.instance().settings();
		int left = left() + 4;
		int y = rowY(ACCOUNT);
		Theme.card(graphics, left, y, left() + WIDTH - 4, y + CARD, Theme.CARD, Theme.EDGE);
		Icons.draw(graphics, Icon.SITE, left + 4, y + (CARD - Icons.SIZE) / 2, Theme.TEXT_DIM);
		Component said;
		int ink = Theme.TEXT;
		WardrobeSync.Link link = sync.link();
		if (sync.on(WardrobeSync.Where.ACCOUNT)) {
			said = Component.translatable("mosaicary.settings.account.as", settings.siteAccount == null ? "" : settings.siteAccount);
		} else if (link != null && "waiting".equals(link.state())) {
			said = Component.translatable("mosaicary.settings.account.code", link.code());
			ink = Theme.ACCENT;
		} else if (link != null && !"taken".equals(link.state()) && !"starting".equals(link.state())) {
			said = Component.translatable("mosaicary.settings.account." + link.state());
			ink = Theme.WARN;
		} else {
			said = Component.translatable("mosaicary.settings.account");
		}
		int room = accountButton.getX() - 6 - (left + 4 + Icons.SIZE + 6);
		String text = said.getString();
		if (font.width(text) > room) text = font.plainSubstrByWidth(text, Math.max(0, room - font.width("..."))) + "...";
		graphics.text(font, text, left + 4 + Icons.SIZE + 6, y + (CARD - 8) / 2, ink, false);
		if (mouseX >= left && mouseX < accountButton.getX() && mouseY >= y && mouseY < y + CARD) {
			Theme.tooltip(graphics, font, Component.translatable("mosaicary.settings.account.tooltip"), mouseX, mouseY);
		}
	}

	/**
	 * How sharing goes right now: through the site, off, or why it
	 * cannot work here; a reason that stops it is drawn in the warning colour.
	 */
	private void statusRow(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		WearRelay.Status status = MosaicaryClient.instance().relay().shown();
		boolean working = status == WearRelay.Status.SITE || status == WearRelay.Status.ACCOUNT;
		boolean blocked = !working && status != WearRelay.Status.OFF && status != WearRelay.Status.NO_WORLD;
		int left = left() + 4;
		int y = rowY(STATUS);
		Theme.card(graphics, left, y, left() + WIDTH - 4, y + CARD, Theme.CARD, Theme.EDGE);
		Icon icon = working ? Icon.SHARING : blocked ? Icon.ERROR : Icon.INFO;
		int ink = working ? Theme.ACCENT : blocked ? Theme.WARN : Theme.TEXT_DIM;
		Icons.draw(graphics, icon, left + 4, y + (CARD - Icons.SIZE) / 2, ink);
		String key = "mosaicary.relay." + status.name().toLowerCase(java.util.Locale.ROOT);
		String text = Component.translatable(key).getString();
		int room = left() + WIDTH - 12 - (left + 4 + Icons.SIZE + 6);
		if (font.width(text) > room) text = font.plainSubstrByWidth(text, room - font.width("...")) + "...";
		graphics.text(font, text, left + 4 + Icons.SIZE + 6, y + (CARD - 8) / 2, blocked ? Theme.WARN : Theme.TEXT, false);
		if (mouseX >= left && mouseX < left() + WIDTH - 4 && mouseY >= y && mouseY < y + CARD) {
			Theme.tooltip(graphics, font, Component.translatable(key + ".tooltip"), mouseX, mouseY);
		}
	}

	/** One setting's card: icon and name on the left, its explanation in the tooltip of the name. */
	private void row(GuiGraphicsExtractor graphics, int row, Icon icon, Component name, int mouseX, int mouseY,
			Component explanation) {
		int left = left() + 4;
		int y = rowY(row);
		Theme.card(graphics, left, y, left() + WIDTH - 4, y + CARD, Theme.CARD, Theme.EDGE);
		Icons.draw(graphics, icon, left + 4, y + (CARD - Icons.SIZE) / 2, Theme.TEXT_DIM);
		graphics.text(font, name, left + 4 + Icons.SIZE + 6, y + (CARD - 8) / 2, Theme.TEXT, false);
		int nameRight = left + 4 + Icons.SIZE + 6 + font.width(name);
		if (mouseX >= left && mouseX < nameRight && mouseY >= y && mouseY < y + CARD) {
			Theme.tooltip(graphics, font, explanation, mouseX, mouseY);
		}
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}

package com.mopicmp.mosaicary.client.ui;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import com.mopicmp.mosaicary.client.MosaicaryClient;
import com.mopicmp.mosaicary.client.site.Settings;
import com.mopicmp.mosaicary.client.skins.FreshMoves;
import com.mopicmp.mosaicary.client.skins.LiveEyes;
import com.mopicmp.mosaicary.client.skins.Skins;

import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.model.Model;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * The player's own skin standing to the right of the main menu and of the pause menu, as the
 * Bedrock edition shows its character: the game's player model, swaying a little, the head
 * following the pointer, the name on a card under the feet. Dragging turns it; a click opens
 * the wardrobe.
 *
 * <h2>Which skin</h2>
 *
 * The skin on the account as Mojang says it is now, or one just put on in the wardrobe
 * ({@code Skins.refreshOwn}); the figure is not drawn while that is being asked. Otherwise, with
 * no answer, the skin as the game knows it. The game fetches its own profile in the background
 * when it starts, and asking for it waits until it has come, so it is asked for off the client
 * thread; until it is there, the figure wears the default skin for the account.
 *
 * <h2>Live eyes</h2>
 *
 * A skin carrying a copy of its eyes for Fresh Moves moves them here as in the wardrobe's
 * preview (skins/LiveEyes), when the pack and its mods are there and on. The account's skin is
 * only known to the game as a texture on the graphics card, so its pixels are fetched by its
 * address on Mojang's servers. The moving copy is one for all figures: the menu makes a new
 * widget every time it is laid out, and a widget is not told when it is dropped.
 *
 * <h2>Switched off, moved, resized</h2>
 *
 * {@code menuSkin}, {@code menuSkinX}, {@code menuSkinY} and {@code menuSkinSize} in the
 * settings, and {@code pauseSkin} and the rest for the pause menu, each menu's figure placed on
 * its own. Read on every frame, so a change, in the wardrobe's settings, through Mod Menu or in
 * the settings file, shows on the open menu at once.
 */
public final class MenuSkin extends AbstractWidget {

	/** Which menu a figure stands beside. */
	public enum Beside {
		MAIN, PAUSE;

		public boolean on(Settings settings) {
			return this == MAIN ? settings.menuSkin : settings.pauseSkin;
		}

		public int x(Settings settings) {
			return this == MAIN ? settings.menuSkinX : settings.pauseSkinX;
		}

		public int y(Settings settings) {
			return this == MAIN ? settings.menuSkinY : settings.pauseSkinY;
		}

		public int size(Settings settings) {
			return this == MAIN ? settings.menuSkinSize : settings.pauseSkinSize;
		}
	}

	/** Where the main menu's buttons end, to the right of the middle, and the gap kept from them. */
	private static final int BUTTONS_HALF = 100;
	private static final int CLEAR_OF_BUTTONS = 12;
	private static final int NAME_ROW = 14;

	private static Supplier<PlayerSkin> accountSkin;
	private static boolean asked;
	private static String accountPixelsOf;
	private static int[] accountPixels;
	private static LiveEyes liveEyes;
	private static int[] liveEyesOf;
	private static boolean freshReady;
	private static long freshAsked;

	private final Model.Simple wideModel;
	private final Model.Simple slimModel;
	private final Screen screen;
	private final Beside beside;
	private final long born = System.nanoTime();
	private float turned;
	private boolean pressed;
	private boolean dragged;

	/** Drawn on the settings screen to show where it will stand: not clickable, no tooltip. */
	private final boolean preview;

	public MenuSkin(Screen screen, Beside beside, boolean preview) {
		super(0, 0, 0, 0, Component.translatable("mosaicary.menu_skin.tooltip"));
		this.screen = screen;
		this.beside = beside;
		this.preview = preview;
		this.wideModel = PlayerPose.model(false);
		this.slimModel = PlayerPose.model(true);
	}

	/** How tall the figure stands, in GUI units, at 100 percent: about the menu's column of buttons. */
	private static final int FIGURE = 120;

	/** The figure's box: where it stands and how large, in GUI units. */
	public record Place(int x, int y, int width, int height) { }

	/**
	 * Where the figure stands on a menu of this size with these settings: as tall as the column
	 * of buttons at 100 percent, its feet level with the last row, just to the right of the
	 * buttons; then moved by the settings' offset and kept on the screen. Its size is in GUI
	 * units, as the buttons' is, so it follows the GUI scale chosen in the options. Null when the
	 * screen has no room for it at all.
	 */
	public static Place place(int width, int height, Settings settings) {
		return place(width, height, width / 2 + BUTTONS_HALF, height / 4 + 48 + 4 * 24 + 20, Beside.MAIN, settings);
	}

	private static Place place(int width, int height, int buttonsRight, int feet, Beside beside, Settings settings) {
		int figure = FIGURE * beside.size(settings) / 100;
		// Smaller than chosen when the screen is not tall enough for it.
		figure = Math.min(figure, height - 8 - NAME_ROW);
		if (figure < 40) return null;
		int boxWidth = figure * 3 / 5;
		int x = buttonsRight + CLEAR_OF_BUTTONS + beside.x(settings);
		int y = feet - figure + beside.y(settings);
		x = Math.max(0, Math.min(width - boxWidth, x));
		y = Math.max(2, Math.min(height - figure - NAME_ROW - 2, y));
		return new Place(x, y, boxWidth, figure);
	}

	/** Puts the figure beside a main menu that has just been laid out; where, it works out every frame. */
	public static void addTo(Screen screen, int width, int height) {
		List<AbstractWidget> widgets = Screens.getWidgets(screen);
		widgets.add(new MenuSkin(screen, screen instanceof PauseScreen ? Beside.PAUSE : Beside.MAIN, false));
	}

	private boolean enabled() {
		MosaicaryClient client = MosaicaryClient.instance();
		return client != null && beside.on(client.settings());
	}

	@Override
	public boolean isActive() {
		return super.isActive() && !preview && enabled() && placeNow() != null;
	}

	/** The box for the screen and settings as they are now; the widget follows it. */
	private Place placeNow() {
		Settings settings = MosaicaryClient.instance().settings();
		Place place = beside == Beside.PAUSE ? besidePauseMenu(settings) : place(screen.width, screen.height, settings);
		if (place != null) {
			setX(place.x());
			setY(place.y());
			setWidth(place.width());
			setHeight(place.height());
		}
		return place;
	}

	/**
	 * Beside the pause menu's buttons, wherever they are; what stands in the screen's right quarter
	 * is not one of them. On the screen that places the figure, where the buttons are only drawn
	 * as outlines, where the game puts them when no other mod adds any.
	 */
	private Place besidePauseMenu(Settings settings) {
		if (!(screen instanceof PauseScreen)) {
			return place(screen.width, screen.height, screen.width / 2 + PAUSE_BUTTONS_HALF, pauseButtonsTop(screen.height) + PAUSE_BUTTONS_TALL,
				beside, settings);
		}
		int right = screen.width / 2;
		int bottom = screen.height / 2;
		for (AbstractWidget widget : Screens.getWidgets(screen)) {
			if (widget == this || !(widget instanceof AbstractButton) || widget.getX() > screen.width * 3 / 4) continue;
			right = Math.max(right, widget.getRight());
			bottom = Math.max(bottom, widget.getBottom());
		}
		return place(screen.width, screen.height, right, bottom, beside, settings);
	}

	public static final int PAUSE_BUTTONS_HALF = 102;
	public static final int PAUSE_BUTTONS_TALL = 116;

	public static int pauseButtonsTop(int height) {
		return height / 4 + 8;
	}

	/** A skin as the figure wears it; its pixels are null until they are known. */
	private record Worn(Identifier texture, PlayerModelType model, int[] pixels) { }

	/** The skin to draw: one just put on in the wardrobe, else the account's. */
	private static Worn worn() {
		Minecraft minecraft = Minecraft.getInstance();
		UUID me = minecraft.getUser().getProfileId();
		Skins.Shown shown = MosaicaryClient.instance().skins().shownOn(me);
		if (shown != null) return new Worn(shown.texture(), shown.model(), shown.pixels());
		if (!asked) {
			asked = true;
			CompletableFuture.supplyAsync(minecraft::getGameProfile, Util.nonCriticalIoPool())
				.thenAccept(profile -> minecraft.execute(() -> accountSkin = minecraft.getSkinManager().createLookup(profile, false)));
		}
		PlayerSkin skin = accountSkin != null ? accountSkin.get() : DefaultPlayerSkin.get(me);
		return new Worn(skin.body().texturePath(), skin.model(), accountPixels(skin));
	}

	/** The pixels of the account's skin: asked for once per skin, null until they have come. */
	private static int[] accountPixels(PlayerSkin skin) {
		if (!(skin.body() instanceof ClientAsset.DownloadedTexture downloaded)) return null;
		String url = downloaded.url();
		if (!url.equals(accountPixelsOf)) {
			accountPixelsOf = url;
			accountPixels = null;
			Minecraft minecraft = Minecraft.getInstance();
			MosaicaryClient.instance().skins().pixelsOfTexture(url).whenComplete((pixels, problem) -> minecraft.execute(() -> {
				if (problem == null && url.equals(accountPixelsOf)) accountPixels = pixels;
			}));
		}
		return accountPixels;
	}

	/** The texture to draw this frame: the moving copy when there are eyes to move and the game would move them too. */
	private static Identifier textureNow(Worn worn) {
		if (worn.pixels() != liveEyesOf) {
			if (liveEyes != null) liveEyes.close();
			liveEyesOf = worn.pixels();
			liveEyes = liveEyesOf == null ? null : LiveEyes.of(liveEyesOf);
		}
		return liveEyes != null && freshReady() ? liveEyes.frame() : worn.texture();
	}

	/** Whether the pack and its mods are there and on; asked once a second, as it does not change often. */
	private static boolean freshReady() {
		long now = System.currentTimeMillis();
		if (now - freshAsked >= 1000) {
			freshAsked = now;
			freshReady = FreshMoves.status().ready();
		}
		return freshReady;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		// Hidden while the menu fades in, which a drawn model cannot follow, and when switched off.
		if ((!enabled() && !preview) || alpha < 0.6f || placeNow() == null) return;
		if (MosaicaryClient.instance().skins().askingOwn()
				&& MosaicaryClient.instance().skins().shownOn(Minecraft.getInstance().getUser().getProfileId()) == null) {
			return;
		}
		Worn worn = worn();
		Model.Simple model = worn.model() == PlayerModelType.SLIM ? slimModel : wideModel;

		// The head follows the pointer, as far as a neck turns.
		float middle = getX() + getWidth() / 2f;
		float eyes = getY() + getHeight() * 0.12f;
		float seconds = (System.nanoTime() - born) / 1e9f;
		PlayerPose.rest(model);
		PlayerPose.idle(model, seconds);
		// The skin renderer looks at the model from its front, so a positive turn of the head
		// turns the face to the left of the screen: the pointer to the left gives a positive turn.
		// (Measured on the game test's screenshots: the other sign looked away from the pointer.)
		lastYaw = (float) Math.atan((middle - mouseX) / 70f) * 0.9f;
		PlayerPose.look(model, lastYaw, (float) Math.atan((mouseY - eyes) / 70f) * 0.6f);

		float scale = 0.97f * getHeight() / 2.125f;
		graphics.skin(model, textureNow(worn), scale, -5, 20 + turned, -1.0625f, getX(), getY(), getRight(), getBottom());

		// The name on a card under the feet.
		var font = Minecraft.getInstance().font;
		String name = Minecraft.getInstance().getUser().getName();
		int nameWidth = font.width(name) + 10;
		int cardX = (int) middle - nameWidth / 2;
		int cardY = getBottom() + 2;
		Theme.card(graphics, cardX, cardY, cardX + nameWidth, cardY + NAME_ROW - 2, isHovered() ? Theme.CARD_HOVER : Theme.CARD,
			isHovered() ? Theme.ACCENT : Theme.EDGE);
		graphics.text(font, name, cardX + 5, cardY + 2, Theme.TEXT, false);
		if (isHovered() && !preview) Theme.tooltip(graphics, font, Component.translatable("mosaicary.menu_skin.tooltip"), mouseX, mouseY);
	}

	private float lastYaw;

	/** The head's turn last drawn, in radians; for the game test. */
	public float headYaw() {
		return lastYaw;
	}

	/** How many times the figure's eyes have been painted anew, 0 without live eyes; for the game test. */
	public static int eyesPainted() {
		return liveEyes == null ? 0 : liveEyes.painted();
	}

	/** The figure on a screen, or null; for the game test. */
	public static MenuSkin on(Screen screen) {
		for (AbstractWidget widget : Screens.getWidgets(screen)) if (widget instanceof MenuSkin figure) return figure;
		return null;
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		pressed = true;
		dragged = false;
		if (doubleClick) turned = 0;
	}

	@Override
	protected void onDrag(MouseButtonEvent event, double dx, double dy) {
		turned += (float) dx * 2.5f;
		if (Math.abs(dx) > 0.5) dragged = true;
	}

	@Override
	public void onRelease(MouseButtonEvent event) {
		// A click opens the wardrobe; a drag only turns the figure.
		if (pressed && !dragged) {
			Minecraft minecraft = Minecraft.getInstance();
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
			MosaicaryClient.instance().openWardrobe(screen);
		}
		pressed = false;
	}

	@Override
	public void playDownSound(SoundManager soundManager) {
		// The click sound plays when the wardrobe opens: a press that turns the figure is not a click.
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, Component.translatable("mosaicary.menu_skin.tooltip"));
	}
}

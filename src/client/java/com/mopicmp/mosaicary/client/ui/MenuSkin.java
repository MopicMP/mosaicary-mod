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

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
//#if MC>=11700
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
//#endif
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
//#if MC>=12111
import net.minecraft.client.input.MouseButtonEvent;
//#endif
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
//#if MC>=12111
import net.minecraft.core.ClientAsset;
//#endif
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * The player's own skin standing to the right of the main menu and of the pause menu, as the
 * Bedrock edition shows its character: the game's player model, swaying a little, the head
 * following the pointer. No name under it: the menu is the player's own, and a card there only
 * covers the background. Dragging turns it; a click opens the wardrobe.
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
// Before 1.19.3 a widget has no getters for its place, no isHovered and no tooltip (PlacedWidget).
//#if MC>=11903
public final class MenuSkin extends AbstractWidget {
//#else
//$$ public final class MenuSkin extends com.mopicmp.mosaicary.client.platform.PlacedWidget {
//#endif

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

	private static Supplier<PlayerSkin> accountSkin;
	private static boolean asked;
	private static String accountPixelsOf;
	private static int[] accountPixels;
	private static LiveEyes liveEyes;
	private static int[] liveEyesOf;
	private static boolean freshReady;
	private static long freshAsked;

	private final PlayerPose.Figure wideModel;
	private final PlayerPose.Figure slimModel;
	private final Screen screen;
	private final Beside beside;
	private final long born = System.nanoTime();
	private float turned;
	private boolean pressed;
	private boolean dragged;
	//#if MC<12111
	//$$ private long lastClickAt;
	//#endif

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

	/** The main menu's column of buttons where the game puts it when no mod adds to it. */
	public static final int MAIN_BUTTONS_TALL = 116;

	public static int mainButtonsTop(int height) {
		return height / 4 + 48;
	}

	/**
	 * Where the figure stands on a main menu of this size with these settings, its buttons where
	 * the game puts them when no mod adds any: the settings screen's outlines. The live menu is
	 * measured instead (besideMainMenu). Null when the screen has no room for it at all.
	 */
	public static Place place(int width, int height, Settings settings) {
		int top = mainButtonsTop(height);
		return level(width, height, width / 2 + BUTTONS_HALF, top, top + MAIN_BUTTONS_TALL, Beside.MAIN, settings);
	}

	/**
	 * As tall as a column of buttons at 100 percent and level with it, its middle at the column's
	 * middle, just to the right of the buttons; then moved by the settings' offset and kept on the
	 * screen. Its size is in GUI units, as the buttons' is, so it follows the GUI scale chosen in
	 * the options.
	 */
	private static Place level(int width, int height, int buttonsRight, int top, int bottom, Beside beside, Settings settings) {
		int figure = FIGURE * beside.size(settings) / 100;
		return place(width, height, buttonsRight, (top + bottom) / 2 + figure / 2, beside, settings);
	}

	private static Place place(int width, int height, int buttonsRight, int feet, Beside beside, Settings settings) {
		int figure = FIGURE * beside.size(settings) / 100;
		// Smaller than chosen when the screen is not tall enough for it.
		figure = Math.min(figure, height - 8);
		if (figure < 40) return null;
		int boxWidth = figure * 3 / 5;
		int x = buttonsRight + CLEAR_OF_BUTTONS + beside.x(settings);
		int y = feet - figure + beside.y(settings);
		x = Math.max(0, Math.min(width - boxWidth, x));
		y = Math.max(2, Math.min(height - figure - 2, y));
		return new Place(x, y, boxWidth, figure);
	}

	/** Puts the figure beside a main menu that has just been laid out; where, it works out every frame. */
	public static void addTo(Screen screen, int width, int height) {
		ScreenWidgets.add(screen, new MenuSkin(screen, screen instanceof PauseScreen ? Beside.PAUSE : Beside.MAIN, false));
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
		Place place = beside == Beside.PAUSE ? besidePauseMenu(settings) : besideMainMenu(settings);
		if (place != null) {
			setX(place.x());
			setY(place.y());
			setWidth(place.width());
			//#if MC>=12002
			setHeight(place.height());
			//#else
			//$$ height = place.height();
			//#endif
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
		for (AbstractWidget widget : ScreenWidgets.of(screen)) {
			if (widget == this || !(widget instanceof AbstractButton) || widget.getX() > screen.width * 3 / 4) continue;
			right = Math.max(right, widget.getRight());
			bottom = Math.max(bottom, widget.getBottom());
		}
		return place(screen.width, screen.height, right, bottom, beside, settings);
	}

	/**
	 * Beside the main menu's column of buttons as it stands on the screen, which other mods add to
	 * (NeoForge puts a button to the right of Singleplayer) and which is not always where the
	 * game's numbers say: level with the column and to the right of its rightmost button. The
	 * copyright line at the bottom and buttons far from the middle are not part of it. On the
	 * screen that places the figure, against the outlines.
	 */
	private Place besideMainMenu(Settings settings) {
		if (!(screen instanceof TitleScreen)) return place(screen.width, screen.height, settings);
		int middle = screen.width / 2;
		int top = Integer.MAX_VALUE;
		int bottom = 0;
		int right = middle + BUTTONS_HALF;
		for (AbstractWidget widget : ScreenWidgets.of(screen)) {
			if (widget == this || !(widget instanceof AbstractButton)) continue;
			if (widget.getX() < middle - COLUMN_REACH || widget.getRight() > middle + COLUMN_REACH) continue;
			if (widget.getBottom() > screen.height - 14) continue;
			top = Math.min(top, widget.getY());
			bottom = Math.max(bottom, widget.getBottom());
			right = Math.max(right, widget.getRight());
		}
		if (top == Integer.MAX_VALUE) return place(screen.width, screen.height, settings);
		return level(screen.width, screen.height, right, top, bottom, beside, settings);
	}

	/** How far from the middle a button can stand and still be part of the main menu's column. */
	private static final int COLUMN_REACH = 160;

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
			//#if MC>=12002
			CompletableFuture.supplyAsync(minecraft::getGameProfile, Util.nonCriticalIoPool())
			//#else
			//$$ CompletableFuture.supplyAsync(() -> PlayerSkin.accountProfile(minecraft), Util.ioPool())
			//#endif
				//#if MC>=12111
				.thenAccept(profile -> minecraft.execute(() -> accountSkin = minecraft.getSkinManager().createLookup(profile, false)));
				//#elseif MC>=12002
				//$$ .thenAccept(profile -> minecraft.execute(() -> accountSkin = minecraft.getSkinManager().lookupInsecure(profile)));
				//#else
				//$$ .thenAccept(profile -> minecraft.execute(() -> accountSkin = PlayerSkin.lookup(minecraft, profile)));
				//#endif
		}
		//#if MC>=12002
		PlayerSkin skin = accountSkin != null ? accountSkin.get() : DefaultPlayerSkin.get(me);
		//#else
		//$$ PlayerSkin skin = accountSkin != null ? accountSkin.get() : PlayerSkin.ofDefault(me);
		//#endif
		// Before 1.21.9 a skin names its texture and the address it came from rather than one asset.
		//#if MC>=12111
		return new Worn(skin.body().texturePath(), skin.model(), accountPixels(skin));
		//#else
		//$$ return new Worn(skin.texture(), skin.model(), accountPixels(skin));
		//#endif
	}

	/** The pixels of the account's skin: asked for once per skin, null until they have come. */
	private static int[] accountPixels(PlayerSkin skin) {
		//#if MC>=12111
		if (!(skin.body() instanceof ClientAsset.DownloadedTexture downloaded)) return null;
		String url = downloaded.url();
		//#else
		//$$ String url = skin.textureUrl();
		//$$ if (url == null) return null;
		//#endif
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
	public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		// Hidden while the menu fades in, which a drawn model cannot follow, and when switched off.
		if ((!enabled() && !preview) || alpha < 0.6f || placeNow() == null) return;
		if (MosaicaryClient.instance().skins().askingOwn()
				&& MosaicaryClient.instance().skins().shownOn(Minecraft.getInstance().getUser().getProfileId()) == null) {
			return;
		}
		Worn worn = worn();
		PlayerPose.Figure model = worn.model() == PlayerModelType.SLIM ? slimModel : wideModel;

		// The head follows the pointer, as far as a neck turns.
		float middle = getX() + getWidth() / 2f;
		float eyes = getY() + getHeight() * 0.12f;
		float seconds = (System.nanoTime() - born) / 1e9f;
		PlayerPose.rest(model);
		PlayerPose.idle(model, seconds);
		// The skin renderer looks at the model from its front, so a positive turn of the head
		// turns the face to the left of the screen: the pointer to the left gives a positive turn.
		// (Measured on the game test's screenshots: the other sign looked away from the pointer.)
		// The pointer is taken 70 points in front of the screen. The body faces 20 + turned degrees
		// to the right of the screen, so the head turns by the rest, the shorter way round: with the
		// figure's back to the viewer, the pointer on the left is over its left shoulder.
		lastYaw = neck(shorter((float) Math.atan2(middle - mouseX, 70f) + body()));
		PlayerPose.look(model, lastYaw, (float) Math.atan((mouseY - eyes) / 70f) * 0.6f);

		float scale = 0.97f * getHeight() / 2.125f;
		PlayerPose.draw(graphics, model, textureNow(worn), scale, -5, 20 + turned, -1.0625f, getX(), getY(), getRight(), getBottom());

		if (isHovered() && !preview) {
			Theme.tooltip(graphics, Minecraft.getInstance().font, Component.translatable("mosaicary.menu_skin.tooltip"), mouseX, mouseY);
		}
	}

	private float lastYaw;

	/** The head's turn last drawn, in radians, within the neck's limit; for the game test. */
	public float headYaw() {
		return lastYaw;
	}

	/** Where the face last drawn points, in radians from the viewer, positive to the left of the screen; for the game test. */
	public float faceYaw() {
		return shorter(PlayerPose.headYaw(lastYaw) - body());
	}

	/** How far the body is turned to the right of the screen, in radians. */
	private float body() {
		return (float) Math.toRadians(20 + turned);
	}

	/** How far round from the face the pointer can be with the head still turned as far as it goes: 120 degrees. */
	private static final float HELD = (float) (2 * Math.PI / 3);

	/**
	 * The head's turn for a pointer that far round from the face, in radians: nine tenths of the way,
	 * up to the neck's limit; held there while the pointer goes on round to the side; then back
	 * towards straight ahead as it goes on behind, reaching it with the pointer right behind the head.
	 * So with the figure's back to the viewer, the pointer crossing behind it swings the head across
	 * from one shoulder to the other, rather than throwing it there in one frame.
	 */
	private static float neck(float wanted) {
		float away = Math.abs(wanted);
		if (away * 0.9f <= PlayerPose.NECK) return wanted * 0.9f;
		if (away <= HELD) return Math.signum(wanted) * PlayerPose.NECK;
		return Math.signum(wanted) * PlayerPose.NECK * (float) ((Math.PI - away) / (Math.PI - HELD));
	}

	/** The same turn between minus and plus half a circle. */
	private static float shorter(float radians) {
		return (float) (radians - 2 * Math.PI * Math.rint(radians / (2 * Math.PI)));
	}

	/** How many times the figure's eyes have been painted anew, 0 without live eyes; for the game test. */
	public static int eyesPainted() {
		return liveEyes == null ? 0 : liveEyes.painted();
	}

	/** The figure on a screen, or null; for the game test. */
	public static MenuSkin on(Screen screen) {
		for (AbstractWidget widget : ScreenWidgets.of(screen)) if (widget instanceof MenuSkin figure) return figure;
		return null;
	}

	//#if MC>=12111
	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
	//#else
	//$$ @Override
	//$$ public void onClick(double mouseX, double mouseY) {
	//$$ 	// The game tells a double click from 1.21.9 on; before, it is two clicks close together.
	//$$ 	long now = System.currentTimeMillis();
	//$$ 	boolean doubleClick = now - lastClickAt < 250;
	//$$ 	lastClickAt = now;
	//#endif
		pressed = true;
		dragged = false;
		if (doubleClick) turned = 0;
	}

	//#if MC>=12111
	@Override
	protected void onDrag(MouseButtonEvent event, double dx, double dy) {
	//#else
	//$$ @Override
	//$$ protected void onDrag(double mouseX, double mouseY, double dx, double dy) {
	//#endif
		turned += (float) dx * 2.5f;
		if (Math.abs(dx) > 0.5) dragged = true;
	}

	//#if MC>=12111
	@Override
	public void onRelease(MouseButtonEvent event) {
	//#else
	//$$ @Override
	//$$ public void onRelease(double mouseX, double mouseY) {
	//#endif
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

	//#if MC>=11700
	@Override
	public void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, Component.translatable("mosaicary.menu_skin.tooltip"));
	}
	//#else
	//$$ // Before 1.17 a widget is narrated by one message, its label unless it says otherwise.
	//$$ @Override
	//$$ protected net.minecraft.network.chat.MutableComponent createNarrationMessage() {
	//$$ 	return new net.minecraft.network.chat.TranslatableComponent("mosaicary.menu_skin.tooltip");
	//$$ }
	//#endif

	//#if MC<12002
	//$$ /** The widget's right and bottom edges, which 1.20.1 does not give, only its place and size. */
	//$$ private int getRight() {
	//$$ 	return getX() + getWidth();
	//$$ }
	//$$
	//$$ private int getBottom() {
	//$$ 	return getY() + getHeight();
	//$$ }
	//#endif
}

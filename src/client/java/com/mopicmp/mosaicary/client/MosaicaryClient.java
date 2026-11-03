package com.mopicmp.mosaicary.client;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import com.mopicmp.mosaicary.Mosaicary;
import com.mopicmp.mosaicary.client.build.Builds;
import com.mopicmp.mosaicary.client.platform.ClientPlatform;
import com.mopicmp.mosaicary.client.render.PreviewRenderer;
import com.mopicmp.mosaicary.client.render.PreviewState;
import com.mopicmp.mosaicary.client.site.Settings;
import com.mopicmp.mosaicary.client.site.SiteClient;
import com.mopicmp.mosaicary.client.skins.SkinRateProbe;
import com.mopicmp.mosaicary.client.skins.Skins;
import com.mopicmp.mosaicary.client.skins.WardrobeSync;
import com.mopicmp.mosaicary.client.ui.CatalogueScreen;
import com.mopicmp.mosaicary.client.ui.HintToast;
import com.mopicmp.mosaicary.client.ui.MenuSkin;
import com.mopicmp.mosaicary.client.ui.PauseMenuButton;
import com.mopicmp.mosaicary.client.ui.RingScreen;
import com.mopicmp.mosaicary.client.ui.ScreenWidgets;
import com.mopicmp.mosaicary.client.ui.SkinsScreen;
import com.mopicmp.mosaicary.client.wear.WearRelay;
import com.mopicmp.mosaicary.client.wear.Wearing;
import com.mopicmp.mosaicary.net.MosaicaryPayloads;
import com.mopicmp.mosaicary.platform.Platform;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

//#if FABRIC>=1
import net.fabricmc.api.ClientModInitializer;
//#endif
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/**
 * The client half: the skin wardrobe, the key that opens it and its button on the
 * pause menu.
 *
 * The wardrobe works the same on any server and in single player. A server with the mod
 * greets the client, keeps the wardrobe too and shows a changed skin to everybody at
 * once; without it, other players with the mod see a changed skin through the site's
 * wear relay, and only with sharing switched on.
 *
 * <h2>The model catalogue</h2>
 *
 * The catalogue of Blockbench models, wearing models, the quick-select ring, Figura
 * avatars and the shelf of builds are all still here, but hidden in this release: the key
 * opens the wardrobe, the ring key is not registered, and no model is drawn on anyone.
 * {@code -Dmosaicary.models=true} brings them back, for the game tests that cover them.
 */
//#if FABRIC>=1
public final class MosaicaryClient implements ClientModInitializer {
//#else
//$$ public final class MosaicaryClient {
//#endif

	/** Whether the model catalogue and everything that goes with it is shown. */
	public static final boolean MODELS = Boolean.getBoolean("mosaicary.models");

	private static MosaicaryClient instance;

	private Settings settings;
	private Path settingsFile;
	private Catalogue catalogue;
	private Wearing wearing;
	private WearRelay relay;
	private Skins skins;
	private WardrobeSync wardrobeSync;
	private KeyMapping openKey;
	private KeyMapping ringKey;
	private long ticks;

	public static MosaicaryClient instance() {
		return instance;
	}

	public Settings settings() {
		return settings;
	}

	public Catalogue catalogue() {
		return catalogue;
	}

	public Wearing wearing() {
		return wearing;
	}

	public WearRelay relay() {
		return relay;
	}

	public Skins skins() {
		return skins;
	}

	public WardrobeSync wardrobeSync() {
		return wardrobeSync;
	}

	public KeyMapping ringKey() {
		return ringKey;
	}

	/** Writes the settings back after a change, and puts it into effect at once. */
	public void saveSettings() {
		settings.save(settingsFile);
		settingsSeen = modified();
		settingsChanged();
	}

	/** When the settings file was last read or written here; a newer one was changed outside. */
	private long settingsSeen;
	private String siteInUse;

	private long modified() {
		try {
			return java.nio.file.Files.getLastModifiedTime(settingsFile).toMillis();
		} catch (java.io.IOException missing) {
			return 0;
		}
	}

	/**
	 * Reads the settings file again when it changed outside the game (by hand, or by another
	 * mod that edits mods' settings), so nothing needs a restart. Looked at once a second.
	 */
	private void watchSettings() {
		long now = modified();
		if (now == 0 || now == settingsSeen) return;
		settingsSeen = now;
		if (settings.reloadFrom(settingsFile)) {
			Mosaicary.LOGGER.info("Settings file changed outside the game; read again");
			settingsChanged();
		}
	}

	/**
	 * Puts settings into effect that something holds on to: the site's address. Everything
	 * else is read where it is used, every time, and needs nothing here.
	 */
	private void settingsChanged() {
		String site = settings.siteBase();
		if (siteInUse != null && !siteInUse.equals(site)) {
			Mosaicary.LOGGER.info("Catalogue site is now {}", site);
			catalogue.site().base(site);
			skins.forgetShelves();
			if (catalogue.listState() != null) catalogue.refresh();
		}
		siteInUse = site;
	}

	//#if FABRIC>=1
	@Override
	//#endif
	public void onInitializeClient() {
		instance = this;
		Path gameDir = Platform.gameDir();
		settingsFile = Platform.configDir().resolve("mosaicary.json");
		settings = Settings.load(settingsFile);
		settingsSeen = modified();
		siteInUse = settings.siteBase();
		String version = Platform.version(Mosaicary.ID);
		SiteClient site = new SiteClient(settings.siteBase(), gameDir.resolve("mosaicary"),
			"Mosaicary/" + version + " (Minecraft mod; +https://mosaicary.com)");
		// The mods here that place builds, which is where an installed build goes.
		List<Builds.Format> placers = new ArrayList<>();
		for (Builds.Format one : Builds.Format.values()) {
			if (Platform.isLoaded(one.modId)) placers.add(one);
		}
		site.placers(placers);
		catalogue = new Catalogue(site);
		// Figura keeps the player's avatars here; they can be worn without Figura.
		catalogue.figuraFolder(gameDir.resolve("figura").resolve("avatars"));
		wearing = new Wearing(catalogue, settings, settingsFile);
		relay = new WearRelay(catalogue, wearing, settings);
		skins = new Skins(site);
		relay.skins(skins);
		wardrobeSync = new WardrobeSync(skins, relay, settings, this::saveSettings);
		skins.signedIn(() -> wardrobeSync.on(WardrobeSync.Where.ACCOUNT));
		skins.onApplied(applied -> {
			// Worn through the site, as Mojang would not take it: nothing to tell a server about.
			if (applied.mojang() == null) {
				relay.skinOnSite(applied.entry());
				return;
			}
			relay.skinChanged(applied.mojang().texture(), applied.mojang().arms());
			// A server with the mod shows the new skin to everybody without the player joining again.
			if (ClientPlatform.canSend(MosaicaryPayloads.SkinChanged.TYPE)) {
				ClientPlatform.send(new MosaicaryPayloads.SkinChanged(Minecraft.getInstance().getUser().getProfileId(),
					applied.mojang().texture()));
			}
		});
		ClientPlatform.onReceive(MosaicaryPayloads.Hello.TYPE,
			payload -> Minecraft.getInstance().execute(wardrobeSync::serverGreeted));
		ClientPlatform.onReceive(MosaicaryPayloads.WardrobeState.TYPE,
			payload -> Minecraft.getInstance().execute(() -> wardrobeSync.fromServer(payload.json())));

		ClientPlatform.pictureInPicture(PreviewState.class, PreviewRenderer::new);

		// The measurement of how many skin changes in a row Mojang takes; run by hand only,
		// since it changes the account's skin back and forth (SkinRateProbe).
		// Written for any command source, since the loaders' client commands use different ones;
		// the lines go straight to the chat.
		ClientPlatform.commands(new ClientPlatform.Commands() {
			@Override
			public <S> void register(CommandDispatcher<S> dispatcher) {
				dispatcher.register(LiteralArgumentBuilder.<S>literal("mosaicary-measure-skins").then(
					RequiredArgumentBuilder.<S, Integer>argument("count", IntegerArgumentType.integer(1, 60)).then(
						RequiredArgumentBuilder.<S, Integer>argument("seconds", IntegerArgumentType.integer(0, 600)).executes(command -> {
							Minecraft minecraft = Minecraft.getInstance();
							SkinRateProbe.run(IntegerArgumentType.getInteger(command, "count"),
								IntegerArgumentType.getInteger(command, "seconds"),
								line -> minecraft.execute(() -> minecraft.gui.hud.getChat().addClientSystemMessage(Component.literal(line))));
							return 1;
						}))));
			}
		});

		KeyMapping.Category category = ClientPlatform.keyCategory(Mosaicary.id("main"));
		openKey = ClientPlatform.key(
			new KeyMapping("key.mosaicary.wardrobe", InputConstants.Type.KEYBOARD, InputConstants.KEY_M, category));

		// Held to show the quick-select ring; R is free in the game's own key list. Models only.
		if (MODELS) {
			ringKey = ClientPlatform.key(
				new KeyMapping("key.mosaicary.ring", InputConstants.Type.KEYBOARD, InputConstants.KEY_R, category));
		}

		ClientPlatform.onTick(minecraft -> {
			if (++ticks % 20 == 0) watchSettings();
			relay.tick(minecraft);
			wardrobeSync.tick();
			Screen open = minecraft.gui.screen();
			if (open instanceof TitleScreen || open instanceof PauseScreen || open instanceof SkinsScreen) skins.refreshOwn(false);
			while (openKey.consumeClick()) {
				if (minecraft.gui.screen() == null) openWardrobe(null);
			}
			while (ringKey != null && ringKey.consumeClick()) {
				if (minecraft.gui.screen() == null && minecraft.player != null) {
					minecraft.gui.setScreen(new RingScreen(ringKey));
				}
			}
		});

		ClientPlatform.onScreenInit((minecraft, screen, width, height) -> {
			if (screen instanceof PauseScreen) addPauseButton(screen);
			// The player's skin beside the main and pause menus (shown or not by the settings, read live).
			if (screen instanceof TitleScreen || screen instanceof PauseScreen pause && pause.showsPauseMenu()) {
				MenuSkin.addTo(screen, width, height);
			}
			if (screen instanceof TitleScreen || screen instanceof PauseScreen || screen instanceof SkinsScreen) skins.refreshOwn(true);
		});

		// Hints on entering a world, as toasts that disappear by themselves.
		ClientPlatform.onJoin(minecraft -> minecraft.execute(() -> {
			hintsOnJoin(minecraft);
			skins.refreshOwn(true);
		}));
		ClientPlatform.onLeave(minecraft -> minecraft.execute(() -> {
			relay.leftWorld();
			wearing.forgetOthers();
			skins.forgetOthers();
			wardrobeSync.leftServer();
		}));

		ClientPlatform.onStopping(minecraft -> {
			site.close();
			skins.close();
		});

		String lan = System.getProperty("mosaicary.devLan");
		if (lan != null && !lan.isBlank()) DevLan.start(Integer.parseInt(lan.trim()));
		String shots = System.getProperty("mosaicary.shots");
		if (shots != null && !shots.isBlank()) Shots.start(Path.of(shots), this);

		Mosaicary.LOGGER.info("Mosaicary wardrobe ready, site {}", settings.siteBase());
	}

	/** Opens the skin wardrobe on top of the given screen (or of the game when null). */
	public SkinsScreen openWardrobe(Screen parent) {
		SkinsScreen screen = new SkinsScreen(parent, skins);
		Minecraft.getInstance().gui.setScreen(screen);
		wardrobeSync.syncAll();
		if (!settings.catalogueOpened) {
			settings.catalogueOpened = true;
			saveSettings();
		}
		return screen;
	}

	/** Opens the model catalogue on top of the given screen (or of the game when null). */
	public CatalogueScreen open(Screen parent) {
		CatalogueScreen screen = new CatalogueScreen(parent, catalogue);
		Minecraft.getInstance().gui.setScreen(screen);
		if (!settings.catalogueOpened) {
			settings.catalogueOpened = true;
			saveSettings();
		}
		return screen;
	}

	/**
	 * The two hints a world can bring.
	 *
	 * The key hint names the catalogue's key as it is actually bound, until the
	 * catalogue has been opened once and at most three times. The sharing hint is
	 * shown once, on the first multiplayer server joined while sharing is not on: other
	 * players' models are only shown with sharing on, and the place to switch it on is
	 * otherwise easy to miss. Neither asks anything; both disappear by themselves.
	 */
	private void hintsOnJoin(Minecraft minecraft) {
		boolean changed = false;
		if (!settings.catalogueOpened && settings.hintShown < 3) {
			settings.hintShown++;
			changed = true;
			minecraft.gui.toastManager().addToast(new HintToast(minecraft.font,
				Component.translatable("mosaicary.hint.title"),
				Component.translatable("mosaicary.hint.key", openKey.getTranslatedKeyMessage()), 7000));
		}
		boolean multiplayer = minecraft.getSingleplayerServer() == null;
		if (multiplayer && MODELS && !Settings.YES.equals(settings.sharing) && !settings.sharingHintShown) {
			settings.sharingHintShown = true;
			changed = true;
			minecraft.gui.toastManager().addToast(new HintToast(minecraft.font,
				Component.translatable("mosaicary.hint.sharing.title"),
				Component.translatable("mosaicary.hint.sharing", openKey.getTranslatedKeyMessage()), 9000));
		}
		if (changed) saveSettings();
	}

	/**
	 * Puts the catalogue button into the pause menu's row of small icon buttons,
	 * shifting that row left by half a button so it stays centred.
	 *
	 * The row is found by what it is (20 x 20 sprite buttons on one line) rather
	 * than by position, so a menu without it (opened with F3 + Esc) gets no button.
	 */
	private void addPauseButton(Screen screen) {
		List<AbstractWidget> widgets = ScreenWidgets.of(screen);
		List<AbstractWidget> row = new ArrayList<>();
		for (AbstractWidget widget : widgets) {
			if (widget instanceof SpriteIconButton && widget.getWidth() == 20 && widget.getHeight() == 20) {
				if (row.isEmpty() || row.get(0).getY() == widget.getY()) row.add(widget);
			}
		}
		if (row.isEmpty()) return;
		int right = 0;
		for (AbstractWidget widget : row) {
			widget.setX(widget.getX() - 12);
			right = Math.max(right, widget.getRight());
		}
		Component label = Component.translatable("mosaicary.pause.button",
			openKey.getTranslatedKeyMessage());
		ScreenWidgets.add(screen, new PauseMenuButton(right + 4, row.get(0).getY(), label, () -> openWardrobe(screen)));
	}
}

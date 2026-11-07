package com.mopicmp.mosaicary.client.site;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

/**
 * The mod's small settings file, {@code config/mosaicary.json}.
 *
 * Read once at start and written back whole whenever something changes. A file
 * that cannot be read is replaced by defaults rather than stopping the game; the
 * broken one is kept beside it as {@code mosaicary.json.broken} so nothing typed
 * into it by hand is lost.
 */
public final class Settings {

	/** Where the catalogue lives. Changeable for a local copy of the site. */
	public String site = "https://mosaicary.com";

	/**
	 * Whether this player shows other players' models and shares their own
	 * through the site. Asked once on first launch; changeable in settings.
	 * One of {@link #NOT_ASKED}, {@link #YES}, {@link #NO}.
	 */
	public String sharing = NOT_ASKED;

	public static final String NOT_ASKED = "not_asked";
	public static final String YES = "yes";
	public static final String NO = "no";

	/** How many quick-select slots the ring has. */
	public static final int SLOTS = 8;

	/** The model the local player wears, or null for their own skin. */
	public Saved worn;

	/** The models bound to the ring's slots; null for an empty slot. */
	public List<Saved> slots = new ArrayList<>();

	/**
	 * Models whose first wear has been counted as a download on the site, as
	 * {@code <player uuid>/<model id>}. Wearing counts once per model per player, not
	 * once per press.
	 */
	public List<String> counted = new ArrayList<>();

	/**
	 * Whether the player's own skin stands beside the main menu. Read on every frame, so a
	 * change shows at once, whichever way it was made.
	 */
	public boolean menuSkin = true;

	/**
	 * Where the figure stands, moved from its own place beside the menu, in GUI units: right
	 * and down are positive. An offset rather than a place on the screen, so it stays beside
	 * the menu whatever the window's size.
	 */
	public int menuSkinX;
	public int menuSkinY;

	/** How tall the figure is, in percent of its own height (about the menu's column of buttons). */
	public int menuSkinSize = 100;

	/** The same for the skin beside the pause menu, which is placed on its own. */
	public boolean pauseSkin = true;
	public int pauseSkinX;
	public int pauseSkinY;
	public int pauseSkinSize = 100;

	/** Whether the player's name stands over the figure, as over a player in the world: beside each menu on its own. */
	public boolean menuSkinName = true;
	public boolean pauseSkinName = true;

	/**
	 * Whether the figures the mod draws itself (the preview of a skin, the wardrobe, the figures
	 * beside the menus) have their outer layer in three dimensions where 3D Skin Layers is installed.
	 * Read on every frame. The player in the world is the other mod's own and is left to it.
	 */
	public boolean skinLayersOnFigures = true;

	/**
	 * The same for Female Gender Mod's breasts on those figures, by its settings: on the girls of the
	 * catalogue, on "My skins" by the body kept with each, and on the skin worn.
	 */
	public boolean genderOnFigures = true;

	/**
	 * A body for Female Gender Mod set in the menus, or with a skin put on there, waiting for a world,
	 * where the mod has the player's settings to put it on (compat/GenderMod); null when none waits.
	 */
	public JsonObject genderWaiting;

	/** Whether the catalogue has ever been opened; until then a hint names its key on entering a world. */
	public boolean catalogueOpened;

	/** How many times the key hint has been shown; it stops after three. */
	public int hintShown;

	/** Whether the hint that sharing can be switched on has been shown on a multiplayer server. */
	public boolean sharingHintShown;

	/**
	 * Which animation plays for which action, per player and model, as
	 * {@code <player uuid>/<model id>} to {@code action -> value}; a value is an
	 * animation name, {@link #VANILLA} or {@link #NOTHING}. Only what the player
	 * changed is stored; the rest is filled in from the animation names.
	 */
	public Map<String, Map<String, String>> roles = new LinkedHashMap<>();

	/**
	 * The size each player picked for each model, as a multiplier over the model's own
	 * size, by {@code "<player uuid>/<model id>"}; a model drawn at its own size has no entry.
	 */
	public Map<String, Double> scales = new LinkedHashMap<>();

	/**
	 * Whether "My skins" is also kept on mosaicary.com under the Minecraft account, signed
	 * in the way the wear relay signs in. Off until the player switches it on.
	 */
	public boolean wardrobeOnSite;

	/**
	 * The key the site gave when the game was let into an account there with a code, or
	 * null. Kept in this file as the Blockbench plugin keeps its key: whoever can read the
	 * game's settings can use it, and it can be withdrawn on the site at any time.
	 */
	public String siteKey;

	/** The name of that account on the site, to show which one the game is in. */
	public String siteAccount;

	public static final int MENU_SKIN_SMALLEST = 50;
	public static final int MENU_SKIN_LARGEST = 200;

	/** A role value: the game's own motion for that action. */
	public static final String VANILLA = "@vanilla";

	/** A role value: no motion at all for that action. */
	public static final String NOTHING = "@none";

	/** What is kept about a model so it can be shown and fetched without the list. */
	public static final class Saved {
		public String id;
		public String name;
		public String updated;
		public String preview;
		public boolean adult;

		public static Saved of(ModelCard card) {
			Saved saved = new Saved();
			saved.id = card.id();
			saved.name = card.name();
			saved.updated = card.updated();
			saved.preview = card.preview();
			saved.adult = card.adult();
			return saved;
		}

		/** A card with what is known, enough to fetch the file and the picture. */
		public ModelCard card() {
			return ModelCard.minimal(id, name, updated, preview, adult);
		}
	}

	/** Makes the slot list exactly {@link #SLOTS} long, whatever the file said. */
	public void normalise() {
		if (slots == null) slots = new ArrayList<>();
		while (slots.size() < SLOTS) slots.add(null);
		while (slots.size() > SLOTS) slots.remove(slots.size() - 1);
		if (counted == null) counted = new ArrayList<>();
		if (roles == null) roles = new LinkedHashMap<>();
		roles.values().removeIf(java.util.Objects::isNull);
		if (scales == null) scales = new LinkedHashMap<>();
		scales.values().removeIf(value -> value == null || !Double.isFinite(value) || value < 0.1 || value > 10);
		if (hintShown < 0) hintShown = 0;
		menuSkinSize = Math.max(MENU_SKIN_SMALLEST, Math.min(MENU_SKIN_LARGEST, menuSkinSize == 0 ? 100 : menuSkinSize));
		menuSkinX = Math.max(-2000, Math.min(2000, menuSkinX));
		menuSkinY = Math.max(-2000, Math.min(2000, menuSkinY));
		pauseSkinSize = Math.max(MENU_SKIN_SMALLEST, Math.min(MENU_SKIN_LARGEST, pauseSkinSize == 0 ? 100 : pauseSkinSize));
		pauseSkinX = Math.max(-2000, Math.min(2000, pauseSkinX));
		pauseSkinY = Math.max(-2000, Math.min(2000, pauseSkinY));
		if (worn != null && (worn.id == null || worn.id.isBlank())) worn = null;
		for (int i = 0; i < slots.size(); i++) {
			Saved slot = slots.get(i);
			if (slot != null && (slot.id == null || slot.id.isBlank())) slots.set(i, null);
		}
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/** The base address without a trailing slash, falling back to the default when blank. */
	public String siteBase() {
		String base = site == null || site.isBlank() ? "https://mosaicary.com" : site.trim();
		while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
		return base;
	}

	public static Settings load(Path file) {
		if (!Files.exists(file)) {
			Settings fresh = new Settings();
			fresh.normalise();
			fresh.save(file);
			return fresh;
		}
		try {
			Settings read = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Settings.class);
			if (read == null) read = new Settings();
			if (read.sharing == null || !(read.sharing.equals(YES) || read.sharing.equals(NO))) {
				read.sharing = NOT_ASKED;
			}
			read.normalise();
			return read;
		} catch (IOException | JsonParseException broken) {
			try {
				Files.copy(file, file.resolveSibling(file.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
				// Keeping the copy is a courtesy; defaults are used either way.
			}
			Settings fresh = new Settings();
			fresh.normalise();
			fresh.save(file);
			return fresh;
		}
	}

	/**
	 * Reads the file again into this same object, so every part of the mod holding it sees
	 * the new values at once: for a change made outside the game, by hand or by another mod.
	 * Returns whether anything changed.
	 */
	public boolean reloadFrom(Path file) {
		Settings read;
		try {
			read = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Settings.class);
		} catch (IOException | JsonParseException broken) {
			// Half written, or broken by hand: the values in use stay until it reads again.
			return false;
		}
		if (read == null) return false;
		if (read.sharing == null || !(read.sharing.equals(YES) || read.sharing.equals(NO))) read.sharing = NOT_ASKED;
		read.normalise();
		boolean changed = false;
		for (java.lang.reflect.Field field : Settings.class.getFields()) {
			if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
			try {
				Object now = field.get(this);
				Object then = field.get(read);
				if (!java.util.Objects.equals(GSON.toJson(now), GSON.toJson(then))) {
					field.set(this, then);
					changed = true;
				}
			} catch (IllegalAccessException unreachable) {
				// Public fields of this class; cannot happen.
			}
		}
		return changed;
	}

	public void save(Path file) {
		try {
			Files.createDirectories(file.toAbsolutePath().getParent());
			Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(temporary, GSON.toJson(this), StandardCharsets.UTF_8);
			Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException failed) {
			// The settings stay in memory for this session; nothing else depends on the file.
		}
	}
}

package com.mopicmp.mosaicary.wardrobe;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

/**
 * "My skins": the skins a player keeps, one wardrobe per Minecraft account.
 *
 * <h2>What an entry is</h2>
 *
 * A skin from the site's catalogue, by its id; or a Mojang texture, by its hash, which
 * is what any skin becomes once it has been put on; or a file of the player's own, kept
 * beside the wardrobe until it has been put on once and so has a hash. The site and the
 * game server keep no pictures of their own for a wardrobe: an entry they hold is an id
 * or a hash, and the picture comes from the site or from textures.minecraft.net.
 *
 * <h2>Kept in several places</h2>
 *
 * The copy here always exists and works without a connection. The same wardrobe can also
 * be kept on mosaicary.com (by the Minecraft account, or by an account on the site) and on
 * a game server that has the mod. They are brought together by {@link #merge}: entries
 * are matched by key and the later change wins, a removal included, which is kept as a
 * mark so that a copy that has not heard of it does not bring the entry back. Marks older
 * than {@link #MARKS_KEPT_MS} are dropped.
 *
 * The client keeps one per account in the game folder; a game server with the mod keeps
 * one per player in the world's folder. Neither touches the other's threads: each copy is
 * used from one thread, and {@link #merge} is pure.
 */
public final class Wardrobe {

	/** How long a removal is remembered, so every copy has time to hear of it. */
	public static final long MARKS_KEPT_MS = 90L * 86_400_000;

	/** The most entries a wardrobe holds; the oldest unworn go first. */
	public static final int MOST = 200;

	/** One skin in the wardrobe, or the mark of one taken out of it. */
	public static final class Entry {
		/** {@code site:<id>}, {@code file:<sha1>} or {@code tex:<hash>}. */
		public String key;
		/** The site's id, for a skin from the catalogue. */
		public String id;
		/** The Mojang texture hash, once the skin has been put on, or for a skin known only by it. */
		public String texture;
		/** The SHA-1 of a file of the player's own, kept in the wardrobe's folder. */
		public String file;
		public String name;
		/** {@code classic} or {@code slim}. */
		public String arms;
		/** The site's picture of the skin worn, for a skin from the catalogue. */
		public String picture;
		/** When it was added, in milliseconds. */
		public long added;
		/** When this entry last changed, a removal included: the later change wins in a merge. */
		public long changed;
		/** When it was last put on; 0 for never. */
		public long worn;
		/** Taken out of the wardrobe; kept as a mark. */
		public boolean gone;
		/** The body it was last worn with in Female Gender Mod ({@link Body}), or null. */
		public JsonObject body;

		public static Entry ofSite(String id, String name, String arms, String picture, long now) {
			Entry entry = new Entry();
			entry.key = "site:" + id;
			entry.id = id;
			entry.name = name;
			entry.arms = arms;
			entry.picture = picture;
			entry.added = now;
			entry.changed = now;
			return entry;
		}

		public static Entry ofFile(String sha1, String name, String arms, long now) {
			Entry entry = new Entry();
			entry.key = "file:" + sha1;
			entry.file = sha1;
			entry.name = name;
			entry.arms = arms;
			entry.added = now;
			entry.changed = now;
			return entry;
		}

		public static Entry ofTexture(String hash, String name, String arms, long now) {
			Entry entry = new Entry();
			entry.key = "tex:" + hash;
			entry.texture = hash;
			entry.name = name;
			entry.arms = arms;
			entry.added = now;
			entry.changed = now;
			return entry;
		}

		/**
		 * Whether any other copy can hold this entry: it has an id or a hash, not only a file
		 * here. The site keeps files as well ({@link #travelling(boolean)}); a game server does
		 * not, and neither the entry of such a file nor its removal is any use to it.
		 */
		public boolean travels() {
			return id != null || texture != null;
		}

		/** The entry as it goes to another copy: without the name of a file only this computer has. */
		public Entry travelling() {
			Entry out = copy();
			out.file = null;
			return out;
		}

		public boolean slim() {
			return "slim".equals(arms);
		}

		Entry copy() {
			Entry out = new Entry();
			out.key = key;
			out.id = id;
			out.texture = texture;
			out.file = file;
			out.name = name;
			out.arms = arms;
			out.picture = picture;
			out.added = added;
			out.changed = changed;
			out.worn = worn;
			out.gone = gone;
			out.body = Body.copy(body);
			return out;
		}

		/**
		 * Whether the entry is shaped as one: a key of a known kind matching what it names.
		 * A file of one's own arrives from another copy without the file, only with the
		 * texture it became, and is sound as long as it has that.
		 */
		boolean sound() {
			if (key == null || (texture != null && !texture.matches("[0-9a-f]{32,128}"))) return false;
			if (arms != null && !arms.equals("classic") && !arms.equals("slim")) return false;
			if (key.startsWith("site:")) return id != null && key.equals("site:" + id) && id.matches("[0-9A-Za-z]{12}");
			if (key.startsWith("file:")) {
				if (!key.matches("file:[0-9a-f]{40}")) return false;
				return file != null ? key.equals("file:" + file) : texture != null || gone;
			}
			if (key.startsWith("tex:")) return texture != null && key.equals("tex:" + texture);
			return false;
		}
	}

	private static final class Saved {
		List<Entry> entries = new ArrayList<>();
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private final Path folder;
	private final String owner;
	private final Map<String, Entry> entries = new LinkedHashMap<>();
	private final List<Runnable> listeners = new ArrayList<>();

	/**
	 * @param folder the wardrobe folder, {@code mosaicary/wardrobe} in the game folder
	 * @param owner  the Minecraft account's UUID
	 */
	public Wardrobe(Path folder, String owner) {
		this.folder = folder;
		this.owner = owner;
		load();
	}

	public String owner() {
		return owner;
	}

	/** The folder that holds the player's own files, by their SHA-1. */
	public Path files() {
		return folder.resolve("files");
	}

	public Path fileOf(Entry entry) {
		return entry.file == null ? null : files().resolve(entry.file + ".png");
	}

	/** Called after every change, for the copies that have to hear of it. */
	public void listen(Runnable listener) {
		listeners.add(listener);
	}

	// ------------------------------------------------------------------ reading

	/** The entries, the most recently worn first, then the most recently added. */
	public List<Entry> shown() {
		List<Entry> out = new ArrayList<>();
		for (Entry entry : entries.values()) if (!entry.gone) out.add(entry);
		out.sort(Comparator.comparingLong((Entry entry) -> entry.worn).reversed()
			.thenComparing(Comparator.comparingLong((Entry entry) -> entry.added).reversed()));
		return out;
	}

	public boolean has(String key) {
		Entry entry = entries.get(key);
		return entry != null && !entry.gone;
	}

	public Entry get(String key) {
		Entry entry = entries.get(key);
		return entry == null || entry.gone ? null : entry;
	}

	/** The entry that holds a texture, whatever its kind. */
	public Entry byTexture(String hash) {
		for (Entry entry : entries.values()) if (!entry.gone && hash.equals(entry.texture)) return entry;
		return null;
	}

	/** Every entry another copy can hold, marks included. */
	public List<Entry> travelling() {
		return travelling(false);
	}

	/** The same, with the player's own files when the copy keeps them too. */
	public List<Entry> travelling(boolean withFiles) {
		List<Entry> out = new ArrayList<>();
		for (Entry entry : entries.values()) if (entry.travels() || (withFiles && entry.file != null)) out.add(entry.travelling());
		return out;
	}

	/** Entries as they go over the wire: a JSON array of {@link #travelling()} entries. */
	public String travellingJson() {
		return GSON.toJson(travelling());
	}

	public String travellingJson(boolean withFiles) {
		return GSON.toJson(travelling(withFiles));
	}

	/** Reads entries sent by another copy; anything unreadable is an empty list. */
	public static List<Entry> fromJson(String json) {
		try {
			Entry[] read = GSON.fromJson(json, Entry[].class);
			List<Entry> out = new ArrayList<>();
			if (read != null) {
				for (Entry entry : read) {
					if (entry == null) continue;
					// A file's name travels in its key: its entry says only which file it is here.
					if (entry.file == null && entry.key != null && entry.key.startsWith("file:")) entry.file = entry.key.substring(5);
					out.add(entry);
				}
			}
			return out;
		} catch (JsonParseException | IllegalStateException unreadable) {
			return List.of();
		}
	}

	// ------------------------------------------------------------------ changing

	/** Adds an entry, or brings back one that was taken out. */
	public void add(Entry entry) {
		Entry known = entries.get(entry.key);
		if (known != null && !known.gone) return;
		if (known != null) {
			// Back in: whatever was learnt about it before (its texture) is kept.
			if (entry.texture == null) entry.texture = known.texture;
		}
		entries.put(entry.key, entry);
		trim();
		changed();
	}

	public void remove(String key, long now) {
		Entry known = entries.get(key);
		if (known == null || known.gone) return;
		known.gone = true;
		known.changed = now;
		changed();
	}

	/** Records that a skin was put on, and the texture it became; adds it if it was not kept. */
	public void worn(Entry entry, String texture, String arms, long now) {
		Entry known = entries.get(entry.key);
		if (known == null || known.gone) {
			known = entry.copy();
			known.gone = false;
			known.added = now;
			entries.put(known.key, known);
		}
		if (texture != null) known.texture = texture;
		if (arms != null) known.arms = arms;
		known.worn = now;
		known.changed = now;
		trim();
		changed();
	}

	/** Records the body a kept skin was worn with; nothing happens for a skin not kept, or the same body. */
	public void bodyOf(String key, JsonObject body, long now) {
		Entry known = get(key);
		JsonObject clean = Body.clean(body);
		if (known == null || clean == null || clean.equals(known.body)) return;
		known.body = clean;
		known.changed = now;
		changed();
	}

	/** Takes in what another copy holds. Returns whether anything here changed. */
	public boolean mergeIn(List<Entry> theirs, long now) {
		List<Entry> merged = merge(new ArrayList<>(entries.values()), theirs, now);
		boolean same = merged.size() == entries.size();
		if (same) {
			for (Entry entry : merged) {
				Entry known = entries.get(entry.key);
				if (known == null || !sameEntry(known, entry)) {
					same = false;
					break;
				}
			}
		}
		if (same) return false;
		entries.clear();
		for (Entry entry : merged) entries.put(entry.key, entry);
		trim();
		save();
		for (Runnable listener : listeners) listener.run();
		return true;
	}

	/**
	 * Two copies made one: by key, the later change wins; at the same moment, a kept entry
	 * beats a removal and the one that knows a texture beats the one that does not. Marks
	 * older than {@link #MARKS_KEPT_MS} are dropped, and so is anything malformed.
	 */
	public static List<Entry> merge(List<Entry> ours, List<Entry> theirs, long now) {
		Map<String, Entry> out = new LinkedHashMap<>();
		for (List<Entry> side : List.of(ours, theirs)) {
			for (Entry entry : side) {
				if (entry == null || !entry.sound()) continue;
				Entry known = out.get(entry.key);
				if (known == null || wins(entry, known)) {
					Entry chosen = entry.copy();
					chosen.body = Body.clean(chosen.body);
					// What either side learnt about the skin stays, whichever side won. A copy that
					// does not know bodies (an older mod, the site's pages) does not lose one.
					if (known != null) {
						if (chosen.texture == null) chosen.texture = known.texture;
						if (chosen.file == null) chosen.file = known.file;
						if (chosen.picture == null) chosen.picture = known.picture;
						if (chosen.body == null) chosen.body = known.body;
						chosen.worn = Math.max(chosen.worn, known.worn);
					}
					out.put(entry.key, chosen);
				} else {
					if (known.texture == null) known.texture = entry.texture;
					if (known.file == null) known.file = entry.file;
					if (known.picture == null) known.picture = entry.picture;
					if (known.body == null) known.body = Body.clean(entry.body);
					known.worn = Math.max(known.worn, entry.worn);
				}
			}
		}
		out.values().removeIf(entry -> entry.gone && now - entry.changed > MARKS_KEPT_MS);
		return new ArrayList<>(out.values());
	}

	private static boolean wins(Entry challenger, Entry known) {
		if (challenger.changed != known.changed) return challenger.changed > known.changed;
		if (challenger.gone != known.gone) return !challenger.gone;
		return challenger.texture != null && known.texture == null;
	}

	private static boolean sameEntry(Entry a, Entry b) {
		return a.changed == b.changed && a.gone == b.gone && a.worn == b.worn && Objects.equals(a.texture, b.texture)
			&& Objects.equals(a.name, b.name) && Objects.equals(a.arms, b.arms) && Objects.equals(a.file, b.file)
			&& Objects.equals(a.picture, b.picture) && Objects.equals(a.body, b.body);
	}

	/** Keeps at most {@link #MOST} entries: the oldest never worn go first, then the oldest worn. */
	private void trim() {
		List<Entry> kept = shown();
		if (kept.size() <= MOST) return;
		kept.sort(Comparator.comparingLong((Entry entry) -> entry.worn).thenComparingLong(entry -> entry.added));
		for (int i = 0; i < kept.size() - MOST; i++) entries.remove(kept.get(i).key);
	}

	private void changed() {
		save();
		for (Runnable listener : listeners) listener.run();
	}

	// ------------------------------------------------------------------ disk

	private Path file() {
		return folder.resolve(owner.replaceAll("[^0-9a-fA-F-]", "_") + ".json");
	}

	private void load() {
		Path file = file();
		if (!Files.exists(file)) return;
		try {
			Saved saved = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Saved.class);
			if (saved == null || saved.entries == null) return;
			for (Entry entry : merge(saved.entries, List.of(), System.currentTimeMillis())) entries.put(entry.key, entry);
		} catch (IOException | JsonParseException broken) {
			try {
				Files.copy(file, file.resolveSibling(file.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
				// Keeping the copy is a courtesy; an empty wardrobe is used either way.
			}
		}
	}

	public void save() {
		Saved saved = new Saved();
		saved.entries = new ArrayList<>(entries.values());
		try {
			Files.createDirectories(folder);
			Path temporary = file().resolveSibling(file().getFileName() + ".tmp");
			Files.writeString(temporary, GSON.toJson(saved), StandardCharsets.UTF_8);
			Files.move(temporary, file(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException failed) {
			// The wardrobe stays in memory for this session.
		}
	}
}

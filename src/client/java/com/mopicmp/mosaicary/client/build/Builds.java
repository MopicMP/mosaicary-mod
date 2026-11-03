package com.mopicmp.mosaicary.client.build;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * A build of blocks, whichever mod saved it: Axiom's blueprint, Litematica's schematic or
 * WorldEdit's (the Sponge format), read into one shape and written out of it, so a build
 * put up on the site in any of the three can be installed for any of the three.
 *
 * <p>The same rules as the site's {@code site/shared/structure.ts}, which turns them over in
 * the browser; this is that file in Java, over the game's own NBT code. A box around the
 * blocks with its lowest corner at nought (around the entities, for a build of displays
 * alone), a palette of block states as the game writes them with air first, and a palette
 * index for every place, x fastest, then z, then y. Block entities and entities go across as
 * their own NBT, with only their place set to the box.
 *
 * <ul>
 * <li>Axiom: a magic number, then three parts each led by its length: the header (plain
 *   NBT), a PNG, and the contents (gzip NBT). Sections of 16³, a palette and indices packed
 *   whole into longs, at least four bits each; {@code structure_void} is "not placed".</li>
 * <li>Litematica: gzip NBT, one or more regions, each a palette (air first) and indices packed
 *   into longs across their edges, at least two bits each; a negative size counts back from
 *   the region's position.</li>
 * <li>Sponge (WorldEdit 7): gzip NBT, versions 1 and 2 at the root, version 3 under
 *   {@code Schematic}; a palette of state names to numbers and indices as varints.</li>
 * </ul>
 */
public final class Builds {

	private Builds() { }

	/** The three formats, with the site's name for each and the mod that places it. */
	public enum Format {
		AXIOM("axiom_blueprint", "bp", "Axiom", "axiom"),
		LITEMATICA("litematica", "litematic", "Litematica", "litematica"),
		WORLDEDIT("sponge_schematic", "schem", "WorldEdit", "worldedit");

		/** The format as the site names it on a model. */
		public final String site;
		public final String extension;
		/** The mod's own name, for the player. */
		public final String mod;
		/** The mod's id, to ask the loader whether it is installed. */
		public final String modId;

		Format(String site, String extension, String mod, String modId) {
			this.site = site;
			this.extension = extension;
			this.mod = mod;
			this.modId = modId;
		}

		/** The format with this site name, or null. */
		public static Format ofSite(String format) {
			for (Format one : values()) if (one.site.equals(format)) return one;
			return null;
		}
	}

	public record PlacedBlockEntity(int x, int y, int z, String id, CompoundTag data) { }

	/** An entity at its place in the box, its NBT without {@code id} and {@code Pos}. */
	public record PlacedEntity(double x, double y, double z, String id, CompoundTag data) { }

	/**
	 * A box of blocks. {@code blocks} holds a palette index for every place,
	 * {@code x + z * width + y * width * length}; the palette's first entry is air.
	 */
	public record Structure(String name, String author, List<String> tags, int dataVersion,
		int width, int height, int length, List<String> palette, int[] blocks,
		List<PlacedBlockEntity> blockEntities, List<PlacedEntity> entities) {

		/** How many places hold a block. */
		public int blockCount() {
			boolean[] empty = emptyOf(palette);
			int count = 0;
			for (int at : blocks) if (!empty[at]) count++;
			return count;
		}
	}

	/** Not one of the three, or one that cannot be read here. */
	public static final class NotABuild extends IOException {
		public NotABuild(String message) {
			super(message);
		}
	}

	/** The most places a box may have to be turned over: 256³, 64 MB of indices. */
	public static final int MOST_PLACES = 256 * 256 * 256;

	/** How much NBT one file may unpack to, as the game's own accounting counts it. */
	static final long MOST_NBT = 256L * 1024 * 1024;

	private static final int AXIOM_MAGIC = 0x0AE5BB36;
	private static final int MOST_HEADER = 64 * 1024;
	private static final int MOST_THUMBNAIL = 2 * 1024 * 1024;

	private static final String AIR = "minecraft:air";
	private static final String VOID = "minecraft:structure_void";
	private static final Set<String> EMPTY = Set.of(AIR, "minecraft:cave_air", "minecraft:void_air", VOID);

	// ------------------------------------------------------------------ telling them apart

	/** Which of the three these bytes are, by their start; null for none of them. */
	public static Format formatOf(byte[] data) {
		if (data.length >= 4 && ByteBuffer.wrap(data, 0, 4).getInt() == AXIOM_MAGIC) return Format.AXIOM;
		if (data.length < 2 || (data[0] & 0xFF) != 0x1F || (data[1] & 0xFF) != 0x8B) return null;
		try {
			CompoundTag root = gzipRoot(data);
			if (root.contains("Regions") && root.contains("Metadata")) return Format.LITEMATICA;
			// Version 3 under Schematic or, as some writers do, at the root; 1 and 2 at the root.
			if (root.contains("Schematic") || root.contains("Blocks") || root.contains("Palette") || root.contains("BlockData")) {
				return Format.WORLDEDIT;
			}
		} catch (IOException | RuntimeException unreadable) {
			return null;
		}
		return null;
	}

	// Reading and writing use the game's NBT, whose accessors changed in 1.21.5; builds belong to the
	// model shelf, which is built from 1.21.11 on (docs/mod-versions-plan.md), and older versions only
	// tell one kind from another.
	//#if MC>=12111
	/** Any of the three, read into the common shape. */
	public static Structure read(byte[] data) throws IOException {
		Format format = formatOf(data);
		if (format == null) throw new NotABuild("not a blueprint, a Litematica schematic or a WorldEdit schematic");
		try {
			return switch (format) {
				case AXIOM -> fromBlueprint(data);
				case LITEMATICA -> fromLitematic(gzipRoot(data));
				case WORLDEDIT -> fromSponge(gzipRoot(data));
			};
		} catch (RuntimeException broken) {
			throw new NotABuild("unreadable " + format.mod + " file: " + broken);
		}
	}

	/**
	 * Any of the three, written out of the common shape. {@code thumbnail} is the PNG an
	 * Axiom blueprint carries, null for a see-through pixel; the others have no picture.
	 */
	public static byte[] write(Structure structure, Format format, byte[] thumbnail, long now) throws IOException {
		return switch (format) {
			case AXIOM -> toBlueprint(structure, thumbnail == null ? BLANK_PNG : thumbnail);
			case LITEMATICA -> gzip(toLitematic(structure, now));
			case WORLDEDIT -> gzip(toSponge(structure, now));
		};
	}

	private static CompoundTag gzipRoot(byte[] data) throws IOException {
		return NbtIo.readCompressed(new ByteArrayInputStream(data), NbtAccounter.create(MOST_NBT));
	}

	private static byte[] gzip(CompoundTag root) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		NbtIo.writeCompressed(root, out);
		return out.toByteArray();
	}

	//#else
	//$$ public static Structure read(byte[] data) throws IOException {
	//$$ 	throw new NotABuild("builds are read from Minecraft 1.21.11 on");
	//$$ }
	//$$
	//$$ public static byte[] write(Structure structure, Format format, byte[] thumbnail, long now) throws IOException {
	//$$ 	throw new IOException("builds are written from Minecraft 1.21.11 on");
	//$$ }
	//$$
	//$$ private static CompoundTag gzipRoot(byte[] data) throws IOException {
	//$$ 	return NbtIo.readCompressed(new ByteArrayInputStream(data), NbtAccounter.create(MOST_NBT));
	//$$ }
	//#endif

	// ------------------------------------------------------------------ block states

	/** "minecraft:oak_stairs[facing=east]" into its name and its properties in name order. */
	static Map.Entry<String, TreeMap<String, String>> parseState(String state) {
		int open = state.indexOf('[');
		String name = withSpace(open < 0 ? state : state.substring(0, open));
		TreeMap<String, String> props = new TreeMap<>();
		if (open >= 0) {
			int close = state.lastIndexOf(']');
			String inside = state.substring(open + 1, close > open ? close : state.length());
			for (String pair : inside.split(",")) {
				int at = pair.indexOf('=');
				if (at > 0) props.put(pair.substring(0, at).trim(), pair.substring(at + 1).trim());
			}
		}
		return Map.entry(name, props);
	}

	/** The other way, properties in name order, as the game writes them. */
	static String stateText(String name, Map<String, String> props) {
		if (props.isEmpty()) return withSpace(name);
		StringBuilder out = new StringBuilder(withSpace(name)).append('[');
		boolean first = true;
		for (Map.Entry<String, String> one : new TreeMap<>(props).entrySet()) {
			if (!first) out.append(',');
			first = false;
			out.append(one.getKey()).append('=').append(one.getValue());
		}
		return out.append(']').toString();
	}

	private static String withSpace(String name) {
		String trimmed = name.trim();
		return trimmed.contains(":") ? trimmed : "minecraft:" + trimmed;
	}

	private static boolean isEmpty(String state) {
		return EMPTY.contains(parseState(state).getKey());
	}

	private static boolean[] emptyOf(List<String> palette) {
		boolean[] empty = new boolean[palette.size()];
		for (int i = 0; i < empty.length; i++) empty[i] = isEmpty(palette.get(i));
		return empty;
	}

	//#if MC>=12111
	/** A palette entry as Axiom and Litematica keep one: {@code {Name, Properties}}. */
	private static String stateFromTag(Tag tag) {
		if (!(tag instanceof CompoundTag compound)) return AIR;
		TreeMap<String, String> props = new TreeMap<>();
		CompoundTag properties = compound.getCompoundOrEmpty("Properties");
		for (String key : properties.keySet()) props.put(key, properties.getStringOr(key, ""));
		return stateText(compound.getStringOr("Name", AIR), props);
	}

	private static CompoundTag stateToTag(String state) {
		Map.Entry<String, TreeMap<String, String>> parsed = parseState(state);
		CompoundTag out = new CompoundTag();
		out.putString("Name", parsed.getKey());
		if (!parsed.getValue().isEmpty()) {
			CompoundTag properties = new CompoundTag();
			parsed.getValue().forEach(properties::putString);
			out.put("Properties", properties);
		}
		return out;
	}

	private static List<String> palette(ListTag list) {
		List<String> out = new ArrayList<>(list.size());
		for (Tag one : list) out.add(stateFromTag(one));
		return out;
	}

	// ------------------------------------------------------------------ the box

	/** A box being filled from places anywhere, its corner found as it goes. */
	private static final class Gatherer {
		final List<String> palette = new ArrayList<>(List.of(AIR));
		private final Map<String, Integer> index = new HashMap<>(Map.of(AIR, -1));
		private int[] places = new int[4 * 1024];
		private int used;
		private final int[] min = { Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE };
		private final int[] max = { Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE };
		final List<PlacedBlockEntity> blockEntities = new ArrayList<>();
		final List<PlacedEntity> entities = new ArrayList<>();

		void put(int x, int y, int z, String state) {
			Integer at = index.get(state);
			if (at == null) {
				// Air of any kind is not a block; known once, as -1, so it is not parsed again.
				at = isEmpty(state) ? -1 : palette.size();
				if (at >= 0) palette.add(state);
				index.put(state, at);
			}
			if (at < 0) return;
			if (used + 4 > places.length) places = Arrays.copyOf(places, places.length * 2);
			places[used++] = x;
			places[used++] = y;
			places[used++] = z;
			places[used++] = at;
			grow(x, y, z);
		}

		void grow(int x, int y, int z) {
			min[0] = Math.min(min[0], x);
			min[1] = Math.min(min[1], y);
			min[2] = Math.min(min[2], z);
			max[0] = Math.max(max[0], x);
			max[1] = Math.max(max[1], y);
			max[2] = Math.max(max[2], z);
		}

		/**
		 * The box: everything moved so its lowest corner is at nought. The corner is the
		 * blocks', so the same build in any format is the same box; with no block at all,
		 * the entities' instead.
		 */
		Structure done(String name, String author, List<String> tags, int dataVersion) throws NotABuild {
			if (min[0] == Integer.MAX_VALUE) {
				for (PlacedEntity one : entities) grow((int) Math.floor(one.x()), (int) Math.floor(one.y()), (int) Math.floor(one.z()));
			}
			boolean empty = min[0] == Integer.MAX_VALUE;
			int[] corner = empty ? new int[3] : min.clone();
			int w = empty ? 1 : max[0] - corner[0] + 1;
			int h = empty ? 1 : max[1] - corner[1] + 1;
			int l = empty ? 1 : max[2] - corner[2] + 1;
			if ((long) w * h * l > MOST_PLACES) throw new NotABuild("too big to turn into another format here: " + w + "×" + h + "×" + l);
			int[] blocks = new int[w * h * l];
			for (int i = 0; i < used; i += 4) {
				int x = places[i] - corner[0];
				int y = places[i + 1] - corner[1];
				int z = places[i + 2] - corner[2];
				if (x < 0 || y < 0 || z < 0 || x >= w || y >= h || z >= l) continue;
				blocks[x + z * w + y * w * l] = places[i + 3];
			}
			List<PlacedBlockEntity> movedBlockEntities = new ArrayList<>(blockEntities.size());
			for (PlacedBlockEntity one : blockEntities) {
				movedBlockEntities.add(new PlacedBlockEntity(one.x() - corner[0], one.y() - corner[1], one.z() - corner[2], one.id(), one.data()));
			}
			List<PlacedEntity> movedEntities = new ArrayList<>(entities.size());
			for (PlacedEntity one : entities) {
				movedEntities.add(new PlacedEntity(one.x() - corner[0], one.y() - corner[1], one.z() - corner[2], one.id(), one.data()));
			}
			return new Structure(name, author, List.copyOf(tags), dataVersion, w, h, l, List.copyOf(palette), blocks,
				movedBlockEntities, movedEntities);
		}
	}

	// ------------------------------------------------------------------ entities

	private static PlacedEntity entityFrom(Tag tag, double ox, double oy, double oz) {
		if (!(tag instanceof CompoundTag compound)) return null;
		ListTag pos = compound.getListOrEmpty("Pos");
		if (pos.size() != 3) return null;
		CompoundTag data = compound.copy();
		data.remove("Pos");
		data.remove("id");
		return new PlacedEntity(pos.getDoubleOr(0, 0) + ox, pos.getDoubleOr(1, 0) + oy, pos.getDoubleOr(2, 0) + oz,
			compound.getStringOr("id", ""), data);
	}

	private static CompoundTag entityTag(PlacedEntity entity) {
		CompoundTag out = entity.data().copy();
		if (!entity.id().isEmpty()) out.putString("id", entity.id());
		out.put("Pos", doubles(entity.x(), entity.y(), entity.z()));
		return out;
	}

	private static ListTag doubles(double... values) {
		ListTag out = new ListTag();
		for (double one : values) out.add(DoubleTag.valueOf(one));
		return out;
	}

	private static PlacedBlockEntity blockEntityFrom(CompoundTag tag, int x, int y, int z, String id, String... drop) {
		CompoundTag data = tag.copy();
		for (String name : drop) data.remove(name);
		return new PlacedBlockEntity(x, y, z, id, data);
	}

	/** {@code ceil(log2(n))} for n of at least one. */
	private static int bitsFor(int n) {
		return 32 - Integer.numberOfLeadingZeros(Math.max(1, n) - 1);
	}

	// ------------------------------------------------------------------ Axiom

	private static Structure fromBlueprint(byte[] data) throws IOException {
		ByteBuffer in = ByteBuffer.wrap(data);
		in.getInt();
		byte[] header = part(in, MOST_HEADER, "header");
		part(in, MOST_THUMBNAIL, "picture");
		byte[] contents = part(in, data.length, "contents");
		if (in.hasRemaining()) throw new NotABuild("there is more in the file after the blueprint");
		CompoundTag head = NbtIo.read(new java.io.DataInputStream(new ByteArrayInputStream(header)), NbtAccounter.create(MOST_HEADER * 4L));
		CompoundTag root = gzipRoot(contents);

		Gatherer box = new Gatherer();
		for (Tag one : root.getListOrEmpty("BlockRegion")) {
			if (!(one instanceof CompoundTag section)) continue;
			int sx = section.getIntOr("X", 0) * 16;
			int sy = section.getIntOr("Y", 0) * 16;
			int sz = section.getIntOr("Z", 0) * 16;
			CompoundTag states = section.getCompoundOrEmpty("BlockStates");
			List<String> palette = palette(states.getListOrEmpty("palette"));
			if (palette.isEmpty()) continue;
			long[] packed = states.getLongArray("data").orElse(null);
			int bits = Math.max(4, bitsFor(palette.size()));
			int per = 64 / bits;
			long mask = (1L << bits) - 1;
			for (int i = 0; i < 4096; i++) {
				int at = 0;
				if (palette.size() > 1 && packed != null) {
					int word = i / per;
					if (word >= packed.length) break;
					at = (int) ((packed[word] >>> ((i % per) * bits)) & mask);
				}
				box.put(sx + (i & 15), sy + (i >> 8), sz + ((i >> 4) & 15), at < palette.size() ? palette.get(at) : AIR);
			}
		}
		for (Tag one : root.getListOrEmpty("BlockEntities")) {
			if (!(one instanceof CompoundTag tag)) continue;
			box.blockEntities.add(blockEntityFrom(tag, tag.getIntOr("x", 0), tag.getIntOr("y", 0), tag.getIntOr("z", 0),
				tag.getStringOr("id", ""), "x", "y", "z", "id"));
		}
		for (Tag one : root.getListOrEmpty("Entities")) {
			PlacedEntity entity = entityFrom(one, 0, 0, 0);
			if (entity != null) box.entities.add(entity);
		}
		List<String> tags = new ArrayList<>();
		for (Tag one : head.getListOrEmpty("Tags")) one.asString().ifPresent(tags::add);
		return box.done(head.getStringOr("Name", "").trim(), head.getStringOr("Author", "").trim(), tags, root.getIntOr("DataVersion", 0));
	}

	private static byte[] part(ByteBuffer in, int most, String what) throws NotABuild {
		if (in.remaining() < 4) throw new NotABuild("the file ends before its " + what);
		int length = in.getInt();
		if (length < 0 || length > most || length > in.remaining()) throw new NotABuild("its " + what + " does not fit in the file");
		byte[] out = new byte[length];
		in.get(out);
		return out;
	}

	private static byte[] toBlueprint(Structure structure, byte[] thumbnail) throws IOException {
		int w = structure.width();
		int l = structure.length();
		boolean[] empty = emptyOf(structure.palette());
		// Every place of a section, -1 ("not placed") until filled; sections by their corner.
		Map<Long, int[]> sections = new java.util.LinkedHashMap<>();
		int count = 0;
		int[] blocks = structure.blocks();
		for (int i = 0; i < blocks.length; i++) {
			int at = blocks[i];
			if (empty[at]) continue;
			count++;
			int x = i % w;
			int z = (i / w) % l;
			int y = i / (w * l);
			long key = ((long) (x >> 4) << 42) | ((long) (y >> 4) << 21) | (z >> 4);
			int[] section = sections.computeIfAbsent(key, unused -> {
				int[] fresh = new int[4096];
				Arrays.fill(fresh, -1);
				return fresh;
			});
			section[((y & 15) * 16 + (z & 15)) * 16 + (x & 15)] = at;
		}
		ListTag regions = new ListTag();
		for (Map.Entry<Long, int[]> one : sections.entrySet()) {
			long key = one.getKey();
			int[] section = one.getValue();
			List<String> local = new ArrayList<>();
			Map<Integer, Integer> lookup = new HashMap<>();
			int[] indices = new int[4096];
			for (int i = 0; i < 4096; i++) {
				int at = section[i];
				Integer mine = lookup.get(at);
				if (mine == null) {
					mine = local.size();
					local.add(at < 0 ? VOID : structure.palette().get(at));
					lookup.put(at, mine);
				}
				indices[i] = mine;
			}
			CompoundTag states = new CompoundTag();
			ListTag palette = new ListTag();
			for (String state : local) palette.add(stateToTag(state));
			states.put("palette", palette);
			if (local.size() > 1) {
				int bits = Math.max(4, bitsFor(local.size()));
				int per = 64 / bits;
				long[] packed = new long[(4096 + per - 1) / per];
				for (int i = 0; i < 4096; i++) packed[i / per] |= ((long) indices[i]) << ((i % per) * bits);
				states.putLongArray("data", packed);
			}
			CompoundTag region = new CompoundTag();
			region.putInt("X", (int) (key >> 42));
			region.putInt("Y", (int) ((key >> 21) & 0x1FFFFF));
			region.putInt("Z", (int) (key & 0x1FFFFF));
			region.put("BlockStates", states);
			regions.add(region);
		}
		CompoundTag contents = new CompoundTag();
		contents.putInt("DataVersion", structure.dataVersion());
		contents.put("BlockRegion", regions);
		ListTag blockEntities = new ListTag();
		for (PlacedBlockEntity one : structure.blockEntities()) {
			CompoundTag tag = one.data().copy();
			tag.putInt("x", one.x());
			tag.putInt("y", one.y());
			tag.putInt("z", one.z());
			tag.putString("id", one.id());
			blockEntities.add(tag);
		}
		contents.put("BlockEntities", blockEntities);
		ListTag entities = new ListTag();
		for (PlacedEntity one : structure.entities()) entities.add(entityTag(one));
		contents.put("Entities", entities);

		CompoundTag header = new CompoundTag();
		header.putInt("Version", 2);
		header.putString("Name", structure.name());
		header.putString("Author", structure.author());
		ListTag tags = new ListTag();
		for (String tag : structure.tags()) tags.add(StringTag.valueOf(tag));
		header.put("Tags", tags);
		header.putFloat("ThumbnailYaw", 135);
		header.putFloat("ThumbnailPitch", 30);
		header.putByte("LockedThumbnail", (byte) 0);
		header.putInt("BlockCount", count);
		header.putByte("ContainsAir", (byte) 0);

		ByteArrayOutputStream headBytes = new ByteArrayOutputStream();
		NbtIo.write(header, new DataOutputStream(headBytes));
		byte[] packed = gzip(contents);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		DataOutputStream data = new DataOutputStream(out);
		data.writeInt(AXIOM_MAGIC);
		data.writeInt(headBytes.size());
		headBytes.writeTo(data);
		data.writeInt(thumbnail.length);
		data.write(thumbnail);
		data.writeInt(packed.length);
		data.write(packed);
		data.flush();
		return out.toByteArray();
	}

	// ------------------------------------------------------------------ Litematica

	/** Litematica's packing: entries of {@code bits} running on across the edge of one long into the next. */
	static int[] unpackLitematic(long[] packed, int bits, int count) {
		int[] out = new int[count];
		long mask = (1L << bits) - 1;
		for (int i = 0; i < count; i++) {
			long start = (long) i * bits;
			int word = (int) (start >>> 6);
			int offset = (int) (start & 63);
			long value = (word < packed.length ? packed[word] : 0) >>> offset;
			if (offset + bits > 64) value |= (word + 1 < packed.length ? packed[word + 1] : 0) << (64 - offset);
			out[i] = (int) (value & mask);
		}
		return out;
	}

	static long[] packLitematic(int[] indices, int bits) {
		long[] out = new long[(int) (((long) indices.length * bits + 63) / 64)];
		for (int i = 0; i < indices.length; i++) {
			long start = (long) i * bits;
			int word = (int) (start >>> 6);
			int offset = (int) (start & 63);
			long value = indices[i] & 0xFFFFFFFFL;
			out[word] |= value << offset;
			if (offset + bits > 64) out[word + 1] |= value >>> (64 - offset);
		}
		return out;
	}

	private static int litematicBits(int paletteSize) {
		return Math.max(2, bitsFor(Math.max(2, paletteSize)));
	}

	private static int[] vector(CompoundTag tag) {
		return new int[] { tag.getIntOr("x", 0), tag.getIntOr("y", 0), tag.getIntOr("z", 0) };
	}

	private static Structure fromLitematic(CompoundTag root) throws NotABuild {
		CompoundTag meta = root.getCompoundOrEmpty("Metadata");
		CompoundTag regions = root.getCompoundOrEmpty("Regions");
		Gatherer box = new Gatherer();
		for (String key : regions.keySet()) {
			CompoundTag region = regions.getCompound(key).orElse(null);
			if (region == null) continue;
			int[] position = vector(region.getCompoundOrEmpty("Position"));
			int[] size = vector(region.getCompoundOrEmpty("Size"));
			// A negative size counts back from the position: the region's own corner is the lower.
			int[] corner = new int[3];
			for (int axis = 0; axis < 3; axis++) corner[axis] = size[axis] < 0 ? position[axis] + size[axis] + 1 : position[axis];
			int w = Math.abs(size[0]);
			int h = Math.abs(size[1]);
			int l = Math.abs(size[2]);
			if ((long) w * h * l > MOST_PLACES) throw new NotABuild("a region too big to read here: " + w + "×" + h + "×" + l);
			List<String> palette = palette(region.getListOrEmpty("BlockStatePalette"));
			long[] packed = region.getLongArray("BlockStates").orElse(null);
			if (packed != null && !palette.isEmpty()) {
				int[] indices = unpackLitematic(packed, litematicBits(palette.size()), w * h * l);
				for (int i = 0; i < indices.length; i++) {
					int x = i % w;
					int z = (i / w) % l;
					int y = i / (w * l);
					box.put(corner[0] + x, corner[1] + y, corner[2] + z, indices[i] < palette.size() ? palette.get(indices[i]) : AIR);
				}
			}
			for (Tag one : region.getListOrEmpty("TileEntities")) {
				if (!(one instanceof CompoundTag tag)) continue;
				box.blockEntities.add(blockEntityFrom(tag, corner[0] + tag.getIntOr("x", 0), corner[1] + tag.getIntOr("y", 0),
					corner[2] + tag.getIntOr("z", 0), tag.getStringOr("id", ""), "x", "y", "z", "id"));
			}
			// Entities, unlike blocks, count from the region's position itself (Litematica's
			// own box.getPos1()), which with a negative size is not its lowest corner.
			for (Tag one : region.getListOrEmpty("Entities")) {
				PlacedEntity entity = entityFrom(one, position[0], position[1], position[2]);
				if (entity != null) box.entities.add(entity);
			}
		}
		return box.done(meta.getStringOr("Name", "").trim(), meta.getStringOr("Author", "").trim(), List.of(),
			root.getIntOr("MinecraftDataVersion", 0));
	}

	private static CompoundTag xyz(int x, int y, int z) {
		CompoundTag out = new CompoundTag();
		out.putInt("x", x);
		out.putInt("y", y);
		out.putInt("z", z);
		return out;
	}

	private static CompoundTag toLitematic(Structure structure, long now) {
		int w = structure.width();
		int h = structure.height();
		int l = structure.length();
		String name = structure.name().isEmpty() ? "Mosaicary" : structure.name();
		CompoundTag region = new CompoundTag();
		region.put("Position", xyz(0, 0, 0));
		region.put("Size", xyz(w, h, l));
		ListTag palette = new ListTag();
		for (String state : structure.palette()) palette.add(stateToTag(state));
		region.put("BlockStatePalette", palette);
		region.putLongArray("BlockStates", packLitematic(structure.blocks(), litematicBits(structure.palette().size())));
		ListTag tileEntities = new ListTag();
		for (PlacedBlockEntity one : structure.blockEntities()) {
			CompoundTag tag = one.data().copy();
			tag.putString("id", one.id());
			tag.putInt("x", one.x());
			tag.putInt("y", one.y());
			tag.putInt("z", one.z());
			tileEntities.add(tag);
		}
		region.put("TileEntities", tileEntities);
		ListTag entities = new ListTag();
		for (PlacedEntity one : structure.entities()) entities.add(entityTag(one));
		region.put("Entities", entities);
		region.put("PendingBlockTicks", new ListTag());
		region.put("PendingFluidTicks", new ListTag());

		CompoundTag meta = new CompoundTag();
		meta.putString("Name", name);
		meta.putString("Author", structure.author());
		meta.putString("Description", "");
		meta.putInt("RegionCount", 1);
		meta.putInt("TotalVolume", w * h * l);
		meta.putInt("TotalBlocks", structure.blockCount());
		meta.putLong("TimeCreated", now);
		meta.putLong("TimeModified", now);
		meta.put("EnclosingSize", xyz(w, h, l));

		CompoundTag regions = new CompoundTag();
		regions.put(name, region);
		CompoundTag root = new CompoundTag();
		root.putInt("MinecraftDataVersion", structure.dataVersion());
		root.putInt("Version", 7);
		root.putInt("SubVersion", 1);
		root.put("Metadata", meta);
		root.put("Regions", regions);
		return root;
	}

	// ------------------------------------------------------------------ Sponge

	static int[] readVarints(byte[] data, int count) throws NotABuild {
		int[] out = new int[count];
		int at = 0;
		for (int i = 0; i < count; i++) {
			int value = 0;
			int shift = 0;
			while (true) {
				if (at >= data.length) throw new NotABuild("the block data ends early");
				int next = data[at++] & 0xFF;
				value |= (next & 0x7F) << shift;
				if ((next & 0x80) == 0) break;
				shift += 7;
				if (shift > 28) throw new NotABuild("a block index too long");
			}
			out[i] = value;
		}
		return out;
	}

	static byte[] writeVarints(int[] values) {
		ByteArrayOutputStream out = new ByteArrayOutputStream(values.length);
		for (int one : values) {
			int value = one;
			while ((value & ~0x7F) != 0) {
				out.write((value & 0x7F) | 0x80);
				value >>>= 7;
			}
			out.write(value);
		}
		return out.toByteArray();
	}

	private static Structure fromSponge(CompoundTag root) throws NotABuild {
		CompoundTag inner = root.getCompound("Schematic").orElse(root);
		int version = inner.getIntOr("Version", 1);
		int w = inner.getIntOr("Width", 0) & 0xFFFF;
		int h = inner.getIntOr("Height", 0) & 0xFFFF;
		int l = inner.getIntOr("Length", 0) & 0xFFFF;
		if (w == 0 || h == 0 || l == 0) throw new NotABuild("a schematic with no size");
		if ((long) w * h * l > MOST_PLACES) throw new NotABuild("too big to read here: " + w + "×" + h + "×" + l);
		CompoundTag blocks = version >= 3 ? inner.getCompoundOrEmpty("Blocks") : inner;
		CompoundTag paletteTag = blocks.getCompoundOrEmpty("Palette");
		Map<Integer, String> palette = new HashMap<>();
		for (String state : paletteTag.keySet()) palette.put(paletteTag.getIntOr(state, 0), state);
		byte[] data = blocks.getByteArray(version >= 3 ? "Data" : "BlockData").orElse(new byte[0]);
		int[] indices = readVarints(data, w * h * l);
		Gatherer box = new Gatherer();
		for (int i = 0; i < indices.length; i++) {
			box.put(i % w, i / (w * l), (i / w) % l, palette.getOrDefault(indices[i], AIR));
		}
		ListTag blockEntities = blocks.getList("BlockEntities")
			.orElseGet(() -> inner.getListOrEmpty(version >= 2 ? "BlockEntities" : "TileEntities"));
		for (Tag one : blockEntities) {
			if (!(one instanceof CompoundTag tag)) continue;
			int[] pos = tag.getIntArray("Pos").orElse(null);
			if (pos == null || pos.length < 3) continue;
			// Version 3 keeps the block's own data under Data; version 2 beside Pos and Id.
			CompoundTag inside = tag.getCompound("Data").orElse(null);
			box.blockEntities.add(inside != null
				? new PlacedBlockEntity(pos[0], pos[1], pos[2], tag.getStringOr("Id", ""), inside.copy())
				: blockEntityFrom(tag, pos[0], pos[1], pos[2], tag.getStringOr("Id", ""), "Pos", "Id"));
		}
		for (Tag one : inner.getListOrEmpty("Entities")) {
			if (!(one instanceof CompoundTag tag)) continue;
			CompoundTag merged = tag.getCompound("Data").map(CompoundTag::copy).orElseGet(tag::copy);
			Tag pos = tag.get("Pos");
			if (pos != null) merged.put("Pos", pos.copy());
			merged.putString("id", tag.getStringOr("Id", merged.getStringOr("id", "")));
			PlacedEntity entity = entityFrom(merged, 0, 0, 0);
			if (entity != null) box.entities.add(entity);
		}
		CompoundTag meta = inner.getCompoundOrEmpty("Metadata");
		return box.done(meta.getStringOr("Name", "").trim(), meta.getStringOr("Author", "").trim(), List.of(),
			inner.getIntOr("DataVersion", 0));
	}

	private static CompoundTag toSponge(Structure structure, long now) {
		CompoundTag palette = new CompoundTag();
		for (int at = 0; at < structure.palette().size(); at++) palette.putInt(structure.palette().get(at), at);
		ListTag blockEntities = new ListTag();
		for (PlacedBlockEntity one : structure.blockEntities()) {
			CompoundTag tag = new CompoundTag();
			tag.put("Pos", new IntArrayTag(new int[] { one.x(), one.y(), one.z() }));
			tag.putString("Id", one.id());
			tag.put("Data", one.data().copy());
			blockEntities.add(tag);
		}
		CompoundTag blocks = new CompoundTag();
		blocks.put("Palette", palette);
		blocks.putByteArray("Data", writeVarints(structure.blocks()));
		blocks.put("BlockEntities", blockEntities);

		ListTag entities = new ListTag();
		for (PlacedEntity one : structure.entities()) {
			CompoundTag tag = new CompoundTag();
			tag.put("Pos", doubles(one.x(), one.y(), one.z()));
			tag.putString("Id", one.id());
			tag.put("Data", one.data().copy());
			entities.add(tag);
		}
		CompoundTag meta = new CompoundTag();
		meta.putString("Name", structure.name());
		meta.putString("Author", structure.author());
		meta.putLong("Date", now);

		CompoundTag inner = new CompoundTag();
		inner.putInt("Version", 3);
		inner.putInt("DataVersion", structure.dataVersion());
		inner.put("Metadata", meta);
		inner.putShort("Width", (short) structure.width());
		inner.putShort("Height", (short) structure.height());
		inner.putShort("Length", (short) structure.length());
		inner.put("Offset", new IntArrayTag(new int[3]));
		inner.put("Blocks", blocks);
		inner.put("Entities", entities);
		CompoundTag root = new CompoundTag();
		root.put("Schematic", inner);
		return root;
	}

	/** A 1×1 PNG, see-through, for a blueprint written with no picture of its own. */
	static final byte[] BLANK_PNG = java.util.Base64.getDecoder().decode(
		"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");
	//#endif
}

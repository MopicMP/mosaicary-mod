package com.mopicmp.mosaicary.client.skins;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mopicmp.mosaicary.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;

/**
 * Whether this game makes live eyes move: the Fresh Moves resource pack (or its continuation
 * Fresh Moves+), and the two mods it needs, Entity Model Features and Entity Texture Features.
 *
 * What is missing is told by name, so the wardrobe can say exactly that: one thing missing is
 * named with a link to it; a pack that is there but switched off is offered to be switched on.
 */
public final class FreshMoves {

	private FreshMoves() { }

	/** Something live eyes need: what it is called, where to get it, and its mod id (null for the pack). */
	public enum Part {
		PACK("Fresh Moves+", "https://modrinth.com/resourcepack/freshmoves", null),
		EMF("Entity Model Features", "https://modrinth.com/mod/entity-model-features", "entity_model_features"),
		ETF("Entity Texture Features", "https://modrinth.com/mod/entitytexturefeatures", "entity_texture_features");

		public final String title;
		public final String page;
		final String modId;

		Part(String title, String page, String modId) {
			this.title = title;
			this.page = page;
			this.modId = modId;
		}
	}

	/**
	 * How things stand.
	 *
	 * @param missing what is not installed at all
	 * @param packOff the pack's id when it is there but switched off, else null
	 */
	public record Status(List<Part> missing, String packOff) {
		public boolean ready() {
			return missing.isEmpty() && packOff == null;
		}
	}

	/** The pack among those the game knows: by its id, title or description, any spelling of "Fresh Move". */
	private static Pack pack(PackRepository repository) {
		for (Pack pack : repository.getAvailablePacks()) {
			String said = (pack.getId() + " " + pack.getTitle().getString() + " " + pack.getDescription().getString())
				.toLowerCase(Locale.ROOT).replace(" ", "");
			if (said.contains("freshmove")) return pack;
		}
		return null;
	}

	public static Status status() {
		List<Part> missing = new ArrayList<>();
		for (Part part : Part.values()) {
			if (part.modId != null && !Platform.isLoaded(part.modId)) missing.add(part);
		}
		PackRepository repository = Minecraft.getInstance().getResourcePackRepository();
		Pack pack = pack(repository);
		String off = null;
		if (pack == null) missing.add(0, Part.PACK);
		else if (!repository.getSelectedIds().contains(pack.getId())) off = pack.getId();
		return new Status(List.copyOf(missing), off);
	}

	/**
	 * Reads the resource pack folder again, so a pack put there while the game runs is seen; as
	 * the resource packs screen does when it opens.
	 */
	public static void rescan() {
		Minecraft.getInstance().getResourcePackRepository().reload();
	}

	/** Switches the pack on, as the resource packs screen does: saved to the options, resources reloaded. */
	public static void switchOn(String packId) {
		Minecraft minecraft = Minecraft.getInstance();
		PackRepository repository = minecraft.getResourcePackRepository();
		if (repository.addPack(packId)) minecraft.options.updateResourcePacks(repository);
	}
}

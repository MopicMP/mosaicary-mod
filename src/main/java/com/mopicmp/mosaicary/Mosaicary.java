package com.mopicmp.mosaicary;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mopicmp.mosaicary.net.MosaicaryPayloads;
import com.mopicmp.mosaicary.server.ServerWardrobes;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;

/**
 * The common half of the mod, loaded on clients and on servers.
 *
 * The optional server part: on a server with the mod, each player's wardrobe is kept in
 * the world's folder, and a skin changed in game is shown to everybody at once
 * ({@link ServerWardrobes}). The client never relies on this half being present on the
 * server it joins.
 */
public final class Mosaicary implements ModInitializer {

	public static final String ID = "mosaicary";
	public static final Logger LOGGER = LoggerFactory.getLogger("Mosaicary");

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(ID, path);
	}

	@Override
	public void onInitialize() {
		MosaicaryPayloads.register();
		ServerWardrobes.init();
	}
}

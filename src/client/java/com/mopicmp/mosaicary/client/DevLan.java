package com.mopicmp.mosaicary.client;

import com.mopicmp.mosaicary.Mosaicary;
import com.mopicmp.mosaicary.client.platform.ClientPlatform;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
//#if MC>=260100
import net.minecraft.server.MinecraftServer;
//#endif

/**
 * A development-only helper for trying the mod with two players on one machine: once a world
 * is open in single player, it is opened to the network on a fixed port, as Open to LAN does,
 * so a second client can be started straight into it with {@code --quickPlayMultiplayer}.
 *
 * Switched on by the system property {@code mosaicary.devLan}, the port. The development
 * clients have no Microsoft session, so the world does not ask Mojang who joins; anybody on the
 * machine can come in under any name.
 */
final class DevLan {

	private final int port;
	private boolean published;

	private DevLan(int port) {
		this.port = port;
	}

	static void start(int port) {
		DevLan lan = new DevLan(port);
		Mosaicary.LOGGER.info("Development LAN: the world will be opened on port {}", port);
		ClientPlatform.onTick(lan::tick);
	}

	private void tick(Minecraft minecraft) {
		IntegratedServer server = minecraft.getSingleplayerServer();
		if (server == null) {
			published = false;
			return;
		}
		if (published || minecraft.player == null || server.isPublished()) return;
		//#if MC>=260100
		published = server.publishServer(MinecraftServer.MultiplayerScope.LAN, false, port);
		//#else
		//$$ published = server.publishServer(server.getDefaultGameType(), false, port);
		//#endif
		server.setUsesAuthentication(false);
		Mosaicary.LOGGER.info("Development LAN: {} on port {}", published ? "the world is open" : "could not open the world", port);
	}
}

package com.mopicmp.mosaicary.mixin;

import java.util.List;
import java.util.Set;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.mopicmp.mosaicary.server.ServerWardrobes;

import net.minecraft.server.level.ServerPlayer;
//#if MC>=11700
import net.minecraft.server.network.ServerPlayerConnection;
//#endif

/**
 * Takes an entity out of every watcher's view and puts it straight back, which sends each
 * of them the entity anew: for a player, with the profile, and so the skin, it has now.
 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class TrackedEntityMixin implements ServerWardrobes.Retrack {

	// The watchers are their connections from 1.17 on, and before the players themselves.
	//#if MC>=11700
	@Shadow
	@Final
	private Set<ServerPlayerConnection> seenBy;
	//#else
	//$$ @Shadow
	//$$ @Final
	//$$ private Set<ServerPlayer> seenBy;
	//#endif

	@Shadow
	public abstract void removePlayer(ServerPlayer player);

	@Shadow
	public abstract void updatePlayer(ServerPlayer player);

	@Override
	public void mosaicary$showAgain() {
		//#if MC>=11700
		for (ServerPlayerConnection watcher : List.copyOf(seenBy)) {
			ServerPlayer player = watcher.getPlayer();
			removePlayer(player);
			updatePlayer(player);
		}
		//#else
		//$$ for (ServerPlayer player : List.copyOf(seenBy)) {
		//$$ 	removePlayer(player);
		//$$ 	updatePlayer(player);
		//$$ }
		//#endif
	}
}

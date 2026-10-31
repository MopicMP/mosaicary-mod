package com.mopicmp.mosaicary.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.mopicmp.mosaicary.server.ServerWardrobes;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.level.ChunkMap;

/** Hands the server part the entities the chunk map tracks, to show one of them again. */
@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin implements ServerWardrobes.TrackedEntities {

	@Shadow
	@Final
	private Int2ObjectMap<?> entityMap;

	@Override
	public Int2ObjectMap<?> mosaicary$entities() {
		return entityMap;
	}
}

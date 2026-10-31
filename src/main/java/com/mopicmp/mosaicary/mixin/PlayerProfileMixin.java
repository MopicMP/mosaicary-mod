package com.mopicmp.mosaicary.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

import com.mojang.authlib.GameProfile;
import com.mopicmp.mosaicary.server.ServerWardrobes;

import net.minecraft.world.entity.player.Player;

/**
 * Lets the server part put a new profile on a player: the profile carries the skin, and a
 * player keeps the one they joined with for good otherwise.
 */
@Mixin(Player.class)
public abstract class PlayerProfileMixin implements ServerWardrobes.ProfileHolder {

	@Shadow
	@Final
	@Mutable
	private GameProfile gameProfile;

	@Override
	public void mosaicary$setGameProfile(GameProfile profile) {
		this.gameProfile = profile;
	}
}

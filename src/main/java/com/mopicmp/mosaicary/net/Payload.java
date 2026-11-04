//#if MC<12005
//$$ package com.mopicmp.mosaicary.net;
//$$
//$$ import java.util.function.Function;
//$$
//$$ import net.minecraft.network.FriendlyByteBuf;
//$$ import net.minecraft.resources.ResourceLocation;
//$$
//$$ /**
//$$  * A packet of the mod before 1.20.5, where the game has no typed packets for mods: on the wire a
//$$  * packet is only a channel name and bytes, the same for every loader, so a Fabric server and a
//$$  * Forge client understand each other. Since 1.20.5 the game's CustomPacketPayload does this.
//$$  */
//$$ public interface Payload {
//$$
//$$ 	Type<?> type();
//$$
//$$ 	void write(FriendlyByteBuf buf);
//$$
//$$ 	/** A packet's channel and how to read one off the wire. */
//$$ 	record Type<T extends Payload>(ResourceLocation id, Function<FriendlyByteBuf, T> reader) { }
//$$ }
//#endif

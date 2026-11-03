//#if NEOFORGE>=1 && MC>=12111
//$$ package com.mopicmp.mosaicary.neoforge.mixin;
//$$
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//$$
//$$ import net.minecraft.network.PacketProcessor;
//$$ import net.minecraft.network.protocol.common.ServerCommonPacketListener;
//$$ import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
//$$ import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
//$$ import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
//$$ import net.neoforged.neoforge.network.payload.CommonRegisterPayload;
//$$ import net.neoforged.neoforge.network.registration.NetworkRegistry;
//$$
//$$ /**
//$$  * Takes a client's list of channels on the server's thread, as the game takes every other packet.
//$$  *
//$$  * A Fabric client answers with that list while its connection is set up, and NeoForge handles the
//$$  * answer on the network thread and goes on from there to the next steps of the set-up. In 1.21.11
//$$  * the next is finding the player's spawn, which loads chunks while the server's thread works on
//$$  * them too: the player is refused with "Unexpected error during configuration", or the server
//$$  * stops. NeoForge only asks for the list when some mod has channels a non-NeoForge client may
//$$  * use, as this one does, so without the mod the server does not meet it.
//$$  */
//$$ @Mixin(value = NetworkRegistry.class, remap = false)
//$$ public abstract class NetworkRegistryMixin {
//$$
//$$ 	@Inject(method = "onCommonRegister", at = @At("HEAD"), cancellable = true)
//$$ 	private static void mosaicary$onServerThread(ICommonPacketListener listener, CommonRegisterPayload payload, CallbackInfo info) {
//$$ 		PacketProcessor processor = listener.getPacketProcessor();
//$$ 		if (listener instanceof ServerConfigurationPacketListener configuration && !processor.isSameThread()) {
//$$ 			processor.scheduleIfPossible((ServerCommonPacketListener) configuration, new ServerboundCustomPayloadPacket(payload));
//$$ 			info.cancel();
//$$ 		}
//$$ 	}
//$$ }
//#endif

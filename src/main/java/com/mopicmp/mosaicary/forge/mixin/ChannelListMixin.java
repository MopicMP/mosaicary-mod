//#if FORGE>=1
//$$ package com.mopicmp.mosaicary.forge.mixin;
//$$
//$$ import java.nio.charset.StandardCharsets;
//$$ import java.util.Set;
//$$
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//$$
//$$ import net.minecraft.ResourceLocationException;
//$$ import net.minecraft.resources.ResourceLocation;
//#if MC>=11700
//$$ import net.minecraftforge.network.MCRegisterPacketHandler;
//#else
//$$ import java.util.concurrent.ConcurrentHashMap;
//$$ import java.util.function.Supplier;
//$$
//$$ import org.objectweb.asm.Opcodes;
//$$ import org.spongepowered.asm.mixin.Shadow;
//$$ import org.spongepowered.asm.mixin.Unique;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//$$
//$$ import com.mopicmp.mosaicary.platform.Platform;
//$$
//$$ import net.minecraft.network.FriendlyByteBuf;
//$$ import net.minecraftforge.fml.network.FMLMCRegisterPacketHandler;
//$$ import net.minecraftforge.fml.network.NetworkEvent;
//#endif
//$$
//$$ /**
//$$  * Reads the last channel of a {@code minecraft:register} packet too.
//$$  *
//$$  * The packet is channel names with a zero byte between them, and Fabric writes it so, with none
//$$  * after the last name; Forge takes a name only when a zero byte ends it, so the last channel a
//$$  * Fabric client names is lost, and with it, when it is the mod's greeting, the whole server part
//$$  * for that player. Here the name after the last zero byte is added as well. A packet that does end
//$$  * with a zero byte (Forge's own) has nothing after it, and is read as before.
//$$  *
//$$  * Before 1.17 it also keeps what the other side names. Forge's list there holds this side's own
//$$  * channels as well, and a channel counts as there only when the other side's Forge agreed on it,
//$$  * so to Forge a side with another loader has none of the mod's channels (Platform.isRemotePresent).
//$$  */
//#if MC>=11700
//$$ @Mixin(value = MCRegisterPacketHandler.ChannelList.class, remap = false)
//$$ public abstract class ChannelListMixin {
//#else
//$$ // Before 1.17 Forge's network lies in its fml package, the handler named after it.
//$$ @Mixin(value = FMLMCRegisterPacketHandler.ChannelList.class, remap = false)
//$$ public abstract class ChannelListMixin implements Platform.RemoteChannels {
//#endif
//$$
//$$ 	@Inject(method = "bytesToResLocation", at = @At("RETURN"))
//$$ 	private void mosaicary$lastName(byte[] all, CallbackInfoReturnable<Set<ResourceLocation>> info) {
//$$ 		int start = 0;
//$$ 		for (int i = 0; i < all.length; i++) if (all[i] == 0) start = i + 1;
//$$ 		if (start >= all.length) return;
//$$ 		try {
//$$ 			info.getReturnValue().add(new ResourceLocation(new String(all, start, all.length - start, StandardCharsets.UTF_8)));
//$$ 		} catch (ResourceLocationException ignored) {
//$$ 			// Forge skips a malformed name too.
//$$ 		}
//$$ 	}
//#if MC<11700
//$$
//$$ 	@Shadow
//$$ 	private Set<ResourceLocation> locations;
//$$
//$$ 	/** Written on the network thread, read on the game's. */
//$$ 	@Unique
//$$ 	private final Set<ResourceLocation> mosaicary$remote = ConcurrentHashMap.newKeySet();
//$$
//$$ 	/**
//$$ 	 * Only the other side's packets come here. The names are taken as soon as they are in
//$$ 	 * {@code locations}, since the mod's channels hear of the change before the method returns.
//$$ 	 */
//$$ 	@Inject(method = "updateFrom", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER,
//$$ 		target = "Lnet/minecraftforge/fml/network/FMLMCRegisterPacketHandler$ChannelList;locations:Ljava/util/Set;"))
//$$ 	private void mosaicary$remember(Supplier<NetworkEvent.Context> source, FriendlyByteBuf buffer,
//$$ 			NetworkEvent.RegistrationChangeType change, CallbackInfo info) {
//$$ 		if (change == NetworkEvent.RegistrationChangeType.REGISTER) mosaicary$remote.addAll(locations);
//$$ 		else mosaicary$remote.removeAll(locations);
//$$ 	}
//$$
//$$ 	@Override
//$$ 	public Set<ResourceLocation> mosaicary$remote() {
//$$ 		return mosaicary$remote;
//$$ 	}
//#endif
//$$ }
//#endif

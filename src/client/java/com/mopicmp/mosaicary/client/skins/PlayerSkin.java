//#if MC<12002
//$$ package com.mopicmp.mosaicary.client.skins;
//$$
//$$ import java.util.Map;
//$$ import java.util.UUID;
//$$ import java.util.function.Supplier;
//$$
//$$ import com.mojang.authlib.GameProfile;
//$$ import com.mojang.authlib.minecraft.MinecraftProfileTexture;
//$$
//$$ import net.minecraft.client.Minecraft;
//$$ import net.minecraft.client.resources.DefaultPlayerSkin;
//$$ import net.minecraft.resources.ResourceLocation;
//$$
//$$ /**
//$$  * The game's PlayerSkin of 1.20.2 to 1.21.8, for the versions before it, where a skin is a
//$$  * texture and a model name ("default" or "slim") asked for apart. The rest of the mod reads a
//$$  * skin as it does on 1.21.1; versions/1.21.1-1.20.1.txt points the game's name here.
//$$  */
//$$ public record PlayerSkin(ResourceLocation texture, String textureUrl, ResourceLocation capeTexture,
//$$ 		ResourceLocation elytraTexture, Model model, boolean secure) {
//$$
//$$ 	/** The arms a skin is drawn for, as the game's PlayerSkin.Model. */
//$$ 	public enum Model {
//$$ 		SLIM("slim"),
//$$ 		WIDE("default");
//$$
//$$ 		private final String id;
//$$
//$$ 		Model(String id) {
//$$ 			this.id = id;
//$$ 		}
//$$
//$$ 		/** The game's name for it, which PlayerInfo.getModelName and the renderers use. */
//$$ 		public String id() {
//$$ 			return id;
//$$ 		}
//$$
//$$ 		public static Model byName(String name) {
//$$ 			return "slim".equals(name) ? SLIM : WIDE;
//$$ 		}
//$$ 	}
//$$
//$$ 	/** One of the game's own skins, the one it gives this player's id. */
//$$ 	public static PlayerSkin ofDefault(UUID player) {
//$$ 		return new PlayerSkin(DefaultPlayerSkin.getDefaultSkin(player), null, null, null,
//$$ 			Model.byName(DefaultPlayerSkin.getSkinModelName(player)), true);
//$$ 	}
//$$
//$$ 	/**
//$$ 	 * A profile's skin as the game will draw it, read from the textures in the profile without
//$$ 	 * checking their signature, as SkinManager.lookupInsecure does in the later versions. The
//$$ 	 * texture is fetched in the background; its name can be drawn at once. On the render thread.
//$$ 	 */
//$$ 	public static Supplier<PlayerSkin> lookup(Minecraft minecraft, GameProfile profile) {
//$$ 		Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> textures =
//$$ 			minecraft.getSkinManager().getInsecureSkinInformation(profile);
//$$ 		MinecraftProfileTexture skin = textures.get(MinecraftProfileTexture.Type.SKIN);
//$$ 		PlayerSkin found = skin == null ? ofDefault(profile.getId())
//$$ 			: new PlayerSkin(minecraft.getSkinManager().registerTexture(skin, MinecraftProfileTexture.Type.SKIN),
//$$ 				skin.getUrl(), null, null, Model.byName(skin.getMetadata("model")), false);
//$$ 		return () -> found;
//$$ 	}
//$$
//$$ 	/** The account's profile with its textures, which the game fetches once and keeps. Not on the render thread. */
//$$ 	public static GameProfile accountProfile(Minecraft minecraft) {
//$$ 		GameProfile profile = new GameProfile(minecraft.getUser().getProfileId(), minecraft.getUser().getName());
//$$ 		profile.getProperties().putAll(minecraft.getProfileProperties());
//$$ 		return profile;
//$$ 	}
//$$ }
//#endif

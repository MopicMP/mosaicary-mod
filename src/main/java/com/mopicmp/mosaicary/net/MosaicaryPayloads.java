package com.mopicmp.mosaicary.net;

import java.util.UUID;

import com.mopicmp.mosaicary.Mosaicary;

import io.netty.buffer.ByteBuf;
import com.mopicmp.mosaicary.platform.Platform;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * What a client with the mod and a server with the mod say to each other. A server
 * without the mod never sees any of it, and a client never waits for it.
 *
 * Three messages. The server greets a client so that it knows the server keeps
 * wardrobes; the wardrobe travels both ways as the same JSON the site takes, so there is
 * one shape to keep in step, not two; and the client says when it has put a new skin on
 * its account, so the server can show it to everybody without the player joining again.
 */
public final class MosaicaryPayloads {

	/** What this side of the conversation understands; a server greets with it. */
	public static final int VERSION = 1;

	/** The longest wardrobe sent at once, in characters: 200 entries and their marks with room to spare. */
	public static final int WARDROBE_MOST = 160_000;

	private MosaicaryPayloads() { }

	/** Server to client, on joining: this server keeps wardrobes and shows changed skins at once. */
	public record Hello(int version) implements CustomPacketPayload {
		public static final Type<Hello> TYPE = new Type<>(Mosaicary.id("hello"));
		public static final StreamCodec<ByteBuf, Hello> CODEC = ByteBufCodecs.VAR_INT.map(Hello::new, Hello::version);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * Both ways: a wardrobe as a JSON array of entries. From the client, what it holds;
	 * from the server, the two brought together, which the client takes in.
	 */
	public record WardrobeState(String json) implements CustomPacketPayload {
		public static final Type<WardrobeState> TYPE = new Type<>(Mosaicary.id("wardrobe"));
		public static final StreamCodec<ByteBuf, WardrobeState> CODEC = ByteBufCodecs.stringUtf8(WARDROBE_MOST)
			.map(WardrobeState::new, WardrobeState::json);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * Client to server: a skin was just put on the account.
	 *
	 * @param account the Mojang account it was put on: on a server in offline mode the
	 *                player's id there is made up from the name, and Mojang knows only this one
	 * @param texture the new texture's hash, which tells the server when Mojang hands out the new skin
	 */
	public record SkinChanged(UUID account, String texture) implements CustomPacketPayload {
		public static final Type<SkinChanged> TYPE = new Type<>(Mosaicary.id("skin_changed"));
		public static final StreamCodec<ByteBuf, SkinChanged> CODEC = StreamCodec.composite(
			UUIDUtil.STREAM_CODEC, SkinChanged::account,
			ByteBufCodecs.stringUtf8(128), SkinChanged::texture,
			SkinChanged::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static void register() {
		Platform.toClient(Hello.TYPE, Hello.CODEC);
		Platform.toClient(WardrobeState.TYPE, WardrobeState.CODEC);
		Platform.toServer(WardrobeState.TYPE, WardrobeState.CODEC);
		Platform.toServer(SkinChanged.TYPE, SkinChanged.CODEC);
	}
}

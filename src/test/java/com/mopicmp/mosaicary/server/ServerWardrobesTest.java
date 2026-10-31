package com.mopicmp.mosaicary.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.mojang.authlib.properties.Property;

/** The session server's profile, read for the skin the server waits for. */
class ServerWardrobesTest {

	private static final String HASH = "3b60a1f6d562f52aaebbf1434f1de147933a3affe0e764fa49ea057536623cd3";

	private static String profile(String texture, boolean signed) {
		String textures = "{\"timestamp\":1,\"profileId\":\"0f2b3c9a7e3d4b8e9a1c2d3e4f5a6b7c\",\"profileName\":\"Someone\","
			+ "\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + texture + "\"}}}";
		String value = Base64.getEncoder().encodeToString(textures.getBytes(StandardCharsets.UTF_8));
		return "{\"id\":\"0f2b3c9a7e3d4b8e9a1c2d3e4f5a6b7c\",\"name\":\"Someone\",\"properties\":[{\"name\":\"textures\",\"value\":\""
			+ value + "\"" + (signed ? ",\"signature\":\"c2lnbmVk\"" : "") + "}]}";
	}

	@Test
	void theSignedPropertyIsTakenOnceItCarriesTheNewSkin() {
		Property property = ServerWardrobes.texturesWith(profile(HASH, true), HASH);
		assertEquals("textures", property.name());
		assertEquals("c2lnbmVk", property.signature());
	}

	@Test
	void theOldSkinOrAnUnsignedPropertyIsWaitedPast() {
		assertNull(ServerWardrobes.texturesWith(profile("a".repeat(64), true), HASH));
		assertNull(ServerWardrobes.texturesWith(profile(HASH, false), HASH));
		assertNull(ServerWardrobes.texturesWith("<html>", HASH));
	}
}

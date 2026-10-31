package com.mopicmp.mosaicary.client.skins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * Mojang's answers as the skin upload reads them, and the form it sends. Written from
 * Mojang's documented answers; what Mojang really says is only seen by a real account.
 */
class MojangSkinsTest {

	private static final String HASH = "3b60a1f6d562f52aaebbf1434f1de147933a3affe0e764fa49ea057536623cd3";

	@Test
	void theActiveSkinOfTheProfileIsTheOneOn() {
		String body = """
			{ "id": "0f2b3c9a7e3d4b8e9a1c2d3e4f5a6b7c", "name": "Someone",
			  "skins": [
			    { "id": "a", "state": "INACTIVE", "url": "http://textures.minecraft.net/texture/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "variant": "CLASSIC" },
			    { "id": "b", "state": "ACTIVE", "url": "http://textures.minecraft.net/texture/%s", "variant": "SLIM" }
			  ], "capes": [] }
			""".formatted(HASH);
		MojangSkins.Applied applied = MojangSkins.answer(200, body, "slim");
		assertEquals(HASH, applied.texture());
		assertEquals("slim", applied.arms());
	}

	@Test
	void refusalsBecomeCodes() {
		assertEquals(MojangSkins.Failure.SIGN_IN, refusal(401, "{}"));
		assertEquals(MojangSkins.Failure.TOO_MANY, refusal(429, "{}"));
		assertEquals(MojangSkins.Failure.REFUSED, refusal(400, "{\"errorMessage\":\"Could not validate image data\"}"));
		// What Mojang's firewall answers since October 2026 to a skin given by address.
		assertEquals(MojangSkins.Failure.BLOCKED, refusal(403, "<!DOCTYPE html><html>The request is blocked.</html>"));
		assertEquals(MojangSkins.Failure.ODD_ANSWER, refusal(500, "{}"));
		assertEquals(MojangSkins.Failure.ODD_ANSWER, refusal(200, "{\"skins\":[]}"));
		assertEquals(MojangSkins.Failure.ODD_ANSWER, refusal(200, "not json"));
	}

	private static MojangSkins.Failure refusal(int status, String body) {
		return assertThrows(MojangSkins.Refused.class, () -> MojangSkins.answer(status, body, "classic")).failure;
	}

	@Test
	void theFormCarriesTheVariantAndThePng() {
		byte[] png = { (byte) 0x89, 'P', 'N', 'G' };
		String form = new String(MojangSkins.multipart("XYZ", "slim", png), StandardCharsets.ISO_8859_1);
		assertTrue(form.startsWith("--XYZ\r\nContent-Disposition: form-data; name=\"variant\"\r\n\r\nslim\r\n--XYZ\r\n"));
		assertTrue(form.contains("name=\"file\"; filename=\"skin.png\"\r\nContent-Type: image/png\r\n\r\n\u0089PNG\r\n--XYZ--\r\n"));
	}

	@Test
	void theHashIsReadOnlyFromMojangsTextureAddresses() {
		assertEquals(HASH, MojangSkins.textureOf("http://textures.minecraft.net/texture/" + HASH));
		assertEquals(HASH, MojangSkins.textureOf("https://textures.minecraft.net/texture/" + HASH.toUpperCase()));
		assertNull(MojangSkins.textureOf("https://example.com/texture/" + HASH));
		assertNull(MojangSkins.textureOf("http://textures.minecraft.net/texture/../x"));
	}

	@Test
	void noTokenIsRefusedWithoutAsking() {
		try (MojangSkins mojang = new MojangSkins()) {
			Exception failed = assertThrows(Exception.class, () -> mojang.upload("", new byte[0], "classic").join());
			assertTrue(failed.getCause() instanceof MojangSkins.Refused refused && refused.failure == MojangSkins.Failure.SIGN_IN);
		}
	}
}

package com.mopicmp.mosaicary.client.wear;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;

import org.junit.jupiter.api.Test;

//#if MC>=11900
import net.minecraft.util.Signer;
//#endif

/** What the relay signs with the profile key, in the shape the site checks. */
class WearRelayTest {

	@Test
	void payloadIsFiveLinesInOrder() {
		byte[] bytes = WearRelay.payload("ab".repeat(20), "2026-10-17T10:00:00.000Z", "c2lnbmVk",
			"0f8fad5b-d9cb-469f-a165-70867728950e");
		String expected = "mosaicary-wear-v1\n" + "ab".repeat(20) + "\n2026-10-17T10:00:00.000Z\nc2lnbmVk\n"
			+ "0f8fad5b-d9cb-469f-a165-70867728950e";
		assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8), bytes);
	}

	@Test
	void theGamesSignerMakesWhatTheSiteVerifies() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		KeyPair pair = generator.generateKeyPair();
		byte[] payload = WearRelay.payload("0".repeat(40), "2026-10-17T10:00:00.000Z", "x",
			"0f8fad5b-d9cb-469f-a165-70867728950e");
		//#if MC>=11900
		byte[] signature = Signer.from(pair.getPrivate(), "SHA256withRSA").sign(payload);
		//#else
		//$$ // Before 1.19 the relay signs by itself, with the private key read out of Mojang's answer (PEM).
		//$$ String pem = "-----BEGIN RSA PRIVATE KEY-----\n"
		//$$ 	+ java.util.Base64.getMimeEncoder().encodeToString(pair.getPrivate().getEncoded()) + "\n-----END RSA PRIVATE KEY-----\n";
		//$$ java.security.PrivateKey read = java.security.KeyFactory.getInstance("RSA")
		//$$ 	.generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(WearRelay.pem(pem)));
		//$$ byte[] signature = WearRelay.signed(read, payload);
		//#endif

		Signature check = Signature.getInstance("SHA256withRSA");
		check.initVerify(pair.getPublic());
		check.update(payload);
		assertTrue(check.verify(signature));

		// Another UUID in the last line is another message.
		check.initVerify(pair.getPublic());
		check.update(WearRelay.payload("0".repeat(40), "2026-10-17T10:00:00.000Z", "x",
			"1f8fad5b-d9cb-469f-a165-70867728950e"));
		assertFalse(check.verify(signature));
	}
}

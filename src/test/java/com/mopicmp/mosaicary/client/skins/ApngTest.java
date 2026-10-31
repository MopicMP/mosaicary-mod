package com.mopicmp.mosaicary.client.skins;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.CRC32;

import org.junit.jupiter.api.Test;

class ApngTest {

	private static byte[] chunk(String name, byte[] body) {
		byte[] type = name.getBytes(StandardCharsets.ISO_8859_1);
		CRC32 crc = new CRC32();
		crc.update(type);
		crc.update(body);
		return ByteBuffer.allocate(12 + body.length).putInt(body.length).put(type).put(body).putInt((int) crc.getValue()).array();
	}

	private static byte[] control(int number, int w, int h, int x, int y, int top, int bottom) {
		return ByteBuffer.allocate(26).putInt(number).putInt(w).putInt(h).putInt(x).putInt(y).putShort((short) top).putShort((short) bottom)
			.put((byte) 0).put((byte) 0).array();
	}

	private static byte[] numbered(int number, byte[] data) {
		return ByteBuffer.allocate(4 + data.length).putInt(number).put(data).array();
	}

	private static byte[] file() {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.writeBytes(new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A });
		out.writeBytes(chunk("IHDR", ByteBuffer.allocate(13).putInt(256).putInt(256).put((byte) 8).put((byte) 6).array()));
		out.writeBytes(chunk("acTL", ByteBuffer.allocate(8).putInt(2).putInt(0).array()));
		out.writeBytes(chunk("fcTL", control(0, 256, 256, 0, 0, 500, 1000)));
		out.writeBytes(chunk("IDAT", new byte[] { 1, 2 }));
		out.writeBytes(chunk("IDAT", new byte[] { 3 }));
		out.writeBytes(chunk("fcTL", control(1, 10, 4, 120, 70, 33, 0)));
		out.writeBytes(chunk("fdAT", numbered(2, new byte[] { 4, 5, 6 })));
		out.writeBytes(chunk("IEND", new byte[0]));
		return out.toByteArray();
	}

	/** The chunk of a kind in a PNG, as its body. */
	private static byte[] bodyOf(byte[] png, String name) {
		ByteBuffer read = ByteBuffer.wrap(png);
		read.position(8);
		while (read.remaining() >= 12) {
			int length = read.getInt();
			byte[] type = new byte[4];
			read.get(type);
			byte[] body = new byte[length];
			read.get(body);
			read.getInt();
			if (new String(type, StandardCharsets.ISO_8859_1).equals(name)) return body;
		}
		return null;
	}

	@Test
	void framesComeOutInOrderWithTheirPlaceAndTime() {
		List<Apng.Frame> frames = Apng.frames(file());
		assertEquals(2, frames.size());
		assertEquals(0, frames.get(0).x());
		assertEquals(256, frames.get(0).width());
		assertEquals(500, frames.get(0).millis());
		assertEquals(120, frames.get(1).x());
		assertEquals(70, frames.get(1).y());
		assertEquals(10, frames.get(1).width());
		assertEquals(4, frames.get(1).height());
		// A bottom of zero means hundredths of a second.
		assertEquals(330, frames.get(1).millis());
	}

	@Test
	void eachFrameIsAPngOfItsOwnSize() {
		List<Apng.Frame> frames = Apng.frames(file());
		assertArrayEquals(new byte[] { 1, 2, 3 }, bodyOf(frames.get(0).png(), "IDAT"));
		assertArrayEquals(new byte[] { 4, 5, 6 }, bodyOf(frames.get(1).png(), "IDAT"));
		ByteBuffer header = ByteBuffer.wrap(bodyOf(frames.get(1).png(), "IHDR"));
		assertEquals(10, header.getInt());
		assertEquals(4, header.getInt());
	}

	@Test
	void anythingElseHasNoFrames() {
		assertTrue(Apng.frames(new byte[] { 1, 2, 3 }).isEmpty());
		assertTrue(Apng.frames(null).isEmpty());
	}
}

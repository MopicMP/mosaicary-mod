package com.mopicmp.mosaicary.client.skins;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/**
 * The frames of an animated PNG, each as a PNG of its own that any decoder reads. The site's
 * live renders are made of a whole first frame and small patches around the eyes.
 *
 * Only what those files use is kept: every frame is drawn over the last, in its place, replacing
 * what is there. A file that is not animated has one frame.
 */
public final class Apng {

	private Apng() { }

	private static final byte[] SIGNATURE = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A };

	/** One frame: where it goes, how long it stays in milliseconds, and its pixels as a PNG file. */
	public record Frame(int x, int y, int width, int height, int millis, byte[] png) { }

	/** The frames of a file in order; empty for anything that is not a PNG. */
	public static List<Frame> frames(byte[] file) {
		List<Frame> out = new ArrayList<>();
		if (file == null || file.length < 8) return out;
		for (int i = 0; i < 8; i++) if (file[i] != SIGNATURE[i]) return out;
		ByteBuffer read = ByteBuffer.wrap(file);
		read.position(8);
		byte[] header = null;
		List<byte[]> before = new ArrayList<>();
		ByteArrayOutputStream data = new ByteArrayOutputStream();
		int[] control = null;
		boolean seenData = false;
		while (read.remaining() >= 12) {
			int length = read.getInt();
			byte[] type = new byte[4];
			read.get(type);
			if (length < 0 || read.remaining() < length + 4) break;
			byte[] body = new byte[length];
			read.get(body);
			read.getInt();
			String name = new String(type, StandardCharsets.ISO_8859_1);
			switch (name) {
				case "IHDR" -> header = body;
				case "acTL" -> { }
				case "fcTL" -> {
					if (control != null && data.size() > 0) out.add(frame(control, header, before, data.toByteArray()));
					data.reset();
					ByteBuffer c = ByteBuffer.wrap(body);
					c.getInt();
					int width = c.getInt();
					int height = c.getInt();
					int x = c.getInt();
					int y = c.getInt();
					int top = c.getShort() & 0xFFFF;
					int bottom = c.getShort() & 0xFFFF;
					control = new int[] { x, y, width, height, top * 1000 / (bottom == 0 ? 100 : bottom) };
				}
				case "IDAT" -> {
					seenData = true;
					// Image data before any fcTL is a still picture the animation does not include.
					if (control != null) data.writeBytes(body);
				}
				case "fdAT" -> {
					if (length > 4) data.write(body, 4, length - 4);
				}
				case "IEND" -> { }
				default -> {
					if (!seenData) before.add(chunk(name, body));
				}
			}
		}
		if (control != null && data.size() > 0 && header != null) out.add(frame(control, header, before, data.toByteArray()));
		return out;
	}

	private static Frame frame(int[] control, byte[] header, List<byte[]> before, byte[] data) {
		byte[] own = header.clone();
		ByteBuffer.wrap(own).putInt(control[2]).putInt(control[3]);
		ByteArrayOutputStream png = new ByteArrayOutputStream();
		png.writeBytes(SIGNATURE);
		png.writeBytes(chunk("IHDR", own));
		for (byte[] chunk : before) png.writeBytes(chunk);
		png.writeBytes(chunk("IDAT", data));
		png.writeBytes(chunk("IEND", new byte[0]));
		return new Frame(control[0], control[1], control[2], control[3], control[4], png.toByteArray());
	}

	private static byte[] chunk(String name, byte[] body) {
		byte[] type = name.getBytes(StandardCharsets.ISO_8859_1);
		CRC32 crc = new CRC32();
		crc.update(type);
		crc.update(body);
		ByteBuffer out = ByteBuffer.allocate(12 + body.length);
		out.putInt(body.length).put(type).put(body).putInt((int) crc.getValue());
		return out.array();
	}
}

package com.mopicmp.mosaicary.client.site;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mopicmp.mosaicary.client.model.BbModel;
import com.mopicmp.mosaicary.client.model.BbParser;
import com.sun.net.httpserver.HttpServer;

/**
 * The site client's own logic: reading the list, caching, falling back to the disk.
 *
 * The local server here only stands in for the network, so these tests say
 * nothing about the real site's answers. {@link #liveSite} does, against
 * mosaicary.com itself, when run with {@code -PsiteLive}.
 */
class SiteClientTest {

	private static final String LIST = """
		[{"id":"AAAAAAAAAAAA","name":"First model","format":"free","use":"free","version":"5.0",
		  "boxUv":false,"cubes":5,"meshes":3,"bones":1,"animations":0,"textures":2,
		  "textureWidth":64,"textureHeight":64,"density":2,"bytes":1000,"tags":["one","two"],
		  "license":"cc-by","author":{"name":"Someone","url":"https://example.org/someone"},
		  "by":{"id":"x","handle":"publisher","face":null},"published":"2026-10-01T00:00:00.000Z",
		  "updated":"2026-10-02T00:00:00.000Z","downloads":3,"likes":4,"comments":0,
		  "preview":"/api/models/AAAAAAAAAAAA/preview?v=1","visibility":"public","hidden":false,
		  "adult":false,"background":"#273a49","look":{"spin":0.5,"tilt":-0.1},"pose":null},
		 {"id":"BBBBBBBBBBBB","name":"Second","format":"geckolib_model","use":"mods","license":"cc0",
		  "author":null,"by":{"handle":"other"},"tags":[],"preview":null,"look":null},
		 {"name":"no id, skipped"}]
		""";

	private static final byte[] FILE = "{\"meta\":{}}".getBytes(StandardCharsets.UTF_8);

	/**
	 * The site counts every fetch of a file as a download unless it says
	 * {@code for=view}; the preview path must always say it, the download path never.
	 */
	@Test
	void previewFetchesAreMarkedAsViews() {
		assertEquals("/api/models/AAAAAAAAAAAA/file?for=view", SiteClient.viewPath("AAAAAAAAAAAA"));
		assertEquals("/api/models/AAAAAAAAAAAA/file", SiteClient.countedPath("AAAAAAAAAAAA"));
	}

	@Test
	void listIsReadLeniently() {
		List<ModelCard> cards = SiteClient.cardsOf(LIST);
		assertEquals(2, cards.size());
		ModelCard first = cards.get(0);
		assertEquals("First model", first.name());
		assertEquals("Someone", first.authorName());
		assertEquals("https://example.org/someone", first.authorUrl());
		assertEquals("publisher", first.byHandle());
		assertEquals(List.of("one", "two"), first.tags());
		assertEquals(0x273A49, first.backgroundRgb(0));
		assertTrue(first.hasLook());
		assertEquals(0.5, first.spin(), 1e-9);
		ModelCard second = cards.get(1);
		assertEquals("", second.authorName());
		assertEquals("", second.preview());
		assertFalse(second.hasLook());
		assertEquals(0x101010, second.backgroundRgb(0x101010));
	}

	@Test
	void searchMatchesEveryWord() {
		ModelCard first = SiteClient.cardsOf(LIST).get(0);
		assertTrue(first.matches(""));
		assertTrue(first.matches("first"));
		assertTrue(first.matches("FIRST two"));
		assertTrue(first.matches("someone"));
		assertTrue(first.matches("publisher"));
		assertFalse(first.matches("first three"));
	}

	@Test
	void notAListIsRefused() {
		assertEquals(SiteClient.Failure.BAD_ANSWER,
			assertThrows(SiteClient.SiteException.class, () -> SiteClient.cardsOf("{\"no\":1}")).failure());
		assertEquals(SiteClient.Failure.BAD_ANSWER,
			assertThrows(SiteClient.SiteException.class, () -> SiteClient.cardsOf("<html>")).failure());
	}

	@Test
	void cachesAndFallsBackToTheDisk(@TempDir Path root) throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		AtomicInteger previews = new AtomicInteger();
		List<String> fileQueries = new java.util.concurrent.CopyOnWriteArrayList<>();
		byte[] picture = "RIFF....WEBPVP8 fake".getBytes(StandardCharsets.US_ASCII);
		server.createContext("/api/models", exchange -> {
			String path = exchange.getRequestURI().getPath();
			byte[] body;
			int status = 200;
			if (path.equals("/api/models")) {
				body = LIST.getBytes(StandardCharsets.UTF_8);
			} else if (path.endsWith("/preview")) {
				previews.incrementAndGet();
				body = picture;
			} else if (path.equals("/api/models/BBBBBBBBBBBB/file")) {
				String query = exchange.getRequestURI().getQuery();
				fileQueries.add(query == null ? "-" : query);
				body = FILE;
			} else {
				status = 404;
				body = "{\"no\":\"no_such_model\"}".getBytes(StandardCharsets.UTF_8);
			}
			exchange.sendResponseHeaders(status, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.start();
		String base = "http://127.0.0.1:" + server.getAddress().getPort();
		try (SiteClient client = new SiteClient(base, root, "Mosaicary-test")) {
			SiteClient.Listing listing = client.list().get(10, TimeUnit.SECONDS);
			assertFalse(listing.fromCache());
			assertEquals(2, listing.cards().size());
			assertTrue(Files.exists(root.resolve("cache/list.json")));

			ModelCard first = listing.cards().get(0);
			assertArrayEquals(picture, client.preview(first).get(10, TimeUnit.SECONDS));
			assertArrayEquals(picture, client.preview(first).get(10, TimeUnit.SECONDS));
			assertEquals(1, previews.get(), "the second preview comes from the disk");

			ExecutionException missing = assertThrows(ExecutionException.class,
				() -> client.file(first).get(10, TimeUnit.SECONDS));
			assertEquals(SiteClient.Failure.NOT_FOUND, SiteClient.unwrap(missing).failure());
			assertEquals(404, SiteClient.unwrap(missing).status());

			// Looking at a model is marked as a view; only the explicit download is not.
			ModelCard second = listing.cards().get(1);
			assertArrayEquals(FILE, client.file(second).get(10, TimeUnit.SECONDS));
			client.download(second).get(10, TimeUnit.SECONDS);
			assertEquals(List.of("for=view", "-"), fileQueries);
		} finally {
			server.stop(0);
		}

		// The site is gone: the list comes from the disk and says so.
		try (SiteClient client = new SiteClient(base, root, "Mosaicary-test")) {
			SiteClient.Listing listing = client.list().get(30, TimeUnit.SECONDS);
			assertTrue(listing.fromCache());
			assertEquals(SiteClient.Failure.OFFLINE, listing.whyCached());
			assertEquals(2, listing.cards().size());
		}
	}

	@Test
	void offlineWithNothingKeptFails(@TempDir Path root) {
		try (SiteClient client = new SiteClient("http://127.0.0.1:9", root, "Mosaicary-test")) {
			ExecutionException failed = assertThrows(ExecutionException.class,
				() -> client.list().get(30, TimeUnit.SECONDS));
			assertEquals(SiteClient.Failure.OFFLINE, SiteClient.unwrap(failed).failure());
		}
	}

	@Test
	void downloadNamesAreSafe(@TempDir Path root) {
		try (SiteClient client = new SiteClient("http://127.0.0.1:9", root, "Mosaicary-test")) {
			ModelCard card = SiteClient.cardsOf(LIST.replace("First model", "a/b:c*?")).get(0);
			Path target = client.downloadPath(card);
			assertEquals(root.resolve("models"), target.getParent());
			assertEquals("a_b_c__ (AAAAAAAAAAAA).bbmodel", target.getFileName().toString());
		}
	}

	@Test
	void settingsKeepTheSharingQuestionUnasked(@TempDir Path root) throws Exception {
		Path file = root.resolve("config/mosaicary.json");
		Settings fresh = Settings.load(file);
		assertEquals(Settings.NOT_ASKED, fresh.sharing);
		assertEquals("https://mosaicary.com", fresh.siteBase());
		assertTrue(Files.exists(file));
		fresh.sharing = Settings.YES;
		fresh.site = "http://localhost:8787/";
		fresh.save(file);
		Settings read = Settings.load(file);
		assertEquals(Settings.YES, read.sharing);
		assertEquals("http://localhost:8787", read.siteBase());

		Files.writeString(file, "{ broken");
		Settings recovered = Settings.load(file);
		assertEquals(Settings.NOT_ASKED, recovered.sharing);
		assertTrue(Files.exists(root.resolve("config/mosaicary.json.broken")));
	}

	/**
	 * Against the real site: read-only GETs of the list, the first card, its
	 * picture and its file. Run with {@code gradlew test -PsiteLive}.
	 */
	@Test
	void liveSite(@TempDir Path root) throws Exception {
		assumeTrue(Boolean.getBoolean("mosaicary.siteLive"), "live site check not asked for");
		try (SiteClient client = new SiteClient("https://mosaicary.com", root, "Mosaicary-test")) {
			SiteClient.Listing listing = client.list().get(60, TimeUnit.SECONDS);
			assertFalse(listing.fromCache());
			assertFalse(listing.cards().isEmpty(), "the site lists at least one model");
			for (ModelCard card : listing.cards()) assertFalse(card.adult(), "no adult models without signing in");
			ModelCard first = listing.cards().get(0);
			ModelCard detail = client.detail(first.id()).get(60, TimeUnit.SECONDS);
			assertEquals(first.id(), detail.id());

			byte[] picture = client.preview(first).get(60, TimeUnit.SECONDS);
			assertEquals("RIFF", new String(picture, 0, 4, StandardCharsets.US_ASCII));
			assertEquals("WEBP", new String(picture, 8, 4, StandardCharsets.US_ASCII));

			byte[] file = client.file(first).get(120, TimeUnit.SECONDS);
			BbModel model = BbParser.parse(file);
			assertEquals(first.cubes(), model.cubes());
			System.out.printf("live: %d models; first %s, picture %d bytes, file %d bytes, %d quads%n",
				listing.cards().size(), first.id(), picture.length, file.length, model.quadCount());
		}
	}
}

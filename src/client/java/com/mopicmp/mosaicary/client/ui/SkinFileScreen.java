package com.mopicmp.mosaicary.client.ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mopicmp.mosaicary.client.skins.SkinPixels;
import com.mopicmp.mosaicary.client.skins.Skins;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

/**
 * Choosing a skin file: the folders the player keeps things in as buttons, and the folder open
 * as a grid of its sub-folders and PNG files, each file drawn as the skin worn, the newest first.
 * A click on a skin takes it; a file dropped onto the window is taken too.
 *
 * The game has no file dialog of its own, and a system one would be a different program on every
 * system, some of them without any. Folders are read off the client thread, as one may be slow.
 */
public final class SkinFileScreen extends Screen {

	private static final int MARGIN = 6;
	private static final int GAP = 4;
	private static final int ROW = 22;
	private static final int CARD_TARGET = 84;
	private static final int MOST_ITEMS = 2000;

	/** The folder open last, opened again next time. */
	private static Path last;

	private final Screen parent;
	private final Skins skins;
	private final Consumer<Path> chosen;
	private Path folder;
	private List<Item> items;
	private boolean unreadable;
	private double scroll;
	private final Map<Path, Thumb> thumbs = new HashMap<>();
	private int thumbsMade;

	/** A sub-folder or a PNG file; {@code skin} when the file has a size the game takes. */
	private record Item(Path path, String name, boolean isFolder, boolean skin) { }

	/** A file's picture: null until it is read, or when it cannot be. */
	private record Thumb(Skins.Picture picture, boolean failed) { }

	public SkinFileScreen(Screen parent, Skins skins, Consumer<Path> chosen) {
		super(Component.translatable("mosaicary.files.title"));
		this.parent = parent;
		this.skins = skins;
		this.chosen = chosen;
		Map<Component, Path> places = places();
		Path start = last != null && Files.isDirectory(last) ? last : places.values().stream().findFirst().orElse(Path.of(System.getProperty("user.home", ".")));
		open(start);
	}

	/** Sets the folder to open next; for the game test. */
	public static void startAt(Path folder) {
		last = folder;
	}

	/**
	 * Where people keep things, those that exist: on Linux by the system's own list, whose names
	 * follow the system's language, otherwise by the usual names; then the drives, where there
	 * are several.
	 */
	private static Map<Component, Path> places() {
		Path home = Path.of(System.getProperty("user.home", "."));
		Map<String, Path> named = xdgFolders(home);
		Map<Component, Path> out = new LinkedHashMap<>();
		place(out, "mosaicary.files.downloads", named.getOrDefault("DOWNLOAD", home.resolve("Downloads")));
		Path desktop = named.getOrDefault("DESKTOP", home.resolve("Desktop"));
		place(out, "mosaicary.files.desktop", Files.isDirectory(desktop) ? desktop : home.resolve("OneDrive").resolve("Desktop"));
		Path pictures = named.getOrDefault("PICTURES", home.resolve("Pictures"));
		place(out, "mosaicary.files.pictures", Files.isDirectory(pictures) ? pictures : home.resolve("OneDrive").resolve("Pictures"));
		place(out, "mosaicary.files.home", home);
		File[] roots = File.listRoots();
		if (roots != null && roots.length > 1) {
			for (File root : roots) out.put(Component.literal(root.getPath()), root.toPath());
		}
		return out;
	}

	private static void place(Map<Component, Path> out, String key, Path where) {
		if (Files.isDirectory(where)) out.put(Component.translatable(key), where);
	}

	private static final Pattern XDG = Pattern.compile("^XDG_(\\w+)_DIR=\"(.*)\"\\s*$");

	/** The folders named in {@code ~/.config/user-dirs.dirs}, where there is such a file. */
	private static Map<String, Path> xdgFolders(Path home) {
		Map<String, Path> out = new HashMap<>();
		try {
			Path list = home.resolve(".config").resolve("user-dirs.dirs");
			if (!Files.isRegularFile(list)) return out;
			for (String line : Files.readAllLines(list)) {
				Matcher found = XDG.matcher(line);
				if (found.matches()) out.put(found.group(1), Path.of(found.group(2).replace("$HOME", home.toString())));
			}
		} catch (IOException | RuntimeException unreadable) {
			// The usual names are tried instead.
		}
		return out;
	}

	private void open(Path where) {
		folder = where;
		last = where;
		items = null;
		unreadable = false;
		scroll = 0;
		Path asked = where;
		CompletableFuture.supplyAsync(() -> list(asked), skins.site().work()).whenComplete((found, problem) ->
			net.minecraft.client.Minecraft.getInstance().execute(() -> {
				if (!asked.equals(folder)) return;
				unreadable = problem != null || found == null;
				items = found == null ? List.of() : found;
			}));
	}

	/** Sub-folders by name, then PNG files, the newest first; null when the folder cannot be read. */
	private static List<Item> list(Path where) {
		List<Item> folders = new ArrayList<>();
		List<Path> files = new ArrayList<>();
		try (var found = Files.list(where)) {
			for (Path one : (Iterable<Path>) found::iterator) {
				if (folders.size() + files.size() >= MOST_ITEMS) break;
				String name = one.getFileName().toString();
				if (name.startsWith(".")) continue;
				if (Files.isDirectory(one)) folders.add(new Item(one, name, true, false));
				else if (name.toLowerCase(Locale.ROOT).endsWith(".png")) files.add(one);
			}
		} catch (IOException | RuntimeException refused) {
			return null;
		}
		folders.sort(Comparator.comparing(item -> item.name().toLowerCase(Locale.ROOT)));
		files.sort(Comparator.comparingLong(SkinFileScreen::modified).reversed());
		List<Item> out = new ArrayList<>(folders);
		for (Path file : files) out.add(new Item(file, file.getFileName().toString(), false, takesSize(file)));
		return out;
	}

	private static long modified(Path file) {
		try {
			return Files.getLastModifiedTime(file).toMillis();
		} catch (IOException | RuntimeException unknown) {
			return 0;
		}
	}

	/** Whether a PNG says it is of a size the game takes, from its first bytes. */
	private static boolean takesSize(Path file) {
		try (var in = Files.newInputStream(file)) {
			int[] size = SkinPixels.pngSize(in.readNBytes(24));
			return size != null && size[0] == 64 && (size[1] == 64 || size[1] == 32);
		} catch (IOException | RuntimeException unreadable) {
			return false;
		}
	}

	@Override
	protected void init() {
		addRenderableWidget(new IconButton(MARGIN, MARGIN, IconButton.widthFor(Component.translatable("mosaicary.back.short")), ROW,
			Icon.BACK, Component.translatable("mosaicary.back.short"), Component.translatable("mosaicary.files.back"), this::onClose));

		// The second row: one folder up, and the places, with their names while they fit.
		int y = MARGIN + ROW + GAP;
		int x = MARGIN;
		Component up = Component.translatable("mosaicary.files.up");
		int upWidth = IconButton.widthFor(up);
		IconButton upButton = new IconButton(x, y, upWidth, ROW, Icon.FOLDER, up, Component.translatable("mosaicary.files.up.tooltip"), () -> {
			Path parentFolder = folder.getParent();
			if (parentFolder != null) open(parentFolder);
		});
		upButton.active = folder.getParent() != null;
		addRenderableWidget(upButton);
		x += upWidth + GAP * 2;
		Map<Component, Path> places = places();
		int needed = 0;
		for (Component name : places.keySet()) needed += IconButton.widthFor(name) + GAP;
		boolean named = x + needed <= width - MARGIN;
		for (Map.Entry<Component, Path> place : places.entrySet()) {
			Path where = place.getValue();
			int w = named ? IconButton.widthFor(place.getKey()) : ROW;
			if (x + w > width - MARGIN) break;
			addRenderableWidget(new IconButton(x, y, w, ROW, Icon.FOLDER, named ? place.getKey() : null, place.getKey(), () -> {
				open(where);
				rebuildWidgets();
			}).primary(where.equals(folder)));
			x += w + GAP;
		}
	}

	private record Grid(int x0, int y0, int x1, int y1, int columns, int cardWidth, int pictureHeight, int cardHeight) { }

	private Grid grid() {
		int x0 = MARGIN;
		int x1 = width - MARGIN;
		int available = x1 - x0 - 6;
		int columns = Math.max(1, (available + GAP) / (CARD_TARGET + GAP));
		int cardWidth = (available - (columns - 1) * GAP) / columns;
		int pictureHeight = Math.max(40, cardWidth - 2);
		return new Grid(x0, MARGIN + 2 * (ROW + GAP), x1, height - MARGIN - ROW - GAP, columns, cardWidth, pictureHeight, pictureHeight + 16);
	}

	private int contentHeight(Grid grid) {
		int rows = items == null ? 0 : (items.size() + grid.columns() - 1) / grid.columns();
		return rows == 0 ? 0 : rows * (grid.cardHeight() + GAP) - GAP;
	}

	private int[] cardPlace(Grid grid, int index) {
		int left = grid.x0() + (index % grid.columns()) * (grid.cardWidth() + GAP);
		int top = (int) (grid.y0() - scroll) + (index / grid.columns()) * (grid.cardHeight() + GAP);
		return new int[] { left, top };
	}

	private int itemAt(double mouseX, double mouseY) {
		Grid grid = grid();
		if (items == null || mouseY < grid.y0() || mouseY >= grid.y1()) return -1;
		for (int i = 0; i < items.size(); i++) {
			int[] at = cardPlace(grid, i);
			if (mouseX >= at[0] && mouseX < at[0] + grid.cardWidth() && mouseY >= at[1] && mouseY < at[1] + grid.cardHeight()) return i;
		}
		return -1;
	}

	/** The middle of an item's card, in GUI units, or null while the folder is being read; for the game test. */
	public int[] itemCenter(String name) {
		if (items == null) return null;
		Grid grid = grid();
		for (int i = 0; i < items.size(); i++) {
			if (items.get(i).name().equals(name)) {
				int[] at = cardPlace(grid, i);
				return new int[] { at[0] + grid.cardWidth() / 2, at[1] + grid.cardHeight() / 2 };
			}
		}
		return null;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		graphics.fill(0, 0, width, height, Theme.SCREEN);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		// The path, its end kept when it does not fit.
		int pathX0 = MARGIN + IconButton.widthFor(Component.translatable("mosaicary.back.short")) + GAP;
		int pathX1 = width - MARGIN;
		Theme.card(graphics, pathX0, MARGIN, pathX1, MARGIN + ROW);
		Icons.draw(graphics, Icon.FOLDER, pathX0 + 4, MARGIN + (ROW - Icons.SIZE) / 2, Theme.ACCENT);
		int room = pathX1 - pathX0 - Icons.SIZE - 14;
		String path = folder.toString();
		if (font.width(path) > room) path = "..." + font.plainSubstrByWidth(path, room - font.width("..."), true);
		graphics.text(font, path, pathX0 + Icons.SIZE + 8, MARGIN + (ROW - 8) / 2, Theme.TEXT, false);

		Grid grid = grid();
		if (items == null) {
			message(graphics, grid, Icon.LOADING, Theme.ACCENT, Component.translatable("mosaicary.files.reading"), null);
		} else if (items.isEmpty()) {
			message(graphics, grid, unreadable ? Icon.ERROR : Icon.FOLDER, unreadable ? Theme.WARN : Theme.TEXT_DIM,
				Component.translatable(unreadable ? "mosaicary.files.unreadable" : "mosaicary.files.empty"),
				Component.translatable(unreadable ? "mosaicary.files.unreadable.detail" : "mosaicary.files.empty.detail"));
		} else {
			drawItems(graphics, grid, mouseX, mouseY);
		}

		// What fits, at the bottom.
		int y = height - MARGIN - ROW;
		Theme.card(graphics, MARGIN, y, width - MARGIN, y + ROW);
		Icons.draw(graphics, Icon.INFO, MARGIN + 4, y + (ROW - Icons.SIZE) / 2, Theme.TEXT_DIM);
		String said = Component.translatable("mosaicary.files.hint").getString();
		int textRoom = width - 2 * MARGIN - Icons.SIZE - 14;
		if (font.width(said) > textRoom) said = font.plainSubstrByWidth(said, textRoom - font.width("...")) + "...";
		graphics.text(font, said, MARGIN + Icons.SIZE + 8, y + (ROW - 8) / 2, Theme.TEXT_DIM, false);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	private void drawItems(GuiGraphicsExtractor graphics, Grid grid, int mouseX, int mouseY) {
		int visible = grid.y1() - grid.y0();
		scroll = Math.max(0, Math.min(Math.max(0, contentHeight(grid) - visible), scroll));
		int hovered = itemAt(mouseX, mouseY);
		graphics.enableScissor(grid.x0(), grid.y0(), grid.x1(), grid.y1());
		for (int i = 0; i < items.size(); i++) {
			int[] at = cardPlace(grid, i);
			if (at[1] > grid.y1() || at[1] + grid.cardHeight() < grid.y0()) continue;
			drawItem(graphics, items.get(i), at[0], at[1], grid, i == hovered);
		}
		graphics.disableScissor();
		int content = contentHeight(grid);
		if (content > visible) {
			int barHeight = Math.max(16, visible * visible / content);
			int barTop = grid.y0() + (int) ((visible - barHeight) * scroll / (content - visible));
			graphics.fill(grid.x1() - 3, barTop, grid.x1(), barTop + barHeight, Theme.EDGE);
		}
		if (hovered >= 0) {
			Item item = items.get(hovered);
			Component tip = item.isFolder() || item.skin() ? Component.literal(item.name())
				: Component.translatable("mosaicary.skins.mine.refused.size", item.name());
			Theme.tooltip(graphics, font, tip, mouseX, mouseY);
		}
	}

	private void drawItem(GuiGraphicsExtractor graphics, Item item, int left, int top, Grid grid, boolean hovered) {
		int right = left + grid.cardWidth();
		boolean takes = item.isFolder() || item.skin();
		Theme.card(graphics, left, top, right, top + grid.cardHeight(), hovered && takes ? Theme.CARD_HOVER : Theme.CARD,
			hovered && takes ? Theme.ACCENT : Theme.EDGE);
		int pictureBottom = top + 1 + grid.pictureHeight();
		int middleX = left + (grid.cardWidth() - Icons.SIZE) / 2;
		int middleY = top + 1 + (grid.pictureHeight() - Icons.SIZE) / 2;
		if (item.isFolder()) {
			Icons.draw(graphics, Icon.FOLDER, middleX, middleY, Theme.TEXT_DIM);
		} else if (!item.skin()) {
			Icons.draw(graphics, Icon.ERROR, middleX, middleY, Theme.TEXT_DIM);
		} else {
			graphics.fill(left + 1, top + 1, right - 1, pictureBottom, 0xFF273A49);
			Thumb thumb = thumb(item.path());
			if (thumb.picture() != null) {
				int side = Math.min(grid.cardWidth() - 2, grid.pictureHeight());
				int x = left + 1 + (grid.cardWidth() - 2 - side) / 2;
				int y = top + 1 + (grid.pictureHeight() - side) / 2;
				Skins.Picture picture = thumb.picture();
				graphics.blit(RenderPipelines.GUI_TEXTURED, picture.texture(), x, y, 0, 0, side, side, picture.width(), picture.height(),
					picture.width(), picture.height());
			} else {
				Icons.draw(graphics, thumb.failed() ? Icon.ERROR : Icon.LOADING, middleX, middleY, 0xB0FFFFFF);
			}
		}
		String name = item.name();
		int room = grid.cardWidth() - 10;
		if (font.width(name) > room) name = font.plainSubstrByWidth(name, Math.max(0, room - font.width("..."))) + "...";
		graphics.text(font, name, left + 5, pictureBottom + 4, takes ? Theme.TEXT : Theme.TEXT_DIM, false);
	}

	/** A file's picture, read the first time it is drawn; released when the screen closes. */
	private Thumb thumb(Path file) {
		Thumb known = thumbs.get(file);
		if (known != null) return known;
		Thumb loading = new Thumb(null, false);
		thumbs.put(file, loading);
		int number = thumbsMade++;
		skins.figureOfFile(file, "picking-" + number).whenComplete((picture, problem) -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
			if (thumbs.get(file) != loading) {
				if (picture != null) minecraft.getTextureManager().release(picture.texture());
				return;
			}
			thumbs.put(file, picture == null ? new Thumb(null, true) : new Thumb(picture, false));
		}));
		return loading;
	}

	private void message(GuiGraphicsExtractor graphics, Grid grid, Icon icon, int tint, Component title, Component detail) {
		int boxWidth = Math.min(grid.x1() - grid.x0() - 20, 280);
		var lines = detail == null ? List.<net.minecraft.util.FormattedCharSequence>of() : font.split(detail, boxWidth - 16);
		int boxHeight = 16 + Icons.SIZE + 6 + 10 + lines.size() * 10 + 12;
		int x0 = (grid.x0() + grid.x1() - boxWidth) / 2;
		int y0 = (grid.y0() + grid.y1() - boxHeight) / 2;
		Theme.card(graphics, x0, y0, x0 + boxWidth, y0 + boxHeight);
		Icons.draw(graphics, icon, x0 + (boxWidth - Icons.SIZE) / 2, y0 + 10, tint);
		graphics.centeredText(font, title, x0 + boxWidth / 2, y0 + 10 + Icons.SIZE + 6, Theme.TEXT);
		int y = y0 + 10 + Icons.SIZE + 6 + 12;
		for (var line : lines) {
			graphics.centeredText(font, line, x0 + boxWidth / 2, y, Theme.TEXT_DIM);
			y += 10;
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		int index = itemAt(event.x(), event.y());
		if (index < 0) return false;
		Item item = items.get(index);
		if (item.isFolder()) {
			open(item.path());
			rebuildWidgets();
			return true;
		}
		if (item.skin()) take(item.path());
		return true;
	}

	private void take(Path file) {
		minecraft.gui.setScreen(parent);
		chosen.accept(file);
	}

	@Override
	public void onFilesDrop(List<Path> files) {
		if (!files.isEmpty()) take(files.get(0));
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		scroll -= scrollY * 24;
		return true;
	}

	@Override
	public void removed() {
		for (Thumb thumb : thumbs.values()) {
			if (thumb.picture() != null) minecraft.getTextureManager().release(thumb.picture().texture());
		}
		thumbs.clear();
		super.removed();
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}

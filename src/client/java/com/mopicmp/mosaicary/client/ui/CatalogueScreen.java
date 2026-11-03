package com.mopicmp.mosaicary.client.ui;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mojang.blaze3d.Blaze3D;
import com.mojang.blaze3d.platform.InputConstants;
import com.mopicmp.mosaicary.client.Catalogue;
import com.mopicmp.mosaicary.client.MosaicaryClient;
import com.mopicmp.mosaicary.client.build.Builds;
import com.mopicmp.mosaicary.client.model.Animation;
import com.mopicmp.mosaicary.client.model.BbModel;
import com.mopicmp.mosaicary.client.model.Posing;
import com.mopicmp.mosaicary.client.model.Roles;
import com.mopicmp.mosaicary.client.mixin.GuiGraphicsExtractorAccessor;
import com.mopicmp.mosaicary.client.render.PreviewState;
import com.mopicmp.mosaicary.client.site.ModelCard;
import com.mopicmp.mosaicary.client.site.Settings;
import com.mopicmp.mosaicary.client.site.SiteClient;
import com.mopicmp.mosaicary.client.wear.Wearing;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

/**
 * The model catalogue: a grid of cards, and a detail view with the model in 3D.
 *
 * One screen with two views rather than two screens, so that going back from a
 * model keeps the search, the filters and the scroll position exactly as they were.
 *
 * Layout, top to bottom: a row of controls (search, three dropdown filters,
 * refresh, close), the grid or the detail view, and a bottom row that is always
 * there with the status line, the link to the site and the plugin button.
 */
public final class CatalogueScreen extends Screen {

	/** Where the Blockbench plugin is published; Blockbench reloads it from here on every start. */
	public static final String PLUGIN_URL = "https://raw.githubusercontent.com/MopicMP/mosaicary/main/plugin/gltf_to_minecraft.js";

	private static final int MARGIN = 6;
	private static final int GAP = 4;
	private static final int ROW = 22;
	private static final int CARD_TARGET = 104;

	private static final Component TITLE = Component.translatable("mosaicary.catalogue.title");

	/**
	 * Set by the screenshot run: the screen is drawn as if the mouse were far away,
	 * so no hover state or tooltip lands in a screenshot.
	 */
	public static boolean hideMouse;

	private final Screen parent;
	private final Catalogue catalogue;

	// What the grid shows, kept across rebuilds of the widgets.
	private String query = "";
	private String use = "all";
	private String licence = "any";
	private String sort = "newest";
	private double scroll;

	private ModelCard open;
	private float spin;
	private float tilt;
	private float zoom = 1;
	private boolean turning;

	private EditBox search;
	private final List<Dropdown<?>> dropdowns = new ArrayList<>();
	private IconButton downloadButton;
	private Component notice;
	private long noticeUntil;

	public CatalogueScreen(Screen parent, Catalogue catalogue) {
		super(TITLE);
		this.parent = parent;
		this.catalogue = catalogue;
	}

	// ------------------------------------------------------------------ layout

	private int topRowY() {
		return MARGIN;
	}

	private int bottomRowY() {
		return height - MARGIN - ROW;
	}

	private int bodyTop() {
		return topRowY() + ROW + GAP;
	}

	private int bodyBottom() {
		return bottomRowY() - GAP;
	}

	@Override
	protected void init() {
		catalogue.ensureList();
		dropdowns.clear();
		search = null;
		downloadButton = null;
		downloadShownOnce = false;
		playButton = null;
		animationBox = null;
		wearButton = null;
		bindButton = null;
		shownWorn = null;

		int right = width - MARGIN;
		addRenderableWidget(IconButton.square(right - ROW, topRowY(), ROW, Icon.CLOSE,
			Component.translatable("mosaicary.close"), this::onClose));
		right -= ROW + GAP;

		if (open == null) initGrid(right);
		else initDetail(right);

		initBottom();
	}

	private void initGrid(int right) {
		settingsButton = IconButton.square(right - ROW, topRowY(), ROW, Icon.SETTINGS,
			Component.translatable("mosaicary.settings.title"), () -> minecraft.gui.setScreen(new SettingsScreen(this)));
		addRenderableWidget(settingsButton);
		right -= ROW + GAP;
		addRenderableWidget(IconButton.square(right - ROW, topRowY(), ROW, Icon.REFRESH,
			Component.translatable("mosaicary.refresh"), () -> {
				if (localMode) catalogue.refreshLocal();
				else catalogue.refresh();
				scroll = 0;
			}));
		right -= ROW + GAP;
		// The player's own Figura avatars, when the game folder has Figura's folder.
		if (catalogue.hasFiguraFolder()) {
			figuraButton = IconButton.square(right - ROW, topRowY(), ROW, Icon.FIGURA,
				Component.translatable(localMode ? "mosaicary.figura.back" : "mosaicary.figura.open"), this::toggleLocal)
				.primary(localMode);
			addRenderableWidget(figuraButton);
			right -= ROW + GAP;
		} else {
			figuraButton = null;
			localMode = false;
		}
		// Builds have a shelf of their own, as on the site: they are placed, not worn, and
		// mixed in with the models they would bury them.
		buildsButton = IconButton.square(right - ROW, topRowY(), ROW, Icon.BUILDS,
			Component.translatable(buildsMode ? "mosaicary.builds.back" : "mosaicary.builds.open"), this::toggleBuilds)
			.primary(buildsMode && !localMode);
		addRenderableWidget(buildsButton);
		right -= ROW + GAP;

		// The filters are the site's: the player's own avatars have no use, licence or counts.
		int beforeFilters = right;
		List<Dropdown.Option<String>> uses = new ArrayList<>();
		uses.add(new Dropdown.Option<>("all", Component.translatable("mosaicary.use.all")));
		for (String one : ModelCard.USES) uses.add(new Dropdown.Option<>(one, Component.translatable("mosaicary.use." + one)));
		List<Dropdown.Option<String>> licences = new ArrayList<>();
		licences.add(new Dropdown.Option<>("any", Component.translatable("mosaicary.licence.any")));
		for (String one : ModelCard.LICENSES) licences.add(new Dropdown.Option<>(one, Component.translatable("mosaicary.licence." + one)));
		List<Dropdown.Option<String>> sorts = new ArrayList<>();
		for (String one : List.of("newest", "liked", "downloaded", "name")) {
			sorts.add(new Dropdown.Option<>(one, Component.translatable("mosaicary.sort." + one)));
		}

		// Full labels when they fit beside a search field of a useful width, icons
		// and chevrons only when they do not.
		int wide = Dropdown.widthFor(uses) + Dropdown.widthFor(licences) + Dropdown.widthFor(sorts) + 3 * GAP;
		boolean roomy = right - MARGIN - wide >= 160;
		int[] widths = roomy
			? new int[] { Dropdown.widthFor(sorts), Dropdown.widthFor(licences), Dropdown.widthFor(uses) }
			: new int[] { Dropdown.narrowWidth(), Dropdown.narrowWidth(), Dropdown.narrowWidth() };

		right -= widths[0];
		Dropdown<String> sortBox = new Dropdown<>(right, topRowY(), widths[0], ROW, Icon.SORT,
			Component.translatable("mosaicary.filter.sort"), sorts, sort, value -> {
				sort = value;
				scroll = 0;
			});
		right -= GAP + widths[1];
		Dropdown<String> licenceBox = new Dropdown<>(right, topRowY(), widths[1], ROW, Icon.LICENCE,
			Component.translatable("mosaicary.filter.licence"), licences, licence, value -> {
				licence = value;
				scroll = 0;
			});
		right -= GAP + widths[2];
		Dropdown<String> useBox = new Dropdown<>(right, topRowY(), widths[2], ROW, Icon.USE,
			Component.translatable("mosaicary.filter.use"), uses, use, value -> {
				use = value;
				scroll = 0;
			});
		right -= GAP;
		if (localMode) {
			right = beforeFilters;
		} else {
			if (buildsMode) right += widths[2] + GAP;
			for (Dropdown<String> box : buildsMode ? List.of(licenceBox, sortBox) : List.of(useBox, licenceBox, sortBox)) {
				dropdowns.add(box);
				addRenderableWidget(box);
			}
		}

		int searchLeft = MARGIN + Icons.SIZE + 8;
		search = new EditBox(font, searchLeft, topRowY() + (ROW - 9) / 2 + 1, Math.max(20, right - searchLeft - 4), 10,
			Component.translatable("mosaicary.search.hint"));
		search.setBordered(false);
		search.setMaxLength(80);
		// The full hint when it fits, else the short one: the field does not clip it.
		Component hint = Component.translatable("mosaicary.search.hint");
		if (font.width(hint) > search.getWidth() - 4) hint = Component.translatable("mosaicary.search.short");
		search.setHint(hint.copy().withColor(Theme.TEXT_OFF));
		search.setTextColor(Theme.TEXT);
		search.setValue(query);
		search.setResponder(value -> {
			query = value;
			scroll = 0;
		});
		searchRight = right;
		// Not focused on opening: the game hides the hint of a focused field, and the
		// hint is what says what the field searches.
		addRenderableWidget(search);
	}

	private int searchRight;

	// ------------------------------------------------------------------ the player's own Figura avatars

	/** Whether the grid shows the avatars in the player's Figura folder instead of the site's list. */
	private static boolean localMode;
	private IconButton figuraButton;

	private void toggleLocal() {
		localMode = !localMode;
		if (localMode) {
			catalogue.refreshLocal();
			buildsMode = false;
		}
		scroll = 0;
		rebuildWidgets();
	}

	// ------------------------------------------------------------------ the shelf of builds

	/** Whether the grid shows builds (Axiom, Litematica, WorldEdit) instead of models. */
	private static boolean buildsMode;
	private IconButton buildsButton;

	private void toggleBuilds() {
		buildsMode = !buildsMode;
		if (buildsMode) localMode = false;
		scroll = 0;
		rebuildWidgets();
	}

	/** The builds button; for the game test. */
	public IconButton buildsButton() {
		return buildsButton;
	}

	/** The site's cards on the shelf being looked at, before search and filters. */
	private List<ModelCard> shelf() {
		List<ModelCard> out = new ArrayList<>();
		for (ModelCard card : catalogue.cards()) if (card.isBuild() == buildsMode) out.add(card);
		return out;
	}

	/** The Figura button; for the game test. */
	public IconButton figuraButton() {
		return figuraButton;
	}

	private void initDetail(int right) {
		addRenderableWidget(new IconButton(MARGIN, topRowY(), IconButton.widthFor(Component.translatable("mosaicary.back.short")),
			ROW, Icon.BACK, Component.translatable("mosaicary.back.short"), Component.translatable("mosaicary.back"),
			this::closeModel));

		Detail layout = detailLayout();
		// The reset button sits on the preview panel, in its corner, as a card of its own.
		addRenderableWidget(IconButton.square(layout.previewX1 - ROW - 3, layout.previewY0 + 3, ROW, Icon.RESET_VIEW,
			Component.translatable("mosaicary.detail.reset"), this::resetView));
		addRenderableWidget(IconButton.square(layout.previewX0 + 3, layout.previewY0 + 3, ROW, Icon.INFO,
			Component.translatable("mosaicary.detail.hint"), () -> { }));
		initAnimations(layout);
		initTabs(layout);

		// The actions along the bottom of the info card. Wear comes first and is the
		// main action; when the model is worn, the same button takes it off.
		int y = layout.infoY1 - 6 - ROW;
		int x = layout.infoX0 + 6;
		int available = layout.infoX1 - 6 - x;
		Component wearLabel = Component.translatable("mosaicary.action.wear");
		Component offLabel = Component.translatable("mosaicary.action.take_off");
		Component downloadLabel = Component.translatable("mosaicary.action.download");
		boolean local = open.isLocal();
		if (open.isBuild()) {
			// A build is installed, not worn: the one action puts it in the folder of every
			// mod here that places builds.
			Component installLabel = Component.translatable("mosaicary.action.install");
			int installWidth = IconButton.widthFor(installLabel) + ROW + GAP <= available ? IconButton.widthFor(installLabel) : ROW;
			downloadButton = new IconButton(x, y, installWidth, ROW, Icon.DOWNLOAD, installWidth > ROW ? installLabel : null,
				installTooltip(), this::download).primary(true);
			downloadButton.active = !catalogue.site().placers().isEmpty();
			addRenderableWidget(downloadButton);
			x += installWidth + GAP;
			addRenderableWidget(IconButton.square(x, y, ROW, Icon.OPEN_SITE, Component.translatable("mosaicary.action.site.tooltip"),
				() -> openUri(catalogue.site().pageOf(open.id()))));
			wearButton = null;
			bindButton = null;
			return;
		}
		int wearWidth = Math.max(IconButton.widthFor(wearLabel), IconButton.widthFor(offLabel));
		boolean wearLabelled = wearWidth + 3 * (ROW + GAP) <= available;
		if (!wearLabelled) wearWidth = ROW;
		boolean downloadLabelled = wearLabelled && wearWidth + IconButton.widthFor(downloadLabel) + 2 * ROW + 3 * GAP <= available;
		int downloadWidth = downloadLabelled ? IconButton.widthFor(downloadLabel) : ROW;

		wearButton = new IconButton(x, y, wearWidth, ROW, Icon.WEAR, wearLabelled ? wearLabel : null,
			Component.translatable("mosaicary.action.wear.tooltip"), this::wearOrTakeOff).primary(true);
		wearLabels = wearLabelled;
		addRenderableWidget(wearButton);
		x += wearWidth + GAP;
		// An avatar from the player's own folder is already theirs and is not on the site.
		if (!local) {
			downloadButton = new IconButton(x, y, downloadWidth, ROW, Icon.DOWNLOAD, downloadLabelled ? downloadLabel : null,
				Component.translatable("mosaicary.action.download.tooltip"), this::download);
			addRenderableWidget(downloadButton);
			x += downloadWidth + GAP;
			addRenderableWidget(IconButton.square(x, y, ROW, Icon.OPEN_SITE, Component.translatable("mosaicary.action.site.tooltip"),
				() -> openUri(catalogue.site().pageOf(open.id()))));
			x += ROW + GAP;
		}
		bindButton = IconButton.square(x, y, ROW, Icon.BIND, Component.translatable("mosaicary.action.bind.tooltip"), this::bind);
		addRenderableWidget(bindButton);
		refreshWearButton();
	}

	// ------------------------------------------------------------------ animations in the preview

	/** The animation shown in the preview: an index into the model's list, or -1 for the rest pose. */
	private int animation = -2;
	private boolean playing = true;
	/** Seconds into the animation when it was last paused, or the start offset while playing. */
	private double animationTime;
	private long playStarted = System.nanoTime();
	private ModelCard animationsFor;

	/** The dropdown of the model's animations and the play/pause button, once the model has loaded. */
	private void initAnimations(Detail layout) {
		Catalogue.Model model = catalogue.model(open);
		if (model.state() != Catalogue.State.READY) return;
		List<Animation> clips = model.mesh().model().animations();
		if (clips.isEmpty()) return;
		if (animationsFor != open) {
			animationsFor = open;
			// The first looping animation, as a model is most often seen moving in it.
			animation = -1;
			for (int i = 0; i < clips.size(); i++) {
				if (clips.get(i).loop() == Animation.Loop.LOOP) {
					animation = i;
					break;
				}
			}
			if (animation < 0) animation = 0;
			playing = true;
			animationTime = 0;
			playStarted = System.nanoTime();
		}
		List<Dropdown.Option<Integer>> options = new ArrayList<>();
		options.add(new Dropdown.Option<>(-1, Component.translatable("mosaicary.animation.rest")));
		for (int i = 0; i < clips.size(); i++) {
			String name = clips.get(i).name().isBlank() ? Integer.toString(i + 1) : clips.get(i).name();
			options.add(new Dropdown.Option<>(i, Component.literal(name)));
		}
		int y = layout.previewY1 - ROW - 3;
		int x = layout.previewX0 + 3;
		playButton = IconButton.square(x, y, ROW, playing ? Icon.PAUSE : Icon.PLAY,
			Component.translatable(playing ? "mosaicary.animation.pause" : "mosaicary.animation.play"), this::togglePlay);
		addRenderableWidget(playButton);
		int width = Math.min(Dropdown.widthFor(options), layout.previewX1 - layout.previewX0 - ROW - 3 * GAP - ROW - 6);
		Dropdown<Integer> box = new Dropdown<>(x + ROW + GAP, y, Math.max(Dropdown.narrowWidth(), width), ROW, Icon.ANIMATIONS,
			Component.translatable("mosaicary.animation.choose"), options, animation, this::chooseAnimation);
		animationBox = box;
		addRenderableWidget(box);
	}

	private IconButton playButton;
	private Dropdown<Integer> animationBox;

	private void chooseAnimation(int index) {
		animation = index;
		animationTime = 0;
		playStarted = System.nanoTime();
	}

	private void togglePlay() {
		if (playing) animationTime = currentTime();
		else playStarted = System.nanoTime();
		playing = !playing;
		if (playButton != null) {
			playButton.icon(() -> playing ? Icon.PAUSE : Icon.PLAY);
			playButton.tooltip(Component.translatable(playing ? "mosaicary.animation.pause" : "mosaicary.animation.play"));
		}
	}

	private double currentTime() {
		return playing ? animationTime + (System.nanoTime() - playStarted) / 1e9 : animationTime;
	}

	/**
	 * Shows one moment of an animation, paused; for the screenshot run, which compares
	 * frames with the site's card pictures.
	 */
	public boolean showFrame(String name, double seconds) {
		Catalogue.Model model = open == null ? null : catalogue.model(open);
		if (model == null || model.state() != Catalogue.State.READY) return false;
		List<Animation> clips = model.mesh().model().animations();
		for (int i = 0; i < clips.size(); i++) {
			if (clips.get(i).name().equalsIgnoreCase(name)) {
				animation = i;
				animationsFor = open;
				playing = false;
				animationTime = seconds;
				rebuildWidgets();
				return true;
			}
		}
		if (name.equalsIgnoreCase("rest")) {
			animation = -1;
			animationsFor = open;
			playing = false;
			rebuildWidgets();
			return true;
		}
		return false;
	}

	/** The frame of the chosen animation now, or null at rest. */
	private Posing previewPosing(BbModel model) {
		if (animation < 0 || animation >= model.animations().size()) return null;
		Animation clip = model.animations().get(animation);
		double seconds = playing ? clip.timeInPreview(currentTime()) : Math.min(animationTime, clip.length());
		return new Posing.Frame().add(clip.sample(seconds, model.startingValues(), null));
	}

	// ------------------------------------------------------------------ the roles tab

	private static final int TAB = 20;
	private static final int TAB_INFO = 0;
	private static final int TAB_ROLES = 1;
	private static final int ROLE_ROW = 18;
	/** The size row and the group row above the role rows. */
	private static final int ROLE_HEAD = 2 * ROLE_ROW + 4;
	/** Where the role rows' icons start, after the fold button. */
	private static final int FOLD = ROLE_ROW;

	/** The sizes offered, over the model's own size. */
	private static final double[] SIZES = { 0.1, 0.25, 0.5, 0.75, 1, 1.25, 1.5, 2, 3, 5, 10 };

	private int infoTab = TAB_INFO;
	private int rolesScroll;
	/** The group of roles shown; kept between models, as a player goes through one group for several. */
	private static Roles.Section rolesSection = Roles.Section.MOVEMENT;
	/** Body actions whose arm layers are shown under them. */
	private final java.util.Set<Roles.Action> unfolded = java.util.EnumSet.noneOf(Roles.Action.class);
	private IconButton infoTabButton;
	private IconButton rolesTabButton;
	private Dropdown<Double> sizeBox;
	private Dropdown<Roles.Section> sectionBox;
	private final Map<String, Dropdown<String>> roleBoxes = new java.util.LinkedHashMap<>();
	private final Map<String, RoleRow> shownRows = new java.util.LinkedHashMap<>();

	/**
	 * One row of the roles list: a role, and for an arm layer, which arm.
	 *
	 * @param action the action, or for an arm layer the body action it lies over
	 * @param side   the arm of an arm layer, else null
	 */
	private record RoleRow(String key, Roles.Action action, Roles.Side side) { }

	private static Icon roleIcon(Roles.Action action) {
		return switch (action) {
			case IDLE -> Icon.OWN_SKIN;
			case WALK -> Icon.ROLE_WALK;
			case WALK_BACK, CROUCH_BACK, JUMP_BACK, CRAWL_BACK -> Icon.ROLE_BACK;
			case SPRINT -> Icon.ROLE_SPRINT;
			case CROUCH -> Icon.ROLE_CROUCH;
			case CROUCH_WALK -> Icon.ROLE_CROUCH_WALK;
			case JUMP -> Icon.ROLE_JUMP;
			case SWIM -> Icon.ROLE_SWIM;
			case CRAWL, CRAWL_WALK -> Icon.ROLE_CRAWL;
			case ELYTRA -> Icon.ROLE_ELYTRA;
			case SPIN -> Icon.ROLE_SPIN;
			case SLEEP -> Icon.ROLE_SLEEP;
			case RIDE -> Icon.ROLE_RIDE;
			case HOLD_LEFT, HOLD_RIGHT -> Icon.ROLE_HOLD;
			case CHARGE_LEFT, CHARGE_RIGHT -> Icon.ROLE_CHARGE;
			case THRUST_LEFT, THRUST_RIGHT -> Icon.ROLE_THRUST;
			case USE_SELF_LEFT, USE_SELF_RIGHT -> Icon.ROLE_USE_SELF;
			case SWING -> Icon.ROLE_SWING;
			case HURT -> Icon.ROLE_HURT;
			case DEATH -> Icon.ROLE_DEATH;
		};
	}

	private static Icon sectionIcon(Roles.Section section) {
		return switch (section) {
			case MOVEMENT -> Icon.ROLE_WALK;
			case ITEMS -> Icon.ROLE_HOLD;
			case EVENTS -> Icon.ROLE_HURT;
		};
	}

	/** The rows of the group shown: its roles, each body action followed by its arm layers when unfolded. */
	private List<RoleRow> roleList() {
		List<RoleRow> out = new ArrayList<>();
		for (Roles.Action action : Roles.Action.values()) {
			if (action.section != rolesSection) continue;
			out.add(new RoleRow(action.key, action, null));
			if (action.section == Roles.Section.MOVEMENT && unfolded.contains(action)) {
				for (Roles.Side side : Roles.Side.values()) out.add(new RoleRow(Roles.layerKey(action, side), action, side));
			}
		}
		return out;
	}

	private Component rowName(RoleRow row) {
		if (row.side() != null) return Component.translatable("mosaicary.role.layer." + row.side().key);
		return Component.translatable("mosaicary.role." + row.key());
	}

	private Component rowTooltip(RoleRow row) {
		if (row.side() != null) {
			return Component.translatable("mosaicary.role.layer." + row.side().key + ".tooltip",
				Component.translatable("mosaicary.role." + row.action().key));
		}
		return Component.translatable("mosaicary.role." + row.key() + ".tooltip");
	}

	/** How many role rows fit between the size and group rows and the action buttons. */
	private int roleRows(Detail layout) {
		int top = layout.infoY0 + 6 + TAB + 6 + ROLE_HEAD;
		int bottom = layout.infoY1 - 6 - ROW - 4;
		return Math.max(1, (bottom - top) / ROLE_ROW);
	}

	/**
	 * The two tabs of the info card, Info and Animations, as a row of two icon
	 * buttons with the open one lit. On the Animations tab: the size, the group of
	 * roles, and a dropdown per role of the group with the model's animations, the
	 * game's own motion and nothing. Body actions fold their two arm layers away
	 * behind a small button, lit when a layer has an animation.
	 */
	private void initTabs(Detail layout) {
		roleBoxes.clear();
		shownRows.clear();
		sizeBox = null;
		sectionBox = null;
		int x = layout.infoX0 + 6;
		int y = layout.infoY0 + 6;
		Component info = Component.translatable("mosaicary.tab.info");
		Component roles = Component.translatable("mosaicary.tab.roles");
		int half = (layout.infoX1 - layout.infoX0 - 12 - GAP) / 2;
		infoTabButton = new IconButton(x, y, half, TAB, Icon.INFO, info, Component.translatable("mosaicary.tab.info.tooltip"),
			() -> switchTab(TAB_INFO)).primary(infoTab == TAB_INFO);
		rolesTabButton = new IconButton(x + half + GAP, y, half, TAB, Icon.ANIMATIONS, roles,
			Component.translatable("mosaicary.tab.roles.tooltip"), () -> switchTab(TAB_ROLES)).primary(infoTab == TAB_ROLES);
		addRenderableWidget(infoTabButton);
		addRenderableWidget(rolesTabButton);
		if (infoTab != TAB_ROLES) return;

		Catalogue.Model model = catalogue.model(open);
		if (model.state() != Catalogue.State.READY) return;
		BbModel parsed = model.mesh().model();
		Wearing wearing = MosaicaryClient.instance().wearing();
		int width = layout.infoX1 - 6 - x;
		int top = layout.infoY0 + 6 + TAB + 6;

		// The size, with the height it gives in blocks.
		float[] box = model.mesh().bounds();
		double ownBlocks = box == null ? 0 : (box[4] - box[1]) / 16.0;
		double scale = wearing.scaleFor(open);
		List<Dropdown.Option<Double>> sizes = new ArrayList<>();
		boolean listed = false;
		for (double size : SIZES) {
			sizes.add(new Dropdown.Option<>(size, sizeLabel(size, ownBlocks)));
			listed |= size == scale;
		}
		if (!listed) sizes.add(new Dropdown.Option<>(scale, sizeLabel(scale, ownBlocks)));
		sizes.sort(java.util.Comparator.comparingDouble(option -> option.value()));
		sizeBox = new Dropdown<>(x, top, width, ROLE_ROW - 2, Icon.SIZE, Component.translatable("mosaicary.size"), sizes, scale,
			value -> wearing.setScale(open, value));
		dropdowns.add(sizeBox);
		addRenderableWidget(sizeBox);

		// The group of roles.
		List<Dropdown.Option<Roles.Section>> sections = new ArrayList<>();
		for (Roles.Section section : Roles.Section.values()) {
			sections.add(new Dropdown.Option<>(section, Component.translatable("mosaicary.roles.section." + section.name().toLowerCase(Locale.ROOT))));
		}
		sectionBox = new Dropdown<>(x, top + ROLE_ROW, width, ROLE_ROW - 2, sectionIcon(rolesSection),
			Component.translatable("mosaicary.roles.section"), sections, rolesSection, value -> {
				rolesSection = value;
				rolesScroll = 0;
				rebuildWidgets();
			});
		dropdowns.add(sectionBox);
		addRenderableWidget(sectionBox);

		Roles current = wearing.rolesFor(open, parsed);
		List<Dropdown.Option<String>> options = new ArrayList<>();
		options.add(new Dropdown.Option<>(Roles.VANILLA, Component.translatable("mosaicary.role.vanilla")));
		options.add(new Dropdown.Option<>(Roles.NOTHING, Component.translatable("mosaicary.role.nothing")));
		List<Dropdown.Option<String>> layerOptions = new ArrayList<>();
		layerOptions.add(new Dropdown.Option<>(Roles.NOTHING, Component.translatable("mosaicary.role.layer.none")));
		for (Animation clip : parsed.animations()) {
			if (clip.name().isBlank()) continue;
			options.add(new Dropdown.Option<>(clip.name(), Component.literal(clip.name())));
			layerOptions.add(new Dropdown.Option<>(clip.name(), Component.literal(clip.name())));
		}

		List<RoleRow> list = roleList();
		int rows = roleRows(layout);
		rolesScroll = Math.max(0, Math.min(rolesScroll, list.size() - rows));
		int rowsTop = top + ROLE_HEAD;
		int labelWidth = 0;
		for (RoleRow row : list) labelWidth = Math.max(labelWidth, font.width(rowName(row)) + (row.side() != null ? 8 : 0));
		int labelLeft = x + FOLD + Icons.SIZE + 4;
		int boxLeft = labelLeft + Math.min(labelWidth + 6, (layout.infoX1 - labelLeft) / 2);
		int boxWidth = layout.infoX1 - 6 - boxLeft;
		for (int i = 0; i < rows && rolesScroll + i < list.size(); i++) {
			RoleRow row = list.get(rolesScroll + i);
			int rowY = rowsTop + i * ROLE_ROW;
			boolean layer = row.side() != null;
			Dropdown<String> choice = new Dropdown<>(boxLeft, rowY, boxWidth, ROLE_ROW - 2,
				layer ? Icon.ROLE_HOLD : roleIcon(row.action()), rowName(row), layer ? layerOptions : options,
				current.get(row.key()), value -> chooseRole(row.key(), value));
			roleBoxes.put(row.key(), choice);
			shownRows.put(row.key(), row);
			dropdowns.add(choice);
			addRenderableWidget(choice);
			if (!layer && row.action().section == Roles.Section.MOVEMENT) {
				boolean folded = !unfolded.contains(row.action());
				boolean anyLayer = false;
				for (Roles.Side side : Roles.Side.values()) anyLayer |= !Roles.NOTHING.equals(current.layer(row.action(), side));
				Roles.Action action = row.action();
				addRenderableWidget(IconButton.square(x, rowY, ROLE_ROW - 2, folded ? Icon.FOLD : Icon.EXPAND,
					Component.translatable(folded ? "mosaicary.role.fold.open" : "mosaicary.role.fold.close"), () -> {
						if (!unfolded.remove(action)) unfolded.add(action);
						rebuildWidgets();
					}).primary(anyLayer));
			}
		}
	}

	private static Component sizeLabel(double size, double ownBlocks) {
		String times = BigDecimalText.of(size);
		String blocks = String.format(Locale.ROOT, "%.1f", ownBlocks * size);
		return Component.translatable("mosaicary.size.option", times, blocks);
	}

	/** Numbers as people write them: no trailing zeros. */
	private static final class BigDecimalText {
		static String of(double value) {
			return new java.math.BigDecimal(Double.toString(value)).stripTrailingZeros().toPlainString();
		}
	}

	private void switchTab(int tab) {
		if (infoTab == tab) return;
		infoTab = tab;
		rebuildWidgets();
	}

	private void chooseRole(String key, String value) {
		Catalogue.Model model = catalogue.model(open);
		if (model.state() != Catalogue.State.READY) return;
		BbModel parsed = model.mesh().model();
		MosaicaryClient.instance().wearing().setRole(open, parsed, key, value);
		// The preview shows the animation just chosen, so the choice can be seen.
		List<Animation> clips = parsed.animations();
		for (int i = 0; i < clips.size(); i++) {
			if (clips.get(i).name().equals(value)) {
				chooseAnimation(i);
				if (animationBox != null) animationBox.select(i);
				break;
			}
		}
		// The fold button lights when a layer has an animation.
		if (Roles.isLayer(key)) rebuildWidgets();
	}

	/** The role rows' labels: the role's icon and name to the left of its dropdown, arm layers indented. */
	private void drawRoles(GuiGraphicsExtractor graphics, Detail layout, ModelCard card, int mouseX, int mouseY) {
		Theme.card(graphics, layout.infoX0, layout.infoY0, layout.infoX1, layout.infoY1);
		int x = layout.infoX0 + 6;
		Catalogue.Model model = catalogue.model(card);
		if (model.state() != Catalogue.State.READY) {
			graphics.text(font, Component.translatable("mosaicary.detail.loading"), x, layout.infoY0 + 6 + TAB + 10, Theme.TEXT_DIM, false);
			return;
		}
		for (Map.Entry<String, Dropdown<String>> entry : roleBoxes.entrySet()) {
			Dropdown<String> box = entry.getValue();
			RoleRow row = shownRows.get(entry.getKey());
			int indent = row.side() != null ? 8 : 0;
			int iconX = x + FOLD + indent;
			Icons.draw(graphics, row.side() != null ? Icon.ROLE_HOLD : roleIcon(row.action()), iconX,
				box.getY() + (box.getHeight() - Icons.SIZE) / 2, row.side() != null ? Theme.TEXT_OFF : Theme.TEXT_DIM);
			Component name = rowName(row);
			graphics.text(font, ellipsis(name.getString(), box.getX() - iconX - Icons.SIZE - 8), iconX + Icons.SIZE + 4,
				box.getY() + (box.getHeight() - 8) / 2, row.side() != null ? Theme.TEXT_DIM : Theme.TEXT, false);
			if (mouseX >= iconX && mouseX < box.getX() && mouseY >= box.getY() && mouseY < box.getBottom()) {
				Theme.tooltip(graphics, font, rowTooltip(row), mouseX, mouseY);
			}
		}
		// A thin bar on the right when there are more rows than fit.
		int rows = roleRows(layout);
		int all = roleList().size();
		if (rows < all) {
			int top = layout.infoY0 + 6 + TAB + 6 + ROLE_HEAD;
			int track = rows * ROLE_ROW;
			int thumb = Math.max(8, track * rows / all);
			int at = (track - thumb) * rolesScroll / Math.max(1, all - rows);
			graphics.fill(layout.infoX1 - 4, top + at, layout.infoX1 - 2, top + at + thumb, Theme.EDGE_HOVER);
		}
	}

	/** The tab buttons, the group and size dropdowns and a role's dropdown, or null; for the game test. */
	public IconButton rolesTabButton() {
		return rolesTabButton;
	}

	public Dropdown<Roles.Section> sectionBox() {
		return sectionBox;
	}

	public Dropdown<Double> sizeBox() {
		return sizeBox;
	}

	public Dropdown<String> roleBox(String key) {
		return roleBoxes.get(key);
	}

	private IconButton wearButton;
	private IconButton bindButton;
	private boolean wearLabels;
	private Boolean shownWorn;

	private void wearOrTakeOff() {
		Wearing wearing = MosaicaryClient.instance().wearing();
		if (wearing.isWorn(open)) {
			wearing.takeOff();
			refreshWearButton();
			return;
		}
		ModelCard card = open;
		Runnable wear = () -> {
			wearing.wear(card);
			refreshWearButton();
		};
		// The first Wear asks whether to share it through the site; the model goes on either way.
		if (Settings.NOT_ASKED.equals(MosaicaryClient.instance().settings().sharing)) {
			minecraft.gui.setScreen(new ConsentScreen(this, wear));
		} else {
			wear.run();
		}
	}

	/** The Wear button says Take off while this model is worn; a model for adults cannot be worn. */
	private void refreshWearButton() {
		if (wearButton == null || open == null) return;
		boolean worn = MosaicaryClient.instance().wearing().isWorn(open);
		if (shownWorn != null && shownWorn == worn) return;
		shownWorn = worn;
		wearButton.icon(() -> worn ? Icon.TAKE_OFF : Icon.WEAR);
		if (wearLabels) wearButton.label(Component.translatable(worn ? "mosaicary.action.take_off" : "mosaicary.action.wear"));
		wearButton.tooltip(Component.translatable(worn ? "mosaicary.action.take_off.tooltip" : "mosaicary.action.wear.tooltip"));
		if (open.adult()) {
			wearButton.active = false;
			wearButton.tooltip(Component.translatable("mosaicary.action.wear.adult"));
		}
	}

	private void bind() {
		minecraft.gui.setScreen(new SlotScreen(this, open));
	}

	private void initBottom() {
		int right = width - MARGIN;
		Component pluginLabel = Component.translatable("mosaicary.plugin.button");
		int pluginWidth = IconButton.widthFor(pluginLabel);
		Component siteLabel = Component.translatable("mosaicary.site.button");
		int siteWidth = IconButton.widthFor(siteLabel);
		boolean labels = width - 2 * MARGIN - pluginWidth - siteWidth - 2 * GAP >= 140;
		if (!labels) {
			pluginWidth = ROW;
			siteWidth = ROW;
		}
		addRenderableWidget(new IconButton(right - pluginWidth, bottomRowY(), pluginWidth, ROW, Icon.PLUGIN,
			labels ? pluginLabel : null, Component.translatable("mosaicary.plugin.tooltip"), this::installPlugin));
		right -= pluginWidth + GAP;
		addRenderableWidget(new IconButton(right - siteWidth, bottomRowY(), siteWidth, ROW, Icon.SITE,
			labels ? siteLabel : null, Component.translatable("mosaicary.site.tooltip"),
			() -> openUri(catalogue.site().base())));
		statusRight = right - siteWidth - GAP;
	}

	private int statusRight;

	// ------------------------------------------------------------------ actions

	private void installPlugin() {
		minecraft.keyboardHandler.setClipboard(PLUGIN_URL);
		openUri(catalogue.site().base() + "/plugin");
		say(Component.translatable("mosaicary.plugin.copied"));
	}

	private void say(Component said) {
		notice = said;
		noticeUntil = System.currentTimeMillis() + 8000;
	}

	private static void openUri(String address) {
		try {
			Blaze3D.openUri(URI.create(address));
		} catch (IllegalArgumentException bad) {
			// A malformed address from the settings file; nothing to open.
		}
	}

	private void download() {
		Catalogue.Download state = catalogue.download(open);
		if (state != null && state.state() == Catalogue.State.READY) {
			Blaze3D.openPath(state.where().getParent());
			return;
		}
		catalogue.startDownload(open);
	}

	/** Back to the grid, keeping the search and filters. */
	public void closeModel() {
		if (open == null) return;
		open = null;
		rebuildWidgets();
	}

	public void openModel(ModelCard card) {
		open = card;
		rolesScroll = 0;
		resetView();
		catalogue.model(card);
		rebuildWidgets();
	}

	private void resetView() {
		if (open == null) return;
		// The angle the author picked for the card, so the preview and the card
		// picture can be compared side by side; Blockbench's opening view otherwise.
		spin = open.hasLook() ? (float) open.spin() : (float) (-3 * Math.PI / 4);
		tilt = open.hasLook() ? (float) open.tilt() : 0.515f;
		zoom = 1;
	}

	/** The middle of a card in the grid, in GUI units, or null when it is not shown; for the game test. */
	public int[] cardCenter(ModelCard card) {
		if (open != null) return null;
		Grid grid = grid();
		List<ModelCard> shown = shown();
		for (int i = 0; i < shown.size(); i++) {
			if (!shown.get(i).id().equals(card.id())) continue;
			int left = grid.x0() + (i % grid.columns()) * (grid.cardWidth() + GAP);
			int top = (int) (grid.y0() - scroll) + (i / grid.columns()) * (grid.cardHeight() + GAP);
			return new int[] { left + grid.cardWidth() / 2, top + grid.pictureHeight() / 2 };
		}
		return null;
	}

	private IconButton settingsButton;

	/** The settings button of the grid, or null; for the game test. */
	public IconButton settingsButton() {
		return settingsButton;
	}

	/** The Wear or Take off button of the detail view, or null; for the game test. */
	public IconButton wearButton() {
		return wearButton;
	}

	/** The download button, or Install on a build; for the game test. */
	public IconButton downloadButton() {
		return downloadButton;
	}

	/** The Bind to slot button of the detail view, or null; for the game test. */
	public IconButton bindButton() {
		return bindButton;
	}

	/**
	 * Turns the preview to an angle, in radians, the way {@code look} on the site
	 * measures it; for the screenshot run.
	 */
	public void setView(float spin, float tilt) {
		this.spin = spin;
		this.tilt = tilt;
	}

	/** The angle the preview is turned to, in radians; for the game test. */
	public float spin() {
		return spin;
	}

	/** How far from above the preview is seen, in radians; for the game test. */
	public float tilt() {
		return tilt;
	}

	/** The card currently open in the detail view, or null in the grid. */
	public ModelCard openCard() {
		return open;
	}

	// ------------------------------------------------------------------ the grid

	/** The cards the search and filters let through, in the chosen order. */
	public List<ModelCard> shown() {
		List<ModelCard> out = new ArrayList<>();
		if (localMode) {
			for (ModelCard card : catalogue.localCards()) if (card.matches(query)) out.add(card);
			out.sort(Comparator.comparing(card -> card.name().toLowerCase(Locale.ROOT)));
			return out;
		}
		for (ModelCard card : shelf()) {
			if (!buildsMode && !use.equals("all") && !use.equals(card.use())) continue;
			if (!licence.equals("any") && !licence.equals(card.license())) continue;
			if (!card.matches(query)) continue;
			out.add(card);
		}
		Comparator<ModelCard> order = switch (sort) {
			case "liked" -> Comparator.comparingInt(ModelCard::likes).reversed();
			case "downloaded" -> Comparator.comparingInt(ModelCard::downloads).reversed();
			case "name" -> Comparator.comparing(card -> card.name().toLowerCase(Locale.ROOT));
			default -> Comparator.comparing(ModelCard::published).reversed();
		};
		out.sort(order);
		return out;
	}

	private record Grid(int x0, int y0, int x1, int y1, int columns, int cardWidth, int pictureHeight, int cardHeight) {
		int rows(int count) {
			return (count + columns - 1) / columns;
		}

		int contentHeight(int count) {
			int rows = rows(count);
			return rows == 0 ? 0 : rows * cardHeight + (rows - 1) * GAP;
		}
	}

	private Grid grid() {
		int x0 = MARGIN;
		int x1 = width - MARGIN;
		int y0 = bodyTop();
		int y1 = bodyBottom();
		int available = x1 - x0 - 6;
		int columns = Math.max(1, (available + GAP) / (CARD_TARGET + GAP));
		int cardWidth = (available - (columns - 1) * GAP) / columns;
		// Square, as the site's pictures are: a wider box left a strip of background at each
		// side, and a close-up cut at the picture's own edge showed that cut inside the card.
		int pictureHeight = Math.max(40, cardWidth - 2);
		int cardHeight = pictureHeight + 32;
		return new Grid(x0, y0, x1, y1, columns, cardWidth, pictureHeight, cardHeight);
	}

	private void clampScroll(Grid grid, int count) {
		double most = Math.max(0, grid.contentHeight(count) - (grid.y1() - grid.y0()));
		scroll = Math.max(0, Math.min(most, scroll));
	}

	private ModelCard cardAt(double mouseX, double mouseY) {
		if (open != null || catalogue.listState() == null) return null;
		Grid grid = grid();
		if (mouseX < grid.x0() || mouseX >= grid.x1() || mouseY < grid.y0() || mouseY >= grid.y1()) return null;
		List<ModelCard> shown = shown();
		int column = (int) ((mouseX - grid.x0()) / (grid.cardWidth() + GAP));
		int row = (int) ((mouseY - grid.y0() + scroll) / (grid.cardHeight() + GAP));
		if (column >= grid.columns()) return null;
		int left = grid.x0() + column * (grid.cardWidth() + GAP);
		int top = (int) (grid.y0() - scroll) + row * (grid.cardHeight() + GAP);
		if (mouseX >= left + grid.cardWidth() || mouseY >= top + grid.cardHeight()) return null;
		int index = row * grid.columns() + column;
		return index >= 0 && index < shown.size() ? shown.get(index) : null;
	}

	// ------------------------------------------------------------------ drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		graphics.fill(0, 0, width, height, Theme.SCREEN);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		if (hideMouse) {
			mouseX = -10000;
			mouseY = -10000;
		}
		if (open == null) {
			// The search field sits on a card of its own with the icon in front of it.
			Theme.card(graphics, MARGIN, topRowY(), searchRight, topRowY() + ROW, Theme.FIELD,
				search != null && search.isFocused() ? Theme.ACCENT : Theme.EDGE);
			Icons.draw(graphics, Icon.SEARCH, MARGIN + 4, topRowY() + (ROW - Icons.SIZE) / 2, Theme.TEXT_DIM);
			drawGrid(graphics, mouseX, mouseY);
		} else {
			drawDetail(graphics, mouseX, mouseY);
		}
		drawStatus(graphics);
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.nextStratum();
		for (Dropdown<?> box : dropdowns) box.extractList(graphics, mouseX, mouseY, width);
		if (animationBox != null) animationBox.extractList(graphics, mouseX, mouseY, width);
	}

	private void drawGrid(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Grid grid = grid();
		if (localMode && catalogue.localCards().isEmpty()) {
			if (catalogue.localState() == Catalogue.State.LOADING) {
				message(graphics, grid, Icon.LOADING, Theme.ACCENT, Component.translatable("mosaicary.state.loading"), null);
			} else {
				message(graphics, grid, Icon.FIGURA, Theme.TEXT_DIM, Component.translatable("mosaicary.figura.empty"),
					Component.translatable("mosaicary.figura.empty.detail"));
			}
			return;
		}
		Catalogue.State state = localMode ? Catalogue.State.READY : catalogue.listState();
		if (state == Catalogue.State.LOADING && catalogue.cards().isEmpty()) {
			message(graphics, grid, Icon.LOADING, Theme.ACCENT, Component.translatable("mosaicary.state.loading"), null);
			return;
		}
		if (state == Catalogue.State.FAILED && catalogue.cards().isEmpty()) {
			boolean offline = catalogue.failure() == SiteClient.Failure.OFFLINE;
			message(graphics, grid, offline ? Icon.OFFLINE : Icon.ERROR, Theme.WARN,
				Component.translatable(offline ? "mosaicary.state.offline" : "mosaicary.state.error"),
				Component.translatable(offline ? "mosaicary.state.offline.detail" : "mosaicary.state.error.detail"));
			return;
		}
		List<ModelCard> shown = shown();
		if (!localMode && catalogue.cards().isEmpty()) {
			message(graphics, grid, Icon.EMPTY, Theme.TEXT_DIM, Component.translatable("mosaicary.state.empty"),
				Component.translatable("mosaicary.state.empty.detail"));
			return;
		}
		if (!localMode && shelf().isEmpty()) {
			message(graphics, grid, buildsMode ? Icon.BUILDS : Icon.EMPTY, Theme.TEXT_DIM,
				Component.translatable(buildsMode ? "mosaicary.builds.empty" : "mosaicary.state.empty"),
				Component.translatable(buildsMode ? "mosaicary.builds.empty.detail" : "mosaicary.state.empty.detail"));
			return;
		}
		if (shown.isEmpty()) {
			message(graphics, grid, Icon.EMPTY, Theme.TEXT_DIM, Component.translatable("mosaicary.state.nomatch"),
				Component.translatable("mosaicary.state.nomatch.detail"));
			return;
		}

		clampScroll(grid, shown.size());
		ModelCard hovered = cardAt(mouseX, mouseY);
		graphics.enableScissor(grid.x0(), grid.y0(), grid.x1(), grid.y1());
		for (int i = 0; i < shown.size(); i++) {
			int column = i % grid.columns();
			int row = i / grid.columns();
			int left = grid.x0() + column * (grid.cardWidth() + GAP);
			int top = (int) (grid.y0() - scroll) + row * (grid.cardHeight() + GAP);
			if (top > grid.y1() || top + grid.cardHeight() < grid.y0()) continue;
			drawCard(graphics, shown.get(i), left, top, grid, shown.get(i) == hovered);
		}
		graphics.disableScissor();

		int content = grid.contentHeight(shown.size());
		int visible = grid.y1() - grid.y0();
		if (content > visible) {
			int track = visible;
			int thumb = Math.max(12, track * visible / content);
			int at = (int) ((track - thumb) * (scroll / (content - visible)));
			graphics.fill(grid.x1() - 3, grid.y0(), grid.x1() - 1, grid.y1(), Theme.CARD);
			graphics.fill(grid.x1() - 3, grid.y0() + at, grid.x1() - 1, grid.y0() + at + thumb, Theme.EDGE_HOVER);
		}
	}

	private void drawCard(GuiGraphicsExtractor graphics, ModelCard card, int left, int top, Grid grid, boolean hovered) {
		int right = left + grid.cardWidth();
		int bottom = top + grid.cardHeight();
		Theme.card(graphics, left, top, right, bottom, hovered ? Theme.CARD_HOVER : Theme.CARD,
			hovered ? Theme.ACCENT : Theme.EDGE);

		int pictureBottom = top + 1 + grid.pictureHeight();
		graphics.fill(left + 1, top + 1, right - 1, pictureBottom, Theme.opaque(card.backgroundRgb(0x273A49)));
		Catalogue.Picture picture = catalogue.picture(card);
		if (picture.state() == Catalogue.State.READY) {
			int side = Math.min(grid.cardWidth() - 2, grid.pictureHeight());
			int x = left + 1 + (grid.cardWidth() - 2 - side) / 2;
			int y = top + 1 + (grid.pictureHeight() - side) / 2;
			graphics.blit(RenderPipelines.GUI_TEXTURED, picture.texture(), x, y, 0, 0, side, side,
				picture.width(), picture.height(), picture.width(), picture.height());
		} else {
			Icon icon = card.isLocal() ? Icon.FIGURA : picture.state() == Catalogue.State.LOADING ? Icon.LOADING : Icon.CUBES;
			Icons.draw(graphics, icon, left + (grid.cardWidth() - Icons.SIZE) / 2,
				top + 1 + (grid.pictureHeight() - Icons.SIZE) / 2, 0xB0FFFFFF);
		}

		int textLeft = left + 5;
		int textWidth = grid.cardWidth() - 10;
		graphics.text(font, ellipsis(card.name(), textWidth), textLeft, pictureBottom + 4, Theme.TEXT, false);
		int authorY = pictureBottom + 14;
		Icons.draw(graphics, Icon.AUTHOR, textLeft - 2, authorY, Theme.TEXT_DIM);
		String author = card.authorName().isEmpty() ? card.byHandle() : card.authorName();
		if (author.isEmpty() && card.isLocal()) author = Component.translatable("mosaicary.figura.yours").getString();
		graphics.text(font, ellipsis(author, textWidth - Icons.SIZE), textLeft + Icons.SIZE, authorY + 4, Theme.TEXT_DIM, false);
	}

	private String ellipsis(String text, int width) {
		if (font.width(text) <= width) return text;
		String dots = "...";
		return font.plainSubstrByWidth(text, Math.max(0, width - font.width(dots))) + dots;
	}

	/** A centred card that says what is happening: an empty, loading or failed state. */
	private void message(GuiGraphicsExtractor graphics, Grid grid, Icon icon, int tint, Component title, Component detail) {
		int width = Math.min(grid.x1() - grid.x0() - 20, 260);
		List<FormattedCharSequence> lines = detail == null ? List.of() : font.split(detail, width - 16);
		int height = 16 + Icons.SIZE + 6 + 10 + lines.size() * 10 + 12;
		int x0 = (grid.x0() + grid.x1() - width) / 2;
		int y0 = (grid.y0() + grid.y1() - height) / 2;
		Theme.card(graphics, x0, y0, x0 + width, y0 + height);
		Icons.draw(graphics, icon, x0 + (width - Icons.SIZE) / 2, y0 + 10, tint);
		graphics.centeredText(font, title, x0 + width / 2, y0 + 10 + Icons.SIZE + 6, Theme.TEXT);
		int y = y0 + 10 + Icons.SIZE + 6 + 12;
		for (FormattedCharSequence line : lines) {
			graphics.centeredText(font, line, x0 + width / 2, y, Theme.TEXT_DIM);
			y += 10;
		}
	}

	private void drawStatus(GuiGraphicsExtractor graphics) {
		int y = bottomRowY();
		Theme.card(graphics, MARGIN, y, statusRight, y + ROW);
		Icon icon;
		int tint;
		Component said;
		if (notice != null && System.currentTimeMillis() < noticeUntil) {
			icon = Icon.DOWNLOADED;
			tint = Theme.ACCENT;
			said = notice;
		} else if (localMode && open == null) {
			icon = Icon.FIGURA;
			tint = Theme.TEXT_DIM;
			said = Component.translatable("mosaicary.figura.count", catalogue.localCards().size());
		} else if (catalogue.listState() == Catalogue.State.LOADING) {
			icon = Icon.LOADING;
			tint = Theme.ACCENT;
			said = Component.translatable("mosaicary.state.loading");
		} else if (catalogue.fromCache()) {
			icon = Icon.OFFLINE;
			tint = Theme.WARN;
			said = Component.translatable("mosaicary.status.cached");
		} else if (catalogue.listState() == Catalogue.State.FAILED) {
			icon = Icon.ERROR;
			tint = Theme.WARN;
			said = Component.translatable("mosaicary.status.failed");
		} else {
			icon = buildsMode ? Icon.BUILDS : Icon.CATALOGUE;
			tint = Theme.TEXT_DIM;
			int all = shelf().size();
			int shown = shown().size();
			String count = buildsMode ? "mosaicary.builds.count" : "mosaicary.status.count";
			String filtered = buildsMode ? "mosaicary.builds.filtered" : "mosaicary.status.filtered";
			said = shown == all ? Component.translatable(count, all) : Component.translatable(filtered, shown, all);
		}
		Icons.draw(graphics, icon, MARGIN + 4, y + (ROW - Icons.SIZE) / 2, tint);
		String text = said.getString();
		graphics.text(font, ellipsis(text, statusRight - MARGIN - Icons.SIZE - 12), MARGIN + Icons.SIZE + 8,
			y + (ROW - font.lineHeight) / 2 + 1, Theme.TEXT, false);
	}

	// ------------------------------------------------------------------ the detail view

	private record Detail(int previewX0, int previewY0, int previewX1, int previewY1, int infoX0, int infoY0, int infoX1,
			int infoY1) { }

	private Detail detailLayout() {
		int top = bodyTop();
		int bottom = bodyBottom();
		int left = MARGIN;
		int right = width - MARGIN;
		// At least wide enough for a date beside its icon in half the card.
		int infoWidth = Math.max(180, Math.min(240, (right - left) * 2 / 5));
		int split = right - infoWidth;
		return new Detail(left, top, split - GAP, bottom, split, top, right, bottom);
	}

	private void drawDetail(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Detail layout = detailLayout();
		ModelCard card = open;

		// The name, on a card of its own in the top row.
		int nameX0 = MARGIN + IconButton.widthFor(Component.translatable("mosaicary.back.short")) + GAP;
		int nameX1 = width - MARGIN - ROW - GAP;
		Theme.card(graphics, nameX0, topRowY(), nameX1, topRowY() + ROW);
		Icons.draw(graphics, Icon.CUBES, nameX0 + 4, topRowY() + (ROW - Icons.SIZE) / 2, Theme.ACCENT);
		graphics.text(font, ellipsis(card.name(), nameX1 - nameX0 - Icons.SIZE - 14), nameX0 + Icons.SIZE + 8,
			topRowY() + (ROW - font.lineHeight) / 2 + 1, Theme.TEXT, false);

		// The preview panel, on the colour the author picked for the card.
		Theme.card(graphics, layout.previewX0, layout.previewY0, layout.previewX1, layout.previewY1,
			Theme.opaque(card.backgroundRgb(0x273A49)), Theme.EDGE);
		Catalogue.Model model = catalogue.model(card);
		if (card.isBuild()) {
			// Nothing to turn: the card's picture, as large as fits.
			Catalogue.Picture picture = catalogue.picture(card);
			if (picture.state() == Catalogue.State.READY) {
				int side = Math.min(layout.previewX1 - layout.previewX0, layout.previewY1 - layout.previewY0) - 8;
				int px = (layout.previewX0 + layout.previewX1 - side) / 2;
				int py = (layout.previewY0 + layout.previewY1 - side) / 2;
				graphics.blit(RenderPipelines.GUI_TEXTURED, picture.texture(), px, py, 0, 0, side, side,
					picture.width(), picture.height(), picture.width(), picture.height());
			}
		} else if (model.state() == Catalogue.State.READY) {
			float[] box = model.mesh().bounds();
			float[] center = { (box[0] + box[3]) / 2, (box[1] + box[4]) / 2, (box[2] + box[5]) / 2 };
			float dx = box[3] - box[0];
			float dy = box[4] - box[1];
			float dz = box[5] - box[2];
			// Fitted to the ball around the model rather than its box, so turning the
			// model never moves it in and out of the frame.
			float radius = Math.max(0.5f, (float) Math.sqrt(dx * dx + dy * dy + dz * dz) / 2);
			int w = layout.previewX1 - layout.previewX0 - 2;
			int h = layout.previewY1 - layout.previewY0 - 2;
			float scale = Math.min(w, h) / (2 * radius) * 0.92f * zoom;
			// The animation widgets are made once the model has arrived.
			if (animationsFor != open && !model.mesh().model().animations().isEmpty()) rebuildWidgets();
			else if (infoTab == TAB_ROLES && roleBoxes.isEmpty()) rebuildWidgets();
			// No scissor: the detail view is drawn outside any, the grid's is closed before it.
			((GuiGraphicsExtractorAccessor) graphics).mosaicary$guiRenderState().addPicturesInPictureState(PreviewState.of(model.mesh(),
				previewPosing(model.mesh().model()), spin, tilt, center,
				layout.previewX0 + 1, layout.previewY0 + 1, layout.previewX1 - 1, layout.previewY1 - 1, scale,
				null));
		} else {
			boolean loading = model.state() == Catalogue.State.LOADING;
			Component title = Component.translatable(loading ? "mosaicary.detail.loading" : "mosaicary.detail.failed");
			Component detail = loading ? null : Component.translatable("mosaicary.problem." + model.problem());
			Grid area = new Grid(layout.previewX0, layout.previewY0, layout.previewX1, layout.previewY1, 1, 0, 0, 0);
			message(graphics, area, loading ? Icon.LOADING : Icon.ERROR, loading ? Theme.ACCENT : Theme.WARN, title, detail);
		}

		if (infoTab == TAB_ROLES) drawRoles(graphics, layout, card, mouseX, mouseY);
		else drawInfo(graphics, layout, card, mouseX, mouseY);
		refreshDownloadButton();
		refreshWearButton();
	}

	private Catalogue.Download shownDownload;
	private boolean downloadShownOnce;

	/** What installing a build does here: one line for each mod it goes to, or why it cannot. */
	private Component installTooltip() {
		List<Builds.Format> placers = catalogue.site().placers();
		if (placers.isEmpty()) return Component.translatable("mosaicary.action.install.none");
		MutableComponent out = Component.translatable("mosaicary.action.install.tooltip");
		for (Builds.Format one : placers) {
			Path where = catalogue.site().buildPath(open, one);
			Path game = catalogue.site().root().toAbsolutePath().getParent();
			out.append("\n").append(Component.translatable("mosaicary.build.place", one.mod,
				game.relativize(where.getParent()).toString().replace('\\', '/')));
		}
		return out;
	}

	private void refreshDownloadButton() {
		if (downloadButton == null) return;
		Catalogue.Download state = catalogue.download(open);
		// Only when the state changes: a new tooltip every frame would restart its delay.
		if (downloadShownOnce && java.util.Objects.equals(state, shownDownload)) return;
		downloadShownOnce = true;
		shownDownload = state;
		if (open.isBuild()) {
			if (state == null) {
				downloadButton.icon(() -> Icon.DOWNLOAD).tooltip(installTooltip());
			} else if (state.state() == Catalogue.State.LOADING) {
				downloadButton.icon(() -> Icon.LOADING).tooltip(Component.translatable("mosaicary.action.installing"));
			} else if (state.state() == Catalogue.State.READY) {
				MutableComponent done = Component.translatable("mosaicary.action.installed.tooltip");
				for (Builds.Format one : catalogue.site().placers()) {
					done.append("\n").append(Component.translatable("mosaicary.build.place", one.mod,
						catalogue.site().buildPath(open, one).getFileName().toString()));
				}
				downloadButton.icon(() -> Icon.DOWNLOADED).tooltip(done);
			} else {
				downloadButton.icon(() -> Icon.ERROR).tooltip(Component.translatable("mosaicary.action.install.failed"));
			}
			return;
		}
		if (state == null) {
			downloadButton.icon(() -> Icon.DOWNLOAD).tooltip(Component.translatable("mosaicary.action.download.tooltip"));
		} else if (state.state() == Catalogue.State.LOADING) {
			downloadButton.icon(() -> Icon.LOADING).tooltip(Component.translatable("mosaicary.action.downloading"));
		} else if (state.state() == Catalogue.State.READY) {
			downloadButton.icon(() -> Icon.DOWNLOADED).tooltip(Component.translatable("mosaicary.action.downloaded.tooltip",
				state.where().getFileName().toString()));
		} else {
			downloadButton.icon(() -> Icon.ERROR).tooltip(Component.translatable("mosaicary.action.download.failed"));
		}
	}

	/** One fact in the info card: an icon, a value and a tooltip naming it. */
	private record Fact(Icon icon, String value, Component name) { }

	private int authorRowY;
	private int licenceRowY;

	private void drawInfo(GuiGraphicsExtractor graphics, Detail layout, ModelCard listed, int mouseX, int mouseY) {
		// The list leaves the description out; the model's own card has it.
		ModelCard card = catalogue.detail(listed);
		Theme.card(graphics, layout.infoX0, layout.infoY0, layout.infoX1, layout.infoY1);
		int x = layout.infoX0 + 6;
		int width = layout.infoX1 - layout.infoX0 - 12;
		int y = layout.infoY0 + 6 + TAB + 6;
		Catalogue.Model loaded = catalogue.model(card);

		// A Figura avatar's scripts are not run: said first, in the warning colour.
		if (loaded.scripts() > 0) {
			Icons.draw(graphics, Icon.ERROR, x, y, Theme.WARN);
			graphics.text(font, ellipsis(Component.translatable("mosaicary.figura.scripts").getString(), width - Icons.SIZE - 4),
				x + Icons.SIZE + 4, y + 4, Theme.WARN, false);
			if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + Icons.SIZE) {
				Theme.tooltip(graphics, font, Component.translatable("mosaicary.figura.scripts.tooltip", loaded.scripts()),
					mouseX, mouseY);
			}
			y += Icons.SIZE + 3;
		}

		// Who made it, with a link to their page when the site has one.
		authorRowY = y;
		boolean authorLink = !card.authorUrl().isEmpty();
		boolean overAuthor = authorLink && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + Icons.SIZE;
		Icons.draw(graphics, Icon.AUTHOR, x, y, Theme.ACCENT);
		String author = card.authorName().isEmpty() ? card.byHandle() : card.authorName();
		if (author.isEmpty() && card.isLocal()) author = Component.translatable("mosaicary.figura.yours").getString();
		graphics.text(font, ellipsis(author, width - Icons.SIZE - 20), x + Icons.SIZE + 4, y + 4,
			overAuthor ? Theme.ACCENT : Theme.TEXT, false);
		if (authorLink) {
			Icons.draw(graphics, Icon.OPEN_SITE, x + width - Icons.SIZE, y, overAuthor ? Theme.ACCENT : Theme.TEXT_DIM);
			if (overAuthor) Theme.tooltip(graphics, font, Component.translatable("mosaicary.detail.author.link"), mouseX, mouseY);
		}
		y += Icons.SIZE + 3;

		// The licence, linking to its text.
		licenceRowY = y;
		boolean licenceLink = licenceUrl(card.license()) != null;
		boolean overLicence = licenceLink && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + Icons.SIZE;
		Icons.draw(graphics, card.isLocal() ? Icon.FIGURA : Icon.LICENCE, x, y, Theme.ACCENT);
		graphics.text(font, Component.translatable(card.isLocal() ? "mosaicary.figura.from_folder" : "mosaicary.licence." + card.license()),
			x + Icons.SIZE + 4, y + 4, overLicence ? Theme.ACCENT : Theme.TEXT, false);
		if (licenceLink) {
			Icons.draw(graphics, Icon.OPEN_SITE, x + width - Icons.SIZE, y, overLicence ? Theme.ACCENT : Theme.TEXT_DIM);
			if (overLicence) Theme.tooltip(graphics, font, Component.translatable("mosaicary.detail.licence.link"), mouseX, mouseY);
		}
		y += Icons.SIZE + 3;
		graphics.fill(x, y, x + width, y + 1, Theme.EDGE);
		y += 5;

		// The counts, two to a row. An avatar from the player's own folder has no card
		// from the site: its counts come from the model itself, and it has no likes,
		// downloads, tags or date.
		if (card.isLocal()) {
			y = localFacts(graphics, loaded, x, y, width, layout, mouseX, mouseY);
			drawDescription(graphics, card, x, y, width, layout);
			return;
		}
		List<Fact> facts = new ArrayList<>();
		facts.add(new Fact(Icon.CUBES, Integer.toString(card.cubes()), Component.translatable("mosaicary.fact.cubes")));
		facts.add(new Fact(Icon.MESHES, Integer.toString(card.meshes()), Component.translatable("mosaicary.fact.meshes")));
		facts.add(new Fact(Icon.BONES, Integer.toString(card.bones()), Component.translatable("mosaicary.fact.bones")));
		facts.add(new Fact(Icon.ANIMATIONS, Integer.toString(card.animations()), Component.translatable("mosaicary.fact.animations")));
		facts.add(new Fact(Icon.TEXTURES, card.textures() + (card.textureWidth() > 0 ? "  " + card.textureWidth() + "x" + card.textureHeight() : ""),
			Component.translatable("mosaicary.fact.textures")));
		facts.add(new Fact(Icon.USE, Component.translatable("mosaicary.use." + card.use()).getString(),
			Component.translatable("mosaicary.fact.format", card.format())));
		facts.add(new Fact(Icon.LIKES, Integer.toString(card.likes()), Component.translatable("mosaicary.fact.likes")));
		facts.add(new Fact(Icon.DOWNLOADS, Integer.toString(card.downloads()), Component.translatable("mosaicary.fact.downloads")));
		facts.add(new Fact(Icon.TAG, Integer.toString(card.tags().size()), card.tags().isEmpty()
			? Component.translatable("mosaicary.fact.tags.none")
			: Component.translatable("mosaicary.fact.tags", String.join(", ", card.tags()))));
		facts.add(new Fact(Icon.DATE, card.published().length() >= 10 ? card.published().substring(0, 10) : card.published(),
			Component.translatable("mosaicary.fact.published")));
		// Rows that would run into the buttons are left out rather than drawn under them.
		int factsBottom = layout.infoY1 - 6 - ROW - 3;
		int half = width / 2;
		for (int i = 0; i < facts.size(); i++) {
			Fact fact = facts.get(i);
			int fx = x + (i % 2) * half;
			int fy = y + (i / 2) * (Icons.SIZE + 1);
			if (fy + Icons.SIZE > factsBottom) break;
			Icons.draw(graphics, fact.icon(), fx, fy, Theme.TEXT_DIM);
			graphics.text(font, ellipsis(fact.value(), half - Icons.SIZE - 5), fx + Icons.SIZE + 3, fy + 4, Theme.TEXT, false);
			if (mouseX >= fx && mouseX < fx + half && mouseY >= fy && mouseY < fy + Icons.SIZE) {
				Theme.tooltip(graphics, font, fact.name(), mouseX, mouseY);
			}
		}
		y += ((facts.size() + 1) / 2) * (Icons.SIZE + 1) + 3;
		drawDescription(graphics, card, x, y, width, layout);
	}

	/** The description, as much of it as fits above the action buttons. */
	private void drawDescription(GuiGraphicsExtractor graphics, ModelCard card, int x, int y, int width, Detail layout) {
		int limit = layout.infoY1 - 6 - ROW - 4;
		String description = card.description();
		if (!description.isBlank() && y + 14 < limit) {
			graphics.fill(x, y, x + width, y + 1, Theme.EDGE);
			y += 5;
			for (FormattedCharSequence line : font.split(Component.literal(description.strip()), width)) {
				if (y + 9 > limit) break;
				graphics.text(font, line, x, y, Theme.TEXT_DIM, false);
				y += 10;
			}
		}
	}

	/** A local avatar's counts, read off the model once it has loaded; returns where the next row starts. */
	private int localFacts(GuiGraphicsExtractor graphics, Catalogue.Model loaded, int x, int y, int width, Detail layout,
			int mouseX, int mouseY) {
		if (loaded.state() != Catalogue.State.READY) return y;
		BbModel model = loaded.mesh().model();
		List<Fact> facts = List.of(
			new Fact(Icon.CUBES, Integer.toString(model.cubes()), Component.translatable("mosaicary.fact.cubes")),
			new Fact(Icon.MESHES, Integer.toString(model.meshes()), Component.translatable("mosaicary.fact.meshes")),
			new Fact(Icon.BONES, Integer.toString(model.bones().size()), Component.translatable("mosaicary.fact.bones")),
			new Fact(Icon.ANIMATIONS, Integer.toString(model.animations().size()), Component.translatable("mosaicary.fact.animations")),
			new Fact(Icon.TEXTURES, Integer.toString(model.textures().size()), Component.translatable("mosaicary.fact.textures")));
		int factsBottom = layout.infoY1 - 6 - ROW - 3;
		int half = width / 2;
		for (int i = 0; i < facts.size(); i++) {
			Fact fact = facts.get(i);
			int fx = x + (i % 2) * half;
			int fy = y + (i / 2) * (Icons.SIZE + 1);
			if (fy + Icons.SIZE > factsBottom) break;
			Icons.draw(graphics, fact.icon(), fx, fy, Theme.TEXT_DIM);
			graphics.text(font, ellipsis(fact.value(), half - Icons.SIZE - 5), fx + Icons.SIZE + 3, fy + 4, Theme.TEXT, false);
			if (mouseX >= fx && mouseX < fx + half && mouseY >= fy && mouseY < fy + Icons.SIZE) {
				Theme.tooltip(graphics, font, fact.name(), mouseX, mouseY);
			}
		}
		return y + ((facts.size() + 1) / 2) * (Icons.SIZE + 1) + 3;
	}

	private static String licenceUrl(String licence) {
		return switch (licence) {
			case "cc0" -> "https://creativecommons.org/publicdomain/zero/1.0/";
			case "cc-by" -> "https://creativecommons.org/licenses/by/4.0/";
			case "cc-by-sa" -> "https://creativecommons.org/licenses/by-sa/4.0/";
			case "cc-by-nc" -> "https://creativecommons.org/licenses/by-nc/4.0/";
			case "cc-by-nc-sa" -> "https://creativecommons.org/licenses/by-nc-sa/4.0/";
			default -> null;
		};
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		for (Dropdown<?> box : dropdowns) {
			if (box.isOpen()) {
				if (box.clickList(event.x(), event.y(), width)) return true;
			}
		}
		if (animationBox != null && animationBox.isOpen() && animationBox.clickList(event.x(), event.y(), width)) return true;
		if (super.mouseClicked(event, doubleClick)) return true;
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;

		if (open == null) {
			ModelCard card = cardAt(event.x(), event.y());
			if (card != null) {
				openModel(card);
				return true;
			}
			if (catalogue.listState() == Catalogue.State.FAILED && catalogue.cards().isEmpty()) {
				catalogue.refresh();
				return true;
			}
			return false;
		}

		Detail layout = detailLayout();
		int x = layout.infoX0 + 6;
		int width = layout.infoX1 - layout.infoX0 - 12;
		if (infoTab == TAB_INFO && event.x() >= x && event.x() < x + width) {
			if (event.y() >= authorRowY && event.y() < authorRowY + Icons.SIZE && !open.authorUrl().isEmpty()) {
				openUri(open.authorUrl());
				return true;
			}
			if (event.y() >= licenceRowY && event.y() < licenceRowY + Icons.SIZE && licenceUrl(open.license()) != null) {
				openUri(licenceUrl(open.license()));
				return true;
			}
		}
		if (event.x() >= layout.previewX0 && event.x() < layout.previewX1 && event.y() >= layout.previewY0
				&& event.y() < layout.previewY1) {
			Catalogue.Model model = catalogue.model(open);
			if (model.state() == Catalogue.State.FAILED) {
				catalogue.retryModel(open);
				return true;
			}
			turning = true;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (turning) {
			spin -= (float) dx * 0.02f;
			tilt = (float) Math.max(-1.4, Math.min(1.4, tilt + dy * 0.02));
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		turning = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		for (Dropdown<?> box : dropdowns) {
			if (box.isOpen() && box.scrollList(x, y, scrollY, width)) return true;
		}
		if (animationBox != null && animationBox.isOpen() && animationBox.scrollList(x, y, scrollY, width)) return true;
		if (open != null && infoTab == TAB_ROLES) {
			Detail roles = detailLayout();
			if (x >= roles.infoX0 && x < roles.infoX1 && y >= roles.infoY0 && y < roles.infoY1) {
				int most = Math.max(0, roleList().size() - roleRows(roles));
				int next = (int) Math.max(0, Math.min(most, rolesScroll - Math.signum(scrollY)));
				if (next != rolesScroll) {
					rolesScroll = next;
					rebuildWidgets();
				}
				return true;
			}
		}
		if (open == null) {
			scroll -= scrollY * 24;
			return true;
		}
		Detail layout = detailLayout();
		if (x >= layout.previewX0 && x < layout.previewX1 && y >= layout.previewY0 && y < layout.previewY1) {
			zoom = (float) Math.max(0.3, Math.min(6, zoom * Math.pow(1.15, scrollY)));
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_ESCAPE) {
			for (Dropdown<?> box : dropdowns) {
				if (box.isOpen()) {
					box.close();
					return true;
				}
			}
			if (open != null) {
				closeModel();
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}

	/** The catalogue this screen shows, for the screenshot run. */
	public Catalogue catalogue() {
		return catalogue;
	}
}

package com.mopicmp.mosaicary.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleUnaryOperator;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mopicmp.mosaicary.client.compat.GenderMod;
import com.mopicmp.mosaicary.wardrobe.Body;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

/**
 * Female Gender Mod's body, set in the card of a skin in place of its description: the gender as
 * three icons, the breasts' size, place and turn on sliders, their physics as two switches with
 * its two sliders, and the voice's pitch where the line of the mod has one. The figure beside it
 * shows every change as it is made.
 *
 * The numbers go between the line's own limits and read as the mod's own sliders read them (its
 * WildfireBreastCustomizationScreen): the size in percent of its largest, the place in tenths, the
 * turn in degrees, the bounce as three times its value. What is set goes out ({@code commit}) as
 * soon as a slider has rested for a moment, and at once for a switch.
 */
final class BodyPanel {

	private static final int ROW = 20;
	private static final int GAP = 3;
	/** The room around the line between two groups. */
	private static final int RULE = 5;
	/** How long a slider rests before what it set goes out. */
	private static final long REST_MILLIS = 500;

	/** What a missing setting is, as the mod has it for a player it knows nothing of. */
	private static final String DEFAULT_GENDER = "male";

	private final JsonObject body;
	private final Consumer<JsonObject> commit;
	private boolean dirty;
	private long changedAt;
	private int gendersRight;
	private int physicsRight;
	private int physicsY = Integer.MIN_VALUE;
	private int[] rules = new int[0];

	/** A panel for {@code start}, copied; what is set goes to {@code commit}. */
	BodyPanel(JsonObject start, Consumer<JsonObject> commit) {
		this.body = start == null ? new JsonObject() : Body.copy(start);
		this.commit = commit;
	}

	/** The body as set now, for the figure. */
	JsonObject body() {
		return body;
	}

	private String gender() {
		JsonElement value = body.get(Body.GENDER);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : DEFAULT_GENDER;
	}

	private boolean breasts() {
		return !gender().equals("male");
	}

	private boolean flag(String name, boolean otherwise) {
		JsonElement value = body.get(name);
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() ? value.getAsBoolean() : otherwise;
	}

	private float number(String name, float otherwise) {
		JsonElement value = body.get(name);
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsFloat() : otherwise;
	}

	/**
	 * The widgets, from (x0, y0) to x1, under each other in their groups. {@code rebuilt} lays the
	 * screen out again, after a switch that makes others usable or not; {@code window} opens the
	 * mod's own window, or is null where there is none to open.
	 */
	List<AbstractWidget> widgets(int x0, int y0, int x1, Runnable rebuilt, Runnable window) {
		List<AbstractWidget> out = new ArrayList<>();
		int width = x1 - x0;
		int half = (width - GAP) / 2;
		int y = y0;
		List<Integer> lines = new ArrayList<>();

		// The gender, the one set lit; and the mod's own window for all the rest of its settings.
		String[] genders = Body.GENDERS;
		Icon[] icons = { Icon.GENDER, Icon.MALE, Icon.OTHER_GENDER };
		int x = x0;
		for (int i = 0; i < genders.length; i++) {
			String one = genders[i];
			out.add(IconButton.square(x, y, ROW, icons[i], Component.translatable("mosaicary.body.gender." + one), () -> {
				body.addProperty(Body.GENDER, one);
				commitNow();
				rebuilt.run();
			}).primary(gender().equals(one)));
			x += ROW + GAP;
		}
		gendersRight = x;
		if (window != null) {
			out.add(IconButton.square(x1 - ROW, y, ROW, Icon.GENDER_WINDOW, Component.translatable("mosaicary.body.window"), window));
		}
		y += ROW;
		lines.add(y + RULE / 2);
		y += RULE;

		// The breasts: how large, then where, as the mod lays its own sliders out.
		boolean breasts = breasts();
		out.add(slider(x0, y, width, "bust_size", 0.6f, 0.01f, "mosaicary.body.size", v -> v / range("bust_size")[1] * 100, breasts));
		y += ROW + GAP;
		out.add(slider(x0, y, half, "breasts_xOffset", 0, 0.1f, "mosaicary.body.separation", v -> v * 10, breasts));
		out.add(slider(x1 - half, y, half, "breasts_yOffset", 0, 0.1f, "mosaicary.body.height", v -> v * 10, breasts));
		y += ROW + GAP;
		out.add(slider(x0, y, half, "breasts_zOffset", 0, 0.1f, "mosaicary.body.depth", v -> v * 10, breasts));
		out.add(slider(x1 - half, y, half, "breasts_cleavage", 0, 0.01f, "mosaicary.body.rotation", v -> v * 100, breasts));
		y += ROW;
		lines.add(y + RULE / 2);
		y += RULE;

		// Their physics: on or off, each breast on its own or both as one, and how they move.
		boolean physics = flag("breast_physics", true);
		IconButton physicsButton = IconButton.square(x0, y, ROW, Icon.PHYSICS, Component.translatable("mosaicary.body.physics"), () -> {
			body.addProperty("breast_physics", !physics);
			commitNow();
			rebuilt.run();
		}).primary(physics);
		physicsButton.active = breasts;
		out.add(physicsButton);
		boolean dual = !flag("breasts_uniboob", true);
		IconButton dualButton = IconButton.square(x0 + ROW + GAP, y, ROW, Icon.DUAL_PHYSICS, Component.translatable("mosaicary.body.dual"), () -> {
			body.addProperty("breasts_uniboob", dual);
			commitNow();
			rebuilt.run();
		}).primary(dual);
		dualButton.active = breasts && physics;
		out.add(dualButton);
		physicsY = y;
		physicsRight = x0 + 2 * (ROW + GAP);
		y += ROW + GAP;
		out.add(slider(x0, y, half, "bounce_multiplier", 0.333f, 0.005f, "mosaicary.body.bounce", v -> v * 300, breasts && physics));
		out.add(slider(x1 - half, y, half, "floppy_multiplier", 0.75f, 0.01f, "mosaicary.body.momentum", v -> v * 100, breasts && physics));
		y += ROW;

		// The voice, where the line has one.
		if (GenderMod.has("voice_pitch")) {
			lines.add(y + RULE / 2);
			y += RULE;
			out.add(slider(x0, y, width, "voice_pitch", 1, 0.01f, "mosaicary.body.voice", v -> v * 100, true));
			y += ROW;
		}
		rules = lines.stream().mapToInt(Integer::intValue).toArray();
		return out;
	}

	private static float[] range(String name) {
		float[] range = GenderMod.range(name);
		return range != null ? range : Body.NUMBERS.get(name);
	}

	private Slider slider(int x, int y, int width, String name, float otherwise, float step, String key, DoubleUnaryOperator shown, boolean active) {
		Slider slider = new Slider(x, y, width, name, number(name, otherwise), step, key, shown);
		slider.active = active;
		return slider;
	}

	/** The lines between the groups, and the names beside the gender and the physics switches. */
	void draw(GuiGraphicsExtractor graphics, Font font, int x0, int x1) {
		for (int y : rules) graphics.fill(x0, y, x1, y + 1, Theme.EDGE);
		int shift = (ROW - font.lineHeight) / 2 + 1;
		int nameY = rules.length > 0 ? rules[0] - RULE / 2 - ROW + shift : 0;
		graphics.text(font, Component.translatable("mosaicary.body.gender." + gender()), gendersRight + 2, nameY, Theme.TEXT, false);
		graphics.text(font, Component.translatable("mosaicary.body.physics.name"), physicsRight + 2, physicsY + shift,
			breasts() ? Theme.TEXT : Theme.TEXT_OFF, false);
	}

	/** Called every tick: what a slider set goes out once it has rested. */
	void tick() {
		if (dirty && System.currentTimeMillis() - changedAt >= REST_MILLIS) commitNow();
	}

	/** What is set and has not gone out yet goes now: the panel is closing. */
	void flush() {
		if (dirty) commitNow();
	}

	private void commitNow() {
		dirty = false;
		commit.accept(Body.copy(body));
	}

	/** A number of the body between the line's limits, in steps, shown as the mod's own slider shows it. */
	private final class Slider extends AbstractSliderButton {

		private final String name;
		private final float low;
		private final float high;
		private final float step;
		private final String key;
		private final DoubleUnaryOperator shown;
		/** The number shown: as it is until the slider is moved, which puts it on the slider's steps. */
		private double number;

		Slider(int x, int y, int width, String name, float now, float step, String key, DoubleUnaryOperator shown) {
			super(x, y, width, ROW, Component.empty(), 0);
			float[] range = range(name);
			this.name = name;
			this.low = range[0];
			this.high = range[1];
			this.step = step;
			this.key = key;
			this.shown = shown;
			this.value = Math.max(0, Math.min(1, (now - low) / (high - low)));
			this.number = now;
			updateMessage();
		}

		/** The number where the slider stands, in its steps, to four places as a body keeps it. */
		private double at() {
			double raw = low + value * (high - low);
			double stepped = Math.round(raw / step) * (double) step;
			return Math.round(Math.max(low, Math.min(high, stepped)) * 10_000) / 10_000.0;
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.translatable(key, Math.round(shown.applyAsDouble(number))));
		}

		@Override
		protected void applyValue() {
			number = at();
			body.addProperty(name, number);
			dirty = true;
			changedAt = System.currentTimeMillis();
		}

		//#if MC>=11903
		/**
		 * Drawn as the catalogue's own widgets are: a card, filled with the accent's dark shade up to
		 * the handle, the handle a bar of the accent, the words in the middle; greyed out when unusable.
		 */
		@Override
		public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			boolean on = active;
			boolean hovered = on && isHovered();
			int x0 = getX();
			int y0 = getY();
			int x1 = x0 + getWidth();
			int y1 = y0 + getHeight();
			Theme.card(graphics, x0, y0, x1, y1, hovered ? Theme.CARD_HOVER : Theme.CARD, hovered ? Theme.EDGE_HOVER : Theme.EDGE);
			int handle = x0 + 1 + (int) Math.round(value * (getWidth() - 2 - HANDLE));
			if (on) graphics.fill(x0 + 1, y0 + 1, handle, y1 - 1, Theme.ACCENT_DARK);
			graphics.fill(handle, y0 + 1, handle + HANDLE, y1 - 1, on ? Theme.ACCENT : Theme.EDGE_HOVER);
			Font font = net.minecraft.client.Minecraft.getInstance().font;
			Component words = getMessage();
			graphics.text(font, words, x0 + (getWidth() - font.width(words)) / 2, y0 + (getHeight() - font.lineHeight) / 2 + 1,
				on ? Theme.TEXT : Theme.TEXT_OFF, false);
		}
		//#else
		//$$ // The same before 1.19.3, where a widget's place and hover are fields and drawing goes on a PoseStack.
		//$$ @Override
		//$$ public void renderButton(com.mojang.blaze3d.vertex.PoseStack graphics, int mouseX, int mouseY, float a) {
		//$$ 	boolean on = active;
		//$$ 	boolean hovered = on && isHovered;
		//$$ 	int x0 = x;
		//$$ 	int y0 = y;
		//$$ 	int x1 = x0 + width;
		//$$ 	int y1 = y0 + height;
		//$$ 	Theme.card(graphics, x0, y0, x1, y1, hovered ? Theme.CARD_HOVER : Theme.CARD, hovered ? Theme.EDGE_HOVER : Theme.EDGE);
		//$$ 	int handle = x0 + 1 + (int) Math.round(value * (width - 2 - HANDLE));
		//$$ 	if (on) net.minecraft.client.gui.GuiComponent.fill(graphics, x0 + 1, y0 + 1, handle, y1 - 1, Theme.ACCENT_DARK);
		//$$ 	net.minecraft.client.gui.GuiComponent.fill(graphics, handle, y0 + 1, handle + HANDLE, y1 - 1, on ? Theme.ACCENT : Theme.EDGE_HOVER);
		//$$ 	Font font = net.minecraft.client.Minecraft.getInstance().font;
		//$$ 	Component words = getMessage();
		//$$ 	com.mopicmp.mosaicary.client.platform.Gfx.text(graphics, font, words, x0 + (width - font.width(words)) / 2,
		//$$ 		y0 + (height - font.lineHeight) / 2 + 1, on ? Theme.TEXT : Theme.TEXT_OFF, false);
		//$$ }
		//#endif
	}

	/** How wide the slider's handle is. */
	private static final int HANDLE = 3;
}

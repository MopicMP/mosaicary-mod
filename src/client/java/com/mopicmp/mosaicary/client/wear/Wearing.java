package com.mopicmp.mosaicary.client.wear;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import com.mopicmp.mosaicary.Mosaicary;
import com.mopicmp.mosaicary.client.Catalogue;
import com.mopicmp.mosaicary.client.model.BbModel;
import com.mopicmp.mosaicary.client.model.Motion;
import com.mopicmp.mosaicary.client.model.Posing;
import com.mopicmp.mosaicary.client.model.Rig;
import com.mopicmp.mosaicary.client.model.Roles;
import com.mopicmp.mosaicary.client.render.ModelMesh;
import com.mopicmp.mosaicary.client.site.ModelCard;
import com.mopicmp.mosaicary.client.site.Settings;
import com.mopicmp.mosaicary.client.site.SiteClient;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;

/**
 * Who wears which model, with which animation roles, and what each worn model looks
 * like this frame.
 *
 * The local player's model is kept in the settings, so it survives a restart, and so
 * are the roles the player chose for each model. Other players' models and roles
 * arrive from the site relay or from a server that has the mod, and live in maps by
 * UUID; rendering reads both the same way.
 *
 * A model marked for adults is never drawn on another player: the mod has no sign-in
 * and so no confirmed age. The local player cannot wear one either, because the
 * catalogue does not list them without a sign-in.
 */
public final class Wearing {

	/**
	 * What the renderer needs for one player this frame.
	 *
	 * @param scale the size the model is drawn at, over its own size
	 */
	public record Worn(ModelMesh mesh, Posing posing, boolean crouching, float scale) { }

	/** The sizes a player may pick, over the model's own size. */
	public static final double SCALE_MIN = 0.1;
	public static final double SCALE_MAX = 10;

	/**
	 * The tallest another player's model is drawn, in blocks, whatever size its wearer
	 * picked: one player's giant cannot cover a server. Twice the height of a player.
	 */
	public static final double OTHERS_TALLEST = 3.6;

	/** Where the extracted frame travels from the entity to the renderer. */
	public static final RenderStateDataKey<Worn> KEY = RenderStateDataKey.create(() -> "Mosaicary worn model");

	/** Told whenever the local player's model or its roles change, to pass it on. */
	public interface Listener {
		void changed(ModelCard worn, Map<String, String> roles);
	}

	/**
	 * What a player was last seen doing, and since when, so animations restart when it
	 * changes: the body action, each hand, and the last time they were hurt.
	 */
	private static final class Doing {
		Roles.Action action;
		double since;
		Motion.Hand left = Motion.Hand.EMPTY;
		double leftSince;
		Motion.Hand right = Motion.Hand.EMPTY;
		double rightSince;
		boolean hurt;
		double hurtAt = -1;
		/** What plays on the body and on each arm, said as one line; a change starts a blend. */
		String chosen;
		/** The frame on screen last time, blended or not, and the model it was for. */
		Posing.Frame shown;
		ModelMesh shownFor;
		/** The frame a blend starts from, when one is running; when it started and for how long. */
		Posing.Frame from;
		double blendAt;
		double blendFor;
	}

	/**
	 * How long one animation takes to give way to the next, in seconds.
	 *
	 * Without it a change of what the player is doing snapped the whole model from one
	 * pose to the next in one frame. The swing and getting hurt come and go faster, as
	 * the game's own arm does.
	 */
	private static final double BLEND = 0.2;
	private static final double BLEND_QUICK = 0.1;

	private final Catalogue catalogue;
	private final Settings settings;
	private final Path settingsFile;
	private final Map<UUID, ModelCard> others = new HashMap<>();
	private final Map<UUID, Map<String, String>> otherRoles = new HashMap<>();
	private final Map<UUID, Double> otherScales = new HashMap<>();
	private final Map<UUID, Doing> doing = new HashMap<>();
	private final Map<ModelMesh, Rig> rigs = new IdentityHashMap<>();
	private final java.util.List<Listener> listeners = new CopyOnWriteArrayList<>();
	private int unreadableLogged;

	public Wearing(Catalogue catalogue, Settings settings, Path settingsFile) {
		this.catalogue = catalogue;
		this.settings = settings;
		this.settingsFile = settingsFile;
		if (settings.worn != null) catalogue.pin(settings.worn.card());
	}

	public void listen(Listener listener) {
		listeners.add(listener);
	}

	private void tell() {
		ModelCard worn = worn();
		Map<String, String> roles = worn == null ? Map.of() : rolesToSend(worn);
		for (Listener listener : listeners) listener.changed(worn, roles);
	}

	// ------------------------------------------------------------------ the local player

	/** The model the local player wears, or null. */
	public ModelCard worn() {
		return settings.worn == null ? null : settings.worn.card();
	}

	public boolean isWorn(ModelCard card) {
		return settings.worn != null && settings.worn.id.equals(card.id());
	}

	private static String player() {
		return String.valueOf(Minecraft.getInstance().getUser().getProfileId());
	}

	/**
	 * Puts a model on the local player. The first time a player wears a model, the
	 * site counts it as a download, once; which player has had which model counted is
	 * remembered in the settings, so pressing Wear again counts nothing.
	 */
	public void wear(ModelCard card) {
		if (card.adult()) return;
		ModelCard before = worn();
		if (before != null && !before.id().equals(card.id())) catalogue.unpin(before);
		settings.worn = Settings.Saved.of(card);
		catalogue.pin(card);
		catalogue.model(card);
		// Once per model for this player: an install shared by two accounts counts each.
		String countKey = player() + "/" + card.id();
		if (!settings.counted.contains(countKey) && !card.isLocal()) {
			settings.counted.add(countKey);
			catalogue.site().counted(card).whenComplete((bytes, problem) -> {
				if (problem != null) {
					Mosaicary.LOGGER.debug("Counting the wear of {} failed: {}", card.id(), SiteClient.describe(problem));
				}
			});
		}
		settings.save(settingsFile);
		tell();
	}

	/** Back to the player's own skin. */
	public void takeOff() {
		ModelCard before = worn();
		settings.worn = null;
		settings.save(settingsFile);
		if (before != null) catalogue.unpin(before);
		tell();
	}

	// ------------------------------------------------------------------ roles

	/** The roles the local player has for a model: filled in from the names, with their own choices on top. */
	public Roles rolesFor(ModelCard card, BbModel model) {
		return Roles.auto(model).with(settings.roles.get(player() + "/" + card.id()), model);
	}

	/** Sets one role for a model, keeping only what differs from the filled-in roles. */
	public void setRole(ModelCard card, BbModel model, String key, String value) {
		Map<String, String> chosen = new LinkedHashMap<>(settings.roles.getOrDefault(player() + "/" + card.id(), Map.of()));
		chosen.put(key, value);
		Roles base = Roles.auto(model);
		Map<String, String> kept = base.with(chosen, model).differencesFrom(base);
		String stored = player() + "/" + card.id();
		if (kept.isEmpty()) settings.roles.remove(stored);
		else settings.roles.put(stored, kept);
		settings.save(settingsFile);
		if (isWorn(card)) tell();
	}

	/**
	 * The roles that travel with the local player's worn model. All of them, not only
	 * the changed ones: another player may have an older mod whose names fill in
	 * differently, and what they see should be what this player chose.
	 */
	public Map<String, String> rolesToSend(ModelCard card) {
		Catalogue.Model model = catalogue.model(card);
		if (model.state() != Catalogue.State.READY) {
			return new LinkedHashMap<>(settings.roles.getOrDefault(player() + "/" + card.id(), Map.of()));
		}
		return rolesFor(card, model.mesh().model()).all();
	}

	// ------------------------------------------------------------------ size

	/** A size kept within what can be picked, to a thousandth, as the site keeps it. */
	public static double cleanScale(double scale) {
		if (!Double.isFinite(scale)) return 1;
		return Math.round(Math.max(SCALE_MIN, Math.min(SCALE_MAX, scale)) * 1000) / 1000.0;
	}

	/** The size the local player picked for a model, over the model's own size; 1 when none. */
	public double scaleFor(ModelCard card) {
		return settings.scales.getOrDefault(player() + "/" + card.id(), 1.0);
	}

	/** Sets the size for a model; the model's own size is not stored. */
	public void setScale(ModelCard card, double scale) {
		double clean = cleanScale(scale);
		String key = player() + "/" + card.id();
		if (clean == 1) settings.scales.remove(key);
		else settings.scales.put(key, clean);
		settings.save(settingsFile);
		if (isWorn(card)) tell();
	}

	// ------------------------------------------------------------------ other players

	/** Sets the model, roles and size another player wears, or clears them with a null card. */
	public void setOther(UUID player, ModelCard card, Map<String, String> roles, double scale) {
		ModelCard before = others.get(player);
		if (card == null || card.adult()) {
			others.remove(player);
			otherRoles.remove(player);
			otherScales.remove(player);
			if (before != null) catalogue.unpin(before);
			return;
		}
		if (before != null && !before.id().equals(card.id())) catalogue.unpin(before);
		others.put(player, card);
		otherRoles.put(player, roles == null ? Map.of() : roles);
		otherScales.put(player, cleanScale(scale));
		catalogue.pin(card);
		catalogue.model(card);
	}

	/** The model another player is known to wear, or null. */
	public ModelCard other(UUID player) {
		return others.get(player);
	}

	/** Forgets every other player, for leaving a world. */
	public void forgetOthers() {
		for (ModelCard card : others.values()) catalogue.unpin(card);
		others.clear();
		otherRoles.clear();
		otherScales.clear();
		doing.clear();
	}

	// ------------------------------------------------------------------ per frame

	/**
	 * Fills the render state of a player with the worn model's frame, or clears it.
	 * Called while the game extracts the player's render state, on the client thread.
	 */
	public void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		state.setData(KEY, null);
		UUID uuid = entity.getUUID();
		boolean local = isLocal(uuid);
		ModelCard card = local ? worn() : others.get(uuid);
		if (card == null || (!local && card.adult())) return;
		Catalogue.Model model = catalogue.model(card);
		if (model.state() != Catalogue.State.READY) return;
		ModelMesh mesh = model.mesh();
		BbModel parsed = mesh.model();
		Rig rig = rigs.computeIfAbsent(mesh, m -> Rig.of(parsed));
		Roles roles = local ? rolesFor(card, parsed) : Roles.auto(parsed).with(otherRoles.get(uuid), parsed);

		double now = (entity.tickCount + partialTicks) / 20.0;
		Doing doing = this.doing.computeIfAbsent(uuid, id -> new Doing());

		// Moving against the way the body faces: the body's forward is (-sin, cos) of its turn.
		double dx = entity.getX() - entity.xo;
		double dz = entity.getZ() - entity.zo;
		double yaw = Math.toRadians(state.bodyRot);
		boolean backwards = dx * -Math.sin(yaw) + dz * Math.cos(yaw) < -0.003;
		Motion.State flags = new Motion.State(state.isCrouching, entity.isSprinting(), entity.onGround(),
			state.isVisuallySwimming, state.isInWater, state.isFallFlying, entity.hasPose(Pose.SLEEPING), state.isPassenger,
			state.isAutoSpinAttack, backwards, state.deathTime > 0);
		Roles.Action action = Motion.action(flags, state.walkAnimationSpeed);
		if (doing.action != action || doing.since > now) {
			doing.action = action;
			doing.since = now;
		}

		// The hands, by the model's arms; the game's main hand is the right arm unless set to the left.
		Motion.Hand left = hand(entity, state, HumanoidArm.LEFT);
		Motion.Hand right = hand(entity, state, HumanoidArm.RIGHT);
		if (doing.left != left || doing.leftSince > now) {
			doing.left = left;
			doing.leftSince = now;
		}
		if (doing.right != right || doing.rightSince > now) {
			doing.right = right;
			doing.rightSince = now;
		}
		InteractionHand swingHand = state.currentSwing != null ? state.currentSwing.hand() : InteractionHand.MAIN_HAND;
		HumanoidArm swingArm = swingHand == InteractionHand.MAIN_HAND ? state.mainArm : state.mainArm.getOpposite();
		Motion.Arms arms = new Motion.Arms(left, right, swingArm == HumanoidArm.LEFT ? Roles.Side.LEFT : Roles.Side.RIGHT,
			now - doing.leftSince, now - doing.rightSince);

		// Hurt: from the moment the game starts its red flash.
		boolean hurt = entity.hurtTime > 0;
		if (hurt && !doing.hurt) doing.hurtAt = now;
		doing.hurt = hurt;
		double hurtSeconds = doing.hurtAt >= 0 && now >= doing.hurtAt && now - doing.hurtAt < 10 ? now - doing.hurtAt : -1;

		Motion motion = new Motion(flags, state.yRot, state.xRot, state.walkAnimationPos, state.walkAnimationSpeed,
			state.swingAnimation, now - doing.since, arms, hurtSeconds);
		int[] unreadable = { 0 };
		Posing.Frame frame = blended(doing, mesh, now, motion.frame(parsed, rig, roles, unreadable),
			action + "|" + Motion.itemRole(arms, Roles.Side.LEFT, state.swingAnimation) + "|"
				+ Motion.itemRole(arms, Roles.Side.RIGHT, state.swingAnimation),
			(state.swingAnimation > 0 ? "swing" : "") + (hurt ? "|hurt" : ""));
		if (unreadable[0] > 0 && unreadableLogged < 20) {
			unreadableLogged++;
			Mosaicary.LOGGER.info("Model {}: {} animation values could not be read and count as 0", card.id(), unreadable[0]);
		}
		state.setData(KEY, new Worn(mesh, frame, state.isCrouching, (float) drawnScale(local ? scaleFor(card) : otherScales.getOrDefault(uuid, 1.0), mesh, local)));
	}

	/**
	 * The frame to draw: this moment's, blended out of the last one shown when what plays
	 * has just changed.
	 *
	 * The blend starts from the frame that was on screen, blended or not, and not from
	 * the old animation's own frame. A change in the middle of a blend (walking and
	 * standing flick back and forth as a player comes to a stop) then carries on from
	 * where the model is, rather than jumping back to where the first blend began. The
	 * same reason npc_studio's emotes blend from where the limbs are, not from rest.
	 *
	 * @param body  what plays on the body and the arms; a change here blends slowly
	 * @param quick the swing and the hurt; a change here alone blends quickly
	 */
	private static Posing.Frame blended(Doing doing, ModelMesh mesh, double now, Posing.Frame frame, String body, String quick) {
		String chosen = body + "#" + quick;
		if (doing.shownFor != mesh || doing.shown == null) {
			// Another model, or the first frame: nothing on screen to blend out of.
			doing.from = null;
		} else if (!chosen.equals(doing.chosen)) {
			String was = doing.chosen == null ? "" : doing.chosen;
			doing.from = doing.shown;
			doing.blendAt = now;
			doing.blendFor = was.startsWith(body + "#") ? BLEND_QUICK : BLEND;
		}
		doing.chosen = chosen;
		Posing.Frame shown = frame;
		if (doing.from != null) {
			double t = (now - doing.blendAt) / doing.blendFor;
			if (t < 0 || t >= 1) {
				doing.from = null;
			} else {
				float eased = (float) (t * t * (3 - 2 * t));
				shown = Posing.Frame.blend(doing.from, frame, eased);
			}
		}
		doing.shown = shown;
		doing.shownFor = mesh;
		return shown;
	}

	/**
	 * The size to draw at: the picked one, except that another player's model is never
	 * drawn taller than {@link #OTHERS_TALLEST} blocks.
	 */
	static double drawnScale(double picked, ModelMesh mesh, boolean local) {
		double scale = cleanScale(picked);
		if (local) return scale;
		float[] box = mesh.bounds();
		if (box == null) return scale;
		double blocks = (box[4] - box[1]) / 16.0 * scale;
		return blocks > OTHERS_TALLEST ? scale * OTHERS_TALLEST / blocks : scale;
	}

	/** What a hand of the model's arm on this side does with its item. */
	private static Motion.Hand hand(Avatar entity, AvatarRenderState state, HumanoidArm arm) {
		if (entity.isUsingItem()) {
			HumanoidArm using = entity.getUsedItemHand() == InteractionHand.MAIN_HAND ? state.mainArm : state.mainArm.getOpposite();
			if (using == arm) {
				ItemUseAnimation use = entity.getUseItem().getUseAnimation();
				if (use == ItemUseAnimation.EAT || use == ItemUseAnimation.DRINK || use == ItemUseAnimation.SPYGLASS
					|| use == ItemUseAnimation.TOOT_HORN || use == ItemUseAnimation.BUNDLE) return Motion.Hand.USE_SELF;
				if (use == ItemUseAnimation.BLOCK || use == ItemUseAnimation.BOW || use == ItemUseAnimation.TRIDENT
					|| use == ItemUseAnimation.CROSSBOW || use == ItemUseAnimation.SPEAR) return Motion.Hand.CHARGE;
				return Motion.Hand.HOLD;
			}
		}
		ItemStack stack = arm == HumanoidArm.RIGHT ? state.rightHandItemStack : state.leftHandItemStack;
		return stack == null || stack.isEmpty() ? Motion.Hand.EMPTY : Motion.Hand.HOLD;
	}

	private static boolean isLocal(UUID uuid) {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.player != null && minecraft.player.getUUID().equals(uuid);
	}

	/** The rig found for a mesh, for tests and the detail view. */
	public Rig rigOf(ModelMesh mesh) {
		return rigs.computeIfAbsent(mesh, m -> Rig.of(m.model()));
	}
}

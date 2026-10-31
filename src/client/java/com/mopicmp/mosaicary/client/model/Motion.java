package com.mopicmp.mosaicary.client.model;

import java.util.Map;

/**
 * How a worn model moves with the player wearing it.
 *
 * <h2>The body</h2>
 *
 * The player is doing one body action at a time, chosen by this priority when
 * several apply: dying, sleeping, the riptide spin, swimming (crawling when not in
 * water), elytra flight, riding, crouching, in the air, sprinting, walking, standing.
 * The order goes from the state that changes the whole body the most to the least:
 * a player swimming is also moving and sprinting, and the swim is what should be
 * seen. Moving against the way the body faces picks the backwards variant of
 * walking, crouching, jumping and crawling. Death is held on its last frame.
 *
 * <h2>The arms</h2>
 *
 * Over the body, each arm on its own: while that hand holds, charges (a bow, a
 * crossbow, a trident, a raised shield), uses on itself (eating, drinking) or
 * attacks with an item, the role of that item state plays; while the hand is
 * empty, the arm layer of the body action (if any) plays on that arm only. An
 * attack with the empty hand is the swing: laid over the arms when its animation
 * moves only arms, otherwise it takes the whole body while it lasts.
 *
 * <h2>Events</h2>
 *
 * Getting hurt plays the hurt role once, over everything.
 *
 * <h2>The game's motion</h2>
 *
 * On whichever of the six humanoid parts the model names ({@link Rig}): arms and
 * legs swing with the walk, the body leans when crouching, the legs go forward when
 * riding, an arm holding something comes forward a little, an arm charging points
 * where the player looks, an arm attacking swings. The head follows where the player
 * looks in every action.
 *
 * The game's angles are converted to Blockbench's: a model part in the game is drawn
 * under a half turn about Z (its x and y axes point the other way from Blockbench's),
 * so a turn about X or Y changes sign and a turn about Z does not.
 *
 * @param state       what the player is doing
 * @param headYaw     the head's turn relative to the body, degrees, as the game gives it
 * @param headPitch   the head's pitch, degrees, positive looking down
 * @param walkPos     the game's walk cycle position
 * @param walkSpeed   the game's walk cycle amplitude, 0 standing to 1 at full speed
 * @param attack      how far through an attack swing, 0 to 1, 0 when not attacking
 * @param seconds     seconds since the current body action started
 * @param arms        what each hand is doing
 * @param hurtSeconds seconds since the player was last hurt, or a negative number
 */
public record Motion(State state, float headYaw, float headPitch, float walkPos, float walkSpeed, float attack,
		double seconds, Arms arms, double hurtSeconds) {

	/** The player's state flags, as the game reports them. */
	public record State(boolean crouching, boolean sprinting, boolean onGround, boolean swimming, boolean inWater,
			boolean fallFlying, boolean sleeping, boolean riding, boolean spinning, boolean backwards, boolean dying) {

		public static final State STANDING = new State(false, false, true, false, false, false, false, false, false, false, false);
	}

	/** What one hand does with an item; an attack is told apart by {@link Arms#swing}. */
	public enum Hand { EMPTY, HOLD, CHARGE, USE_SELF }

	/**
	 * Both hands.
	 *
	 * @param left         the model's left arm's hand
	 * @param right        the right one's
	 * @param swing        the arm an attack swings, or null when nobody attacks
	 * @param leftSeconds  seconds since the left hand's state changed
	 * @param rightSeconds the same for the right
	 */
	public record Arms(Hand left, Hand right, Roles.Side swing, double leftSeconds, double rightSeconds) {

		public static final Arms EMPTY = new Arms(Hand.EMPTY, Hand.EMPTY, Roles.Side.RIGHT, 0, 0);

		public Hand of(Roles.Side side) {
			return side == Roles.Side.LEFT ? left : right;
		}

		public double secondsOf(Roles.Side side) {
			return side == Roles.Side.LEFT ? leftSeconds : rightSeconds;
		}
	}

	/** A motion with empty hands and no hurt, for previews and tests. */
	public Motion(State state, float headYaw, float headPitch, float walkPos, float walkSpeed, float attack, double seconds) {
		this(state, headYaw, headPitch, walkPos, walkSpeed, attack, seconds, Arms.EMPTY, -1);
	}

	/** Below this walk speed the player counts as standing. */
	private static final float MOVING = 0.15f;

	/** How long a hurt that has a still pose rather than an animation is shown, in seconds: the game's red flash. */
	private static final double HURT_POSE = 0.5;

	/** How far a crouching model is lowered, in model pixels. */
	public static final float CROUCH_DROP = 2;

	/** The body action that plays, by the priority above. */
	public static Roles.Action action(State state, float walkSpeed) {
		boolean moving = walkSpeed > MOVING;
		boolean back = moving && state.backwards();
		if (state.dying()) return Roles.Action.DEATH;
		if (state.sleeping()) return Roles.Action.SLEEP;
		if (state.spinning()) return Roles.Action.SPIN;
		if (state.swimming()) {
			if (state.inWater()) return Roles.Action.SWIM;
			return !moving ? Roles.Action.CRAWL : back ? Roles.Action.CRAWL_BACK : Roles.Action.CRAWL_WALK;
		}
		if (state.fallFlying()) return Roles.Action.ELYTRA;
		if (state.riding()) return Roles.Action.RIDE;
		if (state.crouching()) return !moving ? Roles.Action.CROUCH : back ? Roles.Action.CROUCH_BACK : Roles.Action.CROUCH_WALK;
		if (!state.onGround()) return back ? Roles.Action.JUMP_BACK : Roles.Action.JUMP;
		if (moving && state.sprinting() && !back) return Roles.Action.SPRINT;
		if (moving) return back ? Roles.Action.WALK_BACK : Roles.Action.WALK;
		return Roles.Action.IDLE;
	}

	public Roles.Action action() {
		return action(state, walkSpeed);
	}

	/** The role an arm plays now: an item state, or null for an empty hand that is not attacking. */
	public static Roles.Action itemRole(Arms arms, Roles.Side side, float attack) {
		Hand hand = arms.of(side);
		boolean left = side == Roles.Side.LEFT;
		if (attack > 0 && arms.swing() == side && hand != Hand.EMPTY) return left ? Roles.Action.THRUST_LEFT : Roles.Action.THRUST_RIGHT;
		return switch (hand) {
			case HOLD -> left ? Roles.Action.HOLD_LEFT : Roles.Action.HOLD_RIGHT;
			case CHARGE -> left ? Roles.Action.CHARGE_LEFT : Roles.Action.CHARGE_RIGHT;
			case USE_SELF -> left ? Roles.Action.USE_SELF_LEFT : Roles.Action.USE_SELF_RIGHT;
			case EMPTY -> null;
		};
	}

	/**
	 * The frame for this moment.
	 *
	 * @param unreadable counts Molang values that could not be read; may be null
	 */
	public Posing.Frame frame(BbModel model, Rig rig, Roles roles, int[] unreadable) {
		Posing.Frame frame = new Posing.Frame();
		Map<String, Double> values = model.startingValues();
		values.put("query.ground_speed", (double) walkSpeed * 4);
		values.put("query.modified_move_speed", (double) walkSpeed);
		values.put("query.modified_distance_moved", (double) walkPos);
		values.put("query.is_sneaking", state.crouching() ? 1.0 : 0.0);
		values.put("query.is_on_ground", state.onGround() ? 1.0 : 0.0);

		// The body.
		Roles.Action action = action();
		Animation clip = roles.animation(model, action);
		if (clip != null) {
			double at = action == Roles.Action.DEATH ? Math.min(seconds, clip.length()) : clip.timeInPlay(seconds);
			frame.add(clip.sample(at, values, unreadable));
		} else if (Roles.VANILLA.equals(roles.get(action))) {
			vanilla(frame, rig, action);
		}

		// The arms, each on its own; death leaves them to the death.
		if (action != Roles.Action.DEATH) {
			for (Roles.Side side : Roles.Side.values()) arm(frame, model, rig, roles, values, unreadable, action, side);
			Roles.Side swinging = arms.swing() == null ? Roles.Side.RIGHT : arms.swing();
			if (attack > 0 && arms.of(swinging) == Hand.EMPTY) swing(frame, model, rig, roles, values, unreadable, swinging);
		}

		for (Bone head : rig.all(Rig.Part.HEAD)) {
			Animation.Change change = frame.of(head);
			change.rotation[0] += -headPitch;
			change.rotation[1] += -headYaw;
		}

		// Hurt, once, over everything.
		if (hurtSeconds >= 0) {
			Animation hurt = roles.animation(model, Roles.Action.HURT);
			if (hurt != null && hurtSeconds <= Math.max(hurt.length(), HURT_POSE)) {
				over(frame, hurt.sample(Math.min(hurtSeconds, hurt.length()), values, unreadable));
			}
		}
		return frame;
	}

	/** One arm: its item state's role, or the arm layer of the body action while the hand is empty. */
	private void arm(Posing.Frame frame, BbModel model, Rig rig, Roles roles, Map<String, Double> values, int[] unreadable,
			Roles.Action action, Roles.Side side) {
		Roles.Action item = itemRole(arms, side, attack);
		if (item != null) {
			Animation clip = roles.animation(model, item);
			boolean thrust = item == Roles.Action.THRUST_LEFT || item == Roles.Action.THRUST_RIGHT;
			if (clip != null) {
				double at = thrust ? attack * clip.length() : clip.timeInPlay(arms.secondsOf(side));
				over(frame, clip.sample(at, values, unreadable));
			} else if (Roles.VANILLA.equals(roles.get(item))) {
				Rig.Part part = side == Roles.Side.LEFT ? Rig.Part.LEFT_ARM : Rig.Part.RIGHT_ARM;
				switch (arms.of(side)) {
					case CHARGE -> turn(frame, rig, part, (float) (-Math.PI / 2 + Math.toRadians(headPitch)), 0);
					default -> turn(frame, rig, part, (float) (-Math.PI / 10), 0);
				}
				if (thrust) {
					for (Bone arm : rig.all(part)) frame.of(arm).rotation[0] += (float) Math.toDegrees(Math.sin(attack * Math.PI) * 1.2);
				}
			}
			return;
		}
		if (!Roles.BODY.contains(action)) return;
		Animation layer = roles.animation(model, Roles.layerKey(action, side));
		if (layer == null) return;
		Map<Bone, Animation.Change> sample = layer.sample(layer.timeInPlay(seconds), values, unreadable);
		sample.keySet().removeIf(bone -> !rig.onArm(side, bone));
		over(frame, sample);
	}

	/** Changes that take the place of what the bones had, rather than adding to it. */
	private static void over(Posing.Frame frame, Map<Bone, Animation.Change> sample) {
		for (Map.Entry<Bone, Animation.Change> entry : sample.entrySet()) {
			Animation.Change into = frame.of(entry.getKey());
			for (int i = 0; i < 3; i++) {
				into.rotation[i] = entry.getValue().rotation[i];
				into.position[i] = entry.getValue().position[i];
				into.scale[i] = entry.getValue().scale[i];
			}
		}
	}

	/** The empty-hand swing: over the arms when it only moves arms, over everything otherwise. */
	private void swing(Posing.Frame frame, BbModel model, Rig rig, Roles roles, Map<String, Double> values,
			int[] unreadable, Roles.Side side) {
		String role = roles.get(Roles.Action.SWING);
		Animation clip = roles.animation(model, Roles.Action.SWING);
		if (clip != null) {
			Map<Bone, Animation.Change> sample = clip.sample(attack * clip.length(), values, unreadable);
			boolean armsOnly = !sample.isEmpty() && sample.keySet().stream().allMatch(rig::onArm);
			if (armsOnly) {
				over(frame, sample);
			} else {
				frame.clear();
				frame.add(sample);
			}
		} else if (Roles.VANILLA.equals(role)) {
			// The game's attack swing, simplified to its main motion: the arm goes up
			// and down once over the swing.
			for (Bone arm : rig.all(side == Roles.Side.LEFT ? Rig.Part.LEFT_ARM : Rig.Part.RIGHT_ARM)) {
				frame.of(arm).rotation[0] += (float) Math.toDegrees(Math.sin(attack * Math.PI) * 1.2);
			}
		}
	}

	/** The game's own motion for a body action, on the parts the model names. */
	private void vanilla(Posing.Frame frame, Rig rig, Roles.Action action) {
		switch (action) {
			case WALK, WALK_BACK, SPRINT, JUMP, JUMP_BACK, SWIM, CROUCH_WALK, CROUCH_BACK, CRAWL_WALK, CRAWL_BACK -> limbs(frame, rig);
			case RIDE -> {
				// The game's riding pose: legs forward and apart, arms a little forward.
				turn(frame, rig, Rig.Part.RIGHT_LEG, -1.4137f, 0.3142f);
				turn(frame, rig, Rig.Part.LEFT_LEG, -1.4137f, -0.3142f);
				turn(frame, rig, Rig.Part.RIGHT_ARM, -0.6283f, 0);
				turn(frame, rig, Rig.Part.LEFT_ARM, -0.6283f, 0);
			}
			default -> { }
		}
		if (action == Roles.Action.CROUCH || action == Roles.Action.CROUCH_WALK || action == Roles.Action.CROUCH_BACK) {
			// The game leans the body half a radian forward. Only a body that does not
			// carry the head: in rigs where everything hangs from the body, leaning it
			// would tip the whole figure over.
			for (Bone body : rig.all(Rig.Part.BODY)) {
				boolean carriesHead = false;
				for (Bone head : rig.all(Rig.Part.HEAD)) carriesHead |= Rig.contains(body, head);
				if (!carriesHead) turn(frame, body, 0.5f, 0);
			}
			turn(frame, rig, Rig.Part.RIGHT_ARM, 0.4f, 0);
			turn(frame, rig, Rig.Part.LEFT_ARM, 0.4f, 0);
		}
	}

	/** The game's humanoid limb swing: arms opposite the legs, legs a little wider. */
	private void limbs(Posing.Frame frame, Rig rig) {
		double cycle = walkPos * 0.6662;
		turn(frame, rig, Rig.Part.RIGHT_ARM, (float) (Math.cos(cycle + Math.PI) * walkSpeed), 0);
		turn(frame, rig, Rig.Part.LEFT_ARM, (float) (Math.cos(cycle) * walkSpeed), 0);
		turn(frame, rig, Rig.Part.RIGHT_LEG, (float) (Math.cos(cycle) * 1.4 * walkSpeed), 0);
		turn(frame, rig, Rig.Part.LEFT_LEG, (float) (Math.cos(cycle + Math.PI) * 1.4 * walkSpeed), 0);
	}

	/** The same turn for every bone that follows a part. */
	private static void turn(Posing.Frame frame, Rig rig, Rig.Part part, float gameX, float gameY) {
		for (Bone bone : rig.all(part)) turn(frame, bone, gameX, gameY);
	}

	/**
	 * A turn in the game's terms (radians about X and Y), given to a Blockbench bone:
	 * X and Y change sign.
	 */
	private static void turn(Posing.Frame frame, Bone bone, float gameX, float gameY) {
		if (bone == null) return;
		Animation.Change change = frame.of(bone);
		change.rotation[0] += (float) -Math.toDegrees(gameX);
		change.rotation[1] += (float) -Math.toDegrees(gameY);
	}
}

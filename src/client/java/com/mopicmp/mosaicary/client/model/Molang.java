package com.mopicmp.mosaicary.client.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The part of Molang that animation keyframes actually use.
 *
 * Measured on the seed sample before this was written: 99 % of keyframe values are
 * plain numbers, and the expressions that remain are arithmetic over
 * {@code query.anim_time} with {@code math.sin}, {@code math.cos},
 * {@code math.clamp} and a handful of other {@code math.*} functions, now and then
 * a {@code ?:}. That is what is supported: numbers, the four operators and
 * {@code %}, unary minus and not, comparisons, {@code &&}, {@code ||}, {@code ??},
 * the ternary, parentheses, {@code math.*} and named values.
 *
 * As in Molang, trigonometry works in degrees and a name nobody has set reads as
 * nought. An expression that cannot be read at all (assignments, loops,
 * {@code return}) also evaluates to nought; the caller counts those so a log line
 * can say how many there were.
 */
public final class Molang {

	/** A compiled expression. */
	public interface Expression {
		double eval(Map<String, Double> values);

		/** Whether this came from text that could not be read and so always gives nought. */
		default boolean unreadable() {
			return false;
		}
	}

	private static final Expression UNREADABLE = new Expression() {
		@Override
		public double eval(Map<String, Double> values) {
			return 0;
		}

		@Override
		public boolean unreadable() {
			return true;
		}
	};

	private static final Map<String, Expression> CACHE = new HashMap<>();

	private Molang() { }

	/** A constant, for values that are plain numbers. */
	public static Expression constant(double value) {
		return values -> value;
	}

	/**
	 * Compiles a keyframe value. Numbers and numeric strings become constants; text
	 * is parsed once and kept, since the same expression is often repeated across
	 * keyframes.
	 */
	public static Expression compile(String text) {
		if (text == null) return constant(0);
		String trimmed = text.trim();
		if (trimmed.isEmpty()) return constant(0);
		try {
			return constant(Double.parseDouble(trimmed));
		} catch (NumberFormatException notANumber) {
			// An expression, read below.
		}
		synchronized (CACHE) {
			Expression known = CACHE.get(trimmed);
			if (known != null) return known;
		}
		Expression made;
		try {
			Parser parser = new Parser(trimmed);
			made = parser.parse();
		} catch (RuntimeException unreadable) {
			made = UNREADABLE;
		}
		synchronized (CACHE) {
			if (CACHE.size() < 4096) CACHE.put(trimmed, made);
		}
		return made;
	}

	/** Molang's short prefixes written out, so {@code q.anim_time} and {@code query.anim_time} are one name. */
	public static String fullName(String name) {
		String lower = name.toLowerCase(Locale.ROOT);
		int dot = lower.indexOf('.');
		if (dot < 0) return lower;
		String head = lower.substring(0, dot);
		String rest = lower.substring(dot);
		return switch (head) {
			case "q" -> "query" + rest;
			case "v" -> "variable" + rest;
			case "t" -> "temp" + rest;
			case "c" -> "context" + rest;
			default -> lower;
		};
	}

	// ------------------------------------------------------------------ parsing

	private static final class Parser {
		private final List<String> tokens = new ArrayList<>();
		private int at;

		Parser(String text) {
			int i = 0;
			while (i < text.length()) {
				char ch = text.charAt(i);
				if (Character.isWhitespace(ch)) {
					i++;
				} else if (Character.isDigit(ch) || (ch == '.' && i + 1 < text.length() && Character.isDigit(text.charAt(i + 1)))) {
					int start = i;
					while (i < text.length() && (Character.isDigit(text.charAt(i)) || text.charAt(i) == '.')) i++;
					if (i < text.length() && (text.charAt(i) == 'e' || text.charAt(i) == 'E')) {
						i++;
						if (i < text.length() && (text.charAt(i) == '+' || text.charAt(i) == '-')) i++;
						while (i < text.length() && Character.isDigit(text.charAt(i))) i++;
					}
					// Molang allows an f suffix on numbers, as in 1.5f.
					if (i < text.length() && (text.charAt(i) == 'f' || text.charAt(i) == 'F')) i++;
					tokens.add(text.substring(start, i).replace("f", "").replace("F", ""));
				} else if (Character.isLetter(ch) || ch == '_') {
					int start = i;
					while (i < text.length() && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_'
							|| text.charAt(i) == '.')) i++;
					tokens.add(text.substring(start, i));
				} else {
					String two = i + 1 < text.length() ? text.substring(i, i + 2) : "";
					if (two.equals("&&") || two.equals("||") || two.equals("==") || two.equals("!=")
							|| two.equals("<=") || two.equals(">=") || two.equals("??")) {
						tokens.add(two);
						i += 2;
					} else {
						tokens.add(String.valueOf(ch));
						i++;
					}
				}
			}
		}

		Expression parse() {
			// A trailing semicolon is common in hand-written values; more than one
			// statement is not supported.
			if (!tokens.isEmpty() && tokens.get(tokens.size() - 1).equals(";")) tokens.remove(tokens.size() - 1);
			Expression made = ternary();
			if (at != tokens.size()) throw new IllegalArgumentException("unexpected " + tokens.get(at));
			return made;
		}

		private String peek() {
			return at < tokens.size() ? tokens.get(at) : "";
		}

		private boolean take(String token) {
			if (peek().equals(token)) {
				at++;
				return true;
			}
			return false;
		}

		private void expect(String token) {
			if (!take(token)) throw new IllegalArgumentException("expected " + token);
		}

		private Expression ternary() {
			Expression condition = coalesce();
			if (take("?")) {
				Expression yes = ternary();
				if (take(":")) {
					Expression no = ternary();
					return values -> condition.eval(values) != 0 ? yes.eval(values) : no.eval(values);
				}
				// Molang's binary conditional: the value when true, else nought.
				return values -> condition.eval(values) != 0 ? yes.eval(values) : 0;
			}
			return condition;
		}

		private Expression coalesce() {
			Expression left = or();
			while (take("??")) {
				Expression first = left;
				Expression second = or();
				// A missing name is nought here, so the left side is taken unless it is nought.
				left = values -> {
					double value = first.eval(values);
					return value != 0 ? value : second.eval(values);
				};
			}
			return left;
		}

		private Expression or() {
			Expression left = and();
			while (take("||")) {
				Expression a = left;
				Expression b = and();
				left = values -> a.eval(values) != 0 || b.eval(values) != 0 ? 1 : 0;
			}
			return left;
		}

		private Expression and() {
			Expression left = comparison();
			while (take("&&")) {
				Expression a = left;
				Expression b = comparison();
				left = values -> a.eval(values) != 0 && b.eval(values) != 0 ? 1 : 0;
			}
			return left;
		}

		private Expression comparison() {
			Expression left = sum();
			while (true) {
				String op = peek();
				if (!(op.equals("<") || op.equals(">") || op.equals("<=") || op.equals(">=") || op.equals("==")
						|| op.equals("!="))) {
					return left;
				}
				at++;
				Expression a = left;
				Expression b = sum();
				left = switch (op) {
					case "<" -> values -> a.eval(values) < b.eval(values) ? 1 : 0;
					case ">" -> values -> a.eval(values) > b.eval(values) ? 1 : 0;
					case "<=" -> values -> a.eval(values) <= b.eval(values) ? 1 : 0;
					case ">=" -> values -> a.eval(values) >= b.eval(values) ? 1 : 0;
					case "==" -> values -> a.eval(values) == b.eval(values) ? 1 : 0;
					default -> values -> a.eval(values) != b.eval(values) ? 1 : 0;
				};
			}
		}

		private Expression sum() {
			Expression left = product();
			while (peek().equals("+") || peek().equals("-")) {
				boolean plus = tokens.get(at++).equals("+");
				Expression a = left;
				Expression b = product();
				left = plus ? values -> a.eval(values) + b.eval(values) : values -> a.eval(values) - b.eval(values);
			}
			return left;
		}

		private Expression product() {
			Expression left = unary();
			while (peek().equals("*") || peek().equals("/") || peek().equals("%")) {
				String op = tokens.get(at++);
				Expression a = left;
				Expression b = unary();
				left = switch (op) {
					case "*" -> values -> a.eval(values) * b.eval(values);
					case "/" -> values -> {
						double divisor = b.eval(values);
						return divisor == 0 ? 0 : a.eval(values) / divisor;
					};
					default -> values -> {
						double divisor = b.eval(values);
						return divisor == 0 ? 0 : a.eval(values) % divisor;
					};
				};
			}
			return left;
		}

		private Expression unary() {
			if (take("-")) {
				Expression inner = unary();
				return values -> -inner.eval(values);
			}
			if (take("+")) return unary();
			if (take("!")) {
				Expression inner = unary();
				return values -> inner.eval(values) == 0 ? 1 : 0;
			}
			return primary();
		}

		private Expression primary() {
			String token = peek();
			if (token.isEmpty()) throw new IllegalArgumentException("unexpected end");
			if (take("(")) {
				Expression inner = ternary();
				expect(")");
				return inner;
			}
			char first = token.charAt(0);
			if (Character.isDigit(first) || first == '.') {
				at++;
				double value = Double.parseDouble(token);
				return values -> value;
			}
			if (Character.isLetter(first) || first == '_') {
				at++;
				String name = fullName(token);
				if (take("(")) {
					List<Expression> arguments = new ArrayList<>();
					if (!take(")")) {
						do {
							arguments.add(ternary());
						} while (take(","));
						expect(")");
					}
					return function(name, arguments);
				}
				if (name.equals("math.pi")) return values -> Math.PI;
				if (name.equals("true")) return values -> 1;
				if (name.equals("false")) return values -> 0;
				return values -> values.getOrDefault(name, 0.0);
			}
			throw new IllegalArgumentException("unexpected " + token);
		}

		private static double arg(List<Expression> arguments, int index, Map<String, Double> values) {
			return index < arguments.size() ? arguments.get(index).eval(values) : 0;
		}

		private static Expression function(String name, List<Expression> a) {
			return switch (name) {
				case "math.sin" -> v -> Math.sin(Math.toRadians(arg(a, 0, v)));
				case "math.cos" -> v -> Math.cos(Math.toRadians(arg(a, 0, v)));
				case "math.asin" -> v -> Math.toDegrees(Math.asin(arg(a, 0, v)));
				case "math.acos" -> v -> Math.toDegrees(Math.acos(arg(a, 0, v)));
				case "math.atan" -> v -> Math.toDegrees(Math.atan(arg(a, 0, v)));
				case "math.atan2" -> v -> Math.toDegrees(Math.atan2(arg(a, 0, v), arg(a, 1, v)));
				case "math.abs" -> v -> Math.abs(arg(a, 0, v));
				case "math.clamp" -> v -> Math.max(arg(a, 1, v), Math.min(arg(a, 2, v), arg(a, 0, v)));
				case "math.exp" -> v -> Math.exp(arg(a, 0, v));
				case "math.ln" -> v -> Math.log(arg(a, 0, v));
				case "math.pow" -> v -> Math.pow(arg(a, 0, v), arg(a, 1, v));
				case "math.sqrt" -> v -> Math.sqrt(arg(a, 0, v));
				case "math.mod" -> v -> {
					double divisor = arg(a, 1, v);
					return divisor == 0 ? 0 : arg(a, 0, v) % divisor;
				};
				case "math.min" -> v -> Math.min(arg(a, 0, v), arg(a, 1, v));
				case "math.max" -> v -> Math.max(arg(a, 0, v), arg(a, 1, v));
				case "math.floor" -> v -> Math.floor(arg(a, 0, v));
				case "math.ceil" -> v -> Math.ceil(arg(a, 0, v));
				case "math.round" -> v -> (double) Math.round(arg(a, 0, v));
				case "math.trunc" -> v -> (double) (long) arg(a, 0, v);
				case "math.lerp" -> v -> {
					double from = arg(a, 0, v);
					return from + (arg(a, 1, v) - from) * arg(a, 2, v);
				};
				case "math.lerprotate" -> v -> {
					double from = arg(a, 0, v);
					double to = arg(a, 1, v);
					double gap = ((to - from) % 360 + 540) % 360 - 180;
					return from + gap * arg(a, 2, v);
				};
				case "math.hermite_blend" -> v -> {
					double t = arg(a, 0, v);
					return 3 * t * t - 2 * t * t * t;
				};
				// A random value would make the model shiver from frame to frame; the
				// middle of the range is the steady stand-in.
				case "math.random", "math.random_integer" -> v -> (arg(a, 0, v) + arg(a, 1, v)) / 2;
				case "math.die_roll", "math.die_roll_integer" -> v -> arg(a, 0, v) * (arg(a, 1, v) + arg(a, 2, v)) / 2;
				default -> throw new IllegalArgumentException("unknown function " + name);
			};
		}
	}
}

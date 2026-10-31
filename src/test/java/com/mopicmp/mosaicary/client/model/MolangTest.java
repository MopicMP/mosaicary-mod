package com.mopicmp.mosaicary.client.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** The Molang subset, on expressions of the kinds found in the seed sample. */
class MolangTest {

	private static double eval(String text, double animTime) {
		Map<String, Double> values = new HashMap<>();
		values.put("query.anim_time", animTime);
		return Molang.compile(text).eval(values);
	}

	@Test
	void numbersAndNumericStrings() {
		assertEquals(2.5, eval("2.5", 0), 1e-9);
		assertEquals(-3, eval(" -3 ", 0), 1e-9);
		assertEquals(1.5, eval("1.5f", 0), 1e-9);
		assertEquals(15.68 + 0.33, eval("0.33+15.68", 0), 1e-9);
	}

	@Test
	void trigonometryIsInDegrees() {
		assertEquals(1, eval("math.sin(90)", 0), 1e-9);
		assertEquals(-1, eval("math.cos(180)", 0), 1e-9);
		// Seen in the sample: math.sin(q.anim_time*90*2-90)*15 at a quarter second.
		assertEquals(Math.sin(Math.toRadians(0.25 * 180 - 90)) * 15, eval("math.sin(q.anim_time*90*2-90)*15", 0.25), 1e-9);
		assertEquals(45, eval("math.atan(1)", 0), 1e-9);
	}

	@Test
	void precedenceAndUnaryMinus() {
		assertEquals(-25 - Math.sin(Math.toRadians(90)) * 10, eval("-25-math.sin(q.anim_time*90)*10", 1), 1e-9);
		assertEquals(14, eval("2+3*4", 0), 1e-9);
		assertEquals(20, eval("(2+3)*4", 0), 1e-9);
		assertEquals(-Math.sin(Math.toRadians(180)) * 8, eval("-math.sin(q.anim_time*90*2)*8", 1), 1e-9);
	}

	@Test
	void functionsTernaryAndComparisons() {
		assertEquals(5, eval("math.clamp(12, -5, 5)", 0), 1e-9);
		assertEquals(8, eval("math.pow(2, 3)", 0), 1e-9);
		assertEquals(1, eval("math.mod(7, 3)", 0), 1e-9);
		assertEquals(10, eval("q.anim_time >= 1 ? 10 : 20", 1), 1e-9);
		assertEquals(20, eval("q.anim_time >= 1 ? 10 : 20", 0.5), 1e-9);
		assertEquals(3, eval("math.max(math.min(3, 4), 1)", 0), 1e-9);
	}

	@Test
	void unknownNamesAreNought() {
		Molang.Expression expression = Molang.compile("v.strength * 10 + 1");
		assertFalse(expression.unreadable());
		assertEquals(1, expression.eval(new HashMap<>()), 1e-9);
	}

	@Test
	void unreadableTextIsNoughtAndSaysSo() {
		for (String text : new String[] { "v.x = 1; return v.x;", "math.unknown_thing(3)", "3 +", "loop(3, {})" }) {
			Molang.Expression expression = Molang.compile(text);
			assertTrue(expression.unreadable(), text);
			assertEquals(0, expression.eval(new HashMap<>()), 1e-9, text);
		}
	}

	@Test
	void shortPrefixesAreTheLongNames() {
		Map<String, Double> values = new HashMap<>();
		values.put("query.anim_time", 2.0);
		assertEquals(2, Molang.compile("q.anim_time").eval(values), 1e-9);
		assertEquals(2, Molang.compile("Query.Anim_Time").eval(values), 1e-9);
		assertEquals("variable.speed", Molang.fullName("v.speed"));
	}
}

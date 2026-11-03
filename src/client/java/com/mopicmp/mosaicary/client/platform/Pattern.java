package com.mopicmp.mosaicary.client.platform;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method whose body is the same call written for each game version, one per
 * {@code //#if} branch. The preprocessor finds the current version's form anywhere in the
 * source and writes the other version's form in its place, so the code everywhere else is
 * written once, for the main version (see ClientPatterns).
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface Pattern {
}

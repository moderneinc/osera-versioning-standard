package org.finos.osera.fixers.npm;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Runs the test once per {@link NpmRemediator} per package manager. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ParameterizedTest(name = "{displayName} — {0} — {1}")
@MethodSource("org.finos.osera.fixers.npm.NpmRemediator#allAcrossManagers")
public @interface EachNpmRemediatorAndManager {
}

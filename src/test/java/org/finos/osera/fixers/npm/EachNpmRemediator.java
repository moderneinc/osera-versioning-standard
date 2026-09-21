package org.finos.osera.fixers.npm;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Runs the test once per {@link NpmRemediator}, passed as the method's parameter. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ParameterizedTest(name = "{displayName} — {0}")
@MethodSource("org.finos.osera.fixers.npm.NpmRemediator#all")
public @interface EachNpmRemediator {
}

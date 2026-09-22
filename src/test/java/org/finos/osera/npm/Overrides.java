package org.finos.osera.npm;

import org.openrewrite.javascript.marker.NodeResolutionResult.PackageManager;

/**
 * How each package manager spells "pin this package, wherever it appears in the graph".
 *
 * <p>The three spellings are the same fact, and a scenario that writes one out by hand is a copy
 * that can disagree with the others — one did, expecting npm's key while running against all
 * three. The fragments are emitted with absolute indentation so an expected manifest can
 * concatenate one without a text block swallowing its leading whitespace, which is the trap that
 * produced the copies in the first place.
 */
public final class Overrides {

    private Overrides() {
    }

    /** The key a consumer writes, for naming a measurement rather than building one. */
    public static String key(PackageManager pm) {
        return switch (pm) {
            case Npm -> "overrides";
            case Pnpm -> "pnpm.overrides";
            case YarnClassic, YarnBerry -> "resolutions";
            case Bun -> throw PackageManagers.notMeasured(pm);
        };
    }

    /**
     * The member as it appears at the top level of a two-space {@code package.json}, without a
     * trailing comma, and already indented.
     *
     * <p>The indentation is applied here rather than left to the caller because interpolating a
     * multi-line fragment into a text block indents only its first line, and whitespace is what
     * these expectations compare. Callers interpolate at column zero.
     */
    public static String member(PackageManager pm, String name, String constraint) {
        String block = switch (pm) {
            case Npm -> """
              "overrides": {
                "%s": "%s"
              }""".formatted(name, constraint);
            case Pnpm -> """
              "pnpm": {
                "overrides": {
                  "%s": "%s"
                }
              }""".formatted(name, constraint);
            case YarnClassic, YarnBerry -> """
              "resolutions": {
                "%s": "%s"
              }""".formatted(name, constraint);
            case Bun -> throw PackageManagers.notMeasured(pm);
        };
        return block.indent(2).stripTrailing();
    }
}

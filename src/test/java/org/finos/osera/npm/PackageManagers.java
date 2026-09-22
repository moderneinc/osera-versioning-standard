package org.finos.osera.npm;

import org.openrewrite.javascript.marker.NodeResolutionResult.PackageManager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Which package managers this machine can actually run, and how to invoke each one.
 *
 * <p>A manager that is absent is a cell the matrix has no answer for, which is not the same answer
 * as a manager that resolved the backpatch differently, so scenarios skip rather than fail.
 *
 * <p>Presence is not enough to go on. This machine had a pnpm on the PATH installed under Node 16
 * and running under Node 26, which dies on startup; and a Homebrew {@code yarn} that is Classic,
 * shadowing the corepack shim, where the matrix wants Berry. So each manager is asked for its
 * version and the major is checked.
 */
public final class PackageManagers {

    /**
     * The major version each manager is measured at. A different major is a different tool for
     * this purpose: Yarn Classic and Yarn Berry disagree about resolution, and pnpm changed its
     * default {@code resolution-mode} across majors.
     */
    private static final Map<PackageManager, Integer> MAJOR = Map.of(
      PackageManager.Npm, 11,
      PackageManager.Pnpm, 9,
      PackageManager.YarnClassic, 1,
      PackageManager.YarnBerry, 4);

    private PackageManagers() {
    }

    /**
     * The command prefix that runs this manager, as a list because Berry is not an executable of
     * its own: it is {@code corepack yarn}.
     */
    public static List<String> command(PackageManager pm) {
        return switch (pm) {
            case Npm -> List.of(System.getProperty("osera.npm", "npm"));
            case Pnpm -> List.of(System.getProperty("osera.pnpm", "pnpm"));
            case YarnClassic -> List.of(System.getProperty("osera.yarn", "yarn"));
            case YarnBerry -> List.of(System.getProperty("osera.corepack", "corepack"), "yarn");
            case Bun -> throw new IllegalArgumentException("Bun is not measured");
        };
    }

    /** Whether the manager runs at all, and reports the major version the scenarios expect. */
    public static boolean isUsable(PackageManager pm) {
        return version(pm).startsWith(MAJOR.get(pm) + ".");
    }

    /** What the manager says its version is, or an empty string if it cannot be run. */
    public static String version(PackageManager pm) {
        try {
            Process process = new ProcessBuilder(concat(command(pm), "--version"))
              .redirectErrorStream(true)
              .start();
            process.getOutputStream().close();
            String output = new String(process.getInputStream().readAllBytes()).trim();
            if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
                return "";
            }
            // corepack prints its own chatter before the version on first provision.
            return output.isEmpty() ? "" : output.lines().reduce((first, last) -> last).orElse("");
        } catch (Exception e) {
            return "";
        }
    }

    private static List<String> concat(List<String> command, String argument) {
        return java.util.stream.Stream.concat(command.stream(), java.util.stream.Stream.of(argument)).toList();
    }
}

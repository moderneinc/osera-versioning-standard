package org.finos.osera.npm;

import org.finos.osera.providers.npm.PackageVersioning;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.openrewrite.javascript.marker.NodeResolutionResult.PackageManager;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * How old a version has to be before a package manager will install it.
 *
 * <p>Every other scenario here dates the registry's versions well in the past, because they ask
 * which version a manager picks and a real registry dates what it serves. This one asks the
 * opposite question. A backpatch exists because a vulnerability was disclosed, so it is new exactly
 * when a consumer most needs it, and Yarn 4.10 added {@code npmMinimalAgeGate} — 1440 minutes by
 * default — which quarantines anything the registry dates inside that window.
 *
 * <p>This is not a result about OSERA. The gate cannot see a producer token and does not know a
 * prerelease from a release; it reads a date. The second scenario measures that rather than
 * asserting it, because a cost that falls on the maintainer's own fix equally is a cost of the
 * ecosystem and not of the scheme, and the chapter has to be able to say which it is.
 */
public record NpmPublishAgeTest(PackageVersioning versioning) {

    private static final String UPSTREAM_FIX = "1.4.2";

    /**
     * The producer publishes a backpatch and a consumer asks for it by the constraint the
     * recommendation would have a remediator write. Nothing else satisfies that constraint, so a
     * manager that will not take it installs nothing at all.
     */
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = PackageManager.class, names = {"Npm", "Pnpm", "YarnBerry"})
    void aBackpatchPublishedMinutesAgo(PackageManager pm) {
        assumeTrue(PackageManagers.isUsable(pm), pm + " is not installed at the measured major");

        String logger = versioning.freshPackage();
        versioning.publishUpstream(logger, "1.4.0", "1.4.1", "1.5.0", "2.0.0");
        String patched = versioning.publishJustNow(logger, "1.4.1");

        assertThat(NpmProject.resolve(versioning.registry(), pm, logger, versioning.constraint(patched)))
          .as("what %s installs for a backpatch published minutes ago", pm)
          .isEqualTo(patched);
    }

    /**
     * The same question with the producer taken out of it: the maintainer ships the fix themselves,
     * as {@code 1.4.2}, and the consumer is on a tilde that already admits it.
     *
     * <p>Here something older does satisfy the range, so the interesting outcome is not an error.
     * It is which version a manager that declines the new one falls back to.
     */
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = PackageManager.class, names = {"Npm", "Pnpm", "YarnBerry"})
    void theMaintainersOwnFixPublishedMinutesAgo(PackageManager pm) {
        assumeTrue(PackageManagers.isUsable(pm), pm + " is not installed at the measured major");

        String logger = versioning.freshPackage();
        versioning.publishUpstream(logger, "1.4.0", "1.4.1", "1.5.0", "2.0.0");
        versioning.registry().publish(logger, UPSTREAM_FIX, Instant.now());

        assertThat(NpmProject.resolve(versioning.registry(), pm, logger, "~1.4.1"))
          .as("what %s installs for ~1.4.1 when the maintainer's fix is minutes old", pm)
          .isEqualTo(UPSTREAM_FIX);
    }
}

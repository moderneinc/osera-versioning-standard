package org.finos.osera.npm;

import org.finos.osera.providers.npm.PackageVersioning;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.openrewrite.javascript.marker.NodeResolutionResult.PackageManager;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * How old a version has to be before a package manager will install it. Yarn's
 * {@code npmMinimalAgeGate} quarantines anything the registry dates inside the last day, and a
 * backpatch is new exactly when a consumer most needs it.
 */
public record NpmPublishAgeTest(PackageVersioning versioning) {

    private static final String UPSTREAM_FIX = "1.4.2";

    @ParameterizedTest(name = "{displayName} — {0}")
    @EnumSource(value = PackageManager.class, names = {"Npm", "Pnpm", "YarnBerry"})
    void freshBackpatch(PackageManager pm) {
        assumeTrue(PackageManagers.isUsable(pm), pm + " is not installed at the measured major");

        String logger = versioning.freshPackage();
        versioning.publishUpstream(logger, "1.4.0", "1.4.1", "1.5.0", "2.0.0");
        String patched = versioning.publishJustNow(logger, "1.4.1");

        assertThat(NpmProject.resolve(versioning.registry(), pm, logger, versioning.constraint(patched)))
          .as("what %s installs for a backpatch published minutes ago", pm)
          .isEqualTo(patched);
    }

    /**
     * The same question with the producer taken out of it. Something older satisfies the range
     * here, so a manager that declines the new version falls back rather than failing, and whether
     * this is a cost of the scheme or of the ecosystem turns on it.
     */
    @ParameterizedTest(name = "{displayName} — {0}")
    @EnumSource(value = PackageManager.class, names = {"Npm", "Pnpm", "YarnBerry"})
    void freshMaintainerFix(PackageManager pm) {
        assumeTrue(PackageManagers.isUsable(pm), pm + " is not installed at the measured major");

        String logger = versioning.freshPackage();
        versioning.publishUpstream(logger, "1.4.0", "1.4.1", "1.5.0", "2.0.0");
        versioning.registry().publish(logger, UPSTREAM_FIX, Instant.now());

        assertThat(NpmProject.resolve(versioning.registry(), pm, logger, "~1.4.1"))
          .as("what %s installs for ~1.4.1 when the maintainer's fix is minutes old", pm)
          .isEqualTo(UPSTREAM_FIX);
    }
}

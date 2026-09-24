package org.finos.osera.npm;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.openrewrite.javascript.marker.NodeResolutionResult.PackageManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Reports what this machine can measure. Not an assertion about the machine — a manager nobody
 * installed is a skip — but a manager that is present and unusable has to say so out loud, because
 * the alternative is a matrix column that quietly never runs.
 */
class PackageManagersTest {

    @ParameterizedTest
    @EnumSource(value = PackageManager.class, names = {"Npm", "Pnpm", "YarnClassic", "YarnBerry"})
    void reportsAUsableVersion(PackageManager pm) {
        String version = PackageManagers.version(pm);
        assumeTrue(!version.isEmpty(), pm + " is not installed");

        assertThat(PackageManagers.isUsable(pm))
          .as("%s runs and reports %s, which the scenarios expect to be a different major", pm, version)
          .isTrue();
    }
}

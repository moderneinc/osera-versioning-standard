package org.finos.osera.npm;

import org.finos.osera.providers.npm.PackageVersioning;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.javascript.marker.NodeResolutionResult.PackageManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * What a consumer gets with no remediation tool involved.
 *
 * <p>The registry holds a realistic history. A backpatch exists because the maintainer has moved on,
 * so the version being patched is never the newest thing on the registry, and a scenario where it is
 * would flatter every range that reaches for the newest.
 *
 * <p>Every range asserts it resolves to the backpatch. Most do not, and that is the measurement:
 * the reds here are why {@code REL03-JAVASCRIPT} has to require a remediation tool.
 */
public record NpmDeclaredRangeTest(PackageVersioning versioning) {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
      // Held on the 1.4 line: the population a backpatch exists for.
      "~1.4.1",
      "1.4.x",
      "1.4.1",
      // Free to move: these never needed a backpatch, and are here to show they do not get one.
      "^1.4.1",
      ">=1.4.1",
      "1.x",
      "*",
      "",
      "1.4.1 - 1.5.0",
      ">=1.4.1 <2.0.0",
      "^1.4.1-0",
      // Ranges naming a prerelease at the backpatch's own tuple.
      "~1.4.2-0",
      "^1.4.2-0",
      "~1.4.2-osera-00001",
      "1.4.2-osera-00001"
    })
    void resolvesToTheBackpatch(String declared) {
        String pkg = versioning.freshPackage();
        versioning.publishUpstream(pkg, "1.4.0", "1.4.1", "1.5.0", "2.0.0");
        String patched = versioning.publish(pkg, "1.4.1");

        assertThat(NpmProject.resolve(versioning.registry(), pkg, declared))
          .as("what npm installs for %s", declared)
          .isEqualTo(patched);
    }

    /**
     * Whether resolution really is shared across the package managers.
     *
     * <p>The table above runs against npm alone, on the grounds that all three use
     * {@code node-semver}. That reasoning is why it is fifteen rows and not forty-five, so it is
     * worth checking rather than asserting. Range <em>satisfaction</em> being shared does not make
     * <em>selection among satisfying versions</em> shared: pnpm has a {@code resolution-mode}, and
     * pnpm 8 shipped {@code lowest-direct} as the default for direct dependencies before
     * reverting it. Under that, {@code ^1.4.1} stays on {@code 1.4.1}.
     */
    @ParameterizedTest(name = "{0} on {1}")
    @CsvSource({
      // declared,            manager,     what npm resolves it to
      "~1.4.1,                Pnpm,        1.4.1",
      "^1.4.1,                Pnpm,        1.5.0",
      "~1.4.2-osera-00001,    Pnpm,        PATCH",
      "^1.4.2-osera-00001,    Pnpm,        1.5.0",
      "~1.4.1,                YarnBerry,   1.4.1",
      "^1.4.1,                YarnBerry,   1.5.0",
      "~1.4.2-osera-00001,    YarnBerry,   PATCH",
      "^1.4.2-osera-00001,    YarnBerry,   1.5.0"
    })
    void resolvesAsNpmDoes(String declared, PackageManager pm, String expected) {
        assumeTrue(PackageManagers.isUsable(pm), pm + " is not installed at the measured major");

        String pkg = versioning.freshPackage();
        versioning.publishUpstream(pkg, "1.4.0", "1.4.1", "1.5.0", "2.0.0");
        String patched = versioning.publish(pkg, "1.4.1");

        assertThat(NpmProject.resolve(versioning.registry(), pm, pkg, declared))
          .as("what %s installs for %s, where npm installs %s", pm, declared, expected)
          .isEqualTo("PATCH".equals(expected) ? patched : expected);
    }
}

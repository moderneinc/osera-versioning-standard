package org.finos.osera.npm;

import org.finos.osera.providers.npm.PackageVersioning;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

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
}

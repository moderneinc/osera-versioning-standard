package org.finos.osera.npm;

import org.finos.osera.providers.npm.PackageVersioning;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The other scheme the suite measures, asked of npm. SemVer §11 excludes build metadata from
 * precedence, so {@code 1.4.1+osera.1} and {@code 1.4.1} are the same version, and npm resolves
 * through {@code node-semver}, which implements it.
 *
 * <p>This registry will serve two versions differing only in build metadata, which npmjs need not,
 * so a failure here is a floor rather than a ceiling.
 */
public record NpmBuildMetadataTest(PackageVersioning versioning) {

    private static final String BASE = "1.4.1";

    /** What the producer would publish under the scheme the JVM side measures. */
    private static final String PATCHED = "1.4.1+osera.1";

    @Test
    void semverRanksABackpatchAboveTheReleaseItPatches() {
        assertThat(SemverOrder.INSTANCE.compare(PATCHED, BASE))
          .as("where SemVer §11 puts %s against %s", PATCHED, BASE)
          .isPositive();
    }

    /** The least a scheme has to support: if naming the build does not deliver it, nothing can. */
    @Test
    void aConsumerWhoNamesTheProducersBuildExactly() {
        String logger = versioning.freshPackage();
        versioning.publishUpstream(logger, "1.4.0", BASE, "1.5.0", "2.0.0");
        versioning.registry().publish(logger, PATCHED);

        assertThat(NpmProject.resolve(versioning.registry(), logger, PATCHED))
          .as("what npm installs for an exact %s", PATCHED)
          .isEqualTo(PATCHED);
    }

    @Test
    void aConsumerHeldOnTheLineByATilde() {
        String logger = versioning.freshPackage();
        versioning.publishUpstream(logger, "1.4.0", BASE, "1.5.0", "2.0.0");
        versioning.registry().publish(logger, PATCHED);

        assertThat(NpmProject.resolve(versioning.registry(), logger, "~" + BASE))
          .as("what npm installs for ~%s when %s is also published", BASE, PATCHED)
          .isEqualTo(PATCHED);
    }
}

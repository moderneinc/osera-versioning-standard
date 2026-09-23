package org.finos.osera.npm;

import org.finos.osera.providers.npm.PackageVersioning;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The other scheme the suite measures, asked of npm.
 *
 * <p>Chapter 1 exists because OSERA had candidate schemes and the JVM side measured two of them:
 * {@code 2.14.1.1-osera-00001}, and {@code 2.14.1+osera.1}, which carries the producer in SemVer's
 * build metadata. The npm side evaluates one scheme, so the question a reader of the JVM chapters
 * will ask is what happened to the other one.
 *
 * <p>SemVer 2.0.0 §11 answers it: build metadata is excluded from precedence, so
 * {@code 1.4.1+osera.1} and {@code 1.4.1} are the same version as far as the spec is concerned.
 * Maven and Gradle do not implement SemVer and rank the two apart, which is why the scheme was
 * worth measuring there at all; npm resolves through {@code node-semver}, which does implement it.
 * These scenarios measure the consequence rather than restating the rule.
 *
 * <p>This registry is more permissive than npmjs, which is what makes the result usable. The mock
 * stores whatever string it is handed and will serve two versions that differ only in build
 * metadata; a public registry need not accept the publish at all. So a failure here is a floor, not
 * a ceiling: the scheme does not work even where the registry cooperates.
 */
public record NpmBuildMetadataTest(PackageVersioning versioning) {

    private static final String BASE = "1.4.1";

    /** What the producer would publish under the scheme the JVM side measures. */
    private static final String PATCHED = "1.4.1+osera.1";

    /**
     * The scheme needs a backpatch to outrank the release it patches, because every tool in the
     * matrix decides what to offer by comparing versions. §11 says it does not.
     */
    @Test
    void semverRanksABackpatchAboveTheReleaseItPatches() {
        assertThat(SemverOrder.INSTANCE.compare(PATCHED, BASE))
          .as("where SemVer §11 puts %s against %s", PATCHED, BASE)
          .isPositive();
    }

    /**
     * A consumer who has been told exactly which build they need, and asks for it by name. This is
     * the least a scheme has to support: if naming the producer's version does not deliver it,
     * nothing a remediator writes can help.
     */
    @Test
    void aConsumerWhoNamesTheProducersBuildExactly() {
        String logger = versioning.freshPackage();
        versioning.publishUpstream(logger, "1.4.0", BASE, "1.5.0", "2.0.0");
        versioning.registry().publish(logger, PATCHED);

        assertThat(NpmProject.resolve(versioning.registry(), logger, PATCHED))
          .as("what npm installs for an exact %s", PATCHED)
          .isEqualTo(PATCHED);
    }

    /**
     * A consumer held on the line by a tilde, which is the population a backpatch exists for and
     * the one the chosen scheme's recommendation is written for.
     */
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

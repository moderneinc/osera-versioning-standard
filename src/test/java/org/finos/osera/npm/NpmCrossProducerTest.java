package org.finos.osera.npm;

import org.finos.osera.fixers.npm.EachNpmRemediator;
import org.finos.osera.fixers.npm.NpmRemediator;
import org.finos.osera.providers.npm.PackageVersioning;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.javascript.Assertions.dependency;
import static org.openrewrite.javascript.Assertions.nodeResolutionResult;
import static org.openrewrite.javascript.Assertions.packageJson;
import static org.openrewrite.javascript.marker.NodeResolutionResult.PackageManager.Npm;

/**
 * More than one producer patching the same release, the npm counterpart of {@code CrossProducerTest}.
 * Chapter 4's two complaints about CARE are that a busier producer outranks a more relevant one and
 * that ties break on the name. This scheme puts the sequence after the token, so only the second
 * should apply.
 */
public record NpmCrossProducerTest(PackageVersioning versioning) implements NpmBuild {

    private static final String RIVAL = "zenith";

    /** Told which producer it wants, does a tool stay on ours when another sorts higher? */
    @EachNpmRemediator
    void namedProducer(NpmRemediator remediator) {
        String pkg = versioning.freshPackage();
        versioning.publishUpstream(pkg, "1.4.0", "1.4.1", "1.5.0", "2.0.0");
        String ours = versioning.publish(pkg, "1.4.1");
        versioning.publish(pkg, "1.4.1", RIVAL, 1);

        rewriteRun(
          NpmRemediator.asSpec(remediator, versioning, pkg),
          packageJson(manifest(pkg, "~1.4.1"),
            remediator.alsoPinsWithAnOverride() ?
              manifestWithOverride(pkg, versioning.constraint(ours)) :
              manifest(pkg, versioning.constraint(ours)),
            nodeResolutionResult(Npm, dependency(pkg, "~1.4.1"))));
    }

    /**
     * Chapter 4's first complaint, asked of this scheme: the rival has shipped one backpatch and
     * we have shipped five. Under CARE that would put us ahead on the fourth segment. Here the
     * sequence sits after the producer token, so it should not enter the comparison at all.
     */
    @Test
    void busierProducer() {
        String pkg = versioning.freshPackage();
        versioning.publishUpstream(pkg, "1.4.0", "1.4.1", "1.5.0", "2.0.0");
        for (int sequence = 1; sequence <= 5; sequence++) {
            versioning.publish(pkg, "1.4.1", PackageVersioning.OSERA, sequence);
        }
        String rival = versioning.publish(pkg, "1.4.1", RIVAL, 1);

        assertThat(NpmProject.resolve(versioning.registry(), Npm, pkg, "~1.4.2-0"))
          .as("five of ours against one of theirs, and nobody named a producer")
          .isEqualTo(rival);
    }

    /** And with nobody naming a producer, whose backpatch does a consumer actually install? */
    @Test
    void nobodyNamesAProducer() {
        String pkg = versioning.freshPackage();
        versioning.publishUpstream(pkg, "1.4.0", "1.4.1", "1.5.0", "2.0.0");
        String ours = versioning.publish(pkg, "1.4.1");
        versioning.publish(pkg, "1.4.1", RIVAL, 1);

        assertThat(NpmProject.resolve(versioning.registry(), Npm, pkg,
          versioning.constraint(ours)))
          .as("the constraint names our backpatch, and the rival sits at the same tuple")
          .isEqualTo(ours);
    }

    private static String manifest(String pkg, String constraint) {
        //language=JSON
        return """
          {
            "name": "consumer",
            "version": "1.0.0",
            "dependencies": {
              "%s": "%s"
            }
          }
          """.formatted(pkg, constraint);
    }

    private static String manifestWithOverride(String pkg, String constraint) {
        //language=JSON
        return """
          {
            "name": "consumer",
            "version": "1.0.0",
            "dependencies": {
              "%s": "%s"
            },
          %s
          }""".formatted(pkg, constraint, Overrides.member(Npm, pkg, constraint));
    }
}

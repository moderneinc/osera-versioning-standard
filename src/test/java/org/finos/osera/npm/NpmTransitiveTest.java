package org.finos.osera.npm;

import org.finos.osera.fixers.npm.EachNpmRemediator;
import org.finos.osera.fixers.npm.NpmRemediator;
import org.finos.osera.providers.npm.PackageVersioning;
import org.junit.jupiter.api.Test;
import org.openrewrite.Tree;
import org.openrewrite.javascript.UpgradeTransitiveDependencyVersion;
import org.openrewrite.javascript.marker.NodeResolutionResult;
import org.openrewrite.javascript.marker.NodeResolutionResult.Dependency;
import org.openrewrite.javascript.marker.NodeResolutionResult.ResolvedDependency;

import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.javascript.Assertions.packageJson;
import static org.openrewrite.javascript.Assertions.packageLock;
import static org.openrewrite.javascript.marker.NodeResolutionResult.PackageManager.Npm;

/**
 * A backpatch for something the consumer never declared.
 *
 * <p>This is the common case a backpatch exists for and the one the direct-dependency scenarios
 * cannot speak to: there is no declaration to rewrite, so a tool either reaches the resolution
 * graph or it does not. A column that declines is the measurement, not a gap.
 */
public record NpmTransitiveTest(PackageVersioning versioning) implements NpmBuild {

    private static final String WRAPPER = "acme-wrapper";

    /**
     * Proves the recipe can change this fixture at all; see {@link NpmTest#control}.
     *
     * <p>It asserts the manifest only. Lock regeneration is asserted by the scenario below rather
     * than here, because the lock not moving turned out to be the tool's behaviour and not a
     * broken fixture: regeneration runs — it fails loudly with {@code RESOLUTION_REQUIRED} if the
     * registry cannot describe the graph — and then leaves the lock pinning the old version,
     * where the same engine does move it when a direct declaration changes.
     */
    @Test
    void control() {
        String logger = versioning.freshPackage();
        versioning.publishUpstream(logger, "1.4.0", "1.4.1", "1.5.0", "2.0.0");
        versioning.registry().publish(WRAPPER, "3.0.0", Map.of(logger, "~1.4.1"));

        rewriteRun(
          spec -> spec.recipe(new UpgradeTransitiveDependencyVersion(logger, "~1.5.0", null)),
          packageJson(manifest(), withOverride(logger, "~1.5.0"), marker(logger)),
          packageLock(lock(logger, "1.4.1"), null));
    }

    @EachNpmRemediator
    void transitiveDependency(NpmRemediator remediator) {
        String logger = versioning.freshPackage();
        versioning.publishUpstream(logger, "1.4.0", "1.4.1", "1.5.0", "2.0.0");
        versioning.registry().publish(WRAPPER, "3.0.0", Map.of(logger, "~1.4.1"));
        String patched = versioning.publish(logger, "1.4.1");

        rewriteRun(
          NpmRemediator.asSpec(remediator, versioning, logger),
          packageJson(manifest(), withOverride(logger, versioning.constraint(patched)), marker(logger)),
          // A consumer is only remediated once the lock pins the backpatch; a manifest that says
          // one thing while the lock installs another is not a fix. Measured separately from the
          // manifest because the two answers differ.
          packageLock(lock(logger, "1.4.1"), null, lockSpec -> lockSpec.after(regenerated -> {
              assertThat(regenerated)
                .as("the lock %s left behind", remediator)
                .contains("\"version\": \"" + patched + "\"");
              return regenerated;
          })));
    }

    /** The consumer declares the wrapper and nothing else. */
    private static String manifest() {
        return """
          {
            "name": "consumer",
            "version": "1.0.0",
            "dependencies": {
              "%s": "^3.0.0"
            }
          }
          """.formatted(WRAPPER);
    }

    private static String withOverride(String logger, String constraint) {
        return """
          {
            "name": "consumer",
            "version": "1.0.0",
            "dependencies": {
              "%s": "^3.0.0"
            },
            "overrides": {
              "%s": "%s"
            }
          }""".formatted(WRAPPER, logger, constraint);
    }

    /** The graph a real install would have produced: the logger sits under the wrapper. */
    private static NodeResolutionResult marker(String logger) {
        return new NodeResolutionResult(
          Tree.randomId(), "consumer", "1.0.0", null, "package.json", null,
          List.of(new Dependency(WRAPPER, "^3.0.0", null)),
          emptyList(), emptyList(), emptyList(), emptyList(),
          List.of(
            new ResolvedDependency(WRAPPER, "3.0.0",
              List.of(new Dependency(logger, "~1.4.1", null)), null, null, null, null, null),
            new ResolvedDependency(logger, "1.4.1", null, null, null, null, null, null)),
          Npm, null, null);
    }

    private String lock(String logger, String resolved) {
        String base = versioning.registry().url();
        return """
          {
            "name": "consumer",
            "version": "1.0.0",
            "lockfileVersion": 3,
            "requires": true,
            "packages": {
              "": {
                "name": "consumer",
                "version": "1.0.0",
                "dependencies": { "%s": "^3.0.0" }
              },
              "node_modules/%s": {
                "version": "3.0.0",
                "resolved": "%s%s/-/%s-3.0.0.tgz",
                "integrity": "sha512-placeholder",
                "dependencies": { "%s": "~1.4.1" }
              },
              "node_modules/%s": {
                "version": "%s",
                "resolved": "%s%s/-/%s-%s.tgz",
                "integrity": "sha512-placeholder"
              }
            }
          }
          """.formatted(WRAPPER, WRAPPER, base, WRAPPER, WRAPPER, logger,
          logger, resolved, base, logger, logger, resolved);
    }
}

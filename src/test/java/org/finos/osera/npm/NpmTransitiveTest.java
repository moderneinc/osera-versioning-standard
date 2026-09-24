package org.finos.osera.npm;

import org.finos.osera.fixers.npm.EachNpmRemediator;
import org.finos.osera.fixers.npm.NpmRemediator;
import org.finos.osera.providers.npm.PackageVersioning;
import org.junit.jupiter.api.Test;
import org.openrewrite.Tree;
import org.openrewrite.javascript.UpgradeTransitiveDependencyVersion;
import org.openrewrite.javascript.marker.NodeResolutionResult;
import org.openrewrite.javascript.marker.NodeResolutionResult.Dependency;
import org.openrewrite.javascript.marker.NodeResolutionResult.PackageManager;
import org.openrewrite.javascript.marker.NodeResolutionResult.ResolvedDependency;

import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.javascript.Assertions.packageJson;
import static org.openrewrite.javascript.Assertions.packageLock;
import static org.openrewrite.javascript.marker.NodeResolutionResult.PackageManager.Npm;

/**
 * A backpatch for something the consumer never declared, which is the common case and the one the
 * direct scenarios cannot speak to.
 *
 * <p>OpenRewrite's cell is red for a reason that is not about this scheme: it writes the right
 * override, {@link NpmOverrideTest} shows all three managers then install from it, but lock
 * regeneration ignores the override and {@code npm ci} refuses the result. Reproduced upstream on
 * an ordinary version bump.
 */
public record NpmTransitiveTest(PackageVersioning versioning) implements NpmBuild {

    private static final String WRAPPER = "acme-wrapper";

    /**
     * Proves the recipe can change this fixture at all; see {@link NpmTest#control}. A liveness
     * check, not a no-op one: a manifest carrying no marker makes the recipe return the tree
     * untouched, and every "no change" cell in this class would then mean nothing.
     */
    @Test
    void control() {
        String logger = versioning.freshPackage();
        versioning.publishUpstream(logger, "1.4.0", "1.4.1", "1.5.0", "2.0.0");
        versioning.registry().publish(WRAPPER, "3.0.0", Map.of(logger, "~1.4.1"));

        rewriteRun(
          spec -> spec.recipe(new UpgradeTransitiveDependencyVersion(logger, "~1.5.0", null)),
          packageJson(manifest(), withOverride(Npm, logger, "~1.5.0"), marker(logger, Npm)),
          // The lock is present because the recipe needs one, and unasserted because what
          // happens to it is the scenario's question rather than this one's.
          packageLock(lock(logger, "1.4.1"), null, spec -> spec.after(actual -> actual)));
    }

    /**
     * npm only: the fixture's lock is a {@code package-lock.json}, so naming another manager fails
     * with {@code MALFORMED_LOCK}, which would be a cell about the fixture rather than the tool.
     */
    @EachNpmRemediator
    void transitiveDependency(NpmRemediator remediator) {
        PackageManager pm = Npm;
        String logger = versioning.freshPackage();
        versioning.publishUpstream(logger, "1.4.0", "1.4.1", "1.5.0", "2.0.0");
        versioning.registry().publish(WRAPPER, "3.0.0", Map.of(logger, "~1.4.1"));
        String patched = versioning.publish(logger, "1.4.1");

        rewriteRun(
          NpmRemediator.asSpec(remediator, versioning, logger),
          packageJson(manifest(), withOverride(pm, logger, versioning.constraint(patched)), marker(logger, pm)),
          // A manifest that names the backpatch while the lock installs the old version is not a fix.
          packageLock(lock(logger, "1.4.1"), null, lockSpec -> lockSpec.after(regenerated -> {
              assertThat(regenerated)
                .as("the lock %s left behind", remediator)
                .contains("\"version\": \"" + patched + "\"");
              return regenerated;
          })));
    }

    /** The consumer declares the wrapper and nothing else. */
    private static String manifest() {
        //language=JSON
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

    private static String withOverride(PackageManager pm, String logger, String constraint) {
        //language=JSON
        return """
          {
            "name": "consumer",
            "version": "1.0.0",
            "dependencies": {
              "%s": "^3.0.0"
            },
          %s
          }""".formatted(WRAPPER, Overrides.member(pm, logger, constraint));
    }

    /** The graph a real install would have produced: the logger sits under the wrapper. */
    private static NodeResolutionResult marker(String logger, PackageManager pm) {
        return new NodeResolutionResult(
          Tree.randomId(), "consumer", "1.0.0", null, "package.json", null,
          List.of(new Dependency(WRAPPER, "^3.0.0", null)),
          emptyList(), emptyList(), emptyList(), emptyList(),
          List.of(
            new ResolvedDependency(WRAPPER, "3.0.0",
              List.of(new Dependency(logger, "~1.4.1", null)), null, null, null, null, null),
            new ResolvedDependency(logger, "1.4.1", null, null, null, null, null, null)),
          pm, null, null);
    }

    private String lock(String logger, String resolved) {
        String base = versioning.registry().url();
        //language=JSON
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

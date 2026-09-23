package org.finos.osera.npm;

import org.finos.osera.fixers.npm.EachNpmRemediator;
import org.finos.osera.fixers.npm.NpmRemediator;
import org.finos.osera.providers.npm.PackageVersioning;
import org.junit.jupiter.api.Test;
import org.openrewrite.javascript.marker.NodeResolutionResult.PackageManager;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.openrewrite.javascript.Assertions.dependency;
import static org.openrewrite.javascript.Assertions.nodeResolutionResult;
import static org.openrewrite.javascript.Assertions.packageJson;
import static org.openrewrite.yaml.Assertions.yaml;

/**
 * A version that does not live in the manifest at all.
 *
 * <p>The npm analogue of the JVM's version catalogs, which chapter 3 gives a table of their own
 * because a tool that reads one spelling of a catalog does not necessarily read the rest. pnpm's
 * catalog moves the version out of {@code package.json} into {@code pnpm-workspace.yaml} and leaves
 * a bare {@code "catalog:"} behind, so a remediator that only knows how to rewrite a version
 * literal in a manifest has nothing to edit, the same way Renovate has nothing to edit in the
 * JVM's imported-BOM and managed-parent rows.
 *
 * <p>pnpm only. Yarn grew catalogs in 4.10 and the matrix measures Yarn Berry 4.5, so the Yarn half
 * of this question is unmeasured rather than answered; npm has no catalog at all.
 */
public record NpmCatalogTest(PackageVersioning versioning) implements NpmBuild {

    private static final String WORKSPACE = "pnpm-workspace.yaml";

    /**
     * The catalog is not a second resolution path.
     *
     * <p>One row rather than the fifteen {@link NpmDeclaredRangeTest} runs, for the same reason
     * that class spot-checks the other package managers rather than repeating itself: a catalog
     * entry is a constraint like any other and the question is only whether pnpm treats it as one.
     * If it does, everything already measured about ranges applies here unchanged.
     */
    @Test
    void aCatalogEntryResolvesLikeAnyOtherConstraint() {
        assumeTrue(PackageManagers.isUsable(PackageManager.Pnpm), "pnpm is not installed at the measured major");

        String logger = versioning.freshPackage();
        versioning.publishUpstream(logger, "1.4.0", "1.4.1", "1.5.0", "2.0.0");
        String patched = versioning.publish(logger, "1.4.1");

        assertThat(NpmProject.install(versioning.registry(), PackageManager.Pnpm, logger,
          manifest(logger), Map.of(WORKSPACE, workspace(logger, versioning.constraint(patched)))))
          .as("what pnpm installs for a catalog entry holding %s", versioning.constraint(patched))
          .isEqualTo(patched);
    }

    /**
     * The consumer is held on the line by a catalog entry, and the backpatch exists. A tool that
     * fixes this build moves the version where it actually lives.
     *
     * <p>Renovate, configured, is the only column that does. Both unconfigured bots edit the
     * catalog to {@code ~2.0.0}, which is the migration they propose everywhere else, so a catalog
     * is not a place their answer changes.
     *
     * <p>The OpenRewrite cell is red for a reason that is not about catalogs, and the chapter must
     * not read it as one. Run directly against these sources, the recipe replaces the manifest's
     * {@code "catalog:"} with the literal constraint and leaves {@code pnpm-workspace.yaml}
     * untouched: it takes the package out of the catalog rather than updating it, which fixes this
     * one member and leaves every other member of a real workspace on the vulnerable version.
     * Inside the matrix that behaviour is invisible, because the column composes
     * {@code UpgradeTransitiveDependencyVersion} and that recipe does not converge on pnpm —
     * every {@code OpenRewrite — Pnpm} cell in the suite fails the same way, before any assertion
     * about content is reached. Measured by running the two composed recipes separately:
     * {@code UpgradeDependencyVersion} alone converges on pnpm, the transitive one alone does not.
     * Same recipe as {@link NpmTransitiveTest}'s stale lock, and the same upstream issue.
     */
    @EachNpmRemediator
    void versionInACatalog(NpmRemediator remediator) {
        String logger = versioning.freshPackage();
        versioning.publishUpstream(logger, "1.4.0", "1.4.1", "1.5.0", "2.0.0");
        String patched = versioning.publish(logger, "1.4.1");

        rewriteRun(
          NpmRemediator.asSpec(remediator, versioning, logger),
          // The manifest says `catalog:` before and after: there is no version in it to move, and
          // a tool that wrote one here would be taking the package out of the catalog rather than
          // updating it.
          packageJson(manifest(logger), null,
            nodeResolutionResult(PackageManager.Pnpm, dependency(logger, "catalog:"))),
          yaml(workspace(logger, "~1.4.1"), workspace(logger, versioning.constraint(patched)),
            spec -> spec.path(WORKSPACE)));
    }

    /** A single-package workspace, which is the smallest project a catalog can exist in. */
    private static String workspace(String logger, String constraint) {
        return """
          packages:
            - '.'
          catalog:
            %s: '%s'
          """.formatted(logger, constraint);
    }

    private static String manifest(String logger) {
        return """
          {
            "name": "consumer",
            "version": "1.0.0",
            "dependencies": {
              "%s": "catalog:"
            }
          }
          """.formatted(logger);
    }
}

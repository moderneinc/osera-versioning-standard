package org.finos.osera.npm;

import org.finos.osera.fixers.npm.NpmRemediator;
import org.finos.osera.providers.npm.PackageVersioning;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.javascript.marker.NodeResolutionResult.PackageManager;

import java.util.Map;
import java.util.stream.Stream;

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
 * because a tool that reads one spelling of a catalog does not necessarily read the rest. A catalog
 * moves the version out of {@code package.json} and leaves a bare {@code "catalog:"} behind, so a
 * remediator that only knows how to rewrite a version literal in a manifest has nothing there to
 * edit, the same way Renovate has nothing to edit in the JVM's imported-BOM and managed-parent
 * rows.
 *
 * <p>Two spellings, which is the whole of the surface: pnpm keeps the catalog in
 * {@code pnpm-workspace.yaml} and Yarn keeps it in {@code .yarnrc.yml}, under the same
 * {@code catalog} key. npm has no catalog at all. Unlike the package-manager axis on the rest of
 * the matrix, this one is real for every column: the catalog is a source file the external tools
 * are handed, not a marker only the recipe reads.
 */
public record NpmCatalogTest(PackageVersioning versioning) implements NpmBuild {

    /** Which file holds the catalog, and what it looks like holding one entry. */
    private static String catalogFile(PackageManager pm) {
        return switch (pm) {
            case Pnpm -> "pnpm-workspace.yaml";
            case YarnBerry -> ".yarnrc.yml";
            default -> throw PackageManagers.notMeasured(pm);
        };
    }

    private static String catalog(PackageManager pm, String logger, String constraint) {
        return switch (pm) {
            // packages is what makes this a workspace, which is the smallest project a pnpm
            // catalog can exist in.
            case Pnpm -> """
              packages:
                - '.'
              catalog:
                %s: '%s'
              """.formatted(logger, constraint);
            case YarnBerry -> """
              catalog:
                %s: '%s'
              """.formatted(logger, constraint);
            default -> throw PackageManagers.notMeasured(pm);
        };
    }

    /**
     * The catalog is not a second resolution path.
     *
     * <p>One row per manager rather than the fifteen {@link NpmDeclaredRangeTest} runs, for the
     * same reason that class spot-checks the other package managers rather than repeating itself:
     * a catalog entry is a constraint like any other, and the question is only whether the manager
     * treats it as one. If it does, everything already measured about ranges applies unchanged.
     */
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = PackageManager.class, names = {"Pnpm", "YarnBerry"})
    void aCatalogEntryResolvesLikeAnyOtherConstraint(PackageManager pm) {
        assumeTrue(PackageManagers.isUsable(pm), pm + " is not installed at the measured major");

        String logger = versioning.freshPackage();
        versioning.publishUpstream(logger, "1.4.0", "1.4.1", "1.5.0", "2.0.0");
        String patched = versioning.publish(logger, "1.4.1");

        assertThat(NpmProject.install(versioning.registry(), pm, logger, manifest(logger),
          Map.of(catalogFile(pm), catalog(pm, logger, versioning.constraint(patched)))))
          .as("what %s installs for a catalog entry holding %s", pm, versioning.constraint(patched))
          .isEqualTo(patched);
    }

    static Stream<Arguments> eachColumnAndCatalog() {
        return NpmRemediator.all().flatMap(remediator ->
          Stream.of(PackageManager.Pnpm, PackageManager.YarnBerry)
            .map(pm -> Arguments.of(remediator, pm)));
    }

    /**
     * The consumer is held on the line by a catalog entry, and the backpatch exists. A tool that
     * fixes this build moves the version where it actually lives.
     *
     * <p>The OpenRewrite cells are red for a reason that is not about catalogs, and the chapter
     * must not read them as one. Run directly against these sources, the recipe replaces the
     * manifest's {@code "catalog:"} with the literal constraint and leaves the catalog file
     * untouched: it takes the package out of the catalog rather than updating it, which fixes this
     * one member and leaves every other member of a real workspace on the vulnerable version.
     * Inside the matrix that behaviour is invisible on pnpm, because the column composes
     * {@code UpgradeTransitiveDependencyVersion} and that recipe does not converge there — every
     * {@code OpenRewrite — Pnpm} cell in the suite fails the same way, before any assertion about
     * content is reached. Measured by running the two composed recipes separately:
     * {@code UpgradeDependencyVersion} alone converges on pnpm, the transitive one alone does not.
     * Same recipe as {@link NpmTransitiveTest}'s stale lock, and the same upstream issue.
     */
    @ParameterizedTest(name = "{displayName} — {0} — {1}")
    @MethodSource("eachColumnAndCatalog")
    void versionInACatalog(NpmRemediator remediator, PackageManager pm) {
        String logger = versioning.freshPackage();
        versioning.publishUpstream(logger, "1.4.0", "1.4.1", "1.5.0", "2.0.0");
        String patched = versioning.publish(logger, "1.4.1");

        rewriteRun(
          NpmRemediator.asSpec(remediator, versioning, logger),
          // The manifest says `catalog:` before and after: there is no version in it to move, and
          // a tool that wrote one here would be taking the package out of the catalog rather than
          // updating it.
          packageJson(manifest(logger), null,
            nodeResolutionResult(pm, dependency(logger, "catalog:"))),
          yaml(catalog(pm, logger, "~1.4.1"), catalog(pm, logger, versioning.constraint(patched)),
            spec -> spec.path(catalogFile(pm))));
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

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
 * <p>Four spellings, which is the whole surface: pnpm and Yarn each keep catalogs in a file of
 * their own, and each offers a default catalog and any number of named ones. npm has none. Unlike
 * the package-manager axis on the rest of the matrix this one is real for every column, because a
 * catalog is a source file the external tools are handed rather than a marker only the recipe
 * reads.
 */
public record NpmCatalogTest(PackageVersioning versioning) implements NpmBuild {

    /**
     * Every way a catalog can hold a version. Each is a separate parse path in every tool, which is
     * the argument chapter 3 makes for giving the Gradle catalog its own thirteen rows.
     */
    public enum Catalog {
        PnpmDefault(PackageManager.Pnpm, "pnpm-workspace.yaml", "catalog:", """
          packages:
            - '.'
          catalog:
            %s: '%s'
          """),
        PnpmNamed(PackageManager.Pnpm, "pnpm-workspace.yaml", "catalog:stable", """
          packages:
            - '.'
          catalogs:
            stable:
              %s: '%s'
          """),
        YarnDefault(PackageManager.YarnBerry, ".yarnrc.yml", "catalog:", """
          catalog:
            %s: '%s'
          """),
        YarnNamed(PackageManager.YarnBerry, ".yarnrc.yml", "catalog:stable", """
          catalogs:
            stable:
              %s: '%s'
          """);

        private final PackageManager pm;
        private final String file;
        private final String reference;
        private final String template;

        Catalog(PackageManager pm, String file, String reference, String template) {
            this.pm = pm;
            this.file = file;
            this.reference = reference;
            this.template = template;
        }

        /** What the manifest writes in place of a version. */
        String reference() {
            return reference;
        }

        String file() {
            return file;
        }

        String holding(String logger, String constraint) {
            return template.formatted(logger, constraint);
        }
    }

    /**
     * The catalog is not a second resolution path.
     *
     * <p>One row per spelling rather than the fifteen {@link NpmDeclaredRangeTest} runs, for the
     * same reason that class spot-checks the other package managers rather than repeating itself:
     * a catalog entry is a constraint like any other, and the question is only whether the manager
     * treats it as one. If it does, everything already measured about ranges applies unchanged.
     */
    @ParameterizedTest(name = "{0}")
    @EnumSource(Catalog.class)
    void aCatalogEntryResolvesLikeAnyOtherConstraint(Catalog catalog) {
        assumeTrue(PackageManagers.isUsable(catalog.pm),
          catalog.pm + " is not installed at the measured major");

        String logger = versioning.freshPackage();
        versioning.publishUpstream(logger, "1.4.0", "1.4.1", "1.5.0", "2.0.0");
        String patched = versioning.publish(logger, "1.4.1");

        assertThat(NpmProject.install(versioning.registry(), catalog.pm, logger,
          manifest(logger, catalog),
          Map.of(catalog.file(), catalog.holding(logger, versioning.constraint(patched)))))
          .as("what %s installs for a %s entry holding %s",
            catalog.pm, catalog, versioning.constraint(patched))
          .isEqualTo(patched);
    }

    static Stream<Arguments> eachColumnAndCatalog() {
        return NpmRemediator.all().flatMap(remediator ->
          Stream.of(Catalog.values()).map(catalog -> Arguments.of(remediator, catalog)));
    }

    /**
     * The consumer is held on the line by a catalog entry, and the backpatch exists. A tool that
     * fixes this build moves the version where it actually lives.
     *
     * <p>The OpenRewrite cells are red for a reason that is not about catalogs, and the chapter
     * must not read them as one. The recipe replaces the manifest's catalog reference with the
     * literal constraint and leaves the catalog file untouched: it takes the package out of the
     * catalog rather than updating it, which fixes this one member and leaves every other member of
     * a real workspace on the vulnerable version. That is visible in the Yarn cells and not in the
     * pnpm ones, because the column composes {@code UpgradeTransitiveDependencyVersion} and that
     * recipe does not converge on pnpm, so those cells fail before any assertion about content is
     * reached. Measured by running the two composed recipes separately:
     * {@code UpgradeDependencyVersion} alone converges on pnpm, the transitive one alone does not.
     * Same recipe as {@link NpmTransitiveTest}'s stale lock, and the same upstream issue.
     */
    @ParameterizedTest(name = "{displayName} — {0} — {1}")
    @MethodSource("eachColumnAndCatalog")
    void versionInACatalog(NpmRemediator remediator, Catalog catalog) {
        String logger = versioning.freshPackage();
        versioning.publishUpstream(logger, "1.4.0", "1.4.1", "1.5.0", "2.0.0");
        String patched = versioning.publish(logger, "1.4.1");

        rewriteRun(
          NpmRemediator.asSpec(remediator, versioning, logger),
          // The manifest keeps its catalog reference before and after: there is no version in it
          // to move, and a tool that wrote one here would be taking the package out of the catalog
          // rather than updating it.
          packageJson(manifest(logger, catalog), null,
            nodeResolutionResult(catalog.pm, dependency(logger, catalog.reference()))),
          yaml(catalog.holding(logger, "~1.4.1"),
            catalog.holding(logger, versioning.constraint(patched)),
            spec -> spec.path(catalog.file())));
    }

    private static String manifest(String logger, Catalog catalog) {
        return """
          {
            "name": "consumer",
            "version": "1.0.0",
            "dependencies": {
              "%s": "%s"
            }
          }
          """.formatted(logger, catalog.reference());
    }
}

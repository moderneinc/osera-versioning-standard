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
 * A version that lives outside the manifest, which is the npm analogue of a Gradle version
 * catalog. Four spellings is the whole surface: pnpm and Yarn each keep catalogs in a file of
 * their own, and each offers a default catalog and named ones.
 */
public record NpmCatalogTest(PackageVersioning versioning) implements NpmBuild {

    /** Each spelling is a separate parse path in every tool, so each gets a row. */
    public enum Catalog {
        // packages is what makes this a workspace, the smallest project a pnpm catalog needs.
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

    /** If a catalog entry is an ordinary constraint, {@link NpmDeclaredRangeTest} applies inside one. */
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
     * A tool that fixes this build edits the catalog. OpenRewrite instead overwrites the manifest's
     * catalog reference with the constraint, taking the package out of the catalog; its pnpm cells
     * fail earlier still, on {@code UpgradeTransitiveDependencyVersion} not converging there.
     */
    @ParameterizedTest(name = "{displayName} — {0} — {1}")
    @MethodSource("eachColumnAndCatalog")
    void versionInACatalog(NpmRemediator remediator, Catalog catalog) {
        String logger = versioning.freshPackage();
        versioning.publishUpstream(logger, "1.4.0", "1.4.1", "1.5.0", "2.0.0");
        String patched = versioning.publish(logger, "1.4.1");

        rewriteRun(
          NpmRemediator.asSpec(remediator, versioning, logger),
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

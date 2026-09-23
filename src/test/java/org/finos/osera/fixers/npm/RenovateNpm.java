package org.finos.osera.fixers.npm;

import org.finos.osera.fixers.ext.ExternalRemediation;
import org.finos.osera.fixers.ext.RenovateProcess;
import org.finos.osera.providers.npm.PackageVersioning;
import org.openrewrite.Recipe;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.finos.osera.fixers.ext.ToolProcess.request;

/**
 * Renovate against a {@code package.json}, twice. {@code ignoreUnstable} ships true and a backpatch
 * is a prerelease, so the difference between the columns is what the standard has to tell people.
 */
public sealed interface RenovateNpm extends NpmRemediator permits RenovateNpm.Stock, RenovateNpm.Configured {

    /** What this column passes down to Renovate's lookup. */
    Map<String, Object> lookup();

    /** Which versions this column has been told count. Empty when nothing has been configured. */
    String allowedVersions(PackageVersioning versioning);

    @Override
    default boolean isInstalled() {
        return RenovateProcess.isInstalled();
    }

    @Override
    default Recipe asRecipe(PackageVersioning versioning, String packageName) {
        RenovateNpm column = this;
        return new ExternalRemediation(new ExternalRemediation.Tool() {
            @Override
            public String name() {
                return column.toString();
            }

            @Override
            public void run(Path directory, List<Path> files) {
                // No certificate bundle: this registry serves plain HTTP.
                RenovateProcess.run(directory, Map.of(),
                  request(
                    "dir", directory.toString(),
                    "cacheDir", directory.getParent().resolve("renovate-cache").toString(),
                    "manager", "npm",
                    "versioning", "npm",
                    "files", files.stream().map(Path::toString).toList(),
                    "registryUrls", List.of(versioning.registry().url()),
                    "lookup", column.lookup(),
                    "targets", List.of(request(
                      "packageName", packageName,
                      "allowedVersions", column.allowedVersions(versioning)))));
            }
        });
    }

    /** Every value is what {@code getOptions()} reports as shipped, so this is a consumer who changed nothing. */
    final class Stock implements RenovateNpm {
        @Override
        public Map<String, Object> lookup() {
            return Map.of(
              "rangeStrategy", "auto",
              "separateMajorMinor", true,
              "separateMinorPatch", false,
              "updatePinnedDependencies", true,
              "ignoreUnstable", true,
              "respectLatest", true);
        }

        @Override
        public String allowedVersions(PackageVersioning versioning) {
            // A consumer who has configured nothing has not named the producer's line either.
            return "";
        }

        @Override
        public String toString() {
            return "Renovate (stock)";
        }
    }

    /** Renovate told to consider prereleases and restricted to the producer's line. */
    final class Configured implements RenovateNpm {
        @Override
        public Map<String, Object> lookup() {
            // The shipped defaults with one change, ignoreUnstable, plus the allowedVersions
            // below. Measured: rangeStrategy replace and respectLatest false moved no cell.
            return Map.of(
              "rangeStrategy", "auto",
              "separateMajorMinor", true,
              "separateMinorPatch", false,
              "updatePinnedDependencies", true,
              "ignoreUnstable", false,
              "respectLatest", true);
        }

        @Override
        public String allowedVersions(PackageVersioning versioning) {
            return "/" + versioning.backpatchPattern() + "/";
        }

        @Override
        public String toString() {
            return "Renovate (configured)";
        }
    }
}

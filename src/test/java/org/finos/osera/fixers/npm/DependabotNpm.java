package org.finos.osera.fixers.npm;

import org.finos.osera.fixers.ext.ExternalRemediation;
import org.finos.osera.fixers.ext.DependabotProcess;
import org.finos.osera.providers.npm.PackageVersioning;
import org.openrewrite.Recipe;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.finos.osera.fixers.ext.ToolProcess.request;

/**
 * Dependabot's npm updater. {@code filter_prerelease_versions} drops a prerelease unless the
 * current version or a declared requirement is already one at the same tuple, so no configuration
 * reaches the backpatch. A different reason from the JVM columns' {@code suffix_compatible?}.
 */
public final class DependabotNpm implements NpmRemediator {

    @Override
    public boolean isInstalled() {
        return DependabotProcess.isInstalled();
    }

    @Override
    public Recipe asRecipe(PackageVersioning versioning, String packageName) {
        return new ExternalRemediation(new ExternalRemediation.Tool() {
            @Override
            public String name() {
                return "Dependabot";
            }

            @Override
            public void run(Path directory, List<Path> files) {
                DependabotProcess.run(directory, Map.of(),
                  request(
                    "dir", directory.toString(),
                    "ecosystem", "npm_and_yarn",
                    "files", files.stream().map(Path::toString).toList(),
                    "registryUrls", List.of(versioning.registry().url()),
                    // Nothing is ruled out: the question is what Dependabot offers a consumer
                    // who has configured it no further, and the answer is part of the measurement.
                    "targets", List.of(request(
                      "packageName", packageName,
                      "ignoredVersions", List.<String>of(),
                      // A backpatch may arrive underneath something the consumer declares, and a
                      // subdependency has no requirement of its own to unlock.
                      "unlockStrategies", List.of("own", "none")))));
            }
        });
    }

    @Override
    public String toString() {
        return "Dependabot";
    }
}

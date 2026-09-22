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
 * Dependabot's npm updater.
 *
 * <p>{@code LatestVersionFinder#filter_prerelease_versions} drops a prerelease unless
 * {@code related_to_current_pre?} holds, which needs the current version or a declared requirement
 * to already be a prerelease at the same release tuple. A consumer sitting on a stable release is
 * neither, so no configuration reaches the backpatch; what it is offered instead is the next major.
 *
 * <p>This is a different reason from the JVM columns, where {@code suffix_compatible?} rejects a
 * qualified candidate against an unqualified current version.
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
                // No certificate bundle: this registry serves plain HTTP.
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
                      "ignoredVersions", List.<String>of()))));
            }
        });
    }

    @Override
    public String toString() {
        return "Dependabot";
    }
}

package org.finos.osera.fixers.npm;

import org.finos.osera.fixers.ext.ExternalRemediation;
import org.finos.osera.fixers.ext.ToolProcess;
import org.finos.osera.providers.npm.PackageVersioning;
import org.openrewrite.Recipe;

import java.nio.file.Files;
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

    private static final Path GEMS = Path.of(System.getProperty("osera.dependabot", "build/dependabot/gems"));

    /** The system Ruby on macOS is 2.6 and too old for the gems; {@code -Posera.ruby} names a newer one. */
    private static final String RUBY = System.getProperty("osera.ruby", "ruby");

    @Override
    public boolean isInstalled() {
        return Files.isDirectory(GEMS.resolve("gems"));
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
                ToolProcess.run(
                  List.of(RUBY, ToolProcess.script("tools/dependabot/update.rb", directory.getParent()).toString()),
                  Map.of("GEM_HOME", GEMS.toAbsolutePath().toString()),
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

package org.finos.osera.fixers;

import org.finos.osera.fixers.ext.DependabotProcess;
import org.finos.osera.fixers.ext.ExternalRemediation;
import org.finos.osera.fixers.ext.ToolProcess;
import org.finos.osera.providers.ProviderVersioning;
import org.openrewrite.Recipe;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.finos.osera.fixers.ext.ToolProcess.request;

/**
 * Dependabot's Maven and Gradle update checkers require a candidate's version suffix to equal the
 * current version's ({@code Shared::SharedVersionFinder#suffix_compatible?}), so a dependency at
 * an unqualified release is never offered a qualified one. That rules out every version a producer
 * could choose, {@code -osera}, {@code -osera-00001} and {@code +osera.1} alike, before its
 * ordering is ever consulted.
 */
public final class Dependabot implements Remediator {

    @Override
    public boolean isInstalled() {
        return DependabotProcess.isInstalled();
    }

    @Override
    public Recipe asRecipe(ProviderVersioning versioning) {
        return new ExternalRemediation(new ExternalRemediation.Tool() {
            @Override
            public String name() {
                return "Dependabot";
            }

            @Override
            public void run(Path directory, List<Path> files) {
                boolean maven = files.stream().anyMatch(file -> file.endsWith("pom.xml"));
                DependabotProcess.run(directory,
                  Map.of("SSL_CERT_FILE", versioning.repository().certificateBundle().toString()),
                  request(
                    "dir", directory.toString(),
                    // Dependabot ships Maven::Version and Gradle::Version separately, and they do
                    // not agree about a patch qualifier. Letting it pick is the point.
                    "ecosystem", maven ? "maven" : "gradle",
                    "files", files.stream().map(Path::toString).toList(),
                    "registryUrls", List.of(versioning.repository().url(), MAVEN_CENTRAL),
                    "targets", targets(versioning)));
            }
        });
    }

    /**
     * One target per patch the producer has published. Dependabot can only be told which versions
     * to ignore, never which to prefer, so the patch itself becomes the ceiling; whether Dependabot
     * then counts it as newer than the version it patches is the question being asked.
     */
    private static List<Map<String, Object>> targets(ProviderVersioning versioning) {
        return versioning.repository().published().stream()
          .map(patched -> request(
            "packageName", patched.getGroupId() + ":" + patched.getArtifactId(),
            "ignoredVersions", List.of("> " + requireNonNull(patched.getVersion()))))
          .toList();
    }

    @Override
    public String toString() {
        return "Dependabot";
    }
}

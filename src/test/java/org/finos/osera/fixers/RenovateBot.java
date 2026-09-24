package org.finos.osera.fixers;

import org.finos.osera.fixers.ext.ExternalRemediation;
import org.finos.osera.fixers.ext.RenovateProcess;
import org.finos.osera.fixers.ext.ToolProcess;
import org.finos.osera.providers.OseraMavenCentralCare;
import org.finos.osera.providers.ProviderVersioning;
import org.finos.osera.providers.SemverV2BuildMetadata;
import org.openrewrite.Recipe;
import org.openrewrite.maven.tree.GroupArtifactVersion;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.finos.osera.fixers.ext.ToolProcess.request;

public final class RenovateBot implements Remediator {

    @Override
    public boolean isInstalled() {
        return RenovateProcess.isInstalled();
    }

    @Override
    public Recipe asRecipe(ProviderVersioning versioning) {
        return new ExternalRemediation(new ExternalRemediation.Tool() {
            @Override
            public String name() {
                return "Renovate";
            }

            @Override
            public void run(Path directory, List<Path> files) {
                boolean maven = files.stream().anyMatch(file -> file.endsWith("pom.xml"));
                RenovateProcess.run(directory,
                  Map.of("NODE_EXTRA_CA_CERTS", versioning.repository().certificateBundle().toString()),
                  request(
                    "dir", directory.toString(),
                    // What the script used to impose on every caller, stated here instead.
                    "lookup", Map.of(
                      "rangeStrategy", "replace",
                      "separateMinorPatch", true,
                      "updatePinnedDependencies", true,
                      "respectLatest", false),
                    "cacheDir", directory.getParent().resolve("renovate-cache").toString(),
                    // Renovate picks a versioning module per manager, and the two do not agree
                    // about a patch qualifier. Letting it pick is the point.
                    "manager", maven ? "maven" : "gradle",
                    "versioning", maven ? "maven" : "gradle",
                    "files", files.stream().map(Path::toString).toList(),
                    "registryUrls", List.of(versioning.repository().url(), MAVEN_CENTRAL),
                    "targets", targets(versioning)));
            }
        });
    }

    /**
     * One target per artifact the producer has patched. {@code allowedVersions} is Renovate's own
     * way to say "only versions on this line count", the same instruction the recipe remediator
     * gets as a {@code versionPattern}.
     */
    private static List<Map<String, Object>> targets(ProviderVersioning versioning) {
        String allowedVersions = switch (versioning) {
            case OseraMavenCentralCare _ -> "/-osera-\\d+$/";
            case SemverV2BuildMetadata _ -> "/\\+osera\\./";
        };
        return versioning.repository().published().stream()
          .map(GroupArtifactVersion::asGroupArtifact)
          .distinct()
          .map(patched -> request(
            "packageName", patched.getGroupId() + ":" + patched.getArtifactId(),
            "allowedVersions", allowedVersions))
          .toList();
    }

    @Override
    public String toString() {
        return "Renovate Bot";
    }
}

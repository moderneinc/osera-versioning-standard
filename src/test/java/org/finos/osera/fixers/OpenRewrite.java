package org.finos.osera.fixers;

import org.finos.osera.providers.MavenCentralCare;
import org.finos.osera.providers.OseraQualifiedSequence;
import org.finos.osera.providers.ProviderVersioning;
import org.finos.osera.providers.SemverV2BuildMetadata;
import org.openrewrite.Recipe;
import org.openrewrite.config.CompositeRecipe;
import org.openrewrite.config.Environment;
import org.openrewrite.maven.tree.GroupArtifact;
import org.openrewrite.maven.tree.GroupArtifactVersion;

import java.util.Properties;

/** The stock upgrade recipes for direct and transitive dependencies, composed in {@code META-INF/rewrite/osera.yml}. */
public final class OpenRewrite implements Remediator {
    @Override
    public Recipe asRecipe(ProviderVersioning versioning) {
        // Without a pattern, OpenRewrite reads any qualifier as a pre-release and never selects a patch.
        String versionPattern = switch (versioning) {
            case MavenCentralCare _ -> "-osera";
            case OseraQualifiedSequence _ -> "-osera-\\d+";
            case SemverV2BuildMetadata _ -> "\\+osera.*";
        };
        // One activation per patched artifact: the Gradle transitive recipe skips any configuration
        // whose direct dependencies match its pattern, so a wildcard would match the declaring dependency itself.
        return new CompositeRecipe(versioning.repository().published().stream()
          .map(GroupArtifactVersion::asGroupArtifact)
          .distinct()
          .map(patched -> upgradeToPatch(patched, versionPattern))
          .toList());
    }

    private static Recipe upgradeToPatch(GroupArtifact patched, String versionPattern) {
        Properties properties = new Properties();
        properties.setProperty("groupId", patched.getGroupId());
        properties.setProperty("artifactId", patched.getArtifactId());
        properties.setProperty("versionPattern", versionPattern);
        return Environment.builder(properties)
          .scanYamlResources()
          .build()
          .activateRecipes("org.finos.osera.UpgradeToPatch");
    }

    @Override
    public String toString() {
        return "OpenRewrite";
    }
}

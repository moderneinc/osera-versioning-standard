package org.finos.osera.fixers;

import org.finos.osera.providers.OseraMavenCentralCare;
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
        String versionPattern = switch (versioning) {
            case OseraMavenCentralCare _ -> "(\\.[A-Za-z]\\w*)?-osera-\\d+";
            case SemverV2BuildMetadata _ -> "(\\.[A-Za-z]\\w*)?\\+osera.*";
        };
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

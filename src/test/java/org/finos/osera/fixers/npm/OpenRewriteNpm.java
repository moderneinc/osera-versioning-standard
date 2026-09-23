package org.finos.osera.fixers.npm;

import org.finos.osera.providers.npm.PackageVersioning;
import org.openrewrite.Recipe;

import java.util.List;
import org.openrewrite.config.CompositeRecipe;
import org.openrewrite.javascript.UpgradeDependencyVersion;
import org.openrewrite.javascript.UpgradeTransitiveDependencyVersion;

/** The stock upgrade recipes for direct and transitive dependencies, told the scheme's constraint. */
public final class OpenRewriteNpm implements NpmRemediator {

    @Override
    public Recipe asRecipe(PackageVersioning versioning, String packageName) {
        String constraint = versioning.constraint(versioning.newestBackpatch(packageName));
        // Both, as the JVM column composes both in osera.yml: a column is not told which shape it faces.
        return new CompositeRecipe(List.of(
          new UpgradeDependencyVersion(packageName, null, constraint),
          new UpgradeTransitiveDependencyVersion(packageName, constraint, null)));
    }

    @Override
    public boolean alsoPinsWithAnOverride() {
        return true;
    }

    @Override
    public boolean readsPackageManager() {
        return true;
    }

    @Override
    public String toString() {
        return "OpenRewrite";
    }
}

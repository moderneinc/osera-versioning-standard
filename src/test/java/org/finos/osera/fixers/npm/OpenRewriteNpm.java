package org.finos.osera.fixers.npm;

import org.finos.osera.providers.npm.PackageVersioning;
import org.openrewrite.Recipe;

import java.util.List;
import org.openrewrite.config.CompositeRecipe;
import org.openrewrite.javascript.UpgradeDependencyVersion;
import org.openrewrite.javascript.UpgradeTransitiveDependencyVersion;

/**
 * OpenRewrite's npm upgrade recipe, told the scheme's constraint. Unlike the Maven and Gradle
 * recipes it takes a literal constraint rather than a selector, so this column measures whether it
 * can reach a declaration, not whether it can choose a version.
 */
public final class OpenRewriteNpm implements NpmRemediator {

    @Override
    public Recipe asRecipe(PackageVersioning versioning, String packageName) {
        String constraint = versioning.constraint(versioning.newestBackpatch(packageName));
        // Both, as the JVM column composes both in osera.yml: a consumer may declare the
        // vulnerable package or may only have it underneath something they do declare, and the
        // column should not have to be told which.
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

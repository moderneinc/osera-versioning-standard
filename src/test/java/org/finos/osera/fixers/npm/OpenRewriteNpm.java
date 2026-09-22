package org.finos.osera.fixers.npm;

import org.finos.osera.providers.npm.PackageVersioning;
import org.openrewrite.Recipe;
import org.openrewrite.javascript.UpgradeDependencyVersion;

/**
 * OpenRewrite's npm upgrade recipe, told the scheme's constraint.
 *
 * <p>Unlike the Maven and Gradle recipes, this one takes a literal constraint rather than a
 * {@code latest.patch} selector with a version pattern, and does no semver reasoning of its own: it
 * writes whatever it is given whenever the string differs. So this column measures whether the
 * recipe can reach a declaration, not whether it can choose a version. Choosing is
 * {@code NpmWrittenConstraintTest}'s question.
 */
public final class OpenRewriteNpm implements NpmRemediator {

    @Override
    public Recipe asRecipe(PackageVersioning versioning, String packageName) {
        return new UpgradeDependencyVersion(packageName, null,
          versioning.constraint(versioning.newestBackpatch(packageName)));
    }

    @Override
    public String toString() {
        return "OpenRewrite";
    }
}

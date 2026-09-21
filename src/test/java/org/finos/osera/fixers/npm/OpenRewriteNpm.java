package org.finos.osera.fixers.npm;

import org.finos.osera.npm.MockNpmRegistry;
import org.finos.osera.providers.npm.PackageVersioning;
import org.openrewrite.Recipe;
import org.openrewrite.config.CompositeRecipe;
import org.openrewrite.javascript.UpgradeDependencyVersion;

import java.util.Comparator;
import java.util.Optional;

/**
 * OpenRewrite's npm upgrade recipe, told the scheme's constraint.
 *
 * <p>Unlike the Maven and Gradle recipes, this one takes a literal constraint rather than a
 * {@code latest.patch} selector with a version pattern, and does no semver reasoning of its own: it
 * writes whatever it is given whenever the string differs. So this column measures whether the
 * recipe can reach a declaration and regenerate its lock, not whether it can choose a version.
 * Choosing is {@code NpmWrittenConstraintTest}'s question.
 */
public final class OpenRewriteNpm implements NpmRemediator {

    @Override
    public Recipe asRecipe(PackageVersioning versioning) {
        MockNpmRegistry registry = versioning.registry();
        // The registry is shared for the run, so it also holds packages belonging to other
        // scenarios and packages this producer has not patched at all. Both are skipped.
        return new CompositeRecipe(registry.packages().stream()
          .flatMap(name -> newest(registry, name).stream()
            .map(patched -> (Recipe) new UpgradeDependencyVersion(
              name, null, versioning.constraint(patched))))
          .toList());
    }

    /** The newest backpatch this producer has published for the artifact, if any. */
    private static Optional<String> newest(MockNpmRegistry registry, String name) {
        return registry.published(name).stream()
          .filter(version -> version.contains("-" + PackageVersioning.OSERA + "-"))
          .max(Comparator.naturalOrder());
    }

    @Override
    public String toString() {
        return "OpenRewrite";
    }
}

package org.finos.osera.fixers.npm;

import org.finos.osera.fixers.ext.ExternalRemediation;
import org.finos.osera.providers.npm.PackageVersioning;
import org.openrewrite.Recipe;
import org.openrewrite.test.RecipeSpec;

import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A way a consumer's {@code package.json} might be moved onto a backpatch.
 *
 * <p>Renovate appears twice on purpose. Its shipped defaults and a configuration that names the
 * producer's line give different answers, and merging them would hide the thing the standard has to
 * mandate.
 */
public sealed interface NpmRemediator permits OpenRewriteNpm, RenovateNpm, DependabotNpm {

    /**
     * @param packageName The dependency the scenario is about. One registry serves the whole run,
     *                    so its key set is every other scenario's fixture as well; a column that
     *                    worked from that would re-parse the project once per package it has never
     *                    heard of.
     */
    Recipe asRecipe(PackageVersioning versioning, String packageName);

    /** A toolchain nobody installed is a cell with no answer, which is not a refusal. */
    default boolean isInstalled() {
        return true;
    }

    static Consumer<RecipeSpec> asSpec(NpmRemediator remediator, PackageVersioning versioning, String packageName) {
        return spec -> {
            assumeTrue(remediator.isInstalled(), remediator + " is not installed");
            Recipe recipe = remediator.asRecipe(versioning, packageName);
            // A recipe that shells out to another tool holds a process, not data, so it cannot make
            // the serialization round trip that a recipe published to a catalog has to.
            spec.validateRecipeSerialization(!(recipe instanceof ExternalRemediation))
              .recipe(recipe);
        };
    }

    /**
     * Every column, for use as a {@code @MethodSource}.
     *
     * <p>Written out rather than walked from the permits clause, because Renovate's two columns are
     * leaves of a nested sealed interface and the reflective walk only goes one level. A new column
     * has to be added here by hand.
     */
    static Stream<NpmRemediator> all() {
        return Stream.of(
          new OpenRewriteNpm(),
          new RenovateNpm.Stock(),
          new RenovateNpm.Configured(),
          new DependabotNpm());
    }
}

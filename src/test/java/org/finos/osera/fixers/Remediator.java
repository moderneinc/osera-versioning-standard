package org.finos.osera.fixers;

import org.finos.osera.ProviderVersioningResolver;
import org.finos.osera.fixers.ext.ExternalRemediation;
import org.finos.osera.providers.ProviderVersioning;
import org.openrewrite.Recipe;
import org.openrewrite.test.RecipeSpec;

import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

public sealed interface Remediator permits
  OpenRewrite,
  RenovateBot,
  Dependabot {

    String MAVEN_CENTRAL = "https://repo.maven.apache.org/maven2";

    /**
     * @param versioning The scheme whose patches the remediation has to recognize.
     * @return A remediation in the form of an OpenRewrite recipe.
     * That doesn't mean the remediation itself has to be a recipe. It
     * just helps to fit the test contract for before/after comparisons
     * in the TCK.
     */
    Recipe asRecipe(ProviderVersioning versioning);

    /**
     * Whether this remediation can run at all here. A toolchain nobody installed is a cell the
     * matrix has no answer for, which is not the same answer as a remediation that failed.
     */
    default boolean isInstalled() {
        return true;
    }

    static Consumer<RecipeSpec> asSpec(Remediator remediator, ProviderVersioning versioning) {
        return spec -> {
            assumeTrue(remediator.isInstalled(), remediator + " is not installed");
            Recipe recipe = remediator.asRecipe(versioning);
            // A recipe that shells out to another tool holds a process, not data, so it cannot make
            // the serialization round trip that a recipe published to a catalog has to.
            spec.validateRecipeSerialization(!(recipe instanceof ExternalRemediation))
              .recipe(recipe);
        };
    }

    /** Every remediator, for use as a {@code @MethodSource}. */
    static Stream<Remediator> all() {
        return ProviderVersioningResolver.Sealed.implementations(Remediator.class);
    }
}

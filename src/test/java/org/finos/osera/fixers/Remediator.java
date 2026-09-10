package org.finos.osera.fixers;

import org.finos.osera.ProviderVersioningResolver;
import org.finos.osera.providers.ProviderVersioning;
import org.openrewrite.Recipe;
import org.openrewrite.test.RecipeSpec;

import java.util.function.Consumer;
import java.util.stream.Stream;

public sealed interface Remediator permits
  OpenRewrite,
  RenovateBot,
  Dependabot {
    /**
     * @param versioning The scheme whose patches the remediation has to recognize.
     * @return A remediation in the form of an OpenRewrite recipe.
     * That doesn't mean the remediation itself has to be a recipe. It
     * just helps to fit the test contract for before/after comparisons
     * in the TCK.
     */
    Recipe asRecipe(ProviderVersioning versioning);

    static Consumer<RecipeSpec> asSpec(Remediator remediator, ProviderVersioning versioning) {
        return spec -> spec.recipe(remediator.asRecipe(versioning));
    }

    /** Every remediator, for use as a {@code @MethodSource}. */
    static Stream<Remediator> all() {
        return ProviderVersioningResolver.Sealed.implementations(Remediator.class);
    }
}

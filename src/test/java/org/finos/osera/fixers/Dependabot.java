package org.finos.osera.fixers;

import org.finos.osera.providers.ProviderVersioning;
import org.openrewrite.Recipe;

public final class Dependabot implements Remediator {
    @Override
    public Recipe asRecipe(ProviderVersioning versioning) {
        return null;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}

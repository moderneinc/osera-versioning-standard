package org.finos.osera.fixers.npm;

import org.finos.osera.fixers.ext.ExternalRemediation;
import org.finos.osera.providers.npm.PackageVersioning;
import org.openrewrite.Recipe;
import org.junit.jupiter.params.provider.Arguments;
import org.openrewrite.javascript.marker.NodeResolutionResult.PackageManager;
import org.openrewrite.test.RecipeSpec;

import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** A way a consumer's {@code package.json} might be moved onto a backpatch. */
public sealed interface NpmRemediator permits OpenRewriteNpm, RenovateNpm, DependabotNpm {

    /** @param packageName The dependency the scenario is about; the registry holds every other one too. */
    Recipe asRecipe(PackageVersioning versioning, String packageName);

    /** Whether this column pins with an override as well as moving the declaration. */
    default boolean alsoPinsWithAnOverride() {
        return false;
    }

    /**
     * Only the recipe column sees one: {@link ExternalRemediation} hands the others printed source,
     * and markers are not printed, so the bytes are identical whichever manager a fixture names.
     */
    default boolean readsPackageManager() {
        return false;
    }

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

    /** Every column against every package manager it can tell apart. */
    static Stream<Arguments> allAcrossManagers() {
        return all().flatMap(remediator -> (remediator.readsPackageManager()
          ? Stream.of(PackageManager.Npm, PackageManager.Pnpm, PackageManager.YarnBerry)
          : Stream.of(PackageManager.Npm))
          .map(pm -> Arguments.of(remediator, pm)));
    }

    /** Written out rather than walked from the permits clause: Renovate's two are nested one level down. */
    static Stream<NpmRemediator> all() {
        return Stream.of(
          new OpenRewriteNpm(),
          new RenovateNpm.Stock(),
          new RenovateNpm.Configured(),
          new DependabotNpm());
    }
}

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

    /**
     * Whether this column pins the package with an override as well as moving the declaration.
     *
     * <p>A property of the column, not of the scenario: {@link OpenRewriteNpm} composes the
     * transitive recipe alongside the direct one so that it need not be told which shape it is
     * facing, and on npm — unlike the JVM pair — the transitive recipe also writes an override for
     * a dependency that is already direct and already correct.
     */
    default boolean alsoPinsWithAnOverride() {
        return false;
    }

    /**
     * Whether the package manager reaches this column at all.
     *
     * <p>Only the recipe column sees one. The external columns are handed printed source text by
     * {@link ExternalRemediation}, and markers are not printed, so the bytes they receive are
     * identical whichever manager the fixture names. Running them three times measures nothing and
     * costs three subprocesses.
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

    /**
     * Every column, for use as a {@code @MethodSource}.
     *
     * <p>Written out rather than walked from the permits clause, because Renovate's two columns are
     * leaves of a nested sealed interface and the reflective walk only goes one level. A new column
     * has to be added here by hand.
     */
    /**
     * Every column against every package manager measured. Resolution is shared between them, but
     * the lockfile writers are not: Dependabot ships four for this one ecosystem and OpenRewrite
     * regenerates through a different one per manager.
     */
    static Stream<Arguments> allAcrossManagers() {
        return all().flatMap(remediator -> (remediator.readsPackageManager()
          ? Stream.of(PackageManager.Npm, PackageManager.Pnpm, PackageManager.YarnBerry)
          : Stream.of(PackageManager.Npm))
          .map(pm -> Arguments.of(remediator, pm)));
    }

    static Stream<NpmRemediator> all() {
        return Stream.of(
          new OpenRewriteNpm(),
          new RenovateNpm.Stock(),
          new RenovateNpm.Configured(),
          new DependabotNpm());
    }
}

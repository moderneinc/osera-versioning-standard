package org.finos.osera.npm;

import org.finos.osera.fixers.npm.EachNpmRemediator;
import org.finos.osera.fixers.npm.NpmRemediator;
import org.finos.osera.providers.npm.PackageVersioning;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openrewrite.javascript.UpgradeDependencyVersion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.openrewrite.javascript.Assertions.npm;
import static org.openrewrite.javascript.Assertions.packageJson;
import static org.openrewrite.javascript.Assertions.packageLock;
import static org.openrewrite.test.SourceSpecs.dir;

/**
 * The ways a {@code package.json} arrives at a version, and whether each remediation tool can reach
 * them. This covers a direct dependency; the declaration shapes and the other package managers
 * follow.
 */
public record NpmTest(PackageVersioning versioning) implements NpmBuild {

    /**
     * Proves the recipe can change this fixture at all.
     *
     * <p>Without it, every "no change" result in this class is worthless: a {@code package.json}
     * with no {@code NodeResolutionResult} marker makes the recipe return the tree untouched, and a
     * marker with no package manager makes lock regeneration return null. Both are silent.
     */
    @Test
    void control(@TempDir Path project) throws IOException {
        String pkg = versioning.freshPackage();
        versioning.publishUpstream(pkg, "1.4.0", "1.4.1", "1.5.0", "2.0.0");

        String before = manifest(pkg, "~1.4.1");
        npm(project, packageJson(before));
        String lock = Files.readString(project.resolve("package-lock.json"));

        rewriteRun(
          spec -> spec.recipe(new UpgradeDependencyVersion(pkg, null, "~1.5.0")),
          dir(project.toString(),
            packageJson(before, manifest(pkg, "~1.5.0")),
            packageLock(lock, null, lockSpec -> lockSpec.after(regenerated -> {
                if (!regenerated.contains("\"version\": \"1.5.0\"")) {
                    throw new AssertionError("""
                      The control did not regenerate the lock, so no "unchanged" result in this \
                      class means anything. Check that the fixture was built with Assertions.npm() \
                      and that the marker carries a package manager.""");
                }
                return regenerated;
            }))));
    }

    @EachNpmRemediator
    void directDependency(NpmRemediator remediator, @TempDir Path project) throws IOException {
        String pkg = versioning.freshPackage();
        versioning.publishUpstream(pkg, "1.4.0", "1.4.1", "1.5.0", "2.0.0");
        String patched = versioning.publish(pkg, "1.4.1");

        String before = manifest(pkg, "~1.4.1");
        npm(project, packageJson(before));

        // The manifest only. Two of these columns drive a file updater that rewrites declarations
        // and never regenerates a lock, so scoring them on the lock would measure something this
        // harness does not ask them to do. Lock regeneration is OpenRewrite's, and the control
        // above is where it is measured.
        rewriteRun(
          NpmRemediator.asSpec(remediator, versioning),
          dir(project.toString(),
            packageJson(before, manifest(pkg, versioning.constraint(patched)))));
    }

    private static String manifest(String name, String constraint) {
        return """
          {
            "name": "consumer",
            "version": "1.0.0",
            "dependencies": {
              "%s": "%s"
            }
          }
          """.formatted(name, constraint);
    }
}

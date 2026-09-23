package org.finos.osera.npm;

import org.finos.osera.fixers.npm.EachNpmRemediator;
import org.finos.osera.fixers.npm.EachNpmRemediatorAndManager;
import org.finos.osera.fixers.npm.NpmRemediator;
import org.finos.osera.providers.npm.PackageVersioning;
import org.junit.jupiter.api.Test;
import org.openrewrite.javascript.UpgradeDependencyVersion;

import java.util.List;

import static org.openrewrite.javascript.Assertions.dependency;
import static org.openrewrite.javascript.Assertions.nodeResolutionResult;
import static org.openrewrite.javascript.Assertions.packageJson;
import static org.openrewrite.javascript.Assertions.packageLock;
import static org.openrewrite.test.SourceSpecs.dir;
import org.openrewrite.javascript.marker.NodeResolutionResult.PackageManager;

import static org.openrewrite.javascript.marker.NodeResolutionResult.PackageManager.Npm;

/**
 * The ways a {@code package.json} arrives at a version, and whether each remediation tool can reach
 * them.
 *
 * <p>The resolution marker is supplied rather than produced by an install, so these stay the same
 * shape as the Maven and Gradle scenarios: text in, text out. What npm's own resolver does is
 * {@link NpmDeclaredRangeTest}'s question.
 */
public record NpmTest(PackageVersioning versioning) implements NpmBuild {

    /**
     * Without this, every "no change" result in this class is worthless: a missing marker makes the
     * recipe return the tree untouched and a marker without a package manager makes lock
     * regeneration return null, both silently.
     */
    @Test
    void control() {
        String pkg = versioning.freshPackage();
        versioning.publishUpstream(pkg, "1.4.0", "1.4.1", "1.5.0", "2.0.0");

        rewriteRun(
          spec -> spec.recipe(new UpgradeDependencyVersion(pkg, null, "~1.5.0")),
          packageJson(manifest(pkg, "~1.4.1"), manifest(pkg, "~1.5.0"),
            nodeResolutionResult(Npm, dependency(pkg, "~1.4.1"))),
          packageLock(lock(pkg, "~1.4.1", "1.4.1"), null, spec -> spec.after(regenerated -> {
              if (!regenerated.contains("\"version\": \"1.5.0\"")) {
                  throw new AssertionError("""
                    The control did not regenerate the lock, so no "unchanged" result in this \
                    class means anything.""");
              }
              return regenerated;
          })));
    }

    @EachNpmRemediatorAndManager
    void directDependency(NpmRemediator remediator, PackageManager pm) {
        String pkg = versioning.freshPackage();
        versioning.publishUpstream(pkg, "1.4.0", "1.4.1", "1.5.0", "2.0.0");
        String patched = versioning.publish(pkg, "1.4.1");

        // The manifest only: two columns never regenerate a lock, and the control measures that.
        // OpenRewrite's transitive recipe also writes an override for a dependency that is already
        // direct, so the expected output carries that redundancy rather than hiding it.
        String after = remediator.alsoPinsWithAnOverride()
          ? manifestWithRedundantOverride(pkg, pm, versioning.constraint(patched))
          : manifest(pkg, versioning.constraint(patched));

        rewriteRun(
          NpmRemediator.asSpec(remediator, versioning, pkg),
          packageJson(manifest(pkg, "~1.4.1"), after,
            nodeResolutionResult(pm, dependency(pkg, "~1.4.1"))));
    }

    /** What OpenRewrite leaves behind: the declaration moved, and an override it did not need. */
    private static String manifestWithRedundantOverride(String pkg, PackageManager pm, String constraint) {
        //language=JSON
        return """
          {
            "name": "consumer",
            "version": "1.0.0",
            "dependencies": {
              "%s": "%s"
            },
          %s
          }""".formatted(pkg, constraint, Overrides.member(pm, pkg, constraint));
    }

    /**
     * The npm equivalent of the JVM's {@code secondPatch}, and the only place a tool acts on the
     * zero-padding. One package manager: which backpatch wins is a version comparison, and the
     * manager only decides how the override key is spelled.
     */
    @EachNpmRemediator
    void secondBackpatch(NpmRemediator remediator) {
        String pkg = versioning.freshPackage();
        versioning.publishUpstream(pkg, "1.4.0", "1.4.1", "1.5.0", "2.0.0");
        versioning.publish(pkg, "1.4.1", PackageVersioning.OSERA, 1);
        String newest = versioning.publish(pkg, "1.4.1", PackageVersioning.OSERA, 2);

        String after = remediator.alsoPinsWithAnOverride() ?
          manifestWithRedundantOverride(pkg, Npm, versioning.constraint(newest)) :
          manifest(pkg, versioning.constraint(newest));

        rewriteRun(
          NpmRemediator.asSpec(remediator, versioning, pkg),
          packageJson(manifest(pkg, "~1.4.1"), after,
            nodeResolutionResult(Npm, dependency(pkg, "~1.4.1"))));
    }

    /** The analogue of the JVM's multiModule: the declaration is not in the file a tool starts from. */
    @EachNpmRemediatorAndManager
    void workspaceMember(NpmRemediator remediator, PackageManager pm) {
        String pkg = versioning.freshPackage();
        versioning.publishUpstream(pkg, "1.4.0", "1.4.1", "1.5.0", "2.0.0");
        String patched = versioning.publish(pkg, "1.4.1");

        // OpenRewrite also pins from the root, which for a workspace applies to every member.
        String rootAfter = remediator.alsoPinsWithAnOverride()
          ? workspaceRootWithOverride(pkg, pm, versioning.constraint(patched))
          : null;

        rewriteRun(
          NpmRemediator.asSpec(remediator, versioning, pkg),
          packageJson(workspaceRoot(), rootAfter,
            nodeResolutionResult(pm, List.of("packages/member/package.json"))),
          dir("packages/member",
            packageJson(manifest(pkg, "~1.4.1"),
              // Pinned here as well as in the root, so the package ends up constrained three times.
              remediator.alsoPinsWithAnOverride()
                ? manifestWithRedundantOverride(pkg, pm, versioning.constraint(patched))
                : manifest(pkg, versioning.constraint(patched)),
              nodeResolutionResult(pm, dependency(pkg, "~1.4.1")))));
    }

    /** The root once OpenRewrite has pinned the package for every member. */
    private static String workspaceRootWithOverride(String pkg, PackageManager pm, String constraint) {
        //language=JSON
        return """
          {
            "name": "consumer",
            "version": "1.0.0",
            "private": true,
            "workspaces": [ "packages/*" ],
          %s
          }""".formatted(Overrides.member(pm, pkg, constraint));
    }

    /** A root that declares nothing itself and delegates to its members. */
    private static String workspaceRoot() {
        //language=JSON
        return """
          {
            "name": "consumer",
            "version": "1.0.0",
            "private": true,
            "workspaces": [ "packages/*" ]
          }
          """;
    }

    private static String manifest(String pkg, String constraint) {
        //language=JSON
        return """
          {
            "name": "consumer",
            "version": "1.0.0",
            "dependencies": {
              "%s": "%s"
            }
          }
          """.formatted(pkg, constraint);
    }

    private String lock(String pkg, String constraint, String resolved) {
        //language=JSON
        return """
          {
            "name": "consumer",
            "version": "1.0.0",
            "lockfileVersion": 3,
            "requires": true,
            "packages": {
              "": {
                "name": "consumer",
                "version": "1.0.0",
                "dependencies": { "%s": "%s" }
              },
              "node_modules/%s": {
                "version": "%s",
                "resolved": "%s%s/-/%s-%s.tgz",
                "integrity": "sha512-placeholder"
              }
            }
          }
          """.formatted(pkg, constraint, pkg, resolved,
          versioning.registry().url(), pkg, pkg, resolved);
    }
}

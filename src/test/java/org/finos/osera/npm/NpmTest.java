package org.finos.osera.npm;

import org.finos.osera.fixers.npm.EachNpmRemediatorAndManager;
import org.finos.osera.fixers.npm.NpmRemediator;
import org.finos.osera.fixers.npm.OpenRewriteNpm;
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
 * them. This covers a direct dependency; the declaration shapes and the other package managers
 * follow.
 *
 * <p>The manifest's resolution marker is supplied rather than produced by installing the project,
 * which is what {@code Assertions}' package-manager-free factories are for. That keeps these
 * scenarios the same shape as the Maven and Gradle ones — text in, text out, no build tool in a
 * subprocess. What npm's own resolver does with these versions is measured in
 * {@link NpmDeclaredRangeTest} and {@link NpmWrittenConstraintTest}, where a real install is the
 * measurement rather than scaffolding.
 */
public record NpmTest(PackageVersioning versioning) implements NpmBuild {

    private static final String PACKAGE = "acme-logger";

    /**
     * Proves the recipe can change this fixture at all.
     *
     * <p>Without it, every "no change" result in this class is worthless: a {@code package.json}
     * with no {@code NodeResolutionResult} marker makes the recipe return the tree untouched, and a
     * marker with no package manager makes lock regeneration return null. Both are silent, and both
     * would leave a cell looking like a tool that considered the backpatch and declined it.
     */
    @Test
    void control() {
        versioning.publishUpstream(PACKAGE, "1.4.0", "1.4.1", "1.5.0", "2.0.0");

        rewriteRun(
          spec -> spec.recipe(new UpgradeDependencyVersion(PACKAGE, null, "~1.5.0")),
          packageJson(manifest("~1.4.1"), manifest("~1.5.0"),
            nodeResolutionResult(Npm, dependency(PACKAGE, "~1.4.1"))),
          packageLock(lock("~1.4.1", "1.4.1"), null, spec -> spec.after(regenerated -> {
              if (regenerated == null || !regenerated.contains("\"version\": \"1.5.0\"")) {
                  throw new AssertionError("""
                    The control did not regenerate the lock, so no "unchanged" result in this \
                    class means anything.""");
              }
              return regenerated;
          })));
    }

    @EachNpmRemediatorAndManager
    void directDependency(NpmRemediator remediator, PackageManager pm) {
        versioning.publishUpstream(PACKAGE, "1.4.0", "1.4.1", "1.5.0", "2.0.0");
        String patched = versioning.publish(PACKAGE, "1.4.1");

        // The manifest only. Two of these columns drive a file updater that rewrites declarations
        // and never regenerates a lock, so scoring them on the lock would measure something this
        // harness does not ask them to do. Lock regeneration is OpenRewrite's, and the control
        // above is where it is measured.
        // OpenRewrite's two npm dependency recipes are composed together, as the JVM column
        // composes them in osera.yml, because a column cannot know in advance whether a consumer
        // declares the vulnerable package or only has it underneath something they declare. On
        // npm — unlike the JVM pair — the transitive recipe also writes an override for a
        // dependency that is already direct and already correct, so the expected output carries
        // that redundancy rather than hiding it by running the recipes separately.
        String after = remediator instanceof OpenRewriteNpm
          ? manifestWithRedundantOverride(pm, versioning.constraint(patched))
          : manifest(versioning.constraint(patched));

        rewriteRun(
          NpmRemediator.asSpec(remediator, versioning, PACKAGE),
          packageJson(manifest("~1.4.1"), after,
            nodeResolutionResult(pm, dependency(PACKAGE, "~1.4.1"))));
    }

    /**
     * What OpenRewrite leaves behind: the declaration moved, and an override it did not need,
     * under whichever key the package manager uses for one. Written out per manager rather than
     * composed, because a text block interpolated into another loses its relative indentation and
     * the whitespace is what is being compared.
     */
    private static String manifestWithRedundantOverride(PackageManager pm, String constraint) {
        return switch (pm) {
            case Npm -> """
              {
                "name": "consumer",
                "version": "1.0.0",
                "dependencies": {
                  "%s": "%s"
                },
                "overrides": {
                  "%s": "%s"
                }
              }""".formatted(PACKAGE, constraint, PACKAGE, constraint);
            case YarnBerry, YarnClassic -> """
              {
                "name": "consumer",
                "version": "1.0.0",
                "dependencies": {
                  "%s": "%s"
                },
                "resolutions": {
                  "%s": "%s"
                }
              }""".formatted(PACKAGE, constraint, PACKAGE, constraint);
            case Pnpm -> """
              {
                "name": "consumer",
                "version": "1.0.0",
                "dependencies": {
                  "%s": "%s"
                },
                "pnpm": {
                  "overrides": {
                    "%s": "%s"
                  }
                }
              }""".formatted(PACKAGE, constraint, PACKAGE, constraint);
            case Bun -> throw new IllegalArgumentException("Bun is not measured");
        };
    }

    /**
     * The backpatch is declared by a workspace member, not the root. The analogue of the JVM's
     * multiModule scenario: the declaration a tool has to reach is not in the file it starts from.
     */
    @EachNpmRemediatorAndManager
    void workspaceMember(NpmRemediator remediator, PackageManager pm) {
        versioning.publishUpstream(PACKAGE, "1.4.0", "1.4.1", "1.5.0", "2.0.0");
        String patched = versioning.publish(PACKAGE, "1.4.1");

        // OpenRewrite also pins the package from the root, which for a workspace is a reasonable
        // place for it: the root is where a pin applies to every member at once.
        String rootAfter = remediator instanceof OpenRewriteNpm
          ? workspaceRootWithOverride(pm, versioning.constraint(patched))
          : null;

        rewriteRun(
          NpmRemediator.asSpec(remediator, versioning, PACKAGE),
          packageJson(workspaceRoot(), rootAfter,
            nodeResolutionResult(pm, List.of("packages/member/package.json"))),
          dir("packages/member",
            packageJson(manifest("~1.4.1"),
              // OpenRewrite pins it here as well as in the root, so the same package ends up
              // constrained in three places: the member's declaration, the member's overrides and
              // the root's. Recorded rather than hidden.
              remediator instanceof OpenRewriteNpm
                ? manifestWithRedundantOverride(pm, versioning.constraint(patched))
                : manifest(versioning.constraint(patched)),
              nodeResolutionResult(pm, dependency(PACKAGE, "~1.4.1")))));
    }

    /**
     * The root once OpenRewrite has pinned the package for every member. Written out per manager
     * rather than composed: a text block interpolated into another loses its relative indentation,
     * and whitespace is part of what is being compared.
     */
    private static String workspaceRootWithOverride(PackageManager pm, String constraint) {
        return switch (pm) {
            case Npm -> """
              {
                "name": "consumer",
                "version": "1.0.0",
                "private": true,
                "workspaces": [ "packages/*" ],
                "overrides": {
                  "%s": "%s"
                }
              }""".formatted(PACKAGE, constraint);
            case Pnpm -> """
              {
                "name": "consumer",
                "version": "1.0.0",
                "private": true,
                "workspaces": [ "packages/*" ],
                "pnpm": {
                  "overrides": {
                    "%s": "%s"
                  }
                }
              }""".formatted(PACKAGE, constraint);
            case YarnBerry, YarnClassic -> """
              {
                "name": "consumer",
                "version": "1.0.0",
                "private": true,
                "workspaces": [ "packages/*" ],
                "resolutions": {
                  "%s": "%s"
                }
              }""".formatted(PACKAGE, constraint);
            case Bun -> throw new IllegalArgumentException("Bun is not measured");
        };
    }

    /** A root that declares nothing itself and delegates to its members. */
    private static String workspaceRoot() {
        return """
          {
            "name": "consumer",
            "version": "1.0.0",
            "private": true,
            "workspaces": [ "packages/*" ]
          }
          """;
    }

    private static String manifest(String constraint) {
        return """
          {
            "name": "consumer",
            "version": "1.0.0",
            "dependencies": {
              "%s": "%s"
            }
          }
          """.formatted(PACKAGE, constraint);
    }

    private String lock(String constraint, String resolved) {
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
          """.formatted(PACKAGE, constraint, PACKAGE, resolved,
          versioning.registry().url(), PACKAGE, PACKAGE, resolved);
    }
}

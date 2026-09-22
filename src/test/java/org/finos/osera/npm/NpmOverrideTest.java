package org.finos.osera.npm;

import org.finos.osera.providers.npm.PackageVersioning;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openrewrite.javascript.marker.NodeResolutionResult.PackageManager;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Whether the override a remediator writes actually reaches the consumer.
 *
 * <p>The remediation matrix measures what a tool writes. This measures whether what it wrote
 * works, which is a different question and the one that decides whether a transitive backpatch is
 * deliverable at all. Each manager spells an override differently and OpenRewrite already writes
 * the right key for each; what is unmeasured is whether the key then takes effect at install.
 */
public record NpmOverrideTest(PackageVersioning versioning) {

    private static final String WRAPPER = "acme-wrapper";

    @ParameterizedTest(name = "{0} honours {1} for a dependency it does not declare")
    @CsvSource({
      "Npm,       overrides",
      "Pnpm,      pnpm.overrides",
      "YarnBerry, resolutions"
    })
    void overrideReachesATransitive(PackageManager pm, String key) {
        assumeTrue(PackageManagers.isUsable(pm), pm + " is not installed at the measured major");

        String logger = versioning.freshPackage();
        versioning.publishUpstream(logger, "1.4.0", "1.4.1", "1.5.0", "2.0.0");
        versioning.registry().publish(WRAPPER, "3.0.0", Map.of(logger, "~1.4.1"));
        String patched = versioning.publish(logger, "1.4.1");

        assertThat(NpmProject.install(versioning.registry(), pm, logger,
          manifestWithOverride(pm, logger, versioning.constraint(patched))))
          .as("what %s installs for a transitive pinned through %s", pm, key)
          .isEqualTo(patched);
    }

    /**
     * Yarn spells an override as {@code resolutions}, and accepts more than one key form. Issue #8
     * of backpatch-alliance measured Yarn Classic mis-parsing the {@code pkg@range} form, so the
     * forms are measured separately rather than assumed equivalent — a standard that tells people
     * to write one of them needs to know which.
     */
    @ParameterizedTest(name = "Yarn Classic honours a resolutions key written as {0}")
    @CsvSource({
      "plain name,          NAME",
      "pkg@range,           NAME@~1.4.1",
      "wildcard path,       **/NAME"
    })
    void yarnClassicResolutionKeyForms(String form, String keyTemplate) {
        assumeTrue(PackageManagers.isUsable(PackageManager.YarnClassic), "Yarn Classic is not installed");

        String logger = versioning.freshPackage();
        versioning.publishUpstream(logger, "1.4.0", "1.4.1", "1.5.0", "2.0.0");
        versioning.registry().publish(WRAPPER, "3.0.0", Map.of(logger, "~1.4.1"));
        String patched = versioning.publish(logger, "1.4.1");

        String manifest = """
          {
            "name": "consumer",
            "version": "1.0.0",
            "dependencies": { "%s": "^3.0.0" },
            "resolutions": { "%s": "%s" }
          }
          """.formatted(WRAPPER, keyTemplate.replace("NAME", logger), versioning.constraint(patched));

        assertThat(NpmProject.install(versioning.registry(), PackageManager.YarnClassic, logger, manifest))
          .as("what Yarn Classic installs when the resolutions key is written as %s", form)
          .isEqualTo(patched);
    }

    private static String manifestWithOverride(PackageManager pm, String logger, String constraint) {
        String override = switch (pm) {
            case Npm -> "\"overrides\": { \"%s\": \"%s\" }".formatted(logger, constraint);
            case Pnpm -> "\"pnpm\": { \"overrides\": { \"%s\": \"%s\" } }".formatted(logger, constraint);
            case YarnBerry, YarnClassic -> "\"resolutions\": { \"%s\": \"%s\" }".formatted(logger, constraint);
            case Bun -> throw new IllegalArgumentException("Bun is not measured");
        };
        return """
          {
            "name": "consumer",
            "version": "1.0.0",
            "dependencies": { "%s": "^3.0.0" },
            %s
          }
          """.formatted(WRAPPER, override);
    }
}

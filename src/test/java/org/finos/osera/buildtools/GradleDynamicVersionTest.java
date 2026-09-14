package org.finos.osera.buildtools;

import org.finos.osera.providers.ProviderVersioning;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.maven.tree.GroupArtifactVersion;
import org.openrewrite.maven.tree.ResolvedDependency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;

public record GradleDynamicVersionTest(ProviderVersioning versioning) implements GradleBuild {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
      "2.14.+",
      "[2.14,2.15)",
      "[2.14.1,2.15)",
      "(2.14.1,2.15)"
    })
    void resolvesToPatch(String constraint) {
        String patched = versioning.publish(new GroupArtifactVersion("org.apache.logging.log4j", "log4j-core", "2.14.1"));
        rewriteRun(
          buildGradle(
            //language=groovy
            """
              plugins {
                  id 'java'
              }

              repositories {
                  mavenCentral()
              }

              dependencies {
                  implementation 'org.apache.logging.log4j:log4j-core:%s'
              }
              """.formatted(constraint),
            spec -> spec.afterRecipe(build -> assertThat(resolved(build, "log4j-core"))
              .as("what Gradle selects for %s", constraint)
              .isEqualTo(patched))
          ),
          gradleProperties()
        );
    }

    /**
     * The same question of a base version that already ends in a qualifier. A Gradle range has to
     * carry that qualifier in its own bounds to reach the line at all: {@code [4.1.68,4.1.69)}
     * resolves nothing against Maven Central, patch or no patch, where the identical shape over a
     * numeric line resolves fine. Maven has no such requirement.
     */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
      "4.1.68.+",
      "[4.1.68.Final,4.1.69.Final)",
      "(4.1.68.Final,4.1.69.Final)"
    })
    void resolvesToPatchOfQualifiedVersion(String constraint) {
        String patched = versioning.publish(new GroupArtifactVersion("io.netty", "netty-codec-http", "4.1.68.Final"));
        rewriteRun(
          buildGradle(
            //language=groovy
            """
              plugins {
                  id 'java'
              }

              repositories {
                  mavenCentral()
              }

              dependencies {
                  implementation 'io.netty:netty-codec-http:%s'
              }
              """.formatted(constraint),
            spec -> spec.afterRecipe(build -> assertThat(resolved(build, "netty-codec-http"))
              .as("what Gradle selects for %s", constraint)
              .isEqualTo(patched))
          ),
          gradleProperties()
        );
    }

    private static String resolved(G.CompilationUnit build, String artifactId) {
        return build.getMarkers().findFirst(GradleProject.class)
          .map(project -> project.getConfiguration("compileClasspath"))
          .stream()
          .flatMap(configuration -> configuration.getDirectResolved().stream())
          .filter(dependency -> artifactId.equals(dependency.getArtifactId()))
          .map(ResolvedDependency::getVersion)
          .findFirst()
          .orElse("nothing");
    }
}

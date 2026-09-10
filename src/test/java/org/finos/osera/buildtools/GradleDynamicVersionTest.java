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
            spec -> spec.afterRecipe(build -> assertThat(resolved(build))
              .as("what Gradle selects for %s", constraint)
              .isEqualTo(patched))
          ),
          gradleProperties()
        );
    }

    private static String resolved(G.CompilationUnit build) {
        return build.getMarkers().findFirst(GradleProject.class)
          .map(project -> project.getConfiguration("compileClasspath"))
          .stream()
          .flatMap(configuration -> configuration.getDirectResolved().stream())
          .filter(dependency -> "log4j-core".equals(dependency.getArtifactId()))
          .map(ResolvedDependency::getVersion)
          .findFirst()
          .orElse("nothing");
    }
}

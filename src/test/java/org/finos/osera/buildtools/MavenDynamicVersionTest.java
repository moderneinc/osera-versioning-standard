package org.finos.osera.buildtools;

import org.finos.osera.providers.ProviderVersioning;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.maven.tree.GroupArtifactVersion;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.ResolvedDependency;
import org.openrewrite.maven.tree.Scope;
import org.openrewrite.xml.tree.Xml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.maven.Assertions.pomXml;

public record MavenDynamicVersionTest(ProviderVersioning versioning) implements MavenBuild {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
      "[2.14,2.15)",
      "[2.14.1,2.15)",
      "(2.14.1,2.15)"
    })
    void resolvesToPatch(String constraint) {
        String patched = versioning.publish(new GroupArtifactVersion("org.apache.logging.log4j", "log4j-core", "2.14.1"));
        rewriteRun(
          pomXml(
            //language=xml
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.finos.osera</groupId>
                  <artifactId>osera-test</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.apache.logging.log4j</groupId>
                          <artifactId>log4j-core</artifactId>
                          <version>%s</version>
                      </dependency>
                  </dependencies>
              </project>
              """.formatted(constraint),
            spec -> spec.afterRecipe(pom -> assertThat(resolved(pom))
              .as("what Maven selects for %s", constraint)
              .isEqualTo(patched))
          )
        );
    }

    private static String resolved(Xml.Document pom) {
        return pom.getMarkers().findFirst(MavenResolutionResult.class).stream()
          .flatMap(result -> result.findDependencies("org.apache.logging.log4j", "log4j-core", Scope.Compile).stream())
          .map(ResolvedDependency::getVersion)
          .findFirst()
          .orElse("nothing");
    }
}

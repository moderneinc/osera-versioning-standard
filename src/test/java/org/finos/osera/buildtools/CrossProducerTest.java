package org.finos.osera.buildtools;

import org.finos.osera.fixers.EachRemediator;
import org.finos.osera.fixers.Remediator;
import org.finos.osera.providers.ProviderVersioning;
import org.junit.jupiter.api.Test;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.maven.tree.GroupArtifactVersion;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.ResolvedDependency;
import org.openrewrite.maven.tree.Scope;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.xml.tree.Xml;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.maven.Assertions.pomXml;

/**
 * More than one producer patching the same release, each of them producing patches the way the
 * scheme says to. The issue asks how to keep "provider-specific patch names or numbers from
 * distorting recipient prioritization", and a version string is the only thing a resolver has to go
 * on. Nothing in it says which CVEs a patch closes, so the highest-sorting patch is not necessarily
 * the most remediated one.
 */
public record CrossProducerTest(ProviderVersioning versioning) implements MavenBuild, GradleBuild {

    private static final GroupArtifactVersion HTTPCLIENT =
      new GroupArtifactVersion("org.apache.httpcomponents", "httpclient", "4.5.13");

    @Override
    public void defaults(RecipeSpec spec) {
        MavenBuild.super.defaults(spec);
        GradleBuild.super.defaults(spec);
    }

    /**
     * Nobody named a producer, so the resolver picks on the strength of the version string alone.
     * Green says the busiest producer did not win it: Acme has shipped three patches to Zenith's
     * one, and the answer is still Zenith's, on nothing more than {@code z} sorting after {@code a}.
     * Red naming an Acme version would say the count decided, which is what a scheme that climbs a
     * numeric segment per patch does.
     */
    @Test
    void mavenDynamicVersion() {
        String highest = producers();
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
                          <groupId>org.apache.httpcomponents</groupId>
                          <artifactId>httpclient</artifactId>
                          <version>[4.5.13,4.5.14)</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            spec -> spec.afterRecipe(pom -> assertThat(resolved(pom))
              .as("which producer Maven takes when the consumer named none")
              .isEqualTo(highest))
          )
        );
    }

    /**
     * As {@link #mavenDynamicVersion()}, for the build tool that orders patches differently.
     */
    @Test
    void gradleDynamicVersion() {
        String highest = producers();
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
                  implementation 'org.apache.httpcomponents:httpclient:[4.5.13,4.5.14)'
              }
              """,
            spec -> spec.afterRecipe(build -> assertThat(resolved(build))
              .as("which producer Gradle takes when the consumer named none")
              .isEqualTo(highest))
          ),
          gradleProperties()
        );
    }

    /**
     * Nothing in this build names a producer. The remediation does: every remediator here is
     * configured with OSERA's version pattern, {@code -osera-NNNNN}, and that configuration is the
     * deliberate act a consumer has to perform — in the recipe they run or in the repositories they
     * point at. So it has to land on OSERA's newest even though Zenith's sorts above it, which is
     * the only path in the TCK by which a consumer ends up on the producer they picked rather than
     * on whoever sorts highest.
     */
    @EachRemediator
    void mavenNamedProducer(Remediator remediator) {
        producers();
        rewriteRun(
          Remediator.asSpec(remediator, versioning),
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
                          <groupId>org.apache.httpcomponents</groupId>
                          <artifactId>httpclient</artifactId>
                          <version>4.5.13</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            //language=xml
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.finos.osera</groupId>
                  <artifactId>osera-test</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.apache.httpcomponents</groupId>
                          <artifactId>httpclient</artifactId>
                          <version>PATCH_ME</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            versioning.patch(HTTPCLIENT, 2)
          )
        );
    }

    /**
     * As {@link #mavenNamedProducer(Remediator)}, in a Gradle build.
     */
    @EachRemediator
    void gradleNamedProducer(Remediator remediator) {
        producers();
        rewriteRun(
          Remediator.asSpec(remediator, versioning),
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
                  implementation 'org.apache.httpcomponents:httpclient:4.5.13'
              }
              """,
            //language=groovy
            """
              plugins {
                  id 'java'
              }
              
              repositories {
                  mavenCentral()
              }
              
              dependencies {
                  implementation 'org.apache.httpcomponents:httpclient:PATCH_ME'
              }
              """,
            versioning.patch(HTTPCLIENT, 2)
          ),
          gradleProperties()
        );
    }

    /**
     * Three producers against one release, in the order a resolver has no way to care about: Acme
     * has shipped the most and sorts first, Zenith the fewest and sorts last, and OSERA falls
     * between them on both counts. The tokens stay lowercase because that is how they appear in a
     * version, and Gradle compares the raw part when no special meaning matches.
     *
     * @return Zenith's only patch, which is the highest-sorting of the six.
     */
    private String producers() {
        versioning.publish(HTTPCLIENT, "acme", 1);
        versioning.publish(HTTPCLIENT, "acme", 2);
        versioning.publish(HTTPCLIENT, "acme", 3);
        versioning.publish(HTTPCLIENT, 1);
        versioning.publish(HTTPCLIENT, 2);
        return versioning.publish(HTTPCLIENT, "zenith", 1);
    }

    private static String resolved(Xml.Document pom) {
        return pom.getMarkers().findFirst(MavenResolutionResult.class).stream()
          .flatMap(result -> result.findDependencies(requireNonNull(HTTPCLIENT.getGroupId()),
            HTTPCLIENT.getArtifactId(), Scope.Compile).stream())
          .map(ResolvedDependency::getVersion)
          .findFirst()
          .orElse("nothing");
    }

    private static String resolved(G.CompilationUnit build) {
        return build.getMarkers().findFirst(GradleProject.class)
          .map(project -> project.getConfiguration("compileClasspath"))
          .stream()
          .flatMap(configuration -> configuration.getDirectResolved().stream())
          .filter(dependency -> HTTPCLIENT.getArtifactId().equals(dependency.getArtifactId()))
          .map(ResolvedDependency::getVersion)
          .findFirst()
          .orElse("nothing");
    }
}

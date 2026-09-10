package org.finos.osera.buildtools;

import org.finos.osera.fixers.EachRemediator;
import org.finos.osera.fixers.Remediator;
import org.finos.osera.providers.ProviderVersioning;
import org.openrewrite.maven.tree.GroupArtifactVersion;
import org.openrewrite.toml.tree.Toml;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.test.SourceSpecs.dir;
import static org.openrewrite.toml.Assertions.toml;

/**
 * The ways a Gradle build can arrive at a dependency's version, each of which a remediation tool has
 * to recognize before it can move that dependency to a producer's patch.
 */
public record GradleTest(ProviderVersioning versioning) implements GradleBuild {

    @EachRemediator
    void direct(Remediator remediator) {
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
                  implementation 'org.apache.logging.log4j:log4j-core:2.14.1'
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
                  implementation 'org.apache.logging.log4j:log4j-core:PATCH_ME'
              }
              """,
            versioning.patch(new GroupArtifactVersion("org.apache.logging.log4j", "log4j-core", "2.14.1"))
          ),
          gradleProperties()
        );
    }

    @EachRemediator
    void transitive(Remediator remediator) {
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
                  implementation 'org.springframework.boot:spring-boot-starter-log4j2:2.5.7'
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
                  constraints {
                      implementation('org.apache.logging.log4j:log4j-core:PATCH_ME')
                  }

                  implementation 'org.springframework.boot:spring-boot-starter-log4j2:2.5.7'
              }
              """,
            versioning.patch(new GroupArtifactVersion("org.apache.logging.log4j", "log4j-core", "2.14.1"))
          ),
          gradleProperties()
        );
    }

    /** The version is a local variable the dependency notation interpolates. */
    @EachRemediator
    void versionInVariable(Remediator remediator) {
        rewriteRun(
          Remediator.asSpec(remediator, versioning),
          buildGradle(
            //language=groovy
            """
              plugins {
                  id 'java'
              }

              def log4jVersion = '2.14.1'

              repositories {
                  mavenCentral()
              }

              dependencies {
                  implementation "org.apache.logging.log4j:log4j-core:$log4jVersion"
              }
              """,
            //language=groovy
            """
              plugins {
                  id 'java'
              }

              def log4jVersion = 'PATCH_ME'

              repositories {
                  mavenCentral()
              }

              dependencies {
                  implementation "org.apache.logging.log4j:log4j-core:$log4jVersion"
              }
              """,
            versioning.patch(new GroupArtifactVersion("org.apache.logging.log4j", "log4j-core", "2.14.1"))
          ),
          gradleProperties()
        );
    }

    /** The version lives outside the build script entirely, in {@code gradle.properties}. */
    @EachRemediator
    void versionInGradleProperties(Remediator remediator) {
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
                  implementation "org.apache.logging.log4j:log4j-core:$log4jVersion"
              }
              """
          ),
          gradleProperties(
            //language=properties
            """
              log4jVersion=2.14.1
              """,
            //language=properties
            """
              log4jVersion=PATCH_ME
              """,
            versioning.patch(new GroupArtifactVersion("org.apache.logging.log4j", "log4j-core", "2.14.1")))
        );
    }

    /** The coordinate is spelled out as a map rather than as a colon-separated string. */
    @EachRemediator
    void mapNotation(Remediator remediator) {
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
                  implementation group: 'org.apache.logging.log4j', name: 'log4j-core', version: '2.14.1'
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
                  implementation group: 'org.apache.logging.log4j', name: 'log4j-core', version: 'PATCH_ME'
              }
              """,
            versioning.patch(new GroupArtifactVersion("org.apache.logging.log4j", "log4j-core", "2.14.1"))
          ),
          gradleProperties()
        );
    }

    /** Gradle's own BOM support holds the version, and the BOM is not the producer's to patch. */
    @EachRemediator
    void platform(Remediator remediator) {
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
                  implementation platform('org.springframework.boot:spring-boot-dependencies:2.5.7')
                  implementation 'org.yaml:snakeyaml'
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
                  implementation platform('org.springframework.boot:spring-boot-dependencies:2.5.7')
                  implementation 'org.yaml:snakeyaml:PATCH_ME'
              }
              """,
            versioning.patch(new GroupArtifactVersion("org.yaml", "snakeyaml", "1.28"))
          ),
          gradleProperties()
        );
    }

    /** A version catalog moves every version into a file the build script only references. */
    @EachRemediator
    void versionCatalog(Remediator remediator) {
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
                  implementation libs.log4j.core
              }
              """
          ),
          dir("gradle",
            toml(
              //language=toml
              """
                [libraries]
                log4j-core = { module = "org.apache.logging.log4j:log4j-core", version = "2.14.1" }
                """,
              //language=toml
              """
                [libraries]
                log4j-core = { module = "org.apache.logging.log4j:log4j-core", version = "PATCH_ME" }
                """,
              spec -> {
                  spec.path("libs.versions.toml");
                  versioning.<Toml.Document>patch(new GroupArtifactVersion("org.apache.logging.log4j", "log4j-core", "2.14.1")).accept(spec);
              })
          ),
          gradleProperties()
        );
    }

    /** The Kotlin DSL is a different grammar reaching the same dependency. */
    @EachRemediator
    void kotlinDsl(Remediator remediator) {
        rewriteRun(
          Remediator.asSpec(remediator, versioning),
          buildGradleKts(
            //language=kotlin
            """
              plugins {
                  java
              }

              repositories {
                  mavenCentral()
              }

              dependencies {
                  implementation("org.apache.logging.log4j:log4j-core:2.14.1")
              }
              """,
            //language=kotlin
            """
              plugins {
                  java
              }

              repositories {
                  mavenCentral()
              }

              dependencies {
                  implementation("org.apache.logging.log4j:log4j-core:PATCH_ME")
              }
              """,
            versioning.patch(new GroupArtifactVersion("org.apache.logging.log4j", "log4j-core", "2.14.1"))
          ),
          gradleProperties()
        );
    }

    /**
     * Spring's dependency-management plugin manages snakeyaml's version through the Boot BOM's
     * {@code snakeyaml.version} property, so the idiomatic fix is to override that property rather
     * than to pin a coordinate. Boot 2.5.7's own plugin does not run on the Gradle driving these
     * tests, so the BOM is imported directly.
     */
    @EachRemediator
    void springDependencyManagement(Remediator remediator) {
        rewriteRun(
          Remediator.asSpec(remediator, versioning),
          buildGradle(
            //language=groovy
            """
              plugins {
                  id 'java'
                  id 'io.spring.dependency-management' version '1.1.7'
              }

              repositories {
                  mavenCentral()
              }

              dependencyManagement {
                  imports {
                      mavenBom 'org.springframework.boot:spring-boot-dependencies:2.5.7'
                  }
              }

              dependencies {
                  implementation 'org.yaml:snakeyaml'
              }
              """,
            //language=groovy
            """
              plugins {
                  id 'java'
                  id 'io.spring.dependency-management' version '1.1.7'
              }

              ext['snakeyaml.version'] = 'PATCH_ME'

              repositories {
                  mavenCentral()
              }

              dependencyManagement {
                  imports {
                      mavenBom 'org.springframework.boot:spring-boot-dependencies:2.5.7'
                  }
              }

              dependencies {
                  implementation 'org.yaml:snakeyaml'
              }
              """,
            versioning.patch(new GroupArtifactVersion("org.yaml", "snakeyaml", "1.28"))
          ),
          gradleProperties()
        );
    }
}

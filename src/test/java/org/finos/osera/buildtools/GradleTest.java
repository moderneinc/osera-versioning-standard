package org.finos.osera.buildtools;

import org.finos.osera.fixers.EachRemediator;
import org.finos.osera.fixers.Remediator;
import org.finos.osera.providers.ProviderVersioning;
import org.openrewrite.maven.tree.GroupArtifactVersion;
import org.openrewrite.toml.tree.Toml;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.gradle.Assertions.settingsGradle;
import static org.openrewrite.gradle.Assertions.settingsGradleKts;
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

    /** The coordinate is declared as a map rather than as a colon-separated string. */
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

    /** The same catalog entry written as a single coordinate string rather than as a table. */
    @EachRemediator
    void versionCatalogStringNotation(Remediator remediator) {
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
                log4j-core = "org.apache.logging.log4j:log4j-core:2.14.1"
                """,
              //language=toml
              """
                [libraries]
                log4j-core = "org.apache.logging.log4j:log4j-core:PATCH_ME"
                """,
              spec -> {
                  spec.path("libs.versions.toml");
                  versioning.<Toml.Document>patch(new GroupArtifactVersion("org.apache.logging.log4j", "log4j-core", "2.14.1")).accept(spec);
              })
          ),
          gradleProperties()
        );
    }

    /** The coordinate split across {@code group} and {@code name} instead of a single {@code module}. */
    @EachRemediator
    void versionCatalogGroupAndName(Remediator remediator) {
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
                log4j-core = { group = "org.apache.logging.log4j", name = "log4j-core", version = "2.14.1" }
                """,
              //language=toml
              """
                [libraries]
                log4j-core = { group = "org.apache.logging.log4j", name = "log4j-core", version = "PATCH_ME" }
                """,
              spec -> {
                  spec.path("libs.versions.toml");
                  versioning.<Toml.Document>patch(new GroupArtifactVersion("org.apache.logging.log4j", "log4j-core", "2.14.1")).accept(spec);
              })
          ),
          gradleProperties()
        );
    }

    /**
     * The library's version is a named declaration it points at, and the declaration serves nothing
     * else, so the patch belongs in {@code [versions]}.
     */
    @EachRemediator
    void versionCatalogVersionRef(Remediator remediator) {
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
                [versions]
                log4j = "2.14.1"

                [libraries]
                log4j-core = { module = "org.apache.logging.log4j:log4j-core", version.ref = "log4j" }
                """,
              //language=toml
              """
                [versions]
                log4j = "PATCH_ME"

                [libraries]
                log4j-core = { module = "org.apache.logging.log4j:log4j-core", version.ref = "log4j" }
                """,
              spec -> {
                  spec.path("libs.versions.toml");
                  versioning.<Toml.Document>patch(new GroupArtifactVersion("org.apache.logging.log4j", "log4j-core", "2.14.1")).accept(spec);
              })
          ),
          gradleProperties()
        );
    }

    /**
     * Two libraries share one version declaration and only one of them has a patch, so moving the
     * declaration would move {@code log4j-api} to a version nobody published. The patched library
     * has to take a version of its own instead.
     */
    @EachRemediator
    void versionCatalogSharedVersionRef(Remediator remediator) {
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
                  implementation libs.log4j.api
                  implementation libs.log4j.core
              }
              """
          ),
          dir("gradle",
            toml(
              //language=toml
              """
                [versions]
                log4j = "2.14.1"

                [libraries]
                log4j-api = { module = "org.apache.logging.log4j:log4j-api", version.ref = "log4j" }
                log4j-core = { module = "org.apache.logging.log4j:log4j-core", version.ref = "log4j" }
                """,
              //language=toml
              """
                [versions]
                log4j = "2.14.1"

                [libraries]
                log4j-api = { module = "org.apache.logging.log4j:log4j-api", version.ref = "log4j" }
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

    /** A rich version: the entry states a preference rather than a bare version string. */
    @EachRemediator
    void versionCatalogRequiredVersion(Remediator remediator) {
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
                log4j-core = { module = "org.apache.logging.log4j:log4j-core", version = { require = "2.14.1" } }
                """,
              //language=toml
              """
                [libraries]
                log4j-core = { module = "org.apache.logging.log4j:log4j-core", version = { require = "PATCH_ME" } }
                """,
              spec -> {
                  spec.path("libs.versions.toml");
                  versioning.<Toml.Document>patch(new GroupArtifactVersion("org.apache.logging.log4j", "log4j-core", "2.14.1")).accept(spec);
              })
          ),
          gradleProperties()
        );
    }

    /**
     * A strict version, which is the one form where leaving the catalog alone fails the build
     * outright: nothing else may raise the version, so the patch has to land here.
     */
    @EachRemediator
    void versionCatalogStrictVersion(Remediator remediator) {
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
                log4j-core = { module = "org.apache.logging.log4j:log4j-core", version = { strictly = "2.14.1" } }
                """,
              //language=toml
              """
                [libraries]
                log4j-core = { module = "org.apache.logging.log4j:log4j-core", version = { strictly = "PATCH_ME" } }
                """,
              spec -> {
                  spec.path("libs.versions.toml");
                  versioning.<Toml.Document>patch(new GroupArtifactVersion("org.apache.logging.log4j", "log4j-core", "2.14.1")).accept(spec);
              })
          ),
          gradleProperties()
        );
    }

    /**
     * {@code gradle/libs.versions.toml} is a convention, not a rule. A catalog registered from
     * another file is the same catalog, and a tool that matches on the conventional path misses it.
     */
    @EachRemediator
    void versionCatalogCustomFile(Remediator remediator) {
        rewriteRun(
          Remediator.asSpec(remediator, versioning),
          settingsGradle(
            //language=groovy
            """
              dependencyResolutionManagement {
                  versionCatalogs {
                      libs {
                          from(files('gradle/deps.versions.toml'))
                      }
                  }
              }
              """
          ),
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
                  spec.path("deps.versions.toml");
                  versioning.<Toml.Document>patch(new GroupArtifactVersion("org.apache.logging.log4j", "log4j-core", "2.14.1")).accept(spec);
              })
          ),
          gradleProperties()
        );
    }

    /** The build asks for a bundle, so the entry to patch is named nowhere in the build script. */
    @EachRemediator
    void versionCatalogBundle(Remediator remediator) {
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
                  implementation libs.bundles.logging
              }
              """
          ),
          dir("gradle",
            toml(
              //language=toml
              """
                [libraries]
                log4j-api = { module = "org.apache.logging.log4j:log4j-api", version = "2.14.1" }
                log4j-core = { module = "org.apache.logging.log4j:log4j-core", version = "2.14.1" }

                [bundles]
                logging = ["log4j-api", "log4j-core"]
                """,
              //language=toml
              """
                [libraries]
                log4j-api = { module = "org.apache.logging.log4j:log4j-api", version = "2.14.1" }
                log4j-core = { module = "org.apache.logging.log4j:log4j-core", version = "PATCH_ME" }

                [bundles]
                logging = ["log4j-api", "log4j-core"]
                """,
              spec -> {
                  spec.path("libs.versions.toml");
                  versioning.<Toml.Document>patch(new GroupArtifactVersion("org.apache.logging.log4j", "log4j-core", "2.14.1")).accept(spec);
              })
          ),
          gradleProperties()
        );
    }

    /**
     * A catalog entry consumed as a platform. The entry is an ordinary library declaration and the
     * patch is an ordinary one; only the call around it in the build script differs. The BOM is
     * one no other scenario declares, since the mock repository is shared for the whole run.
     */
    @EachRemediator
    void versionCatalogPlatform(Remediator remediator) {
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
                  implementation platform(libs.jackson.bom)
              }
              """
          ),
          dir("gradle",
            toml(
              //language=toml
              """
                [libraries]
                jackson-bom = { module = "com.fasterxml.jackson:jackson-bom", version = "2.13.4" }
                """,
              //language=toml
              """
                [libraries]
                jackson-bom = { module = "com.fasterxml.jackson:jackson-bom", version = "PATCH_ME" }
                """,
              spec -> {
                  spec.path("libs.versions.toml");
                  versioning.<Toml.Document>patch(new GroupArtifactVersion("com.fasterxml.jackson", "jackson-bom", "2.13.4")).accept(spec);
              })
          ),
          gradleProperties()
        );
    }

    /** The same catalog declared in the settings DSL, where there is no TOML file to read. */
    @EachRemediator
    void settingsVersionCatalog(Remediator remediator) {
        rewriteRun(
          Remediator.asSpec(remediator, versioning),
          settingsGradle(
            //language=groovy
            """
              dependencyResolutionManagement {
                  versionCatalogs {
                      libs {
                          library('log4j-core', 'org.apache.logging.log4j', 'log4j-core').version('2.14.1')
                      }
                  }
              }
              """,
            //language=groovy
            """
              dependencyResolutionManagement {
                  versionCatalogs {
                      libs {
                          library('log4j-core', 'org.apache.logging.log4j', 'log4j-core').version('PATCH_ME')
                      }
                  }
              }
              """,
            versioning.patch(new GroupArtifactVersion("org.apache.logging.log4j", "log4j-core", "2.14.1"))
          ),
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
          gradleProperties()
        );
    }

    /** The settings DSL's own indirection, a named version the library refers to. */
    @EachRemediator
    void settingsVersionCatalogVersionRef(Remediator remediator) {
        rewriteRun(
          Remediator.asSpec(remediator, versioning),
          settingsGradle(
            //language=groovy
            """
              dependencyResolutionManagement {
                  versionCatalogs {
                      libs {
                          version('log4j', '2.14.1')
                          library('log4j-core', 'org.apache.logging.log4j', 'log4j-core').versionRef('log4j')
                      }
                  }
              }
              """,
            //language=groovy
            """
              dependencyResolutionManagement {
                  versionCatalogs {
                      libs {
                          version('log4j', 'PATCH_ME')
                          library('log4j-core', 'org.apache.logging.log4j', 'log4j-core').versionRef('log4j')
                      }
                  }
              }
              """,
            versioning.patch(new GroupArtifactVersion("org.apache.logging.log4j", "log4j-core", "2.14.1"))
          ),
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
          gradleProperties()
        );
    }

    /** The settings DSL in Kotlin, where the catalog is created by name rather than by convention. */
    @EachRemediator
    void settingsVersionCatalogKotlinDsl(Remediator remediator) {
        rewriteRun(
          Remediator.asSpec(remediator, versioning),
          settingsGradleKts(
            //language=kotlin
            """
              dependencyResolutionManagement {
                  versionCatalogs {
                      create("libs") {
                          library("log4j-core", "org.apache.logging.log4j", "log4j-core").version("2.14.1")
                      }
                  }
              }
              """,
            //language=kotlin
            """
              dependencyResolutionManagement {
                  versionCatalogs {
                      create("libs") {
                          library("log4j-core", "org.apache.logging.log4j", "log4j-core").version("PATCH_ME")
                      }
                  }
              }
              """,
            versioning.patch(new GroupArtifactVersion("org.apache.logging.log4j", "log4j-core", "2.14.1"))
          ),
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
                  implementation(libs.log4j.core)
              }
              """
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

    /**
     * Spring's OSGi-era versions end in {@code .RELEASE}, as Hibernate's and Netty's end in
     * {@code .Final}. CARE cannot append a numeric branch segment to one of those, so its alternate
     * format applies, and every tool downstream reads a version carrying two qualifiers.
     */
    @EachRemediator
    void qualifiedVersion(Remediator remediator) {
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
                  implementation 'org.springframework:spring-beans:5.2.19.RELEASE'
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
                  implementation 'org.springframework:spring-beans:PATCH_ME'
              }
              """,
            versioning.patch(new GroupArtifactVersion("org.springframework", "spring-beans", "5.2.19.RELEASE"))
          ),
          gradleProperties()
        );
    }

    /**
     * A second CVE against the same release, so the producer publishes twice against it. Which of
     * the two is newest is decided by the producer's sequence rather than by anything the base
     * version says, and the remediation has to land on the second.
     *
     * <p>Jackson keeps a fourth numeric segment for its own security releases — Central holds
     * {@code 2.12.6.1}, {@code 2.12.7.1}, {@code 2.13.2.1} and more — so a backpatch of
     * {@code 2.12.6} lands on the same numeric point the maintainer uses for the same purpose.
     * Dependabot, which is told a ceiling rather than a producer, takes the maintainer's
     * {@code 2.12.6.1} here rather than making no change as it does everywhere else.
     */
    @EachRemediator
    void secondPatch(Remediator remediator) {
        versioning.publish(new GroupArtifactVersion("com.fasterxml.jackson.core", "jackson-databind", "2.12.6"));
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
                  implementation 'com.fasterxml.jackson.core:jackson-databind:2.12.6'
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
                  implementation 'com.fasterxml.jackson.core:jackson-databind:PATCH_ME'
              }
              """,
            versioning.patch(new GroupArtifactVersion("com.fasterxml.jackson.core", "jackson-databind", "2.12.6"), 2)
          ),
          gradleProperties()
        );
    }
}

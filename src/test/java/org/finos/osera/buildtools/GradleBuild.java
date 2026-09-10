package org.finos.osera.buildtools;

import org.finos.osera.MockMavenRepository;
import org.finos.osera.providers.ProviderVersioning;
import org.openrewrite.gradle.util.GradleWrapper;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpec;
import org.openrewrite.test.SourceSpecs;

import java.util.function.Consumer;

import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.properties.Assertions.properties;

/**
 * Runs the real Gradle tooling model over each build under test, with the scheme's mock repository
 * added by an init script. The mock's URL is only known at runtime, so it never appears in the
 * build text a test declares.
 */
interface GradleBuild extends RewriteTest {

    ProviderVersioning versioning();

    @Override
    default void defaults(RecipeSpec spec) {
        //language=groovy
        String initScript = """
          initscript {
              repositories {
                  mavenCentral()
              }
              dependencies {
                  classpath 'org.openrewrite.gradle.tooling:plugin:latest.release'
              }
          }

          allprojects {
              apply plugin: org.openrewrite.gradle.toolingapi.ToolingApiOpenRewriteModelPlugin
              repositories {
                  maven { url = uri('%s') }
              }
          }
          """.formatted(versioning().repository().url());
        spec.beforeRecipe(withToolingApi((GradleWrapper) null, initScript));
    }

    /**
     * The Gradle daemon is its own JVM, so it is told here to trust the mock repository's certificate.
     */
    default SourceSpecs gradleProperties() {
        return properties(daemon(), spec -> spec.path("gradle.properties"));
    }

    /**
     * As {@link #gradleProperties()}, for a build that keeps a dependency's version in the same file.
     */
    default SourceSpecs gradleProperties(String before, String after, Consumer<SourceSpec<Properties.File>> patch) {
        return properties(daemon() + before, daemon() + after, spec -> {
            spec.path("gradle.properties");
            patch.accept(spec);
        });
    }

    private String daemon() {
        //language=properties
        return """
          org.gradle.jvmargs=-Djavax.net.ssl.trustStore=%s -Djavax.net.ssl.trustStorePassword=%s
          org.gradle.daemon.idletimeout=60000
          """.formatted(versioning().repository().trustStore(), MockMavenRepository.TRUST_STORE_PASSWORD);
    }
}

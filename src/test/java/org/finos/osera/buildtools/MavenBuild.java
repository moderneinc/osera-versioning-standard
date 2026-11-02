package org.finos.osera.buildtools;

import org.finos.osera.providers.ProviderVersioning;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Parser;
import org.openrewrite.maven.MavenExecutionContextView;
import org.openrewrite.maven.MavenSettings;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpec;

/**
 * Points Maven's resolution at the scheme's mock repository. The mock's URL is only known at
 * runtime, so it arrives through settings rather than through a {@code <repositories>} block every
 * pom under test would have to interpolate.
 */
interface MavenBuild extends RewriteTest {

    ProviderVersioning versioning();

    @Override
    default void defaults(RecipeSpec spec) {
        ExecutionContext ctx = defaultExecutionContext(new SourceSpec[0]);
        spec.executionContext(MavenExecutionContextView.view(ctx).setMavenSettings(
          //language=xml
          MavenSettings.parse(Parser.Input.fromString(String.format("""
            <settings>
                <profiles>
                    <profile>
                        <id>osera</id>
                        <repositories>
                            <repository>
                                <id>osera</id>
                                <url>%s</url>
                            </repository>
                        </repositories>
                    </profile>
                </profiles>
                <activeProfiles>
                    <activeProfile>osera</activeProfile>
                </activeProfiles>
            </settings>
            """, versioning().repository().url())), ctx)
        ));
    }
}

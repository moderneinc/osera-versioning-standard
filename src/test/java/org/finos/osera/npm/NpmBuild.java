package org.finos.osera.npm;

import org.finos.osera.providers.npm.PackageVersioning;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.javascript.NodeExecutionContextView;
import org.openrewrite.javascript.NodeRegistry;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.List;

/** Points OpenRewrite's npm resolution at the scheme's registry, as {@code MavenBuild} does with settings. */
public interface NpmBuild extends RewriteTest {

    PackageVersioning versioning();

    @Override
    default void defaults(RecipeSpec spec) {
        spec.executionContext(npmContext());
    }

    default ExecutionContext npmContext() {
        // The default handler swallows what a recipe throws, so a crash would read as a refusal.
        ExecutionContext ctx = new InMemoryExecutionContext(t -> {
            throw t instanceof RuntimeException runtime ? runtime : new IllegalStateException(t);
        });
        NodeExecutionContextView.view(ctx).setRegistries(List.of(new NodeRegistry(
          null, versioning().registry().url(),
          null, null, null, null,
          false,
          null,
          // strictSsl false takes the custom-CA path even for http, and fails there.
          true,
          false)));
        return ctx;
    }
}

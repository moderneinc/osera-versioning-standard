package org.finos.osera.npm;

import org.finos.osera.providers.npm.PackageVersioning;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.javascript.NodeExecutionContextView;
import org.openrewrite.javascript.NodeRegistry;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.List;

/**
 * Points OpenRewrite's npm resolution at the scheme's registry, the counterpart of what
 * {@code MavenBuild} does with Maven settings. Lock regeneration runs in the JVM rather than by
 * shelling out, so it reads these registries; the package manager itself reads the npmrc the build
 * points it at.
 */
public interface NpmBuild extends RewriteTest {

    PackageVersioning versioning();

    @Override
    default void defaults(RecipeSpec spec) {
        spec.executionContext(npmContext());
    }

    default ExecutionContext npmContext() {
        ExecutionContext ctx = new InMemoryExecutionContext();
        NodeExecutionContextView.view(ctx).setRegistries(List.of(new NodeRegistry(
          null, versioning().registry().url(),
          null, null, null, null,
          false,
          null,
          // strictSsl false sends the engine down the custom-CA path even for a plain http URL,
          // where it fails with REGISTRY_UNREACHABLE and no CA is involved at all.
          true,
          false)));
        return ctx;
    }
}

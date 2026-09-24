package org.finos.osera.npm;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

class TarballTest {

    @Test
    void holdsThePackageJsonNpmExpects() throws Exception {
        byte[] tgz = Tarball.of("acme-logger", "1.4.1", Map.of());

        try (TarArchiveInputStream tar = new TarArchiveInputStream(
          new GZIPInputStream(new ByteArrayInputStream(tgz)))) {
            var entry = tar.getNextEntry();
            assertThat(entry.getName()).isEqualTo("package/package.json");
            assertThat(new String(tar.readAllBytes(), UTF_8))
              .contains("\"name\": \"acme-logger\"")
              .contains("\"version\": \"1.4.1\"");
            assertThat(tar.getNextEntry()).isNull();
        }
    }

    @Test
    void carriesWhatThePackageDependsOn() throws Exception {
        byte[] tgz = Tarball.of("acme-wrapper", "3.0.0", Map.of("acme-logger", "~1.4.1"));

        try (TarArchiveInputStream tar = new TarArchiveInputStream(
          new GZIPInputStream(new ByteArrayInputStream(tgz)))) {
            tar.getNextEntry();
            assertThat(new String(tar.readAllBytes(), UTF_8))
              .as("a resolver re-resolving the graph has to see this, or it orphans the dependency")
              .contains("\"dependencies\": { \"acme-logger\": \"~1.4.1\" }");
        }
    }
}

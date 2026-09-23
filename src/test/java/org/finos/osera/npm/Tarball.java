package org.finos.osera.npm;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;

/**
 * A publishable npm tarball. Nothing in this suite runs the code inside a package, so a version's
 * artifact is its manifest and nothing else; what is being measured is which version a resolver
 * picks, not what that version does.
 */
final class Tarball {

    private Tarball() {
    }

    static byte[] of(String name, String version, Map<String, String> dependencies) {
        //language=JSON
        byte[] content = """
          {
            "name": "%s",
            "version": "%s",
            "dependencies": %s
          }
          """.formatted(name, version, json(dependencies)).getBytes(UTF_8);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(new GZIPOutputStream(bytes))) {
            TarArchiveEntry entry = new TarArchiveEntry("package/package.json");
            entry.setSize(content.length);
            tar.putArchiveEntry(entry);
            tar.write(content);
            tar.closeArchiveEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    static String json(Map<String, String> dependencies) {
        return dependencies.entrySet().stream()
          .map(entry -> "\"%s\": \"%s\"".formatted(entry.getKey(), entry.getValue()))
          .collect(joining(", ", "{ ", " }"));
    }
}

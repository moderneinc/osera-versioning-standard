package org.finos.osera;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The build discovers where Node, Ruby and the tools they run are installed, and hands each one to
 * the tests as a system property. Nothing links the two halves, so a property can be read and never
 * set — which is not a failure, it is a default silently taking over. {@code osera.npm} was read
 * for a week while the scenarios shelled out to whatever bare {@code npm} the daemon's PATH
 * happened to offer.
 *
 * <p>This asserts the two halves name the same properties. It is a contract between the build and
 * the suite rather than a measurement, so it does not belong to any scheme.
 */
class ToolchainPropertiesTest {

    private static final Pattern READ = Pattern.compile("System\\.getProperty\\(\"(osera\\.[^\"]+)\"");
    private static final Pattern SET = Pattern.compile("systemProperty\\(\"(osera\\.[^\"]+)\"");

    @Test
    void theBuildSetsEveryPropertyTheSuiteReads() throws IOException {
        assertThat(namesMatching(READ, sources()))
          .as("""
            Every osera.* system property the tests read has to be one build.gradle.kts sets. \
            A property missing from the build does not fail, it falls back to its default.""")
          .isEqualTo(namesMatching(SET, Stream.of(Path.of("build.gradle.kts"))));
    }

    private static Set<String> namesMatching(Pattern pattern, Stream<Path> files) throws IOException {
        Set<String> names = new TreeSet<>();
        try (Stream<Path> paths = files) {
            for (Path file : paths.toList()) {
                Matcher matcher = pattern.matcher(Files.readString(file));
                while (matcher.find()) {
                    names.add(matcher.group(1));
                }
            }
        }
        return names;
    }

    private static Stream<Path> sources() throws IOException {
        return Files.walk(Path.of("src/test/java")).filter(path -> path.toString().endsWith(".java"));
    }
}

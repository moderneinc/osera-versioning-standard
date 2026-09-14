package org.finos.osera.fixers.ext;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Runs one of the non-JVM remediation tools: a script, a JSON request on its stdin, and a working
 * directory it is expected to rewrite in place.
 */
public final class ToolProcess {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long TIMEOUT_SECONDS = 300;

    /** Where to keep what each tool said it was doing; {@code -Dosera.tools.log=<dir>} turns it on. */
    private static final @Nullable String LOG = System.getProperty("osera.tools.log");

    private ToolProcess() {
    }

    /** The script, extracted from the test resources so it travels with the TCK rather than the install. */
    public static Path script(String resource, Path directory) {
        try (InputStream in = ToolProcess.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing script resource " + resource);
            }
            Path script = directory.resolve(Path.of(resource).getFileName());
            Files.write(script, in.readAllBytes());
            return script;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void run(List<String> command, Map<String, String> environment, Map<String, Object> request) {
        try {
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
            builder.environment().putAll(environment);
            Process process = builder.start();
            process.getOutputStream().write(JSON.writeValueAsBytes(request));
            process.getOutputStream().close();

            String output = new String(process.getInputStream().readAllBytes(), UTF_8);
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException(command.getFirst() + " did not finish within " + TIMEOUT_SECONDS + "s");
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException(command.getFirst() + " failed:\n" + output);
            }
            log(request, output);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void log(Map<String, Object> request, String output) throws IOException {
        if (LOG == null) {
            return;
        }
        Path directory = Files.createDirectories(Path.of(LOG));
        Files.writeString(Files.createTempFile(directory, "tool", ".log"),
          JSON.writerWithDefaultPrettyPrinter().writeValueAsString(request) + "\n" + output + "\n");
    }

    public static Map<String, Object> request(Object... keysAndValues) {
        Map<String, Object> request = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            request.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return request;
    }
}

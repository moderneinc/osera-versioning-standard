package org.finos.osera.npm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.finos.osera.Directories;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A throwaway consumer project, installed for real. The question every scenario here asks is which
 * version npm picked, so the answer is read out of what was installed rather than inferred.
 */
public final class NpmProject {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String NPM = System.getProperty("osera.npm", "npm");

    private NpmProject() {
    }

    /**
     * @return the version npm resolved {@code name} to, or {@code "nothing"} if it resolved none.
     */
    public static String resolve(MockNpmRegistry registry, String name, String declared) {
        Path project = null;
        try {
            project = Files.createTempDirectory("osera-npm");
            Files.writeString(project.resolve("package.json"), """
              {
                "name": "consumer",
                "version": "1.0.0",
                "dependencies": { "%s": "%s" }
              }
              """.formatted(name, declared));
            // Its own cache, because npm caches a packument per registry origin and these scenarios
            // change what a single origin serves between probes.
            Files.writeString(project.resolve(".npmrc"),
              "registry=%s\ncache=%s\n".formatted(registry.url(), project.resolve("cache")));

            Process install = new ProcessBuilder(NPM, "install", "--silent", "--no-audit",
              "--no-fund", "--prefer-online")
              .directory(project.toFile())
              .redirectErrorStream(true)
              .start();
            install.getOutputStream().close();
            // Reading to EOF already waits for the process, so waitFor only collects the status.
            String output = new String(install.getInputStream().readAllBytes());
            install.waitFor();

            Path installed = project.resolve("node_modules").resolve(name).resolve("package.json");
            if (!Files.exists(installed)) {
                // A range nothing satisfies is an answer, not a failure; anything else is not.
                if (install.exitValue() != 0 && !output.contains("No matching version")) {
                    throw new IllegalStateException("npm install failed:\n" + output);
                }
                return "nothing";
            }
            return JSON.readTree(Files.readString(installed)).get("version").asText();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } finally {
            // A node_modules tree and a whole npm cache per probe, twenty-six probes a run.
            if (project != null) {
                Directories.deleteRecursively(project);
            }
        }
    }
}

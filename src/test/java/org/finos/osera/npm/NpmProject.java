package org.finos.osera.npm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.finos.osera.Directories;
import org.openrewrite.javascript.marker.NodeResolutionResult.PackageManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A throwaway consumer project, installed for real. The question every scenario here asks is which
 * version the package manager picked, so the answer is read out of what was installed rather than
 * inferred.
 */
public final class NpmProject {

    private static final ObjectMapper JSON = new ObjectMapper();

    private NpmProject() {
    }

    /** As {@link #resolve(MockNpmRegistry, PackageManager, String, String)}, using npm. */
    public static String resolve(MockNpmRegistry registry, String name, String declared) {
        return resolve(registry, PackageManager.Npm, name, declared);
    }

    /**
     * @return the version {@code pm} resolved {@code name} to, or {@code "nothing"} if it
     * resolved none.
     */
    public static String resolve(MockNpmRegistry registry, PackageManager pm,
                                 String name, String declared) {
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
            // Its own cache, because a packument is cached per registry origin and these scenarios
            // change what a single origin serves between probes.
            Files.writeString(project.resolve(".npmrc"),
              "registry=%s\ncache=%s\n".formatted(registry.url(), project.resolve("cache")));
            if (pm == PackageManager.YarnBerry) {
                // Berry reads neither the registry nor the linker from .npmrc, and refuses a
                // plaintext http registry unless it is whitelisted.
                Files.writeString(project.resolve(".yarnrc.yml"), """
                  nodeLinker: node-modules
                  enableGlobalCache: false
                  npmRegistryServer: "%s"
                  unsafeHttpWhitelist:
                    - 127.0.0.1
                  """.formatted(registry.url()));
            }

            Process install = new ProcessBuilder(install(pm))
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
                if (install.exitValue() != 0 && !unsatisfiable(output)) {
                    throw new IllegalStateException(pm + " install failed:\n" + output);
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
            // A node_modules tree and a whole cache per probe, dozens of probes a run.
            if (project != null) {
                Directories.deleteRecursively(project);
            }
        }
    }

    private static List<String> install(PackageManager pm) {
        List<String> command = new ArrayList<>(PackageManagers.command(pm));
        command.add("install");
        command.addAll(switch (pm) {
            case Npm -> List.of("--silent", "--no-audit", "--no-fund", "--prefer-online");
            case Pnpm -> List.of("--ignore-scripts", "--no-strict-peer-dependencies");
            case YarnBerry, YarnClassic -> List.of("--mode", "skip-build");
            case Bun -> throw new IllegalArgumentException("Bun is not measured");
        });
        return command;
    }

    /** A range nothing satisfies is an answer; each manager phrases it differently. */
    private static boolean unsatisfiable(String output) {
        return output.contains("No matching version")
               || output.contains("No candidates found")
               || output.contains("doesn't satisfy")
               || output.contains("could not be found");
    }
}

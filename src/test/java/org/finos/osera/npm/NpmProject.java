package org.finos.osera.npm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.finos.osera.Directories;
import org.openrewrite.javascript.marker.NodeResolutionResult.PackageManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
        return install(registry, pm, name, """
          {
            "name": "consumer",
            "version": "1.0.0",
            "dependencies": { "%s": "%s" }
          }
          """.formatted(name, declared));
    }

    /**
     * As {@link #resolve}, for a manifest the caller writes in full — an override, a workspace, a
     * dependency that is only reachable underneath another.
     *
     * @return the version {@code pm} installed for {@code name}, or {@code "nothing"}.
     */
    public static String install(MockNpmRegistry registry, PackageManager pm,
                                 String name, String manifest) {
        return install(registry, pm, name, manifest, Map.of());
    }

    /**
     * As {@link #install(MockNpmRegistry, PackageManager, String, String)}, for a manifest that
     * does not hold the version itself. A pnpm catalog keeps it in {@code pnpm-workspace.yaml},
     * which has to be on disk beside the manifest or the manifest's {@code catalog:} resolves to
     * nothing.
     *
     * @param alongside file name to contents, written into the project root
     */
    public static String install(MockNpmRegistry registry, PackageManager pm,
                                 String name, String manifest, Map<String, String> alongside) {
        Path project = null;
        try {
            project = Files.createTempDirectory("osera-npm");
            Files.writeString(project.resolve("package.json"), manifest);
            // Its own cache, because a packument is cached per registry origin and these scenarios
            // change what a single origin serves between probes.
            Files.writeString(project.resolve(".npmrc"),
              "registry=%s\ncache=%s\n".formatted(registry.url(), project.resolve("cache"))
              // pnpm symlinks by default, so a transitive lives under .pnpm rather than at
              // node_modules/<name>. Hoisting makes the tree the same shape npm produces, which
              // is what "which version did it install" is read from. The Berry equivalent is
              // nodeLinker below.
              + (pm == PackageManager.Pnpm ? "node-linker=hoisted\n" : ""));
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
            // Appended rather than written, and written last, because a Yarn catalog lives in the
            // .yarnrc.yml this method has just filled with registry settings. Replacing that file
            // would take the registry with it; a YAML document takes another top-level key.
            for (Map.Entry<String, String> file : alongside.entrySet()) {
                Files.writeString(project.resolve(file.getKey()), file.getValue(),
                  StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }

            Process install = new ProcessBuilder(installCommand(pm))
              .directory(project.toFile())
              .redirectErrorStream(true)
              .start();
            install.getOutputStream().close();
            // Reading to EOF already waits for the process, so waitFor only collects the status.
            String output = new String(install.getInputStream().readAllBytes());
            if (!install.waitFor(300, TimeUnit.SECONDS)) {
                install.destroyForcibly();
                throw new IllegalStateException(pm + " install did not finish");
            }

            Path installed = project.resolve("node_modules").resolve(name).resolve("package.json");
            if (!Files.exists(installed)) {
                // A manager that considered the versions and installed none of them is an answer,
                // not a failure; anything else is not.
                if (install.exitValue() != 0 && !refused(output)) {
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

    private static List<String> installCommand(PackageManager pm) {
        List<String> command = new ArrayList<>(PackageManagers.command(pm));
        command.add("install");
        command.addAll(switch (pm) {
            // Not --silent: the output is only ever read when an install failed, and --silent
            // suppresses the reason, which leaves "Npm install failed:" and nothing after it.
            case Npm -> List.of("--no-audit", "--no-fund", "--prefer-online");
            case Pnpm -> List.of("--ignore-scripts", "--no-strict-peer-dependencies");
            case YarnBerry -> List.of("--mode", "skip-build");
            // --mode is Berry's; Classic has its own flags and its own resolutions parser, which
            // is the reason it is measured at all.
            case YarnClassic -> List.of("--ignore-scripts", "--non-interactive");
            case Bun -> throw PackageManagers.notMeasured(pm);
        });
        return command;
    }

    /**
     * Whether the manager declined rather than broke. Each phrases it differently, and Yarn has two
     * ways to decline: nothing satisfies the range, or everything that does is too newly published
     * for its age gate to allow.
     */
    private static boolean refused(String output) {
        return output.contains("No matching version")
               || output.contains("No candidates found")
               || output.contains("doesn't satisfy")
               || output.contains("could not be found")
               || output.contains("are quarantined");
    }
}

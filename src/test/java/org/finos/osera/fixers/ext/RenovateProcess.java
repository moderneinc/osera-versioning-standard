package org.finos.osera.fixers.ext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renovate, the program.
 *
 * <p>Where it is installed and how it is invoked is the same question whichever ecosystem is being
 * measured, so it is answered once here; what to ask it for belongs to the column doing the asking.
 */
public final class RenovateProcess {

    private static final Path HOME = Path.of(System.getProperty("osera.renovate", "build/renovate"));

    /** Node arrives through a version manager, so it is not necessarily on the PATH this JVM inherited. */
    private static final String NODE = System.getProperty("osera.node", "node");

    private RenovateProcess() {
    }

    public static boolean isInstalled() {
        return Files.isDirectory(HOME.resolve("node_modules/renovate"));
    }

    /**
     * @param extraEnvironment Anything the ecosystem needs beyond Renovate's own install, such as
     *                         the certificate bundle a mock repository serving HTTPS has to be
     *                         trusted through.
     */
    public static void run(Path directory, Map<String, String> extraEnvironment, Map<String, Object> request) {
        Map<String, String> environment = new LinkedHashMap<>(extraEnvironment);
        environment.put("RENOVATE_HOME", HOME.toAbsolutePath().toString());
        ToolProcess.run(
          List.of(NODE, ToolProcess.script("tools/renovate/update.js", directory.getParent()).toString()),
          environment, request);
    }
}

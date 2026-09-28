package org.finos.osera.fixers.ext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dependabot, the program.
 *
 * <p>Where its gems are and which Ruby can run them is the same question whichever ecosystem is
 * being measured, so it is answered once here; what to ask it for belongs to the column doing the
 * asking.
 */
public final class DependabotProcess {

    private static final Path GEMS = Path.of(System.getProperty("osera.dependabot", "build/dependabot/gems"));

    /** The system Ruby on macOS is 2.6 and too old for the gems; {@code -Posera.ruby} names a newer one. */
    private static final String RUBY = System.getProperty("osera.ruby", "ruby");

    private DependabotProcess() {
    }

    public static boolean isInstalled() {
        return Files.isDirectory(GEMS.resolve("gems"));
    }

    /**
     * @param extraEnvironment Anything the ecosystem needs beyond the gems, such as the certificate
     *                         bundle a mock repository serving HTTPS has to be trusted through.
     */
    public static void run(Path directory, Map<String, String> extraEnvironment, Map<String, Object> request) {
        Map<String, String> environment = new LinkedHashMap<>(extraEnvironment);
        environment.put("GEM_HOME", GEMS.toAbsolutePath().toString());
        ToolProcess.run(
          List.of(RUBY, ToolProcess.script("tools/dependabot/update.rb", directory.getParent()).toString()),
          environment, request);
    }
}

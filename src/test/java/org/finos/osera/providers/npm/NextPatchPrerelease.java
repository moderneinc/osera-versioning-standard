package org.finos.osera.providers.npm;

import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

/**
 * A backpatch of {@code 1.4.1} is published as {@code 1.4.2-osera-00001}, a plain SemVer 2
 * prerelease of the maintainer's next patch version, with no scope.
 * <pre>
 * 1.4.1                the version being patched
 * 1.4.2-osera-00001    the first backpatch
 * 1.4.2-osera-00002    a second, for another CVE against the same release
 * 1.4.2                the maintainer's own patch, which supersedes both
 * </pre>
 * The sequence is zero-padded because prerelease identifiers that are not purely numeric compare
 * as strings. The constraint is a tilde because a caret admits the next minor, and a consumer who
 * can take the next minor never needed a backpatch.
 */
@Suite
@SelectPackages("org.finos.osera.npm")
public final class NextPatchPrerelease extends PackageVersioning {

    @Override
    protected String version(String base, String producer, int patchSequence) {
        String[] parts = base.split("\\.");
        String nextPatch = "%s.%s.%d".formatted(parts[0], parts[1], Integer.parseInt(parts[2]) + 1);
        return "%s-%s-%05d".formatted(nextPatch, producer, patchSequence);
    }

    @Override
    public boolean isBackpatch(String version) {
        return version.matches(".*-" + OSERA + "-\\d+$");
    }

    @Override
    public String backpatchPattern() {
        return "-" + OSERA + "-\\d+$";
    }

    @Override
    public String constraint(String patched) {
        return "~" + patched;
    }

    @Override
    public String toString() {
        return "Next-patch prerelease";
    }
}

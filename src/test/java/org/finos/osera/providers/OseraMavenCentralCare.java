package org.finos.osera.providers;

import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

import java.util.regex.Pattern;

/**
 * What OSERA settled on: a variant of Maven Central's
 * <a href="https://central.sonatype.org/policies/care-policy/#10-care-versioning">CARE format</a>.
 * It takes its shape from the base version being patched, as CARE does, and it ends in CARE's
 * zero-padded sequence; where it deviates is that a clean numeric base also keeps a branch segment,
 * and that segment is always {@code .1} rather than being the counter.
 * <pre>
 * 2.14.1                      the maintainer's release
 * 2.14.1.1-osera-00001        the first backpatch
 * 2.14.1.1-osera-00002        a second, for another CVE against the same release
 * 2.14.2                      a later maintainer patch of the same line
 *
 * 5.2.19.RELEASE              a base version that already carries a qualifier
 * 5.2.19.RELEASE-osera-00001  CARE's alternate format, with nothing to append a segment to
 * </pre>
 * The segment never moves, so the whole run of backpatches sits above the release it patches and
 * below the maintainer's next one. Which backpatch is newest is then a question of sorting
 * {@code osera-NNNNN} rather than of the version's numeric parts.
 */
@Suite
@SelectPackages("org.finos.osera")
public final class OseraMavenCentralCare extends ProviderVersioning {

    private static final Pattern NUMERIC = Pattern.compile("\\d+(\\.\\d+)*");

    @Override
    protected String version(String version, String producer, int patchSequence) {
        return NUMERIC.matcher(version).matches() ?
          "%s.1-%s-%05d".formatted(version, producer, patchSequence) :
          "%s-%s-%05d".formatted(version, producer, patchSequence);
    }

    @Override
    public String toString() {
        return "OSERA Maven Central CARE";
    }
}

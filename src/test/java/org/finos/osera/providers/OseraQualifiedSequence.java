package org.finos.osera.providers;

import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

/**
 * What OSERA settled on: the base version, then the producer, then a zero-padded sequence.
 * It is CARE's <a href="https://central.sonatype.org/policies/care-policy/#alternate-format-for-qualified-base-versions">
 * alternate format for qualified base versions</a> with {@code care} replaced by {@code osera},
 * used for every base version rather than only for those that already carry a qualifier.
 */
@Suite
@SelectPackages("org.finos.osera")
public final class OseraQualifiedSequence extends ProviderVersioning {
    @Override
    protected String version(String version, int patchSequence) {
        return "%s-osera-%05d".formatted(version, patchSequence);
    }

    @Override
    public String toString() {
        return "OSERA qualified sequence";
    }
}

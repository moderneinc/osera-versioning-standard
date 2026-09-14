package org.finos.osera.providers;

import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectPackages("org.finos.osera")
public final class SemverV2BuildMetadata extends ProviderVersioning {
    @Override
    protected String version(String version, String producer, int patchSequence) {
        return "%s+%s.%d".formatted(version, producer, patchSequence);
    }

    @Override
    public String toString() {
        return "Semver V2 build metadata";
    }
}

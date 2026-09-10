package org.finos.osera.providers;

import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectPackages("org.finos.osera")
public final class SemverV2BuildMetadata extends ProviderVersioning {
    @Override
    protected String version(String version, int patchSequence) {
        return version + "+osera." + patchSequence;
    }

    @Override
    public String toString() {
        return "Semver V2 build metadata";
    }
}

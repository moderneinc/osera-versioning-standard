package org.finos.osera.providers;

import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectPackages("org.finos.osera")
public final class MavenCentralCare extends ProviderVersioning {
    @Override
    protected String version(String version, int patchSequence) {
        return version + "." + patchSequence + "-osera";
    }

    @Override
    public String toString() {
        return "Maven Central CARE";
    }
}

package org.finos.osera.providers.npm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NextPatchPrereleaseTest {

    private final NextPatchPrerelease scheme = new NextPatchPrerelease();

    @Test
    void patchesTheNextPatchVersion() {
        assertThat(scheme.publish("acme-logger", "1.4.1")).isEqualTo("1.4.2-osera-00001");
    }

    @Test
    void countsWithinTheSameNextPatchVersion() {
        assertThat(scheme.publish("acme-logger", "1.4.1", "osera", 2)).isEqualTo("1.4.2-osera-00002");
    }

    @Test
    void zeroPadsSoTheTenthSortsAboveTheNinth() {
        assertThat(scheme.publish("acme-logger", "1.4.1", "osera", 10))
          .isGreaterThan(scheme.publish("acme-logger", "1.4.1", "osera", 9));
    }

    /**
     * A caret admits the next minor, which is the one thing a consumer held on this line cannot
     * take; measured against a real registry, {@code ^1.4.2-osera-00001} resolves to 1.5.0.
     */
    @Test
    void writesATildeConstraintNotACaret() {
        assertThat(scheme.constraint("1.4.2-osera-00001")).isEqualTo("~1.4.2-osera-00001");
    }
}

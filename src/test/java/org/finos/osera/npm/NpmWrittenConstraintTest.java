package org.finos.osera.npm;

import org.finos.osera.providers.npm.PackageVersioning;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a remediation tool should write, measured as the registry fills up around it.
 *
 * <p>npm's {@code UpgradeDependencyVersion} takes a literal constraint rather than a selector, so
 * there is no discovery to measure and the constraint is what the standard has to mandate.
 *
 * <p>The assertion is the intent, not the observed answer: a constraint behaves if the consumer
 * lands on the newest backpatch this producer has published until the maintainer ships, on the
 * maintainer's release once it exists, and never leaves the line. Rows that go red are the cost of
 * the constraint on that row, and the recommendation is the column with the fewest of them.
 */
public record NpmWrittenConstraintTest(PackageVersioning versioning) {

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "-", textBlock = """
      constraint         | registry also holds       | ours | upstream           | resolves to
      ~1.4.2-osera-00001 | nothing more              | 1    | -                  | 1.4.2-osera-00001
      ~1.4.2-osera-00001 | our second backpatch      | 2    | -                  | 1.4.2-osera-00002
      ~1.4.2-osera-00001 | the maintainer's 1.4.2    | 1    | 1.4.2              | 1.4.2
      ~1.4.2-osera-00001 | the maintainer's 1.4.3    | 1    | 1.4.2,1.4.3        | 1.4.3
      ~1.4.2-osera-00001 | an unreleased 1.4.2-rc.1  | 1    | 1.4.2-rc.1         | 1.4.2-osera-00001
      ~1.4.2-osera-00001 | a rival's backpatch       | 1    | 1.4.2-zenith-00001 | 1.4.2-osera-00001
      1.4.2-osera-00001  | nothing more              | 1    | -                  | 1.4.2-osera-00001
      1.4.2-osera-00001  | our second backpatch      | 2    | -                  | 1.4.2-osera-00002
      1.4.2-osera-00001  | the maintainer's 1.4.2    | 1    | 1.4.2              | 1.4.2
      ^1.4.2-osera-00001 | nothing more              | 1    | -                  | 1.4.2-osera-00001
      ^1.4.2-osera-00001 | the maintainer's 1.4.2    | 1    | 1.4.2              | 1.4.2
      """, useHeadersInDisplayName = true)
    void landsOnTheRightVersion(String constraint, String description, int ours,
                                @Nullable String upstream, String expected) {
        // Its own package: each row publishes a different future and must not see another row's.
        String pkg = versioning.freshPackage();
        versioning.publishUpstream(pkg, "1.4.0", "1.4.1", "1.5.0", "2.0.0");
        for (int sequence = 1; sequence <= ours; sequence++) {
            versioning.publish(pkg, "1.4.1", PackageVersioning.OSERA, sequence);
        }
        if (upstream != null) {
            versioning.publishUpstream(pkg, upstream.split(","));
        }

        assertThat(NpmProject.resolve(versioning.registry(), pkg, constraint))
          .as("%s, once the registry also holds %s", constraint, description)
          .isEqualTo(expected);
    }
}

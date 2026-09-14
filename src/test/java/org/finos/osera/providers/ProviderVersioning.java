package org.finos.osera.providers;

import org.finos.osera.MockMavenRepository;
import org.openrewrite.SourceFile;
import org.openrewrite.maven.tree.GroupArtifactVersion;
import org.openrewrite.test.SourceSpec;

import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNull;

public sealed abstract class ProviderVersioning permits
  OseraMavenCentralCare,
  SemverV2BuildMetadata {

    /**
     * The producer every test speaks for unless it is asking what happens when there are several.
     */
    public static final String OSERA = "osera";

    private final MockMavenRepository repository = new MockMavenRepository();

    public final <T extends SourceFile> Consumer<SourceSpec<T>> patch(GroupArtifactVersion gav) {
        return patch(gav, 1);
    }

    /**
     * Publishes a patch of the artifact to the mock repository and expects the {@code PATCH_ME}
     * placeholder in the test's expected source to become its version. The placeholder keeps that
     * source a plain literal the IDE can still highlight.
     *
     * @param gav           The artifact to be patched, at the version to be patched
     * @param patchSequence The number of times a patch has been released for the given version.
     *                      For example, if the version is 1.0.0 and there have been 2 patches released,
     *                      the patchSequence would be 2.
     */
    public final <T extends SourceFile> Consumer<SourceSpec<T>> patch(GroupArtifactVersion gav, int patchSequence) {
        String patched = publish(gav, patchSequence);
        return spec -> {
            UnaryOperator<String> after = requireNonNull(spec.getAfter(),
              "patch() needs an expected source containing PATCH_ME");
            spec.after(actual -> after.apply(actual).replace("PATCH_ME", patched));
        };
    }

    public final String publish(GroupArtifactVersion gav) {
        return publish(gav, 1);
    }

    /**
     * Publishes a patch of the artifact to the mock repository, for tests that ask what a build tool
     * resolves rather than what a remediation tool writes.
     *
     * @return The version the patch was published at.
     */
    public final String publish(GroupArtifactVersion gav, int patchSequence) {
        return publish(gav, OSERA, patchSequence);
    }

    /**
     * As {@link #publish(GroupArtifactVersion, int)}, for a producer that is not OSERA.
     */
    public final String publish(GroupArtifactVersion gav, String producer, int patchSequence) {
        String patched = version(requireNonNull(gav.getVersion()), producer, patchSequence);
        repository.publish(gav.withVersion(patched));
        return patched;
    }

    /**
     * @param producer Who published the patch. Every scheme carries this token, and when two
     *                 producers patch the same release it is the only thing separating them.
     */
    protected abstract String version(String version, String producer, int patchSequence);

    public MockMavenRepository repository() {
        return repository;
    }
}

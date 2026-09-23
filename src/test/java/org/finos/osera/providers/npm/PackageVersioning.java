package org.finos.osera.providers.npm;

import org.finos.osera.npm.MockNpmRegistry;
import org.finos.osera.npm.SemverOrder;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * How a producer spells a backpatch on npm, and what it tells a remediation tool to write.
 *
 * <p>The constraint is part of the scheme rather than of any one remediator because npm's
 * {@code UpgradeDependencyVersion} takes a literal constraint rather than a selector: there is no
 * discovery to measure, so which constraint the standard mandates is the question.
 */
public sealed abstract class PackageVersioning permits NextPatchPrerelease {

    /** The producer every test speaks for unless it is asking what happens when there are several. */
    public static final String OSERA = "osera";

    private final MockNpmRegistry registry = new MockNpmRegistry();
    private final AtomicInteger packages = new AtomicInteger();

    protected PackageVersioning() {
        // One scheme is instantiated per run, and its registry is the one every npm process in
        // that run should resolve against.
        registry.asDefaultRegistry();
    }

    /**
     * A package name no other scenario is using. One registry serves the whole run and most
     * scenarios evolve what it holds, so a shared name leaks one scenario's future into another.
     */
    public final String freshPackage() {
        return "acme-logger-" + packages.incrementAndGet();
    }

    public final String publish(String name, String base) {
        return publish(name, base, OSERA, 1);
    }

    /**
     * Publishes a backpatch of {@code base} to the registry.
     *
     * @param patchSequence how many backpatches this producer has released against {@code base}
     * @return the version it was published at
     */
    public final String publish(String name, String base, String producer, int patchSequence) {
        String patched = version(base, producer, patchSequence);
        registry.publish(name, patched);
        return patched;
    }

    /** As {@link #publish(String, String)}, for a backpatch the producer has only just released. */
    public final String publishJustNow(String name, String base) {
        String patched = version(base, OSERA, 1);
        registry.publish(name, patched, Instant.now());
        return patched;
    }

    /** Publishes an upstream version, so a scenario can say what the maintainer has shipped. */
    public final void publishUpstream(String name, String... releases) {
        for (String release : releases) {
            registry.publish(name, release);
        }
    }

    /**
     * The newest backpatch this producer has published. Which versions are theirs is a property of
     * the scheme, so the fixers ask rather than matching on the token themselves.
     */
    public final String newestBackpatch(String name) {
        return registry.published(name).stream()
          .filter(this::isBackpatch)
          .max(SemverOrder.INSTANCE)
          .orElseThrow(() -> new IllegalStateException("No backpatch published for " + name));
    }

    /** Whether a version is one this producer published, as opposed to the maintainer's own. */
    public abstract boolean isBackpatch(String version);

    /** A regular expression matching this producer's versions, for a tool that takes one. */
    public abstract String backpatchPattern();

    protected abstract String version(String base, String producer, int patchSequence);

    /** What a remediation tool should write into a {@code package.json} to reach {@code patched}. */
    public abstract String constraint(String patched);

    public MockNpmRegistry registry() {
        return registry;
    }
}

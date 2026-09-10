package org.finos.osera;

import org.finos.osera.providers.ProviderVersioning;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.platform.commons.support.ReflectionSupport;

import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Binds the provider versioning scheme under test for this TCK run.
 *
 * <p>Each {@link ProviderVersioning} implementation is itself a {@code @Suite}, so a run is
 * "run that class". Tests executed under a suite carry its unique ID as a prefix, which is how
 * the scheme is recovered here. A test class run on its own falls back to {@code osera.versioning}
 * from {@code junit-platform.properties} or {@code -D}.
 */
public class ProviderVersioningResolver implements ParameterResolver {

    public static final String PROPERTY = "osera.versioning";

    private static final Pattern SUITE_SEGMENT = Pattern.compile("\\[suite:([^\\]]+)\\]");
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(ProviderVersioningResolver.class);

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType() == ProviderVersioning.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        String scheme = enclosingSuite(extensionContext)
          .or(() -> extensionContext.getConfigurationParameter(PROPERTY))
          .orElseThrow(() -> new ParameterResolutionException(
            "No provider versioning scheme under test. Run one of " + schemes() +
            " as a suite, or set -D" + PROPERTY + " to its simple name"));

        Class<? extends ProviderVersioning> type = Sealed.types(ProviderVersioning.class)
          .filter(t -> t.getName().equals(scheme) || t.getSimpleName().equals(scheme))
          .findFirst()
          .orElseThrow(() -> new ParameterResolutionException(
            "'" + scheme + "' is not a provider versioning scheme; expected one of " + schemes()));

        // One instance per scheme for the run, so its mock repository starts once and closes with the root store.
        return extensionContext.getRoot().getStore(NAMESPACE)
          .computeIfAbsent(type, t -> ReflectionSupport.newInstance(t), ProviderVersioning.class);
    }

    private static Optional<String> enclosingSuite(ExtensionContext context) {
        Matcher m = SUITE_SEGMENT.matcher(context.getUniqueId());
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    private static String schemes() {
        return Sealed.types(ProviderVersioning.class).map(Class::getSimpleName).toList().toString();
    }

    /** Every implementation a sealed type permits. The permits clause is the registry. */
    public static final class Sealed {

        private Sealed() {
        }

        public static <T> Stream<Class<? extends T>> types(Class<T> type) {
            return Arrays.stream(type.getPermittedSubclasses()).map(impl -> impl.asSubclass(type));
        }

        public static <T> Stream<T> implementations(Class<T> type) {
            return types(type).<T>map(ReflectionSupport::newInstance);
        }
    }
}

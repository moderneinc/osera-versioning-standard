package org.finos.osera;

import org.finos.osera.providers.ProviderVersioning;
import org.finos.osera.providers.npm.PackageVersioning;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.platform.commons.support.ReflectionSupport;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Binds the versioning scheme under test for this TCK run.
 *
 * <p>Each scheme is itself a {@code @Suite}, so a run is "run that class". Tests executed under a
 * suite carry its unique ID as a prefix, which is how the scheme is recovered here. A test class
 * run on its own falls back to the scheme's property from {@code junit-platform.properties} or
 * {@code -D}.
 *
 * <p>There are two hierarchies, one per ecosystem, because neither ecosystem's format is legal in
 * the other: {@code 2.14.1.1-osera-00001} is not valid SemVer, and {@code 1.4.2-osera-00001} is
 * the spelling the JVM chapters rule out.
 */
public class ProviderVersioningResolver implements ParameterResolver {

    /** Each sealed hierarchy of schemes, and the property naming one when no suite is running. */
    private static final Map<Class<?>, String> SCHEMES = Map.of(
      ProviderVersioning.class, "osera.versioning.jvm",
      PackageVersioning.class, "osera.versioning.npm");

    private static final Pattern SUITE_SEGMENT = Pattern.compile("\\[suite:([^\\]]+)\\]");
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(ProviderVersioningResolver.class);

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return SCHEMES.containsKey(parameterContext.getParameter().getType());
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Class<?> hierarchy = parameterContext.getParameter().getType();
        String property = SCHEMES.get(hierarchy);

        String scheme = enclosingSuite(extensionContext)
          .or(() -> extensionContext.getConfigurationParameter(property))
          .orElseThrow(() -> new ParameterResolutionException(
            "No versioning scheme under test. Run one of " + schemes(hierarchy) +
            " as a suite, or set -D" + property + " to its simple name"));

        Class<?> type = Sealed.types(hierarchy)
          .filter(t -> t.getName().equals(scheme) || t.getSimpleName().equals(scheme))
          .findFirst()
          .orElseThrow(() -> new ParameterResolutionException(
            "'" + scheme + "' is not a " + hierarchy.getSimpleName() + "; expected one of " + schemes(hierarchy)));

        // One instance per scheme for the run, so its repository or registry starts once and
        // closes with the root store.
        return extensionContext.getRoot().getStore(NAMESPACE)
          .computeIfAbsent(type, t -> ReflectionSupport.newInstance(t), Object.class);
    }

    private static Optional<String> enclosingSuite(ExtensionContext context) {
        Matcher m = SUITE_SEGMENT.matcher(context.getUniqueId());
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    private static String schemes(Class<?> hierarchy) {
        return Sealed.types(hierarchy).map(Class::getSimpleName).toList().toString();
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

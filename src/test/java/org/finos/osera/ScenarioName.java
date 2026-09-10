package org.finos.osera;

import org.junit.jupiter.api.DisplayNameGenerator;

import java.lang.reflect.Method;
import java.util.List;

/**
 * A row of the matrix is a scenario, not a signature: {@code transitive}, not
 * {@code transitive(Remediator)}. Registered in {@code junit-platform.properties}.
 */
public class ScenarioName extends DisplayNameGenerator.Standard {

    @Override
    public String generateDisplayNameForMethod(List<Class<?>> enclosingInstanceTypes, Class<?> testClass, Method testMethod) {
        return testMethod.getName();
    }
}

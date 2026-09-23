package org.finos.osera.npm;

import java.util.Comparator;
import java.util.List;

/**
 * SemVer 2.0.0 §11 precedence, enough of it to order a packument.
 *
 * <p>Build metadata is discarded before anything is compared, which is §11's own rule and not a
 * simplification: "Build metadata MUST be ignored when determining version precedence", so
 * {@code 1.4.1+osera.1} and {@code 1.4.1} are the same version. That is the whole reason the
 * build-metadata scheme the JVM side measures cannot carry a producer on npm, and
 * {@link NpmBuildMetadataTest} is where the consequence is measured.
 */
public enum SemverOrder implements Comparator<String> {
    INSTANCE;

    @Override
    public int compare(String left, String right) {
        String[] leftParts = withoutBuildMetadata(left).split("-", 2);
        String[] rightParts = withoutBuildMetadata(right).split("-", 2);

        int numeric = compareNumeric(leftParts[0], rightParts[0]);
        if (numeric != 0) {
            return numeric;
        }
        boolean leftPre = leftParts.length > 1;
        boolean rightPre = rightParts.length > 1;
        if (leftPre != rightPre) {
            // A prerelease has lower precedence than the release it hangs off.
            return leftPre ? -1 : 1;
        }
        return leftPre ? comparePrerelease(leftParts[1], rightParts[1]) : 0;
    }

    /** Everything from the first {@code +} is metadata, and §11 says precedence cannot see it. */
    private static String withoutBuildMetadata(String version) {
        int plus = version.indexOf('+');
        return plus < 0 ? version : version.substring(0, plus);
    }

    private static int compareNumeric(String left, String right) {
        List<String> l = List.of(left.split("\\."));
        List<String> r = List.of(right.split("\\."));
        for (int i = 0; i < Math.max(l.size(), r.size()); i++) {
            int a = i < l.size() ? Integer.parseInt(l.get(i)) : 0;
            int b = i < r.size() ? Integer.parseInt(r.get(i)) : 0;
            if (a != b) {
                return Integer.compare(a, b);
            }
        }
        return 0;
    }

    private static int comparePrerelease(String left, String right) {
        String[] l = left.split("\\.");
        String[] r = right.split("\\.");
        for (int i = 0; i < Math.max(l.length, r.length); i++) {
            if (i >= l.length) {
                return -1;
            }
            if (i >= r.length) {
                return 1;
            }
            boolean lNumeric = l[i].matches("\\d+");
            boolean rNumeric = r[i].matches("\\d+");
            int comparison = lNumeric && rNumeric ?
              Integer.compare(Integer.parseInt(l[i]), Integer.parseInt(r[i])) :
              lNumeric != rNumeric ? (lNumeric ? -1 : 1) : l[i].compareTo(r[i]);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }
}

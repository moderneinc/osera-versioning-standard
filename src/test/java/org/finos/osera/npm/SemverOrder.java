package org.finos.osera.npm;

import java.util.Comparator;
import java.util.List;

/**
 * SemVer 2.0.0 §11 precedence, enough of it to order a packument. Not a general implementation:
 * build metadata does not occur in any scheme this suite publishes to npm, so it is not handled.
 */
public enum SemverOrder implements Comparator<String> {
    INSTANCE;

    @Override
    public int compare(String left, String right) {
        String[] leftParts = left.split("-", 2);
        String[] rightParts = right.split("-", 2);

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

# The OSERA Versioning Standard

OSERA versions a backpatch as `2.14.1.1-osera-00001` when the base version is numeric, and as
`5.2.19.RELEASE-osera-00001` when the base version already ends in a qualifier. We took that from
Maven Central's [CARE policy][care] and changed one thing about it.

CARE picks its format from the shape of the version being patched. A clean numeric version takes a
fourth numeric segment, and that segment is what counts the backpatches:

```
1.2.3            the maintainer's release
1.2.3.1-care     the first emergency release
1.2.3.2-care     the second
1.2.4            a later maintainer release
```

A version that already ends in a qualifier takes a zero-padded sequence instead, because appending
a numeric segment to something that already ends in a word is (in the policy's words) where numeric
appending "would create ambiguity or sorting issues":

```
1.2.3.Final                the maintainer's release
1.2.3.Final-care-00001     the first emergency release
1.2.3.Final-care-00002     the second
```

We kept that second form and changed the first one, so that the fourth segment is always `.1` and
the counter always lives in the qualifier:

| base version | CARE | OSERA |
|---|---|---|
| `2.14.1` | `2.14.1.1-care`, `2.14.1.2-care`, … | `2.14.1.1-osera-00001`, `2.14.1.1-osera-00002`, … |
| `5.2.19.RELEASE` | `5.2.19.RELEASE-care-00001` | `5.2.19.RELEASE-osera-00001` |

We did it because CARE's fourth segment counts releases by one producer while the ordering treats
that number as shared. Give two producers, call them Acme and Zenith, the same release to patch,
and Acme's fifth backpatch sorts above Zenith's first because 5 is greater than 1, which is exactly
right while there is one producer and stops meaning anything once there are two. Freezing the
segment takes that number out of the comparison entirely. Chapter 4 measures what takes its place,
which is not a satisfying answer either, though it is at least a stable one.

The frozen segment still does the job CARE gives it. `.1` is a numeric part that `2.14.1` does not
have, so both Maven and Gradle rank the backpatch above it, and the third segment keeps the whole
run below `2.14.2`. Ordering within one producer then falls to the qualifier, compared as a string,
which is why we zero-pad the sequence: `osera-00010` has to sort above `osera-00009`, and
`osera-10` would not.

It is worth being honest that `-osera-00001` looks exactly like a SemVer pre-release, and so does
CARE's `-care`. SemVer 2.0.0 §9 makes a hyphen the start of a pre-release identifier and §11 gives
pre-releases lower precedence than the release they hang off, so by SemVer's own rules
`2.14.1.1-osera-00001` is a pre-release of `2.14.1.1` and sorts below it. That is the opposite of
what a backpatch needs. No JVM build tool implements SemVer, which is what makes the format work at
all, but the convention is old enough that the tools have absorbed some of its instinct and it is
not safe to assume a hyphen is neutral.

Two things save it on a numeric base version. Maven sorts a qualifier it does not recognize after
the release rather than before, which is the reverse of SemVer. Gradle would apply something closer
to SemVer's reading, except that it never gets there: comparing `2.14.1.1-osera-00001` to `2.14.1`
it runs out of parts on the base after three, takes the fourth part of the longer version, finds
`1`, and stops. The comparison is settled by a number one position before anything that looks like
a pre-release is examined. Take the `.1` away, as the qualified form does, and Gradle does reach
the qualifier — chapter 2 is what that costs.

It also puts us on a coordinate that some maintainers are already using, which we did not notice
until a test failed in a way we did not expect. Jackson reserves a fourth numeric segment for its
own security releases, and Central holds `2.12.6.1`, `2.12.7.1`, `2.12.7.2`, `2.13.2.1`,
`2.13.2.2`, `2.13.4.1` and `2.13.4.2`. An OSERA backpatch of `jackson-databind:2.12.6` would be
`2.12.6.1-osera-00001`, which is the number Jackson itself would use for the same fix. We picked
jackson-databind for an unrelated scenario and Dependabot walked straight onto the maintainer's
release; chapter 3 has that run.

## What we measured

Everything in the next three chapters came out of `./gradlew tck` in this repository, which drives
the real implementations (Maven's resolver, a real Gradle 9.7.1 build through the tooling API,
OpenRewrite's `UpgradeDependencyVersion` and `UpgradeTransitiveDependencyVersion`, Renovate
41.173.1 in its own Node process, and Dependabot 0.394.0 in its own Ruby process) against a mock
Maven 2 repository serving HTTPS on a port the test picks, alongside real Maven Central.

[care]: https://central.sonatype.org/policies/care-policy/#10-care-versioning

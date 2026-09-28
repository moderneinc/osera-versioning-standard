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

## The same problem on npm

Everything above turns on one fact, which the previous paragraph states in passing: no JVM build
tool implements SemVer. npm does. It resolves through `node-semver`, which implements §11 exactly,
and every tool in chapter 3 reaches npm through it. So the format this chapter describes cannot be
carried across, and JavaScript needs a scheme of its own.

OSERA's is a plain SemVer 2 prerelease of the maintainer's *next* patch version, with no scope:

```
1.4.1                the version being patched
1.4.2-osera-00001    the first backpatch
1.4.2-osera-00002    a second, for another CVE against the same release
1.4.2                the maintainer's own patch, which supersedes both
```

It works by patching forward rather than sideways. A prerelease sorts below the release it hangs
off, which is the rule that makes a hyphen dangerous on the JVM's numbering; hanging the backpatch
off `1.4.2` rather than `1.4.1` turns that rule into the answer we want, because it then sorts
above the release being patched and below the maintainer's eventual fix. The sequence is
zero-padded for the same reason as on the JVM: prerelease identifiers that are not purely numeric
compare as strings, so `osera-10` would sort below `osera-9`.

Two things follow from it that have no equivalent in the JVM chapters, and both are measured rather
than argued. A remediator is not optional on npm, because no plain range reaches the backpatch at
all: chapter 2. And the constraint the remediator writes has to be a tilde rather than a caret,
which costs something: also chapter 2.

### The alternative we measured

SemVer has a second place to put a producer's name, and the JVM side measures it as a scheme of its
own: build metadata, `2.14.1+osera.1`. It does not work on npm, and §11 is explicit about why.
Build metadata is excluded from precedence, so `1.4.1+osera.1` and `1.4.1` are the same version.

The cell that settles it is not a range missing the backpatch. It is a consumer who has been told
exactly which build they need:

| the consumer asks for | npm installs |
|---|---|
| `1.4.1+osera.1`, exactly | `1.4.1` |
| `~1.4.1` | `1.4.1` |

npm accepts the request, hands back the maintainer's release without the fix, and reports success.
The producer's artifact is unreachable even when it is named, so there is no constraint a
remediator could write that would help and nothing further to measure. Our mock registry is more
permissive than npmjs here, in that it will serve two versions differing only in build metadata at
all, so this is a floor and not a ceiling.

### The alternative we did not measure

A producer could republish under a scope they control, as `@osera/acme-logger`. That does reach a
consumer, and it is not a versioning scheme: it changes the package's identity, so every
declaration, every lockfile entry and every transitive reference to the original name has to be
redirected to it. It is set aside on those grounds rather than on anything measured here, so this
chapter records the reasoning and not a result. Yarn's `npm:` aliasing is the same proposal with a
redirect in front, and goes with it.

## What we measured

Everything in the next three chapters came out of `./gradlew tck` in this repository, which drives
the real implementations rather than reading their documentation.

On the JVM that is Maven's resolver, a real Gradle 9.8.0 build through the tooling API,
OpenRewrite's `UpgradeDependencyVersion` and `UpgradeTransitiveDependencyVersion`, Renovate
41.173.1 in its own Node process, and Dependabot 0.394.0 in its own Ruby process, against a mock
Maven 2 repository serving HTTPS on a port the test picks, alongside real Maven Central.

On npm it is npm 11.4, pnpm 9.15, Yarn Berry 4.18 through corepack and Yarn Classic 1.22, the same
three remediation tools, and a mock npm registry that holds a producer's versions and proxies
everything else to npmjs. Where a question is about which version a manager picks, the answer comes
from a real install into a throwaway project and is read back out of `node_modules`.

[care]: https://central.sonatype.org/policies/care-policy/#10-care-versioning

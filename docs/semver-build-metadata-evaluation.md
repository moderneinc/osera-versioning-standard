# Why OSERA Did Not Take SemVer Build Metadata

`REL-003` drafted a format before OSERA settled on `2.14.1.1-osera-00001`. It produces a backpatch
version as SemVer build metadata, `<UPSTREAM_VERSION>+<PATCH_INITIATIVE>.NNN`, which for log4j
2.14.1 gives `2.14.1+osera.1`. This is the record of what we measured before setting it aside.

The argument for it is a good one, and it is not an argument about sorting. Build metadata keeps
the upstream version intact and visible, so a reader can see that this is 2.14.1 and not some new
release, and SemVer 2.0.0 §10 says build metadata MUST be ignored when determining precedence,
which means the format deliberately makes no ordering claim at all. Nobody gets silently moved
anywhere, because a backpatch is not supposed to compare as newer than anything. `REL-003`'s own
rationale says ordering should not be relied on, and this is what that position looks like once you
write it down.

We ran it through the same suite as the format we chose (`./gradlew tck` in this repository, which
drives Maven's resolver, a real Gradle 9.8.0 build through the tooling API, OpenRewrite's upgrade
recipes, Renovate 41.173.1 and Dependabot 0.394.0 in their own processes, against a mock Maven 2
repository alongside real Maven Central). It is still in the suite today, as the scheme
`SemverV2BuildMetadata`.

## No JVM build tool implements §10

Neither Maven nor Gradle ignores build metadata, and they do not even fail in the same direction.
Maven treats `+osera` as an unrecognized qualifier, and unrecognized qualifiers sort after the
release. Gradle splits a version on `+` as readily as on `.` or `-`, then finds an extra
non-numeric part on the longer version and ranks it lower. So `2.14.1+osera.1` is newer than
`2.14.1` in Maven and older than `2.14.1` in Gradle.

| dynamic version | base | Maven | Gradle |
|---|---|---|---|
| `2.14.+` | `2.14.1` | — | ❌ `2.14.1` |
| `[2.14,2.15)` | `2.14.1` | ✅ | ❌ `2.14.1` |
| `[2.14.1,2.15)` | `2.14.1` | ✅ | ❌ `2.14.1` |
| `(2.14.1,2.15)` | `2.14.1` | ✅ | ❌ nothing resolves |
| `[4.1.68,4.1.69)` | `4.1.68.Final` | ✅ | ❌ `4.1.68.Final` |
| `(4.1.68,4.1.69)` | `4.1.68.Final` | ✅ | ❌ `4.1.68.Final` |

Every Maven range takes the backpatch, including the ones with an exclusive lower bound that can
only be satisfied by ranking strictly above the release being patched. No Gradle dynamic version
takes it in any form. Run the same ones against `2.14.1.1-osera-00001` and Gradle takes it under
all four forms on a numeric version line.

## The same split reaches the remediation tools

Renovate picks a versioning module per manager, and the two modules faithfully model their build
tools, so the disagreement above shows up again one level higher:

| | OpenRewrite | Renovate | Dependabot |
|---|---|---|---|
| Maven scenarios | 1 / 10 | 7 / 10 | 0 / 10 |
| Gradle scenarios | 1 / 23 | 0 / 23 | 0 / 23 |

OpenRewrite's column is the one to read first, because it is the control. Against the format we
chose it passes all thirty-three of these same scenarios, so every red cell above is the format
being rejected rather than a tool that cannot reach the declaration. The two it does pass are the
OSGi-qualified rows, and they pass by accident: `LatestRelease.normalizeVersion` strips a trailing
`.RELEASE`, and `countVersionParts` stops at the first non-digit after a separator, so
`5.2.19.RELEASE+osera.1` counts three parts like its base rather than the four that
`2.14.1+osera.1` counts, and nothing pads or rejects it.

Renovate offers the upgrade in seven of ten `pom.xml` scenarios and in none of twenty-three
`build.gradle` scenarios. Same artifact, same repository, same version string, and whether a
consumer hears about the fix depends on which build file they happen to use. An organization with
both gets remediated in half its repositories, and gets no signal at all that the other half was
skipped.

Dependabot is zero for the reason it is zero for every format we measured:
`Shared::SharedVersionFinder#suffix_compatible?` requires the candidate's suffix to match the
current version's, and `2.14.1` has no suffix while `2.14.1+osera.1` has `+osera`.

## OpenRewrite's two passing cells are an accident

Those two greens are both the same scenario, a direct dependency whose base version is
`5.2.19.RELEASE`. Everything else is red, and it turns out the reason is a counting bug rather than
a ranking one.

`LatestRelease.countVersionParts` walks the string and counts a digit that follows a separator,
where the separators are `.`, `-` and `$`. It does not treat `+` as one. So in `2.14.1+osera.1` the
scan does not stop at the `+`; it keeps going, reaches the `.` inside `osera.1`, sees a digit after
it, and counts a fourth version component. The current version `2.14.1` only has three, so it gets
padded to `2.14.1.0` before the comparison, `2.14.1.0` sorts above the candidate once the metadata
is stripped, and `upgrade()` decides the backpatch is not an upgrade and rejects it.

Give the base version an OSGi qualifier and the same scan stops early, because in
`5.2.19.RELEASE+osera.1` the character after the third `.` is `R`, which is not a digit, so the
count ends at three and matches the base. No padding, no rejection, and the upgrade goes through.
The format's only two successes in the whole remediation matrix come from a qualifier that has
nothing to do with the format.

That one is a fixable defect in OpenRewrite rather than a property of the naming scheme, and
pinning an exact patched version works regardless. We report it as a measurement of what shipping
code does today, not as an argument that the format cannot be made to work.

## What the format is good at

Gradle never adopts it. No Gradle range, prefix or `latest.release` will move a build onto a
backpatch produced this way, so no consumer is silently moved onto a producer they did not choose.
The format we chose does the opposite, and a Gradle range on a coordinate several producers have
patched lands on whichever producer's token sorts last, with nobody asked.

That is a real property and it is the one `REL-003` was reaching for. It is also only half
delivered, since Maven's ranges do adopt it. A format that makes no ordering claim would have to be
ignored by both tools to be worth choosing on those grounds, and it is ignored by neither.

## Why we set it aside

The case for the format rests on build metadata being ignored, and no JVM build tool ignores it.
What they do instead is disagree, with Maven ranking it above the version it patches and Gradle
ranking it below, so the same published artifact reaches Maven consumers through ranges and update
bots while Gradle consumers never hear about it. A producer has no way to tell which of their
consumers were reached.

`2.14.1.1-osera-00001` has no such split. Every tool we measured ranks it above `2.14.1` and below
`2.14.2`, and the cost of that consistency (dynamic versions adopting a backpatch nobody asked for)
is at least uniform, which makes it something a consumer can turn off by not using dynamic
versions.

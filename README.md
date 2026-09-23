# OSERA patched-release versioning: what the tools actually do

Executable evidence for [finos-osera/remediation-standards#33][issue], the discussion of how OSERA
should spell a patched release under `REL-003`.

The thread has two positions in it. Maven Central's CARE policy argues for a numeric branch segment,
`1.0.1.1-care`, because Maven-family tooling orders it predictably. The current `REL-003` draft
argues for SemVer build metadata, `1.0.1+osera-patch.001`, because it preserves the upstream version
visibly and does not claim to be the maintainer's next release. Both positions rest on claims about
what build tools and update tools do with these strings.

This repository does not argue. It runs them.

Every claim below is produced by a test that drives the real implementation — Maven's own
`ComparableVersion`, a real Gradle build, OpenRewrite's version selector, Renovate's versioning
modules, and Dependabot's version classes — against a mock Maven 2 repository that is told to
contain particular versions.

```
./gradlew test
```

82 tests. The Renovate and Dependabot tests need npm and Ruby 3.1+ respectively, and install their
own dependencies into `build/` on first run; they skip cleanly if those are absent. On macOS,
`/usr/bin/ruby` is 2.6 and too old, so pass a newer one:

```
./gradlew test -Dosera.ruby=/usr/local/opt/ruby/bin/ruby
```

## The harness

[`MockMavenRepository`](src/test/java/org/finos/osera/versioning/MockMavenRepository.java) writes a
real Maven 2 layout — poms, jars, checksums, `maven-metadata.xml` — for whatever versions a test
declares, and serves it over both `file:` and HTTP. Several instances can be configured at once, so
the enterprise topology described in the issue — an upstream mirror plus a separate patch repository
— can be set up where a test needs it.

Everything else in the repository is a test pointed at one of those.

## Finding 1: Maven and Gradle order build metadata in opposite directions

| | `1.0.1+osera.001` vs `1.0.1` | `1.0.1.1-osera` vs `1.0.1` |
|---|---|---|
| Maven `ComparableVersion` | **patch is newer** | patch is newer |
| Gradle (real resolution) | **base is newer** | patch is newer |
| Renovate `maven` versioning | **patch is newer** | patch is newer |
| Renovate `gradle` versioning | **base is newer** | patch is newer |
| Dependabot `Maven::Version` | **patch is newer** | patch is newer |
| Dependabot `Gradle::Version` | **base is newer** | patch is newer |

Maven treats `+osera` as an unrecognised qualifier, and unrecognised qualifiers sort after the
release. Gradle splits on `+` as well as `.` and `-`, then finds an extra non-numeric part on the
longer version and ranks it lower. Neither matches SemVer 2.0.0 §10, which requires build metadata
to be ignored entirely — but the disagreement between them is the part that costs something.

Renovate and Dependabot are two independent reimplementations, and both reproduce the split, because
both are faithfully modelling their build tool. The practical consequence is that **the same
artifact, patched identically, published under one version string, is offered as an update in a
`pom.xml` and not in a `build.gradle`.** A mixed estate gets remediated in half its repositories.

The CARE-shaped spelling has no such disagreement. Every tool measured here ranks `1.0.1.1-osera`
above `1.0.1` and below the maintainer's `1.0.2`.

- [`MavenVersionOrderingTest`](src/test/java/org/finos/osera/versioning/ordering/MavenVersionOrderingTest.java)
- [`GradleVersionOrderingTest`](src/test/java/org/finos/osera/versioning/ordering/GradleVersionOrderingTest.java)
- [`RenovateVersioningTest`](src/test/java/org/finos/osera/versioning/updatetools/RenovateVersioningTest.java)
- [`DependabotVersioningTest`](src/test/java/org/finos/osera/versioning/updatetools/DependabotVersioningTest.java)

## Finding 2: no spelling gives a meaningful order across producers

The issue asks how to handle "multiple producers publishing patches against the same upstream
version and artifact namespace", and how to stop "provider-specific patch names or numbers from
distorting recipient prioritization". Neither candidate solves this, and CARE's numeric segment
makes it worse in a specific way.

**A busier producer outranks a more relevant one.** OSERA ships two patches against `1.0.1`; another
producer ships five. Every tool measured ranks `1.0.1.5-care` above `1.0.1.2-osera`:

```
1.0.1.1-osera   OSERA's first patch     closes CVE-A
1.0.1.2-osera   OSERA's second patch    closes CVE-A and CVE-B
1.0.1.5-care    someone else's fifth    closes none of them
```

The sequence number counts releases by one producer but occupies a slot the ordering treats as
shared. Nothing in the version string says which CVEs a release closes, so no comparator can prefer
the one that closes yours. The highest-sorting version is not the most-remediated version.

**Ties break on the producer's name.** `1.0.1.1-zenith` beats `1.0.1.1-osera` beats `1.0.1.1-acme`,
in every tool, for the same reason `z` sorts after `o` sorts after `a`. The same is true of the
build-metadata spelling: `1.0.1+zenith.001` beats `1.0.1+osera.001`.

So the CARE principle that a patched release should "sort logically after the vulnerable base
version" holds only while exactly one producer exists. Add a second and the total order is still
total, but it stops meaning anything.

## Finding 3: what this does to how patches get applied

A version scheme is only worth what consumers can do with it. These are before/after tests over real
`pom.xml` and `build.gradle` sources.

| Declaration | Build-metadata patch | CARE-shaped patch |
|---|---|---|
| literal version, recipe pins it | applied | applied |
| version property / Gradle variable | applied | applied |
| `dependencyManagement` | applied | applied |
| Gradle `1.0.+`, `[1.0,2.0)` | applied (pin replaces the range) | applied |
| Gradle `latest.release` | not rewritten | not rewritten |
| Maven range `[1.0,2.0)` | not rewritten | not rewritten |
| Gradle version catalog | not updated | not updated |

And what happens with no recipe at all, just the build tool resolving:

| Declaration | Build-metadata patch | CARE-shaped patch |
|---|---|---|
| Maven range, base is latest upstream | **silently adopted** | **silently adopted** |
| Gradle `1.+` / `latest.release` / range | never adopted | **silently adopted** |
| OpenRewrite "upgrade to latest", no producer named | never adopted | never adopted |

The two "silently adopted" cells are the accidental-precedence risk the issue raises, and they land
on whichever producer's token sorts highest — not on one the consumer chose. The Maven row is the
uncomfortable one for the build-metadata spelling: because Maven ranks it above the base, a Maven
range picks it up, while Gradle's equivalent never will. Same string, same repository, opposite
outcome.

- [`MavenPatchApplicationTest`](src/test/java/org/finos/osera/versioning/maven/MavenPatchApplicationTest.java)
- [`MavenRangeTest`](src/test/java/org/finos/osera/versioning/maven/MavenRangeTest.java)
- [`GradlePatchApplicationTest`](src/test/java/org/finos/osera/versioning/gradle/GradlePatchApplicationTest.java)
- [`GradleVersionCatalogTest`](src/test/java/org/finos/osera/versioning/gradle/GradleVersionCatalogTest.java)

## Finding 4: `+osera-patch.NNN` breaks OpenRewrite's patch discovery today

A remediation recipe finds a producer's newest patch by asking OpenRewrite for `latest.patch`
filtered by a version metadata pattern. For the CARE-shaped spelling this works. For the spelling
`REL-003` currently mandates, it returns nothing.

OpenRewrite counts version components by scanning for digit groups that follow a separator, and the
`.` inside build metadata counts like any other. `1.0.1+osera.002` reads as **four** components, so
the current `1.0.1` is padded to `1.0.1.0` before comparison, and `1.0.1.0` then sorts above the
metadata-stripped `1.0.1`. The candidate is rejected as not an upgrade.

It is the sequence number that triggers it, not the `+`:

```
1.0.1+osera.001   not discovered      separator, then digits
1.0.1+osera-001   not discovered      separator, then digits
1.0.1+osera001    discovered          no separator before the digits
1.0.1+osera.abc   discovered          separator, but no digits
```

`REL-003.REQ-001` mandates `<UPSTREAM_VERSION>+<PATCH_INITIATIVE>.NNN`, which is exactly the shape
that fails. This is a fixable bug in OpenRewrite 8.90.4 rather than a property of the naming scheme,
and pinning an exact patched version works regardless — but it means the discovery path does not
work today for the drafted spelling.

- [`OpenRewriteSelectorTest`](src/test/java/org/finos/osera/versioning/ordering/OpenRewriteSelectorTest.java)

## A spelling to rule out

`1.0.2-osera.1` — publishing the patch as a pre-release of the maintainer's *next* version — is
worse than either candidate and is included so it can be dismissed with evidence. Maven ranks it
**above** the maintainer's own `1.0.2`, because `-osera` is not one of the qualifiers Maven treats as
a pre-release. A producer using it would displace releases they do not own. Gradle ranks it below.
Renovate's Gradle module agrees with Gradle; Dependabot's `Gradle::Version` does not, and would offer
a Gradle project an upgrade its own build ranks as a downgrade.

That verdict is about the JVM, and it rests on Maven ranking `-osera` above the version it hangs
off rather than below it. npm inverts that, because `node-semver` implements SemVer 2.0.0 §11 and
the JVM's build tools do not: a prerelease sorts below its own version there, so a prerelease of
the maintainer's *next* patch lands above the release being patched and below the maintainer's
eventual fix, which is what a backpatch needs. OSERA settled on `1.4.2-osera-00001` for npm on
exactly the rule this section shows Maven and Gradle failing to follow.
[Chapter 1 of the manual](docs/manual/01-osera-versioning-standard.md) has that argument, and the
alternatives measured against it.

## What the evidence supports

Read against the decision in front of the working group:

1. **The build-metadata spelling cannot be defended on ordering grounds**, because there is no single
   ordering. It can be defended on the grounds that ordering should not be relied on at all — which
   is what `REL-003`'s own rationale already says, and what the Gradle column supports: dynamic
   versions never reach it, so no consumer is silently moved onto a producer they did not pick. That
   is a real property, and it is the opposite of what CARE optimises for.

2. **The CARE spelling delivers consistent ordering and pays for it with silent adoption.** Every
   tool agrees it is newer, which also means every range, `1.+`, `latest.release`, and update bot
   will take it from whichever producer sorts highest.

3. **Neither spelling makes cross-producer ordering meaningful**, so the standard should not claim
   that it does. Ranking is by producer token and by how many patches that producer happens to have
   shipped. If OSERA wants recipients to end up on OSERA's patch rather than an arbitrary one, that
   has to come from repository configuration, an explicit pin, or a recipe that names the producer —
   not from the version string.

4. **Whichever is chosen, exact pinning is the only path that behaves identically everywhere.** It is
   the one row in every table above with no asterisk.

A concrete suggestion, if the A/B test in the thread goes ahead: measuring downloads per coordinate
will not separate these. Both forms will be downloaded by whoever configured them. What would
separate them is measuring how consumers *arrived* at the version — pinned, via a range, or via a
bot PR — because that is where the two schemes behave differently, and it is where the risk is.

## What this repository does not show

- **The JVM and npm.** PyPI, Go and containers have different resolvers and different answers.
  What the npm toolchain does is measured too, and written up in
  [the manual](docs/manual/README.md).
- **Versions are pinned:** OpenRewrite 8.90.4, Gradle 9.7.1, `maven-artifact` 3.9.16, Renovate
  41.173.1, Dependabot 0.394.0. Findings 1 and 2 are ordering semantics and stable; finding 4 is a
  bug that may be fixed.
- **Ordering, not resolution, for real Maven.** Maven ordering is measured with Maven's own
  `ComparableVersion`; range *resolution* is measured with OpenRewrite's implementation of Maven
  range semantics rather than by shelling out to `mvn`. Gradle results all come from a real Gradle
  build.
- **No repository-manager layering.** Nexus and Artifactory group ordering is not modelled.
- **Not the production recipe.** Consumers run Moderne's `UpgradeToBackpatch`, which is not open
  source. It configures the same open-source `UpgradeDependencyVersion` recipes with the same
  `latest.patch` selector and metadata pattern that these tests drive, so finding 4 applies to it.

[issue]: https://github.com/finos-osera/remediation-standards/issues/33

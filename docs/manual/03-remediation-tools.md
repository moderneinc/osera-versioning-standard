# What Remediation Tools Do

OpenRewrite, Renovate and Dependabot each have two jobs here, which are to find the producer's
backpatch among the versions a repository advertises and then to edit whichever part of the build
actually decides the version. The second job is harder than the first, and it is where most of the
red below comes from, so most of these cells say more about the tool than they do about the
version we picked.

We give all three the producer's identity, OpenRewrite through a `versionPattern` of
`(\.[A-Za-z]\w*)?-osera-\d+` and Renovate through an `allowedVersions` of `/-osera-\d+$/`, and the
two need it for different reasons.

OpenRewrite will not select a backpatch without it. `VersionComparator.checkVersion` asks for a
release, and it recognizes only `.final`, `.ga` and `.release` as release qualifiers, so anything
else carrying a qualifier fails the check and an unpatterned `latest.patch` never reaches
`2.14.1.1-osera-00001`. Renovate is the opposite: its `maven` module derives a qualifier type and
returns null for a word it does not know, and its `gradle` module tests against a fixed set of
unstable words, so both of them read `osera` as stable and would happily consider the backpatch.
We give Renovate `allowedVersions` anyway, so that it proposes the producer's line rather than
whatever is newest on the coordinate, which is what a consumer who has chosen a producer wants.
Either way somebody has to name the producer, and chapter 4 is what happens when nobody does.

Each row is a build that declares a vulnerable version one particular way, against a repository
holding an OSERA backpatch of it, and a check means the tool made the change that fixes the build.

## Maven

| how the version reaches the dependency | OpenRewrite | Renovate | Dependabot |
|---|---|---|---|
| literal `<version>` | ✅ | ✅ | ❌ |
| transitive, nothing declared to edit | ✅ | ❌ | ❌ |
| `${property}` in the same pom | ✅ | ✅ | ❌ |
| `<dependencyManagement>` in the same pom | ✅ | ✅ | ❌ |
| BOM imported with `<scope>import</scope>` | ✅ | ❌ | ❌ |
| a `<plugin>`'s own dependency | ✅ | ✅ | ❌ |
| parent property, child without `<version>` | ✅ | ✅ | ❌ |
| `spring-boot-starter-parent` manages it | ✅ | ❌ | ❌ |
| literal, OSGi-qualified base version | ✅ | ✅ | ❌ |
| two backpatches published, newest wins | ✅ | ✅ | ❌ ¹ |
| | **10 / 10** | **7 / 10** | **0 / 10** |

¹ Dependabot did make a change here, to the wrong version, which is the last section of this
chapter.

## Gradle

| how the version reaches the dependency | OpenRewrite | Renovate | Dependabot |
|---|---|---|---|
| string notation | ✅ | ✅ | ❌ |
| transitive, nothing declared to edit | ✅ | ❌ | ❌ |
| a `def` the notation interpolates | ✅ | ✅ | ❌ |
| `gradle.properties` | ✅ | ✅ | ❌ |
| map notation | ✅ | ✅ | ❌ |
| Gradle's own `platform()` | ✅ | ❌ | ❌ |
| Kotlin DSL | ✅ | ✅ | ❌ |
| `io.spring.dependency-management` BOM | ✅ | ❌ | ❌ |
| string notation, OSGi-qualified base version | ✅ | ❌ | ❌ |
| two backpatches published, newest wins | ✅ | ✅ | ❌ |
| | **10 / 10** | **6 / 10** | **0 / 10** |

Version catalogs get their own table below, because there are thirteen of these rows and they do
not agree with each other.

## Gradle version catalogs

A catalog is not another declaration form, it is a small file format with several spellings of the
same entry and several ways for a build script to ask for one. A tool that reads one spelling does
not necessarily read the rest, so each gets a row. Every build below reaches `log4j-core` through a
catalog and differs only in how the catalog writes the version down.

| how the catalog holds the version | OpenRewrite | Renovate | Dependabot |
|---|---|---|---|
| `module` and a literal version | ✅ | ✅ | ❌ |
| string notation, `"group:name:version"` | ✅ | ✅ | ❌ |
| `group` and `name` rather than `module` | ✅ | ✅ | ❌ |
| `version.ref`, one library on the alias | ✅ | ✅ | ❌ |
| `version.ref` shared with a library that has no backpatch | ✅ | ❌ | ❌ |
| `version = { require = "…" }` | ✅ | ✅ | ❌ |
| `version = { strictly = "…" }` | ✅ | ✅ | ❌ |
| a catalog file that is not `gradle/libs.versions.toml` | ✅ | ✅ | ❌ |
| declared in `settings.gradle` rather than in TOML | ✅ | ✅ | ❌ |
| `settings.gradle`, `versionRef` into a named version | ✅ | ✅ | ❌ |
| `settings.gradle.kts` | ✅ | ✅ | ❌ |
| consumed through `libs.bundles.logging` | ✅ | ✅ | ❌ |
| consumed through `platform(libs.jackson.bom)` | ✅ | ✅ | ❌ |
| | **13 / 13** | **12 / 13** | **0 / 13** |

Both tools find a catalog whatever file it lives in and whichever DSL declares it, and both read a
version whether it is a string or a constraint of `strictly`, `require` and `prefer`. One red cell
is left, and it is the interesting one.

Renovate's single failure is not a refusal. It is the only cell in this chapter where a tool leaves
the build worse than it found it. Two libraries share a version declaration, one of them has a
backpatch and the other does not, and Renovate moves the declaration:

```diff
 [versions]
-log4j = "2.14.1"
+log4j = "2.14.1.1-osera-00001"

 [libraries]
 log4j-api = { module = "org.apache.logging.log4j:log4j-api", version.ref = "log4j" }
 log4j-core = { module = "org.apache.logging.log4j:log4j-core", version.ref = "log4j" }
```

`log4j-api` now points at a version nobody published, and the build stops resolving. OpenRewrite
detaches the patched library to a version of its own instead and leaves the declaration alone,
which is the answer the alias can actually support. Sharing an alias is the normal way to write a
catalog — it is how a catalog says two artifacts ship together — and a producer backpatching one
artifact of a family is the normal case for OSERA, so the two meet more often than this one row
suggests.

What we have not measured: `enforcedPlatform`, catalogs consumed from `buildSrc` or a plugin, and
`[plugins]` entries, which OSERA's backpatches do not currently reach.

## Why the red cells are red

Dependabot's column is red everywhere and ordering has nothing to do with it. Its Maven and Gradle
update checkers end in `Shared::SharedVersionFinder#suffix_compatible?`, which requires a
candidate's suffix to equal the current version's, and a dependency sitting at an unqualified
release has no suffix while every backpatch we publish has one. The check fails before anything
compares which version is newer. Dependabot's own version classes are fine (both `Maven::Version`
and `Gradle::Version` rank `-osera-00001` above the release it patches, and neither calls it a
pre-release), but nothing reaches them.

Renovate edits version literals it can find in a file, and all but two of its red cells are a build
with no literal to edit, whether that is a transitive dependency, an imported BOM, or a parent that
manages the version. It has no notion of adding a constraint or writing a `<dependencyManagement>`
block that was not already there, so it reports, correctly, that there is nothing to update. The
other of the two is the shared version declaration in the catalog table above, which is the
opposite failure: it finds a literal, edits it confidently, and edits the wrong one.

The exception among the rest is the OSGi-qualified row in Gradle, which has a literal sitting
right there and is red anyway. Renovate picks a versioning module per manager, and its `gradle`
module reproduces Gradle's own ordering, under which `5.2.19.RELEASE-osera-00001` is older than
the release it patches. That is chapter 2's result arriving one level up: Renovate declines to
offer a downgrade, and it is right that it would be one by Gradle's reckoning. The same row in
Maven is green.

OpenRewrite has no red cells. It makes the change that fixes the build in all thirty-three, which
is worth stating plainly because it is the only column in this chapter that does, and because it
sets the bar the other two are being measured against rather than being a result about OSERA.
Nothing here says a backpatch is easy to consume in general; it says one tool has implemented every
way a build can hold a version, and that a consumer who is not using it will hear about a backpatch
only in the rows where their tool happens to look.

## Picking the newest backpatch

The last row of each table is the one that exercises the counter. We publish two backpatches of the
same release, `2.12.6.1-osera-00001` and `2.12.6.1-osera-00002`, whose numeric parts are identical
at `2.12.6.1`, so nothing in the numeric comparison separates them. A tool that gets this right is
comparing `osera-00001` against `osera-00002` as strings, and OpenRewrite and Renovate both do, in
both build tools.

## Jackson was already using that number

Dependabot failed that row differently from all the others, in that it actually made a change:
`2.12.6` to `2.12.6.1`.

`2.12.6.1` is a real Jackson release. Jackson publishes a fourth numeric segment for its own
security micro-releases, and Central holds `2.12.6.1`, `2.12.7.1`, `2.12.7.2`, `2.13.2.1`,
`2.13.2.2`, `2.13.4.1` and `2.13.4.2`, so an OSERA backpatch of `2.12.6` and Jackson's own patch of
`2.12.6` want the same four numbers. OpenRewrite and Renovate never saw the collision because their
producer pattern excludes anything without `-osera-` in it, while Dependabot, which we give a
version ceiling rather than a producer, took the maintainer's release instead of ours.

CARE asks a backpatch to "avoid consuming the maintainer's next standard version when feasible,"
and a frozen `.1` does that on a project that never uses a fourth segment and does the opposite on
a project that reserves the fourth segment for exactly this kind of release. The ordering still
works out (Maven ranks `2.12.6.1-osera-00001` above `2.12.6.1` and Gradle ranks it below), but a
producer patching Jackson is publishing into numbering its maintainer is actively using, and we
think the standard should say which of the two wins when both exist.

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

## npm

Chapter 2 ended by saying a remediator is mandatory here, which makes this section load-bearing in
a way the JVM tables are not. On the JVM a consumer with a dynamic version gets the backpatch
whether or not any of these tools ever runs. On npm nobody gets it unless one of them rewrites a
declaration.

Renovate appears twice. On the JVM it reads `osera` as a stable qualifier and would consider the
backpatch unprompted, so one column is enough; on npm the backpatch is a prerelease and Renovate
ships `ignoreUnstable: true`, which excludes it before any comparison happens. A consumer who
installs Renovate and configures nothing therefore gets a different answer from one who has been
told what to set, and the difference is itself something the standard has to tell people. The stock
column passes exactly what `getOptions()` reports as shipped, so that it measures a consumer who
changed nothing rather than one this suite half-configured. The configured column sets
`allowedVersions` to the producer's line and `ignoreUnstable: false`, and those two are the whole
of what is required.

Dependabot gets neither. On the JVM it is given a version ceiling, because it has no way to say
"only versions matching the producer's pattern" and a ceiling is the nearest thing. Here it is given
nothing to rule out, because what an unconfigured consumer is offered is itself part of what this
section measures.

### How the version reaches the dependency

| how the version reaches the dependency | OpenRewrite | Renovate (stock) | Renovate (configured) | Dependabot |
|----------------------------------------|-------------|------------------|-----------------------|------------|
| literal constraint in `dependencies`   | ✅ ¹         | ❌                | ✅                     | ❌          |
| a workspace member declares it         | ✅ ¹         | ❌                | ✅                     | ❌          |
| two backpatches published, newest wins | ✅           | ❌                | ✅                     | ❌          |
| transitive, nothing declared to edit   | ❌           | ❌                | ❌                     | ❌          |
|                                        | **3 / 4**   | **0 / 4**        | **3 / 4**             | **0 / 4**  |

¹ On npm and Yarn Berry. The same cell is red on pnpm, for a reason that has nothing to do with
backpatches; "Why the red cells are red" below has it. The third row carries no footnote because it
is asked of npm alone: which of two backpatches a tool picks is a comparison between versions, and
no package manager takes part in it.

Four rows where the JVM has thirty-three, and the gap is the ecosystem rather than the effort. Ten
of Maven's and Gradle's rows are version properties, parent poms, `dependencyManagement`, imported
BOMs, plugin dependencies and two DSLs, and a `package.json` has an analogue for none of them: it
is JSON, so there is no variable to interpolate and one way to write a dependency. What npm does
have is a catalog, and that gets its own table below, exactly as Gradle's does.

### Catalogs

pnpm and Yarn both let a version live outside the manifest, in a file of their own, with the
manifest holding a bare `catalog:` reference. It is the same idea as a Gradle version catalog and
it is measured the same way, one row per spelling, because a tool that reads one spelling does not
necessarily read the rest.

| where the catalog lives                | OpenRewrite | Renovate (stock) | Renovate (configured) | Dependabot |
|----------------------------------------|-------------|------------------|-----------------------|------------|
| `pnpm-workspace.yaml`, default catalog | ❌           | ❌                | ✅                     | ❌          |
| `pnpm-workspace.yaml`, named catalog   | ❌           | ❌                | ✅                     | ❌          |
| `.yarnrc.yml`, default catalog         | ❌           | ❌                | ❌                     | ❌          |
| `.yarnrc.yml`, named catalog           | ❌           | ❌                | ❌                     | ❌          |
|                                        | **0 / 4**   | **0 / 4**        | **2 / 4**             | **0 / 4**  |

This is the only table in the book where OpenRewrite is not the column that works and configured
Renovate is. It is also the only place a spelling makes the difference: Renovate handles both pnpm
catalogs and neither Yarn one.

OpenRewrite's cell is not a refusal, which makes it the more interesting red in the table. There is
no occurrence of `catalog` anywhere in `rewrite-javascript`, so the recipe does not know the
feature exists. What it does instead is treat the string `catalog:` as a version literal and
overwrite it:

```diff
   "dependencies": {
-    "acme-logger": "catalog:"
+    "acme-logger": "~1.4.2-osera-00001"
   }
```

The catalog file is left alone. The build still resolves, and in a one-member workspace it even
installs the right version, which is what makes it worse than doing nothing: the package has been
quietly taken out of the catalog. In a real workspace that fixes one member, leaves the others on
the vulnerable version, and removes the single declaration that was keeping them in step. It is the
same class of failure as Renovate's shared `version.ref` earlier in this chapter, where a tool
finds a literal, edits it confidently, and edits the wrong thing.

### The override works, the automation does not

A transitive dependency is the case a backpatch most often exists for, and it is the one row where
every column is red.

The mechanism itself is sound. Each manager spells "pin this package wherever it appears" its own
way, and each one honours it for a package the consumer never declared. Yarn Classic 1.22 joins the
table here, where it is absent from the rest of the chapter, because it accepts three spellings of
its key and does not treat them alike:

|              | key                         | installs the backpatch from it |
|--------------|-----------------------------|--------------------------------|
| npm          | `overrides`                 | ✅                              |
| pnpm         | `pnpm.overrides`            | ✅                              |
| Yarn Berry   | `resolutions`               | ✅                              |
| Yarn Classic | `resolutions`, plain name   | ✅                              |
| Yarn Classic | `resolutions`, `**/name`    | ✅                              |
| Yarn Classic | `resolutions`, `name@range` | ❌                              |

The last row is a parsing bug old enough to have been reported before this suite existed: given
`acme-logger@~1.4.1` Yarn Classic splits at the `@` and goes looking for `~1.4.1@~1.4.2-osera-00001`.
Any advice that mentions `resolutions` has to say which of the three forms to write.

So the override reaches the consumer. What no tool does is produce one.

Renovate does not act at all, in either column, and it is right not to: its transitive path is
`vulnerabilityAlerts` with `rangeStrategy: update-lockfile`, driven by an alert feed rather than by
the lookup this suite calls. Dependabot sees the subdependency and cannot move it, asked through
both of its unlock strategies. OpenRewrite writes the correct override key for the manager, and
then leaves the lockfile pinning the vulnerable version, so the manifest and the lock disagree and
`npm ci` refuses the result with `EUSAGE`. A commit that does not build is not a remediation.

### Why the red cells are red

**Dependabot** is red everywhere, as on the JVM, and it is worth saying plainly that the reason is
different. The JVM columns die in `Shared::SharedVersionFinder#suffix_compatible?`. The npm column
dies in `filter_prerelease_versions`, which drops a prerelease unless the current version or a
declared requirement is already a prerelease at the same tuple. A consumer sitting on `~1.4.1` is
neither, so the backpatch is filtered before anything compares versions. Dependabot can sustain a
line once a remediator has put a consumer on it, and even then only with `ignored_versions` holding
it below the next minor. It cannot start.

**Renovate, unconfigured**, is red everywhere too, and not because it does nothing. It proposes
`~2.0.0`, in every scenario. So does Dependabot. Both bots offer a consumer pinned to `1.4.x` a
migration to the next major and call it an update, which is worse than missing the backpatch,
because it looks like an answer and a consumer who takes it has changed their major version to fix
a patch-level vulnerability.

**OpenRewrite**'s reds are two defects in `UpgradeTransitiveDependencyVersion`, and both reproduce
on an ordinary version bump with no backpatch involved. `NativeLockEngine` defers an override edit
with `RESOLUTION_REQUIRED`, a reason listed in `RESOLVABLE_REASONS`, and the whole-closure resolver
it falls back to has no notion of overrides, so it recomputes the same graph and reports success.
Separately the recipe never stabilises on pnpm, which is what the footnote in the first table
refers to: run the two composed recipes apart and `UpgradeDependencyVersion` converges there while
the transitive one does not.

### What we have not measured

Bun, which is a fourth package manager this suite does not run. Yarn's `workspace:`, `patch:` and
`portal:` protocols, which cannot carry a third-party backpatch, and `npm:` aliasing, which is the
scoped-republication proposal chapter 1 records as ruled out. Transitive remediation on pnpm and
Yarn Berry, which needs a lockfile fixture in each of their formats: the override half of that
question is measured on all three managers above, and the half that is missing would be red for the
upstream defect already described.

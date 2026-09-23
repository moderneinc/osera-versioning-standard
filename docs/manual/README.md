# How Build Tooling Treats an OSERA Backpatch

FINOS OSERA evaluated several ways to version a backpatch. On the
JVM it is `2.14.1.1-osera-00001` when the base version is numeric, `5.2.19.RELEASE-osera-00001`
when it already ends in a qualifier. On npm it is `1.4.2-osera-00001`, a plain SemVer 2 prerelease
of the maintainer's next patch version. They differ because the JVM's build tools do not implement
SemVer and npm's resolver does, which is the whole of the argument and is chapter 1.

This book is the evidence behind those choices, which is to say what the toolchains actually do
with each candidate rather than what their comparators are documented to do.

Every cell in every table came out of `./gradlew tck` in this repository. It drives the real
implementations: Maven's resolver, a real Gradle 9.7.1 build through the tooling API, npm 11.4,
pnpm 9.15, Yarn Berry 4.18 and Yarn Classic 1.22, OpenRewrite's dependency recipes, Renovate
41.173.1 in its own Node process and Dependabot 0.394.0 in its own Ruby process. They run against a
mock Maven 2 repository serving HTTPS alongside real Maven Central, and a mock npm registry that
holds a producer's versions and proxies everything else to npmjs. Where a question is about which
version a resolver picks, the answer is read back out of a real build or a real install rather than
inferred.

## Contents

1. [The OSERA Versioning Standard](01-osera-versioning-standard.md)
&nbsp;&nbsp;&nbsp;&nbsp;What CARE specifies, the one thing we changed about it, what freezing the
fourth segment at `.1` buys and costs, and why npm needs a different format entirely.

2. [Dynamic Version Resolution Against OSERA Backpatches](02-dynamic-versions.md)
&nbsp;&nbsp;&nbsp;&nbsp;Maven ranges and Gradle dynamic versions against a repository holding a
backpatch. Maven takes every one, Gradle takes them on numeric version lines and none on
`.Final`-style ones, and one rule in its comparator accounts for all of it. Then npm, where twelve
of fifteen declared ranges never reach the backpatch, which is why a remediator is mandatory there
and which constraint it should write.

3. [What Remediation Tools Do](03-remediation-tools.md)
&nbsp;&nbsp;&nbsp;&nbsp;Thirty-three ways a JVM build can arrive at a version, thirteen of them
Gradle version catalogs, and three tools against them: one column green throughout, three distinct
reasons the other two are not, and a collision with Jackson's own security numbering. Then the same
question on npm, where the columns come out in a different order and a catalog is the one table
OpenRewrite does not win.

4. [When Two Producers Patch the Same Release](04-conflicting-producers.md)
&nbsp;&nbsp;&nbsp;&nbsp;Six backpatches from three producers and what each tool takes when nobody
names one, the question about the producer token that we think the standard still has to answer,
and the npm constraint that names a producer without being able to hold a consumer to it.

## Reading a red cell

The suite runs with `ignoreFailures`, because a tool declining to select a backpatch is the
measurement rather than a broken build. Four kinds of red turn up and the chapters try to keep them
apart.

**The format.** A tool found the backpatch and would not take it on account of how it is spelled.
On the JVM this is a tool ranking it below the release it patches; on npm nothing ranks it wrongly,
and the refusal is categorical instead, because a range does not consider a prerelease at all
unless it names one.

**The build tool.** A feature nobody has implemented makes the cell red whatever the version is
called, which is most of Renovate's column in chapter 3 and all four npm columns on a transitive
dependency.

**The tool that did act and got it wrong.** Rarer and worse, because it looks like a fix. There are
two: Renovate moving a shared Gradle `version.ref`, and OpenRewrite taking a package out of an npm
catalog rather than updating it.

**A defect upstream of the scheme.** A tool that did the right thing and had it eaten by a bug with
nothing to do with versioning. All of these are OpenRewrite's, all are in one recipe, and all
reproduce on an ordinary version bump with no backpatch involved. They are red here because the
suite reports what happened, not because they say anything about OSERA, and they are the reds this
working group can turn green itself.

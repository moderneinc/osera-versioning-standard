# How the JVM Ecosystem Treats an OSERA Backpatch

FINOS OSERA evaluated several ways to version a backpatch and settled on one:
`2.14.1.1-osera-00001` when the base version is numeric, `5.2.19.RELEASE-osera-00001` when it
already ends in a qualifier. This book is the evidence behind that choice, which is to say what the
JVM toolchain actually does with each candidate rather than what its comparators are documented to
do.

Every cell in every table came out of `./gradlew tck` in this repository, which runs 23 build
scenarios against OpenRewrite, Renovate and Dependabot, and another fourteen that ask a build tool
directly with no remediation involved. It drives the real implementations (Maven's resolver, a real
Gradle 9.7.1 build through the tooling API, Renovate 41.173.1 in its own Node process, and
Dependabot 0.394.0 in its own Ruby process) against a mock Maven 2 repository over HTTPS alongside
real Maven Central.

## Contents

1. [The OSERA Versioning Standard](01-osera-versioning-standard.md)
&nbsp;&nbsp;&nbsp;&nbsp;What CARE specifies, the one thing we changed about it, and what freezing
the fourth segment at `.1` buys and costs.

2. [Dynamic Version Resolution Against OSERA Backpatches](02-dynamic-versions.md)
&nbsp;&nbsp;&nbsp;&nbsp;Maven ranges and Gradle dynamic versions against a repository holding a
backpatch. Maven takes every one, Gradle takes them on numeric version lines and none on
`.Final`-style ones, and one rule in its comparator accounts for all of it.

3. [What Remediation Tools Do](03-remediation-tools.md)
&nbsp;&nbsp;&nbsp;&nbsp;Thirty-three ways a build can arrive at a version, thirteen of them Gradle
version catalogs, and three tools against them: one column green throughout, three distinct reasons
the other two are not, and a collision with Jackson's own security numbering.

4. [When Two Producers Patch the Same Release](04-conflicting-producers.md)
&nbsp;&nbsp;&nbsp;&nbsp;Six backpatches from three producers and what each tool takes when nobody
names one, plus the question about the producer token that we think the standard still has to
answer.

## Reading a red cell

The suite runs with `ignoreFailures`, because a tool declining to select a backpatch is the
measurement rather than a broken build. Three kinds of red turn up and the chapters try to keep
them apart: the format, where a tool found the backpatch and ranked it below the release it
patches; the build tool, where a feature nobody has implemented makes the cell red whatever the
version is called; and the tool that did act and got it wrong, which happens in exactly one cell
and whose diff is in chapter 3.

# Dynamic Version Resolution Against OSERA Backpatches

A build with a dynamic version will take a backpatch without anybody deciding to take it, which is
either the fastest remediation path available or the accidental-precedence problem the issue
raises, depending mostly on whether you are the producer or the consumer. Which one you get comes
down to whether the resolver ranks the backpatch above the release it patches, and the two
resolvers do not agree about that as often as you would hope.

Each row below publishes one backpatch of the named release into a repository the build can see,
next to real Maven Central, and then reads back what the tool resolved. A check means the backpatch
won, and an x names what won instead.

## Maven

| version range | base | selects the backpatch |
|---|---|---|
| `[2.14,2.15)` | `2.14.1` | ✅ |
| `[2.14.1,2.15)` | `2.14.1` | ✅ |
| `(2.14.1,2.15)` | `2.14.1` | ✅ |
| `[4.1.68,4.1.69)` | `4.1.68.Final` | ✅ |
| `(4.1.68,4.1.69)` | `4.1.68.Final` | ✅ |

Maven takes the backpatch under every constraint we tried. The two rows with an exclusive lower
bound are the ones that prove it, since `(2.14.1,2.15)` cannot contain `2.14.1` and anything
selected out of it has to rank strictly above the release being patched. Maven sorts an
unrecognized qualifier after the release, and `-osera-00001` is unrecognized, so it clears the
bound.

The netty rows behave like the log4j rows because Maven treats `.Final` as an alias for the release
qualifier, which puts `4.1.68.Final` and `4.1.68` at the same point in its order and lets a range
written with plain numeric bounds still reach the line.

## Gradle

| dynamic version | base | selects the backpatch |
|---|---|---|
| `2.14.+` | `2.14.1` | ✅ |
| `[2.14,2.15)` | `2.14.1` | ✅ |
| `[2.14.1,2.15)` | `2.14.1` | ✅ |
| `(2.14.1,2.15)` | `2.14.1` | ✅ |
| `4.1.68.+` | `4.1.68.Final` | ❌ `4.1.68.Final` |
| `[4.1.68.Final,4.1.69.Final)` | `4.1.68.Final` | ❌ `4.1.68.Final` |
| `(4.1.68.Final,4.1.69.Final)` | `4.1.68.Final` | ❌ nothing resolves |

One rule in Gradle's comparator accounts for every cell there. Gradle splits a version into parts
on `.`, `-` and `+`, compares them pairwise, and when one version turns out to be the other plus
extra parts it decides on the first extra part alone, so that a numeric part makes the longer
version greater and a word Gradle does not recognize makes it lesser. The words it recognizes are
`dev`, `rc`, `snapshot`, `final`, `ga`, `release` and `sp`, which is the whole of `SPECIAL_MEANINGS`
in `StaticVersionComparator` in the 9.8.0 distribution these tests run against. It has never heard
of `osera`.

So `2.14.1.1-osera-00001` is `2.14.1` plus `1`, a number, and it wins. `4.1.68.Final-osera-00001`
is `4.1.68.Final` plus `osera`, a word, and it loses, which means Gradle reads the backpatch as
older than the release it patches and treats selecting it as a downgrade.

That failing arm is CARE's alternate format, which has no numeric segment to work with by
construction, so any producer following CARE gets the same result on the same libraries. Those
libraries are Spring 5.2 and earlier, Boot 2.3 and earlier, Netty, Hibernate and the JBoss stack,
which is a large enough slice that a producer publishing under OSERA today should expect their
backpatches of it to be invisible to Gradle's version selection and to arrive only through the
tools in chapter 3.

## A Gradle range needs the qualifier in its bounds

We had all three netty rows failing with `nothing resolves` at first, which looked like a verdict
on the version format until we ran the same dynamic versions with nothing published at all. Against
Maven Central on its own, no mock repository and no backpatch:

| artifact | dynamic version | resolves to |
|---|---|---|
| `io.netty:netty-codec-http` | `[4.1.68,4.1.69)` | nothing resolves |
| `io.netty:netty-codec-http` | `[4.1.68.Final,4.1.69.Final)` | `4.1.68.Final` |
| `io.netty:netty-codec-http` | `4.1.68.+` | `4.1.68.Final` |
| `org.apache.logging.log4j:log4j-core` | `[2.14,2.15)` | `2.14.1` |

It turns out that a Gradle range whose bounds are numeric cannot reach a line whose versions end in
`.Final`, even though the prefix form reaches it and the identical range shape works fine on a
numeric line. Maven has no such requirement. That is why the netty bounds are written out in full
in the Gradle table, since with numeric bounds those rows fail no matter what is published and only
measure Gradle's range parser.

## npm

The question changes shape here. On the JVM a dynamic version is something a build opts into, and
the two tables above measure builds that went looking for one. On npm every dependency is a range
unless somebody went out of their way, because `^` is what `npm install` writes, so the equivalent
table is not about an unusual build. It is about all of them.

Every row below runs against the same registry, which holds a realistic history rather than a
convenient one:

```
1.4.0   1.4.1   [1.4.2-osera-00001]   1.5.0   2.0.0        dist-tag latest = 2.0.0
```

A backpatch exists because the maintainer has moved on, so the maintainer has moved on. The whole
table depends on it. Publish nothing above the line being patched and a caret has nowhere to float
to, so it reaches the backpatch and looks like the better recommendation; publish the `1.5.0` that
really exists and it never reaches it at all.

It also decides who the chapter is about. A consumer declaring `^1.4.1` floats to `1.5.0` the moment
it ships and never needed a backpatch at all. The population a backpatch exists for is the one
**held on the line**: `~1.4.1`, `1.4.x`, an exact pin, or a transitive pin they do not control. The
caret rows are below to show that they are not the case of interest.

### What a declared range resolves to

| declared | resolves to | reaches the backpatch |
|---|---|---|
| `~1.4.1` | `1.4.1` | ❌ |
| `1.4.x` | `1.4.1` | ❌ |
| `1.4.1` | `1.4.1` | ❌ |
| `^1.4.1` | `1.5.0` | ❌ |
| `^1.4.1-0` | `1.5.0` | ❌ |
| `^1.4.2-0` | `1.5.0` | ❌ |
| `>=1.4.1 <2.0.0` | `1.5.0` | ❌ |
| `1.4.1 - 1.5.0` | `1.5.0` | ❌ |
| `1.x` | `1.5.0` | ❌ |
| `>=1.4.1` | `2.0.0` | ❌ |
| `*` | `2.0.0` | ❌ |
| (no constraint) | `2.0.0` | ❌ |
| `~1.4.2-0` | `1.4.2-osera-00001` | ✅ |
| `~1.4.2-osera-00001` | `1.4.2-osera-00001` | ✅ |
| `1.4.2-osera-00001` | `1.4.2-osera-00001` | ✅ |
| | | **3 / 15** |

Twelve of fifteen miss, and the twelve are not all the same miss. Three of them leave a consumer
sitting on `1.4.1`, the vulnerable version, which is the harm the backpatch was published to
prevent. The other nine move the consumer to `1.5.0` or `2.0.0`, which fixes the vulnerability by
migrating them off the line they were held on, and a consumer who could accept that was never in
the population this chapter is about.

One rule accounts for the whole table. §11 gives a prerelease lower precedence than the release it
hangs off, and node-semver turns that into a policy: a range does not match a prerelease unless the
range itself names a prerelease at the same `[major, minor, patch]` tuple. `~1.4.1` is not such a
range, so `1.4.2-osera-00001` is invisible to it even though it is numerically in reach. The three
rows that work are the three that say a prerelease of `1.4.2` out loud.

**So a remediator is mandatory on npm.** That is the difference from the JVM, where chapter 3 is
about tools reaching declarations that a dynamic version would have reached on its own. Here there
is no dynamic version that gets there, and a backpatch nobody's tooling rewrites is a backpatch
nobody installs.

### The three package managers agree

The table above is npm's. pnpm 9.15 and Yarn Berry 4.18 were asked the four rows that discriminate,
and gave npm's answer to all four, including `^1.4.1` landing on `1.5.0`.

Worth asking rather than assuming, even though all three resolve through `node-semver`. A package
manager can put a policy of its own in front of the resolver: pnpm has a `resolution-mode` setting
that can make a range prefer the lowest version satisfying it rather than the highest, and a
consumer under it would sit on `1.4.1` and be in the held-on-the-line population rather than out of
it. Neither manager's settings changed an answer here. The section below is what it looks
like when one does.

### Which constraint a remediator should write

The table above says a tool has to write something. It does not say what.

Only two of the three candidates reach the backpatch at all. The caret misses it on every row
below, for the same reason `^1.4.1` misses it above: `1.5.0` exists, and a caret takes it. The rest
of the table is what happens to the other two as the registry fills up.

| the registry also holds | `~1.4.2-osera-00001` | `1.4.2-osera-00001` | `^1.4.2-osera-00001` |
|---|---|---|---|
| nothing more | the backpatch | the backpatch | `1.5.0` |
| our own second backpatch | `…-00002` | the backpatch, stuck | `1.5.0` |
| the maintainer's `1.4.2` | `1.4.2` | the backpatch, stuck | `1.5.0` |
| the maintainer's `1.4.2` and `1.4.3` | `1.4.3` | the backpatch, stuck | `1.5.0` |
| the maintainer's unreleased `1.4.2-rc.1` | `1.4.2-rc.1` | the backpatch | `1.5.0` |
| a rival producer's `1.4.2-zenith-00001` | `1.4.2-zenith-00001` | the backpatch | `1.5.0` |

**There is no safe constraint, and the standard has to say which failure it prefers.**

The caret is out on the first row. It abandons the line for `1.5.0` before anything else has
happened, which is the one thing a consumer held on `1.4.x` cannot do, so it fails at the only job
it was given.

The exact pin never takes anything it was not given, which is its virtue and its defect. It is
immune to the last two rows, and it also never takes the maintainer's own fix: a consumer pinned to
`1.4.2-osera-00001` stays there after `1.4.2` ships, holding a private build of a package that has
been properly fixed, and only another remediation run moves them.

The tilde is the recommendation, and it earns it on rows two through four. It picks up our second
backpatch with no tool involved, and it hands the consumer back to the maintainer the moment the
maintainer ships, which is exactly the lifecycle a backpatch is supposed to have. A remediator is
then needed precisely once, to move a consumer from a stable range onto the line.

Rows five and six are what it costs, and they are the same cost twice: a tilde admits the whole
prerelease tuple, so it admits anything else published at `1.4.2-*`. That means the maintainer's
unreleased release candidate, which a consumer who asked for a security fix did not ask to be an
early tester for. And it means a rival producer's backpatch, because `zenith` sorts after `osera`
as a string. **Naming our own patch in the constraint does not keep a consumer on it.** Chapter 4
is what happens when two producers patch the same release, and this row is that chapter arriving
early, in the one place the recommendation cannot avoid it.

### How old a version has to be

One more thing decides whether a range reaches a backpatch, and it is not in the version at all.

Yarn Berry has `npmMinimalAgeGate`, which defaults to 1440 minutes: a version the registry dates
inside the last day is quarantined and will not be installed. It is absent from the Yarn Berry 4.5 this suite
first ran against and present in the 4.18 it runs against now. A backpatch exists because a
vulnerability was disclosed, so it is new exactly when it is most needed.

| published minutes ago | npm | pnpm | Yarn Berry |
|---|---|---|---|
| our backpatch, asked for by `~1.4.2-osera-00001` | the backpatch | the backpatch | ❌ nothing installs |
| the maintainer's own `1.4.2`, asked for by `~1.4.1` | `1.4.2` | `1.4.2` | ❌ `1.4.1` |

The second row is the one to read twice, and it is why the maintainer's release is measured here at
all. Yarn does not fail it. Something older satisfies `~1.4.1`, so Yarn quietly installs `1.4.1`,
the vulnerable version, and reports success.

This is not a cost of OSERA's scheme. The gate cannot see a producer token and does not know a
prerelease from a release; it reads a date, and it holds the maintainer's own security release back
exactly as hard as it holds ours. It belongs in this chapter because it puts a floor under how fast
any backpatch reaches a Yarn consumer, and nothing a producer does can lower it.

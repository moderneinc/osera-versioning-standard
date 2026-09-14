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
in `StaticVersionComparator` in the 9.7.1 distribution these tests run against. It has never heard
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

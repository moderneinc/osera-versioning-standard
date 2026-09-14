# When Two Producers Patch the Same Release

Everything so far assumed one producer, and the issue behind this work does not. It asks how a
standard keeps "provider-specific patch names or numbers from distorting recipient
prioritization," which in practice is the question of what your build should do when two other
producers, Acme and Zenith, have both fixed `httpclient:4.5.13` and so have we.

No comparator answers that well, because nothing in a version string says which CVEs a backpatch
closes and there is no field for it. Whatever a resolver picks, it is picking on the strength of
digits and letters that were never about your vulnerability, so the most any scheme can offer here
is that its answer be the same one tomorrow and explainable to somebody who asks why.

We publish six backpatches of `4.5.13` from those three producers, arranged so that patch count
and alphabetical order disagree. Acme is the busiest and sorts first, Zenith is the quietest and
sorts last, and OSERA falls between them on both counts:

```
4.5.13.1-acme-00001      Acme, three backpatches
4.5.13.1-acme-00002
4.5.13.1-acme-00003
4.5.13.1-osera-00001     OSERA, two
4.5.13.1-osera-00002
4.5.13.1-zenith-00001    Zenith, one
```

## What each tool takes

| the consumer's build | what it lands on |
|---|---|
| Maven version range, no producer named | Zenith's `00001` |
| Gradle dynamic version, no producer named | Zenith's `00001` |
| Maven declared version, OpenRewrite told `-osera-` | OSERA's `00002` |
| Maven declared version, Renovate told `-osera-` | OSERA's `00002` |
| Gradle declared version, OpenRewrite told `-osera-` | OSERA's `00002` |
| Gradle declared version, Renovate told `-osera-` | OSERA's `00002` |
| either, Dependabot | no change |

The first two rows are the ones with nobody steering, and both build tools land on Zenith, which
has shipped one backpatch, rather than on Acme, which has shipped three. All six versions sit at
`4.5.13.1`, so the numeric comparison ties six ways and the tie breaks on the qualifier as a
string, which gives `zenith` over `osera` over `acme` for the same reason `z` beats `o` beats `a`.

That is the frozen segment doing what we froze it for. Under CARE the segment is the counter, so
Acme's third backpatch is `4.5.13.3-care` while Zenith's only one is `4.5.13.1-care`, and every
tool in this book would take Acme's. Acme would win on having published three times, which says
nothing at all about whether Acme fixed the CVE in your build.

We should be clear that the CARE half of that comparison is reasoning rather than a measured row,
since there is no CARE format left in the suite to run it against. The six versions above are ours,
and the CARE behavior follows from the same Maven and Gradle comparators the rest of the book
exercises.

Either way the ordering has to break somewhere, and the choice is between breaking on a number that
counts a producer's releases and breaking on a token that names the producer. We think the token is
the better of the two, though not by a lot. It is stable, in that a producer cannot climb the
ranking by publishing more often, and it is something you can say in advance: a build that resolves
against repositories carrying all three of these will land on Zenith, so either accept that or stop
using dynamic versions on that coordinate. A ranking that moves every time somebody publishes gives
you nothing to plan against.

## Naming the producer is the part that works

The bottom rows are the only place a consumer ends up on the producer they chose, and nothing in
those builds names a producer, since the pom and the build script are ordinary pins. The naming
lives in the tool's configuration (OpenRewrite's `versionPattern`, Renovate's `allowedVersions`),
and once a tool has it, Zenith sorting above OSERA stops mattering and the tool lands on
`osera-00002`, which is the newest OSERA backpatch rather than the highest-sorting version in the
repository.

None of that is a property of the version format, which is the useful thing to take away. If your
consumers are meant to end up on a particular producer's backpatches, it has to come from the
repositories they resolve against or from the tool they run, and a standard that promises the
ordering will deliver it is promising something the ordering does not do.

## The token has to come from somewhere

Those six versions assume the producer token varies, and CARE says the opposite: that the
remediating party's identity belongs in metadata rather than in the version label, and that vendor
names stay out of versions. Take that literally under our format and Acme and Zenith both publish
`4.5.13.1-osera-00001` for different bytes, which is worse than an arbitrary ordering, because now
there is nothing to order.

So something has to separate them, whether that is the token, the `groupId`, or the repository they
publish to, and the standard should say which. We think it should be the token, and that is a place
where we knowingly part company with CARE: a version that cannot say who produced it cannot be told
apart from another producer's fix for the same release, and the artifact is the only thing that
travels with the build.

Choosing the token does make the alphabetical ranking in the table above permanent, and it is worth
being precise about how much that matters, because it is less than it first appears. Ordering only
ever decides between versions a consumer can see, and what a consumer sees is decided by the
repositories they resolve against. A build wired to one producer's repository never has this
question. A build wired to several has already accepted whatever those several publish. Allocating
tokens is worth writing into the standard so that two producers never collide on a coordinate, but
no producer reaches a consumer who has not wired them up.

# Commons compatibility for the independent package

Upstream [Fossify Commons](https://github.com/FossifyOrg/Commons) 7.1.3 contains
an anti-fork notice based on whether the package starts with `org.fossify.`.
The independent ID `org.fossifymigration.baizhu.gallery` does not match that
prefix. Keeping the notice out of this independent app must not require pretending
to have an upstream package identity.

The maintained-source Gradle artifact transform under `buildSrc/**` is restricted
to replacing the allowlist-prefix constant in `BaseSimpleActivity.class`:
`org.fossify.` → `org.fossifymigration.baizhu.gallery`. Other Commons bytecode
and resources remain unchanged. This is a build-time compatibility adjustment,
not a replacement Commons distribution or a claim of upstream endorsement.

There is no spoofed `packageName`, no package-permission identity change, and no
SAF result/return-value tampering. Android continues to see the real independent
application ID. About's source/issues gateway targets BaizhuGallery; explicitly
labeled upstream FAQ, license and credit links retain their upstream destinations.

The transform's version and expected-constant guards fail closed on an unexpected
Commons version or class/constant layout rather than broaden the patch or silently
accept drift. A pure-JDK self-test checks the narrow replacement and rejection
cases. The 1.15.0 self-test and independent comparison of actual AAR/class entries
passed; see [artifact verification and limits](independent-validation.md). This
is not a device/provider/storage behavior certification.

Existing lint/detekt baselines receive only one-to-one namespace/path substitutions
for the package rename: no new suppressed findings or relaxed rules. Earlier
feature reports' “no baseline changes” statements remain historical, not claims
about this identity rename. See [independent release](independent-release.md) for
signing, migration boundaries and historical-evidence limits.

# Releasing

[한국어](/ko/releasing/)

How a version of `kr.devslab:ssrf-guard` reaches Maven Central.
Maintainers only.

## First: check whether the version is already out

`gradle.properties` carries the **next development version**, not the next
unreleased one:

```properties
VERSION=3.4.0-SNAPSHOT
```

`3.4.0-SNAPSHOT` means *3.3.0 has shipped and 3.4.0 is being built*. It
does **not** mean 3.4.0 is sitting unreleased. Reading it the other way is
easy and expensive — it happened on 2026-08-09, and two security fixes
went onto `main` under a version number that was already on Maven Central.
Nothing shipped wrongly only because the tag was never moved.

So before touching anything, ask the registry rather than the file:

```bash
git ls-remote --tags origin 'refs/tags/v*' | tail -5
curl -s https://repo1.maven.org/maven2/kr/devslab/ssrf-guard/maven-metadata.xml \
  | grep -oE '<version>[^<]+</version>' | tail -5
```

**A published version is immutable.** If work has already landed under a
released number, do not move the tag and do not edit that version's
changelog section — the artifact and its notes must keep agreeing. Restore
the released section from the tag and open a new version instead:

```bash
git show v3.3.0:CHANGELOG.md | sed -n '/^## \[3.3.0\]/,/^## \[/p'
```

## Cutting the release

1. Bump `VERSION` in `gradle.properties` — drop `-SNAPSHOT`.
2. Make sure `CHANGELOG.md` has the section, and that its **title
   describes the release** rather than whatever the first merged PR was
   about. A release note that undersells its own security content is one
   people skip.
3. PR → CI green → merge.
4. Tag and push. The tag push is the point of no return:

```bash
git tag v3.4.0
git push origin v3.4.0
```

`release.yml` then publishes to Maven Central and creates the GitHub
Release from the changelog section.

## Before pushing the tag

`./gradlew build` green locally — the whole suite, not one module. Then
walk every surface below and confirm it is updated **or explicitly N/A**.
A bumped jar with stale public docs is the usual slip: readers land on the
docs site or the org profile and see the previous release's story.

| Surface | Usually |
| --- | --- |
| [`devslab-kr/.github`](https://github.com/devslab-kr/.github) profile README, both languages | N/A — Maven Central badges, no hardcoded version |
| This repo's `README.md` + `README.ko.md` | install coordinates |
| `CHANGELOG.md`, `docs/changelog.md`, `docs/changelog.ko.md` | all three, same content |
| `docs/` — `index`, `getting-started/installation`, guides, both languages | `grep -rn "<previous-version>" docs/` |
| GitHub Discussions | N/A while disabled on this repo |
| [`devslab-kr/devslab-examples`](https://github.com/devslab-kr/devslab-examples) | every `ssrf-guard-*` demo pins a version — and a **behaviour** change may need more than a bump |

That last column matters. 3.3.0 made `SsrfGuardedHttpClient` refuse a
delegate that follows redirects itself; the jdkhttp demo still compiled,
but its comment said "Wrap any HttpClient", which had become false. Build
the demos against the real artifact once it is on Central.

## The sibling

[`@devslab/ssrf-guard-js`](https://github.com/devslab-kr/ssrf-guard-js)
ships the same security model in JS/TS on its own version line — same
model, not the same release train. Its release runbook is
[docs/releasing.md](https://github.com/devslab-kr/ssrf-guard-js/blob/main/docs/releasing.md),
and if this release changed core logic — the scanner, IP classification,
redirect semantics, block reasons — walk
[the parity checklist](https://github.com/devslab-kr/ssrf-guard-js/blob/main/docs/parity.md)
before shipping. Round 1 of that audit found divergences in **both**
libraries.

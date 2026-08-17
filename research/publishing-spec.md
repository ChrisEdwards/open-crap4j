# open-crap4j publishing path

Locked 2026-08-16 by crap-diy.8. Settles distribution channels, release sequence, signing, automation, and security posture. Decides only, execution lives in beads crap-0w4.15 through crap-0w4.20.

## Channels

One channel per artifact, with one deliberate exception (the plugin dual-publishes).

- **`crap4j-core`** — Maven Central under `com.architester`. The only artifact anyone resolves as a library.
- **`crap4j-gradle-plugin`** — Gradle Plugin Portal (id `com.architester.crap4j`) **and** Maven Central, including the plugin marker POM (`com.architester.crap4j:com.architester.crap4j.gradle.plugin`). Dual-publish exists for builds whose proxies allowlist `mavenCentral()` but not `plugins.gradle.org`. The plugin stays thin, declaring a normal dependency on `crap4j-core` (the Portal forwards dependency resolution to Central).
- **`crap4j-cli`** — fat jar attached to GitHub Releases only. Never on Central. Matches Checkstyle, google-java-format, ktlint, and detekt. A Homebrew formula in the maintainer's existing tap wraps it post-1.0 (`depends_on "openjdk"`).
- **No SNAPSHOTs anywhere.** Local consumption stays `mavenLocal` / `includeBuild` per the build-layout spec.

See ADR 0004 for the rationale.

## Release sequence

1. v1 proves out on an mcp-contrast branch via `pluginManagement includeBuild` (bead crap-0w4.14). Nothing publishes before this passes.
2. Accounts, namespace, and key setup execute (below).
3. First public release is **1.0.0**. No 0.x version ever publishes. The first Portal submission of a new plugin id goes through manual review by a Gradle engineer (days), the mcp-contrast merge waits on it.
4. mcp-contrast merges the gate on published coordinates.

## Accounts, namespace, signing (one-time setup)

- **Central Portal** (central.sonatype.com) account. Namespace `com.architester` claimed by DNS TXT record on the apex `architester.com` (self-service, minutes, record may stay). Generate a **portal user token** (not the password), publishing auths with it.
- **Gradle Plugin Portal** account plus API key (`gradle.publish.key` / `gradle.publish.secret`).
- **Dedicated project PGP key** (not the maintainer's personal key), public key uploaded to keyserver.ubuntu.com. PGP `.asc` per file is mandatory for Central. Private key and passphrase live only in GitHub secrets, in-memory signing on the runner, no keyring file.
- Six GitHub secrets total (Central token pair, PGP key and passphrase, Portal key pair) in a protected **`release` environment** restricted to `v*` tag deployments.

## Automation

- Trigger: push of a `v*` tag runs the release workflow. Version comes from the tag, checked against `gradle.properties`.
- **Central tooling: `com.vanniktech.maven.publish`** (0.37.x). Handles sources/javadoc jars, POM completeness, in-memory signing, upload, and validation polling. Credentials via `ORG_GRADLE_PROJECT_mavenCentralUsername` / `Password`, key via `ORG_GRADLE_PROJECT_signingInMemoryKey` / `KeyPassword`. It also publishes Gradle plugin projects with markers correctly, which is what makes dual-publish nearly free.
- **Portal tooling: `com.gradle.plugin-publish`** (2.x), `./gradlew publishPlugins`, dry-run with `--validate-only`.
- Order: Central first, abort the Portal push on Central validation failure.
- The **first** Central release stays `USER_MANAGED` (released by hand from the portal UI, acts as the pipeline shakedown, a bad bundle is droppable before it goes live). Later releases auto-release. Published releases are immutable.
- The workflow also builds the fat jar, attests it, generates the SBOM, and creates the GitHub Release with those assets.

## Security posture

Contrast Security is the first consumer, the posture targets an enterprise allowlist review.

**Tier 1, part of the release path (bead crap-0w4.16):**

- Dependabot with `cooldown: default-days: 7` on both `gradle` and `github-actions` ecosystems (GA, version updates only, security updates are never delayed).
- `SECURITY.md` plus GitHub private vulnerability reporting enabled.
- Every workflow action pinned to a commit SHA, explicit least-privilege `permissions:` in every workflow.
- Repository **ruleset** protecting `v*` tags (legacy tag protection is sunset).
- **Immutable releases** enabled on the repo (GA 2025-10), assets frozen, tags unmovable, `gh release verify` works.
- Reproducible jars: `preserveFileTimestamps = false`, `reproducibleFileOrder = true`, explicit file permissions, pinned JDK vendor, in the convention plugin.
- **Build provenance attestation** on the release fat jar via `actions/attest@v4` (`id-token: write`, `attestations: write`), SLSA Build L2, consumers verify with `gh attestation verify crap4j-cli.jar --repo <owner>/open-crap4j`.
- **CycloneDX SBOM** (`org.cyclonedx.bom` plugin) attached to the GitHub Release. It machine-documents the zero-runtime-dependency claim. Central gets no SBOM classifier in v1.

**Tier 2, post-1.0 follow-ups (bead crap-0w4.19):**

- OpenSSF Scorecard action plus badge (`publish_results: true`), branch-protection ruleset, `permissions: read-all` top-level.
- OpenSSF Best Practices badge, passing tier (gold is structurally unreachable solo).
- Sigstore `.sigstore.json` bundles to Central via `dev.sigstore.sign` (optional there, PGP stays mandatory, ships ahead of mainstream).
- Gradle dependency-verification metadata in this repo.

## Naming clearance

Verified 2026-08-16. The original crap4j (Savoia/Evans, Agitar, EPL) is abandoned, last release 1.1.6 circa 2011, crap4j.org frozen since November 2011. Maven Central's only crap4j artifact is the Hudson CI plugin from 2010, the Gradle Plugin Portal has none, no trademark is registered on crap4j (Agitar's live marks are unrelated), and no other open-crap4j repo exists. Both registries restrict the namespace prefix, not the artifact word, so `com.architester.crap4j` and `crap4j-*` break no rule. This project is a clean-room reimplementation, no EPL code is reused, so the EPL imposes nothing. Courtesy: the README credits Savoia and Evans for the CRAP metric and the original crap4j.

## Superseded

Supersedes the "Consumption, local first, public publishing deferred" charting note on map crap-diy, publishing is now planned with a concrete trigger. Nothing in research/crap4j-research-findings.md covered publishing, nothing else is superseded.

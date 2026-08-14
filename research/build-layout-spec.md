# open-crap4j build layout

Locked 2026-08-13 by crap-diy.6. Settles module structure, build shape, TestKit setup, version management, and zero-dependency enforcement for this repo.

## Modules

Three subprojects in one Gradle multi-project build (Kotlin DSL, `rootProject.name = "open-crap4j"`). No composite build.

- **`core`** — XML parsing, lambda folding, scoring, baseline logic, report writers. All real logic lives here.
- **`cli`** — the `crap4j` command, subcommands and hand-rolled arg parsing over core.
- **`gradle-plugin`** — thin task wrappers over core, applied via `java-gradle-plugin`.

Directory names are plain (`core`, `cli`, `gradle-plugin`). Artifact ids are set now via `base.archivesName` to `crap4j-core`, `crap4j-cli`, `crap4j-gradle-plugin`, so the publishing ticket (crap-diy.8) inherits sane coordinates.

## Shared build logic

One `buildSrc` convention plugin, `crap4j.java-conventions`, applied by all three modules. It holds:

- Java toolchain 21, bytecode target 17 (`options.release = 17`).
- JUnit 5 + AssertJ test wiring. Test-scope dependencies do not violate ADR 0002.
- The zero-dependency check (below).
- JaCoCo applied with the XML report forced on. The dogfood step consumes these per-module reports.

## Zero-dependency enforcement

ADR 0002 becomes a build guarantee. A small verification task in the convention plugin resolves `runtimeClasspath` and fails when any resolved artifact is not a project artifact. Wired into `check` on all three modules (the CLI is also zero-dep in v1).

## CLI fat jar

A plain `Jar` task in `cli`, no Shadow plugin. It merges the runtime classpath (`from(zipTree)` over each entry, which is exactly `core`) and sets `Main-Class` in the manifest.

## Version management

One shared version in root `gradle.properties`, starting at `0.1.0`. Stays 0.x until mcp-contrast is gated end to end. Each jar manifest carries `Implementation-Version`, and `crap4j --version` reads `Package.getImplementationVersion()`, so no generated source file exists.

## TestKit setup

`gradle-plugin` uses `java-gradle-plugin` with a dedicated `functionalTest` source set registered through `gradlePlugin.testSourceSets`. Functional tests run in one serial class lane per Gradle version with `GradleRunner.withGradleVersion`, pinning **8.5** and **latest**. Gradle runs the two class lanes in parallel forks, while each lane stays serial to avoid TestKit daemon contention. The matrix lives inside the test code, so a local `./gradlew build` proves both versions, and CI needs one job.

The repo's own wrapper is the latest stable Gradle at scaffold time.

## Dogfooding

open-crap4j gates itself in CI by running the just-built CLI fat jar, once per module, against each module's own JaCoCo XML and a committed per-module baseline (per-module gating, ADR 0003). This exercises the CLI end to end on real data. It is a CI step only, never wired into the local `check` lifecycle.

## Repo CI

One GitHub Actions workflow, triggered on push to main and on pull requests. One Linux job, Java 21, Gradle's official caching action. Steps: build and test everything (unit tests, TestKit functional tests on both pinned Gradle versions, the zero-dependency check), then the dogfood step. Wider matrices (Windows, macOS, more JDKs) wait for a real problem.

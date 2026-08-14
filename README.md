# open-crap4j

A build gate that reads JaCoCo XML reports and fails when complex Java methods lack tests, scored by the CRAP metric.

CRAP stands for Change Risk Anti-Patterns. The formula follows.

```
CRAP(m) = cc(m)² × (1 − coverage(m))³ + cc(m)
```

A simple method with no tests scores low. A complex method with no tests scores high. CRAP scales the coverage demand with complexity, so simple getters pass untested while a 15-branch parser must prove its branches are exercised.

## Quick start

### CLI

Requires Java 17+. Download `crap4j-cli-0.1.0.jar` from Releases.

```sh
# Gate a JaCoCo XML report (exit 2 on violations)
java -jar crap4j-cli-0.1.0.jar check --report build/reports/jacoco/test/jacocoTestReport.xml

# Advisory report (never fails)
java -jar crap4j-cli-0.1.0.jar report --report build/reports/jacoco/test/jacocoTestReport.xml
```

### Gradle plugin

Apply the plugin in your module's `build.gradle.kts`. Requires Gradle 8.5+.

```kotlin
plugins {
    id("com.architester.crap4j") version "0.1.0"
}
```

The plugin auto-applies `jacoco`, forces `jacocoTestReport` to produce XML, and wires all tasks to depend on it. Run the gate.

```sh
./gradlew crapCheck
```

By default `crapCheck` does not attach to `check`. To wire it in, set `attachToCheck`.

```kotlin
crap4j {
    attachToCheck.set(true)
}
```

## Baseline adoption workflow

A codebase with existing debt can adopt the gate in one session.

**1. Create a baseline.** Run once to snapshot current violations as baselined debt.

```sh
# CLI
java -jar crap4j-cli-0.1.0.jar baseline --report build/reports/jacoco/test/jacocoTestReport.xml

# Gradle
./gradlew crapBaseline
```

This writes `crap4j-baseline.json` in the working directory (CLI) or project directory (Gradle). Commit it.

**2. Gate PRs.** `check` (or `crapCheck`) now passes baselined methods with a warning, and fails only on new or worsened violations.

**3. Tighten over time.** As the team fixes methods, slack entries build up in the baseline. Tighten removes them and lowers stored scores that sit above today's values. It never adds entries and never raises numbers.

```sh
# CLI
java -jar crap4j-cli-0.1.0.jar tighten --report build/reports/jacoco/test/jacocoTestReport.xml

# Gradle
./gradlew crapBaselineTighten
```

Commit the result. Review the diff to confirm only removals and reductions.

**4. Re-baseline (deliberate).** If you need to re-admit debt that got worse, run `baseline` again. This shows up in code review as a full rewrite of the baseline file, so the decision is visible.

## Defaults and threshold-cap coupling

The tool has two independent thresholds.

| Setting | Default | Meaning |
|---|---|---|
| CRAP threshold (T) | 15.0 | CRAP score above which a method is a violation |
| Complexity cap (C) | 15 | Cyclomatic complexity above which a method is a violation regardless of coverage |

Because CRAP is always at least equal to cc, the two settings are coupled. The useful range is C <= T < C² + C.

- If T >= C² + C, the CRAP threshold can never fire on any method the cap allows. The tool warns that the gate has become a plain complexity checker.
- If T < C, the CRAP threshold becomes a hidden complexity cap. The tool warns about this too.

The defaults T=15 and C=15 follow the strictest pairing (T = C), which requires 100% branch coverage at the cap and a smooth ramp below it. Some concrete coverage demands at T=15.

| cc | Minimum branch coverage to pass |
|---|---|
| 3 | 0% (CRAP = 12, below threshold) |
| 5 | ~26% |
| 8 | ~52% |
| 10 | ~63% |
| 15 | 100% |

## CLI reference

```
Usage: crap4j <verb> [options]

Verbs:
  check     Gate the report and exit 2 on violations
  report    Render an advisory report
  baseline  Write or replace a baseline
  tighten   Shrink an existing baseline
```

Exit codes. 0 = pass (or advisory), 1 = usage/input error, 2 = violations found.

### Shared flags

| Flag | Default | Description |
|---|---|---|
| `--report <path>` | (required) | Path to the JaCoCo XML report |
| `--threshold <double>` | 15.0 | CRAP threshold |
| `--complexity-cap <int>` | 15 | Complexity cap |
| `--baseline <path>` | `crap4j-baseline.json` | Baseline file path |
| `--exclude <glob>` | | Path glob to exclude (repeatable) |
| `--exclude-class <regex>` | | Class name regex to exclude (repeatable) |
| `--use-default-exclusions <true\|false>` | true | Apply built-in exclusions for generated code |

### Verb-specific flags

| Flag | Verbs | Description |
|---|---|---|
| `--changed-files <path\|->` | check, report | Restrict analysis to methods in listed source files |
| `--require-tight-baseline` | check, report | Fail on slack baseline entries |
| `--advisory` | check | Report violations but exit 0 |
| `--show-passing <N>` | check, report | Show the N highest-scoring passing methods |
| `--json-report <path\|->` | check, report | Write JSON report to a file or stdout (`-`) |
| `--junit-report <path>` | check, report | Write JUnit XML sidecar for CI test-report UIs |

### Default exclusions

When `--use-default-exclusions` is true (the default), these patterns are excluded.

- Path glob `**/generated/**`
- Class regexes `.*MapperImpl$`, `^Dagger.*`, `^Hilt_.*`, `^AutoValue_.*`

JaCoCo XML carries no annotation data, so annotation-based exclusion (`@Generated`) does not work.

## Gradle plugin reference

### Extension properties

```kotlin
crap4j {
    threshold.set(15.0)                // CRAP threshold
    complexityCap.set(15)              // complexity cap
    jacocoXml.set(file("..."))         // auto-wired to jacocoTestReport XML
    baseline.set(file("..."))          // convention: crap4j-baseline.json
    advisory.set(false)                // true = never fail
    attachToCheck.set(false)           // true = check depends on crapCheck
    requireTightBaseline.set(false)    // true = slack entries fail
    excludes.set(listOf("..."))        // path globs
    excludeClasses.set(listOf("...")) // class regexes
    useDefaultExclusions.set(true)     // built-in generated-code exclusions
    formats {
        json.set(true)                 // JSON report in build/reports/crap4j/
        junitXml.set(true)             // JUnit XML sidecar
    }
}
```

### Tasks

| Task | Group | Description |
|---|---|---|
| `crapCheck` | verification | Gate the report and fail on violations |
| `crapReport` | verification | Advisory report, never fails |
| `crapBaseline` | verification | Write or replace the baseline |
| `crapBaselineTighten` | verification | Shrink the baseline |

All tasks depend on `jacocoTestReport` when the `java` plugin is applied.

## CI recipes

### GitHub Actions with JUnit sidecar

```yaml
- name: CRAP gate
  run: ./gradlew crapCheck

- name: Publish CRAP results
  if: always()
  uses: dorny/test-reporter@v1
  with:
    name: CRAP results
    path: build/reports/crap4j/crapCheck/junit.xml
    reporter: java-junit
```

### Changed-file mode (PR-only gating)

Score only the methods that changed in a PR. Pipe the diff file list into `--changed-files -`.

```yaml
- name: CRAP gate (changed files only)
  run: |
    git diff --name-only origin/main... \
      | java -jar crap4j-cli-0.1.0.jar check \
          --report build/reports/jacoco/test/jacocoTestReport.xml \
          --changed-files -
```

If none of the changed files appear in the coverage report, the tool exits 0.

### Multi-module combined gate

Each Gradle module gets its own `crapCheck` by default, reading its own JaCoCo XML. This is the recommended setup.

For a single combined gate across modules, use `jacoco-report-aggregation` to produce one merged XML, then point one `crapCheck` at it. The tool never merges reports itself (see [ADR 0003](docs/adr/0003-one-gate-reads-one-report.md)). Produce the combined XML at the JaCoCo execution-data level, not by merging XML files, because XML-level merging cannot recover the true union of branch coverage.

```kotlin
// In a dedicated :quality module or the root project
plugins {
    id("jacoco-report-aggregation")
    id("com.architester.crap4j")
}

dependencies {
    jacocoAggregation(project(":core"))
    jacocoAggregation(project(":app"))
}

crap4j {
    jacocoXml.set(
        layout.buildDirectory.file(
            "reports/jacoco/testCodeCoverageReport/testCodeCoverageReport.xml"))
    attachToCheck.set(true)
}
```

## Adoption guidance

### Per-module gating (default)

Apply the plugin in each module that should be gated. Each module maintains its own `crap4j-baseline.json`. This keeps baselines small and lets teams adopt at their own pace.

### What adoption looks like in practice

On [mcp-contrast](research/mcp-contrast-case-study.md) (2 modules, 430 methods, 98% line coverage), the gate at defaults (T=15, C=15) found:

- 0 methods over the complexity cap
- 2 methods over the CRAP threshold
- Two small baselines (one per module), 2 entries total

The top offender, `registerKnownTags` at cc=14 and 50% branch coverage (CRAP 38.5), was invisible to the 98% line coverage number. No aggregate coverage gate would have surfaced it. The CRAP gate caught it immediately.

## What this tool is not

**Bytecode complexity is not source complexity.** JaCoCo counts complexity from compiled bytecode. The compiler rewrites code before JaCoCo sees it. A `switch` on strings becomes two switches, `try-with-resources` adds hidden branches. Our cc numbers often run higher than Checkstyle or SonarQube counts from source. This is fine for gating because the inflation is systematic and the baseline compares the tool against itself, but do not compare cc values across tools.

**Coverage shows execution, not assertion.** A test that calls a method but never checks the result still counts as coverage. CRAP inherits this from JaCoCo. If you need to verify that tests actually check behavior, use mutation testing ([pitest](https://pitest.org/)).

**An outdated report describes old code.** The tool trusts the XML it is given. If tests did not re-run after a code change, the report is outdated. The Gradle plugin handles this by depending on `jacocoTestReport`. The CLI warns when a changed source file is newer than the report.

**JaCoCo only.** Projects using a different coverage tool are out of scope.

**No annotation-based exclusion.** JaCoCo XML does not carry annotation data, so `@Generated` with `SOURCE` retention is invisible. Use path globs (`--exclude`) and class regexes (`--exclude-class`) instead.

## How CRAP fits with other gates

CRAP replaces per-method coverage rules. A flat per-method floor picks one number for every method, too high for simple methods (noise, junk tests) or too low for complex ones (misses the risk). CRAP scales the demand with complexity.

CRAP does not replace an overall coverage number. It ignores simple methods on purpose, so it will not catch a slow erosion of test coverage across trivial code. Keep a modest overall floor (around 80%) or gate coverage on changed lines only.

The recommended stack, each gate owning one job.

1. **Complexity cap** stops new complexity at the door
2. **CRAP with a baseline** makes sure complex code is tested, old debt baselined
3. **Modest overall or changed-lines coverage floor** stops simple code from rotting

## License

Apache-2.0. See [LICENSE](LICENSE).

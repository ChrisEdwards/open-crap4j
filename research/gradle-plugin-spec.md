# Gradle Plugin Spec

Locked 2026-08-13 by the crap-diy.5 grilling session. This supersedes research doc section 5.4. It also amends the vocabulary of research/report-and-baseline-spec.md, prune becomes tighten, regenerate becomes re-baseline, stale becomes slack. The amendment note in that file carries the details.

## Identity

- Plugin id `com.architester.crap4j`. Matches the base package. Users never type the "open-" brand name in a build file.
- Extension block `crap4j { }`.
- Minimum Gradle **8.5**. TestKit tests pin 8.5 and the latest release.
- Configuration-cache compliant. Lazy `Property`/`Provider` types throughout, no `Project` access at execution time, every task declares its inputs and outputs.

## Extension

```kotlin
crap4j {
    threshold.set(15.0)                 // Property<Double>, convention 15.0
    complexityCap.set(15)               // Property<Int>, convention 15
    jacocoXml.set(...)                  // RegularFileProperty, convention: this module's jacocoTestReport XML
    baseline.set(...)                   // RegularFileProperty, convention: projectDir/crap4j-baseline.json
    advisory.set(false)                 // Property<Boolean>, convention false
    attachToCheck.set(false)            // Property<Boolean>, convention false
    requireTightBaseline.set(false)     // Property<Boolean>, convention false
    formats {
        json.set(true)                  // Property<Boolean>, convention true
        junitXml.set(true)              // Property<Boolean>, convention true
    }
    excludes.set(listOf(...))           // ListProperty<String>, path globs
    excludeClasses.set(listOf(...))     // ListProperty<String>, class-name regexes
    excludeAnnotations.set(listOf(...)) // ListProperty<String>, annotation simple names
    useDefaultExclusions.set(true)      // Property<Boolean>, convention true
}
```

- **`jacocoXml` is a single file.** One gate reads one report, everywhere. Combining coverage is JaCoCo's job, done correctly at the execution-data level, never ours at the XML level (see ADR 0003). The reserved per-method `module` field in the JSON report stays unwritten in v1.
- **Baseline convention with graceful absence.** If the conventional file exists it is used, if not the gate runs baseline-less. An *explicitly configured* path that is missing is an error, silence there would hide a typo.
- **No changed-file mode in the plugin.** The CLI owns it. Baseline gating already keeps whole-repo runs PR-safe.
- The exclusion knobs and their built-in generated-code defaults are core semantics shared with the CLI, defined in research doc section 2.
- The text summary always prints to the console. The `formats` block governs report files only.

## Tasks

All tasks register in the `verification` group. `crapCheck`, `crapReport`, and `crapBaseline` share one analysis engine and differ in what they do with the result.

- **`crapCheck`** applies baseline and thresholds, writes every configured report file, and fails the build on violations, pass or fail the files are written, CI wants the artifacts of the failed run. With `advisory` true it reports violations and passes. `check` depends on it only when `attachToCheck` is true.
- **`crapReport`** runs the same analysis, writes the same reports, never fails. For report purposes it is a permanently advisory run, its JSON gets `advisory: true` and status `pass` or `advisory`. The locked rule "`fail` appears only when the build really failed" forces this, which refines the round-1 Q3 aside that crapReport would not carry advisory semantics, the *intent* differs from the advisory rollout posture but the report file shape is the same.
- **`crapBaseline`** writes the baseline file from today's code, first run or re-baseline. Re-running is the deliberate, reviewed act because it can re-admit debt that got worse.
- **`crapBaselineTighten`** removes slack, deletes slack entries and lowers stored numbers, never adds an entry, never raises a number.

### Task wiring and up-to-date checks

- Analysis tasks depend on the task that produces `jacocoXml`. The convention wires `dependsOn(jacocoTestReport)`. A user pointing `jacocoXml` at another task's output via a provider carries that dependency automatically.
- `jacocoXml` and the baseline are `@InputFile`s on analysis tasks, report files are `@OutputFile`s. `crapBaseline` and `crapBaselineTighten` treat the baseline as their output.
- **Each task owns its output directory**, `build/reports/crap4j/<taskName>/report.json` and `junit.xml`. Two tasks declaring the same output file would break up-to-date checking. Each format's output path is a `RegularFileProperty` on the task with that convention, so paths are settable per task.
- The JUnit sidecar stays out of `build/test-results/`, that directory belongs to `Test` tasks and CI configs take an explicit path anyway.

## JaCoCo wiring

Applying the plugin to a project with the `java` plugin does three things.

1. Applies the `jacoco` plugin if absent.
2. Forces `xml.required = true` on `jacocoTestReport`.
3. Wires the dependency described above.

Applying to a project without the `java` plugin wires no conventions, set `jacocoXml` explicitly. That is the aggregator-project case.

## Multi-module builds

- **Per-module gating is the default.** Each module applies the plugin and gates its own report against its own baseline. mcp-contrast adopts this way, two modules, two small baselines.
- **One combined gate is user wiring, not plugin magic.** Produce one merged XML with JaCoCo's own machinery, `jacoco-report-aggregation` at an aggregator project, or a `JacocoReport` task fed multiple execution-data files within a module, then point that project's `jacocoXml` at the merged report and keep one baseline there. The plugin never crawls subprojects, that pattern fights the configuration cache and modern Gradle.

## Slack and tight

This vocabulary replaces "stale", amended into the report spec and glossary.

- A baseline entry has **slack** when tightening would change it. Three cases, the method is gone (`method-gone`), the method now passes both gates on its own (`under-limits`), or the stored numbers sit meaningfully above what the method scores today (`excess-allowance`, stored crap exceeds current crap by more than the 0.05 epsilon, or stored complexity exceeds current complexity, integers compare exactly).
- A baseline with no slack is **tight**. Tight means exactly "crapBaselineTighten would change nothing", the epsilon guard keeps coverage wobble from flapping that state.
- **`requireTightBaseline`** false, slack entries warn and are listed in the report. True, every slack entry counts as a violation, including for the top-level status, and the failure message names the remedy, "Baseline is not tight, run crapBaselineTighten."

## Deliberately deferred

- Changed-file support in the plugin. A list-of-files property can be added compatibly later.
- Writing the per-method `module` field.
- Convenience sugar that auto-detects `jacoco-report-aggregation` and wires the merged report.
- JUnit XML sidecar semantics (what is a testcase, what is a failure, advisory behavior) belong to their own ticket.

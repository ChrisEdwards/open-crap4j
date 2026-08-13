# open-crap4j Research Findings and Design Summary

Date 2026-08-12. Sources include a clone of `fabian-barney/crap-java` (v0.6.2, read at `/tmp/crap-java`), the original crap4j articles, and tool documentation for pitest, Checkstyle, SonarQube, and several baseline-file linters.

## 1. State of this repo

`open-crap4j` is empty. No code, no README, no build files, not yet a git repository. This is a greenfield project. Nothing constrains the design except the requirements document.

## 2. What crap-java actually is

The requirements doc frames crap-java as the most feature-complete existing tool. That holds up, but three claims in the requirements need correction or nuance after reading the source.

**Its default threshold is 6.0, not 8.0.** `Thresholds.java` sets `DEFAULT = 6.0` and warns when the user sets a value below 4.0 (too noisy) or above 8.0 (too lenient, in its view). The math argument in the requirements survives either way. Because CRAP >= cc always, a threshold of 6.0 fails every method with cc >= 7 even at 100% coverage. That makes the CRAP threshold double as a hidden complexity cap, which is the design flaw we should fix.

**It computes complexity from source, not from JaCoCo.** crap-java has its own Java parser (`JavaMethodParser`) that walks the source AST and counts decision points. JaCoCo's COMPLEXITY counter is not used. As a consequence it must ship and maintain a parser, and it punts on hard cases. Lambda bodies are excluded from the enclosing method's complexity, and synthetic `lambda$...$N` methods in the JaCoCo XML are dropped on the floor (`JacocoCoverageParser` line 77). Anonymous classes are also out of scope. Our requirements take the opposite approach (JaCoCo COMPLEXITY counter plus lambda folding), which needs no parser, works for any JVM language, and attributes lambda risk to the method that owns it. This is the headline design divergence.

**Its coverage selection differs from our requirements.** crap-java takes the *lower* of instruction coverage and branch coverage (`CoverageData.effectiveCoverage()`). Our requirements say branch coverage when the BRANCH counter exists, instruction coverage as fallback. Both are defensible. Lower-of is more pessimistic and can shift scores when instruction coverage lags branch coverage. Whichever we pick, scores are not comparable across the two tools, and the choice must be recorded in the baseline file format so a future change does not silently invalidate baselines. Recommendation, follow the requirements (branch preferred, instruction fallback) and keep crap-java's good idea of reporting a `covKind` per method.

**It runs the build itself in CLI mode.** The crap-java CLI detects Maven or Gradle, deletes stale JaCoCo artifacts, and spawns `mvn`/`gradlew test jacocoTestReport` before reading the XML (`CoverageRunner`, `ProcessCommandExecutor`). Only its Maven plugin consumes existing reports. Our requirements explicitly want read-only consumption of an existing XML report. That removes an entire subsystem (process execution, timeouts, build-tool detection) and is the right call.

**Its `--changed` mode is git-status based.** `ChangedFileDetector` shells out to `git status --porcelain` and picks up uncommitted `.java` files. That answers "what did I touch locally" but not "what changed in this PR versus the merge base," which is the CI question. Our requirements' design (accept an explicit list of changed file paths, let CI compute it with `git diff --name-only $BASE`) is simpler and more correct. Keep git out of the core.

### crap-java features worth keeping

- **`covKind` per method.** Report whether branch or instruction coverage fed each score. Cheap and useful for trust in the numbers.
- **Three-tier exclusions with sane defaults.** Path globs (`--exclude`), class-name regexes (`--exclude-class`), and annotation names (`--exclude-annotation`), plus built-in generated-code defaults (paths containing `generated`, `*MapperImpl`, `Dagger*`, `Hilt_*`, `AutoValue_*`, any `@Generated` annotation) that the user can switch off with one flag. Generated code is the number one source of CRAP false positives.
- **JUnit XML sidecar.** Emitting each method as a testcase (fail when over threshold) makes GitLab's Tests tab and GitHub JUnit actions render results with zero custom tooling. crap-java always allows the sidecar alongside any primary format.
- **`--format none` plus exit code.** Lets CI use the gate without parsing output.
- **Split exit codes.** 0 pass, 1 usage error, 2 threshold violation. CI can distinguish "you invoked it wrong" from "your code failed the gate."
- **`--failures-only` and an agent-friendly trimmed output.** The `--agent` composite flag (compact format, failures only, drop redundant fields) is aimed at AI coding agents reading the report. Worth keeping as an idea, though we can fold it into a compact JSON rather than adopting the TOON format.
- **Inline option assignment (`--format=json`), repeatable options, duplicate-option errors.** Small CLI hygiene details that make the tool pleasant.
- **Gradle wiring pattern.** Apply the `jacoco` plugin if absent, force the XML report on (`report.getReports().getXml().getRequired().set(true)`), make the check task `dependsOn(test, jacocoTestReport)`, register under the `verification` group, and aggregate subprojects when applied to the root project. All of this transfers directly.
- **Threshold sanity warnings.** Warning on thresholds that are likely too noisy or too lenient is a good UX touch, even though we disagree with crap-java about where those lines sit.

### crap-java features to drop

- The embedded Java source parser and its complexity model.
- The build-spawning coverage runner and build-tool autodetection.
- Git-status changed-file detection inside the tool.
- The TOON output format (niche, JSON serves the same consumers).
- The hyphenated task name `crap-java-check`. Gradle convention is camelCase (`crapCheck`).

### What crap-java lacks (our gaps to fill)

1. **No baseline mechanism at all.** One global threshold, all-or-nothing. A legacy codebase cannot adopt it without either a huge cleanup or a meaninglessly high threshold. Baseline-aware gating is our main differentiator.
2. **No separate complexity cap.** The CRAP threshold silently acts as one (see above).
3. **No merge-base changed-file mode.** Only uncommitted-file detection.
4. **No CSV output** for spreadsheet triage.
5. **No project-level summary metrics** such as the original CRAP load or percent-crappy-methods.
6. **Lambda risk disappears.** Synthetic lambda methods are ignored, so an untested 20-branch lambda contributes nothing to its enclosing method's score.
7. **Questionable license provenance**, which is the reason this project exists.

## 3. The original CRAP4J concept (Savoia and Evans, 2007)

- The original threshold was **30**, picked after debate and explicitly flagged as experimental. At 30, a fully covered method passes up to cc = 30, and a totally uncovered method passes up to cc = 5.
- Coverage was **basis-path coverage**, not line or branch coverage. JaCoCo gives us branch coverage, which is the closest widely available stand-in. Another reason scores from different tools do not compare.
- Beyond the per-method score it reported **CRAP load** (a rough count of tests you would need to write to get under the threshold, summed per project) and a **project-level crap percentage** with a suggested cap of 5% of methods over threshold.
- Reports were sortable by complexity, coverage, CRAP, and CRAP load.

Takeaways for us. A project-level summary (count and percent of methods over threshold, total CRAP load) is cheap to compute and gives teams a trend number to watch. And the original authors set the threshold far above 6.0 or 8.0.

## 4. How neighboring tools handle thresholds, baselines, and gradual adoption

### PIT (pitest)

All enforcement ships off. `mutationThreshold`, `coverageThreshold`, and `testStrengthThreshold` are unset by default, and each is an opt-in percentage that fails the build when the score drops below it. `thresholdPrecision` defaults to 0 decimal places, which leaves a blind spot where a score can regress inside one percentage point, a lesson for our epsilon design.

PIT's "history" files (`historyInputLocation` / `historyOutputLocation`, `withHistory`) are a performance cache, not a baseline. Entries invalidate when code changes, and nothing grandfathers surviving mutants. PIT has no baseline mechanism at all. Gradual adoption in that ecosystem comes from scoping the analysis to changed code instead. The commercial arcmutate extension (successor to pitest-git) mutates only code changed between two git refs (`from`/`to`, with special values for uncommitted changes), defaults to line-level scope, and auto-relaxes "no mutations found" to a pass so no-change builds succeed. That validates our changed-file mode and our clean no-op on an empty changed set. The absence of any grandfathering mechanism in PIT confirms baseline-aware gating as a real gap in this tool space.

### Checkstyle CyclomaticComplexity

Default `max` is **10**, with `switchBlockAsSingleDecisionPoint` (default false) to count a whole switch as one decision. Suppressions are hand-authored, an XML `SuppressionFilter` file matching on files, checks, lines, or message, plus `@SuppressWarnings("checkstyle:...")` via a filter pair. There is no generate-a-baseline command. The closest thing is maven-checkstyle-plugin's `maxAllowedViolations` (default 0), a crude tolerance count that cannot distinguish old debt from new. Severity levels (`ignore`/`info`/`warning`/`error`) matter because the Maven plugin only fails on `error` by default, so demoting a check to warning makes it report-only, which is the same role as our `--advisory` flag.

### SonarQube

Complexity and coverage stay separate metrics, there is no CRAP-like combination (JaCoCo upstream also declined CRAP as out of scope). Per-method enforcement happens through rules, cognitive complexity `S3776` with default threshold **15** (active by default) and cyclomatic complexity `S1541` with default **10** (not active by default). The 15 default on the active rule is a useful anchor for our complexity cap. Gradual adoption is the "Clean as You Code" model, quality gates apply only to *new code* (defined by previous version, days, reference branch, or a specific analysis, and in PRs everything changed counts as new). Old code is simply never examined. That is the time-window approach to the same problem our baseline solves with an explicit file. The baseline approach has an advantage for a CLI tool, it needs no server and no history, and the debt list is reviewable in the repo.

### Baseline-file precedents in linters

- **Android Lint** (`lint-baseline.xml`). First run writes the file, only non-baselined issues are reported afterward. Stale entries never fail the build, lint just prints an informational note suggesting regeneration. Regeneration means deleting the file and re-running.
- **detekt** (baseline XML). Two sections, `CurrentIssues` (grandfathered) and `ManuallySuppressedIssues` (permanent false positives). Generated by a dedicated `detektBaseline` Gradle task, which is the naming precedent for our `crapBaseline` task. Stale-entry handling is undocumented, entries sit until regeneration.
- **PHPStan** (`phpstan-baseline.neon`). Generated with `--generate-baseline`. Strictest stale policy, a fixed error leaves an unmatched ignore entry that PHPStan reports as an error by default, so the baseline can only shrink. A ratchet.
- **ESLint bulk suppressions** (`eslint-suppressions.json`). Generated with `--suppress-all`, records violation counts per file per rule. Stale (unused) suppressions make ESLint exit non-zero, and `--prune-suppressions` removes only the stale entries without re-suppressing anything new. That prune-versus-regenerate distinction is the most refined update workflow of the group.

Two stale-entry philosophies emerge. PHPStan and ESLint fail on stale entries (ratchet), Android Lint and detekt tolerate them. Our recommendation below takes warn-by-default with an optional strict ratchet mode, plus an ESLint-style prune.

## 5. Recommended design

### 5.0 Why build on JaCoCo XML, pros and cons

Every prior tool needed a complexity source and picked a different painful one. The original crap4j predates JaCoCo and built its own instrumentation. crap-java wrote its own Java source parser, which is a big share of its code, has to chase every new Java syntax feature forever, and punts on lambdas and anonymous classes. We read the JaCoCo XML the build already produces and take complexity from its COMPLEXITY counter.

Pros.

- The build already makes the file. No test re-run, no new instrumentation, near-zero CI cost, and the Gradle plugin stays thin.
- No parser to build or maintain. New Java syntax works the day it ships, because javac compiles it and JaCoCo counts the bytecode.
- Complexity and coverage come from the same place, the compiled code that ran. Source-based complexity plus bytecode-based coverage can disagree about what a method even is. One source removes that whole class of matching bugs and makes lambda folding possible, which is how we fix crap-java's biggest gap.

Cons, to document honestly.

- **Bytecode complexity is not source complexity.** The compiler rewrites code before JaCoCo sees it. A switch on strings becomes two switches, try-with-resources and finally add hidden branches. JaCoCo's number often runs higher than Checkstyle's or a human's count, so our scores will not match crap-java or SonarQube. Tolerable for a gate because the inflation is systematic and the baseline compares the tool against itself, but the docs must say that cc here is not cc in Checkstyle.
- **Stale report risk.** The tool trusts the XML it is handed. If tests did not re-run, the report describes old code (seen in the mcp-contrast case study, one module was UP-TO-DATE). The Gradle plugin fixes this by depending on the report task. The CLI should warn when the XML is older than the class files.
- **Compiler-generated noise.** Synthetic methods and branches leak into reports. JaCoCo filters most, lambda folding and exclusion defaults handle more, but oddities will surface, especially on Kotlin where the compiler generates many hidden null-check branches.
- **Locked to JaCoCo's format.** Projects on a different coverage tool get nothing. Fine for the target fleet (Java, Gradle, JaCoCo), but it caps the audience.

### 5.1 Output formats

Recommend three writers plus a sidecar, all available from CLI and Gradle.

- **text** (default for humans). Aligned table sorted by CRAP descending, plus a summary block (methods analyzed, violations, warnings, baseline hits, CRAP load).
- **json** (default machine format). Top-level `status`, `threshold`, `complexityCap`, `coverageSelection`, tool version, summary block, then a `methods` array with `class`, `method`, `desc` (JVM descriptor), `src`, `line`, `cc`, `cov`, `covKind`, `crap`, `status` (`ok`, `warned-baselined`, `violation`, `skipped`). JSON wins over CSV as the primary machine format because it can carry the summary and nested structure.
- **csv** (secondary machine format). Flat per-method rows for spreadsheets. Cheap to add and the requirements ask for CSV or JSON, so offer both.
- **JUnit XML sidecar** (opt-in flag, on by default in the Gradle plugin). For CI test-report UIs, mirroring crap-java's most practically useful integration.

`--format none` stays available for exit-code-only use.

### 5.2 Default thresholds

Two independent knobs, as the requirements ask.

- **CRAP threshold, default 15.** Rationale. The threshold must be read together with the identity CRAP >= cc. At 6.0 or 8.0 the CRAP gate is mostly a complexity gate in disguise. At the original 30 an uncovered cc = 5 method still passes, which is lax for new code. 15 sits between them. Concretely, at threshold 15 an uncovered method fails from cc = 4 up, a cc = 5 method needs about 26% branch coverage, cc = 8 needs about 52%, cc = 10 needs about 63%, and cc = 15 needs 100%. That is a sensible ramp, small simple methods pass untested, moderately complex methods need real coverage.
- **Complexity cap, default 15, checked separately.** This is the explicit "too complex no matter what" gate that the CRAP threshold was accidentally providing. Checkstyle's default max for CyclomaticComplexity is 10, but its own ecosystem treats that as aggressive, and SonarQube leaves the cyclomatic rule (default 10) inactive while its active complexity rule S3776 defaults to 15. A cap of 15, with the option to tighten, matches the one default that major tools actually enforce. Because baseline gating grandfathers existing methods, a moderately strict default is affordable.
**The two knobs are coupled.** The worst CRAP score a method can reach under a complexity cap C is C² + C, a fully uncovered method at the cap. Two failure modes follow.

- If T >= C² + C, the CRAP gate can never fire on any method the cap allows. The tool is then a plain complexity checker and the CRAP machinery is dead weight.
- If T < C, the CRAP threshold becomes the effective complexity limit (CRAP >= cc always), and the configured cap is dead weight instead.

So the sane range is C <= T < C² + C. Within it, lower T demands more coverage. T = C is the strictest sensible pairing, it requires 100% branch coverage at the cap and a smooth ramp below it. Our recommended defaults (T = 15, C = 15) follow that pairing. A mismatched pair asks too little even when it is not fully vacuous. With C = 6 and T = 15 the gate only fails cc 4 to 6 methods that are nearly untested (a cc = 6 method passes at 37% coverage). With C = 6, a threshold near 8 makes the ramp real (cc = 6 then needs 62%).

This coupling also rehabilitates crap-java's 6.0 default. It is not wrong, it is a threshold tuned for a team that already keeps complexity very low. It only misbehaves when used without a cap, where it silently acts as a cc <= 6 limit.

- Replace crap-java's fixed sanity warnings (below 4.0, above 8.0) with cap-derived ones. Warn at startup when T >= C² + C (CRAP gate unreachable, tool degenerates to a complexity checker) and when T < C (threshold is a hidden complexity cap). Suggest T = C as the default pairing when the user sets only one knob.

### 5.3 CLI shape

```
open-crap4j --report build/reports/jacoco/test/jacocoTestReport.xml \
            [--changed-files <file-or-stdin>] \
            [--threshold 15] [--complexity-cap 15] \
            [--baseline .crap4j-baseline.json] [--write-baseline] \
            [--format text|json|csv|none] [--output <path>] \
            [--junit-report <path>] \
            [--exclude <glob>]... [--exclude-class <regex>]... \
            [--exclude-annotation <name>]... [--use-default-exclusions=true|false] \
            [--advisory]
```

Notes. `--report` is repeatable for multi-module aggregation. `--changed-files` takes a file with one path per line (or `-` for stdin) rather than N positional args, which plays better with `git diff --name-only origin/main... > changed.txt`. `--advisory` reports violations but exits 0, for a warn-only rollout phase. Exit codes follow crap-java, 0 pass or advisory, 1 usage or input error, 2 violations.

### 5.4 Gradle plugin API

```kotlin
plugins { id("io.github.<org>.open-crap4j") }

crap4j {
    threshold.set(15.0)
    complexityCap.set(15)
    jacocoXml.set(layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.xml")) // convention wired automatically
    baseline.set(layout.projectDirectory.file("crap4j-baseline.json"))
    advisory.set(false)
    formats { json.set(true); csv.set(false); junitXml.set(true) }
    excludes.set(listOf("**/generated/**"))
    excludeClasses.set(listOf(".*MapperImpl$"))
    excludeAnnotations.set(listOf("Generated"))
    useDefaultExclusions.set(true)
    changedFilesProvider.set(providers.of(GitDiffSource::class) { ... }) // optional
}
```

Tasks.

- **`crapCheck`** reads the JaCoCo XML the build already produced, applies baseline and thresholds, fails the build on violations. `dependsOn(jacocoTestReport)`, wired so `check` depends on it only if the user opts in (`crap4j.attachToCheck.set(true)`), because a metric gate that self-inserts into `check` surprises people.
- **`crapReport`** same analysis, never fails, writes the reports. Useful for dashboards.
- **`crapBaseline`** regenerates the baseline file from the current state and writes it to the configured location.

Implementation notes carried over from crap-java. Apply `jacoco` if missing, force the XML report required flag, register tasks in the `verification` group, use `Property`/`RegularFileProperty` throughout for configuration-cache compatibility, and support root-project application that aggregates subproject reports. Unlike crap-java, tasks should declare the JaCoCo XML as `@InputFile` and reports as `@OutputFile` so up-to-date checks work.

### 5.5 Baseline file design

Purpose. Existing debt does not block PRs, new or worsened debt does.

**Format.** JSON, checked into the repo, human-reviewable in diffs.

```json
{
  "version": 1,
  "tool": "open-crap4j",
  "coverageSelection": "branch-preferred",
  "generated": "2026-08-12T00:00:00Z",
  "threshold": 15.0,
  "complexityCap": 15,
  "entries": [
    { "class": "com.example.OrderService", "method": "processOrder",
      "desc": "(Ljava/lang/String;I)V", "crap": 92.4, "cc": 12 }
  ]
}
```

**Method identity.** `class + method + desc` (the JVM descriptor). Descriptors disambiguate overloads and are stable across edits. Line numbers are not part of identity because they churn on every unrelated edit. Lambda folding happens before baselining, so baseline entries always name real source methods.

**Score comparison.** Store `crap` rounded to one decimal and compare with a small epsilon (0.05). Coverage ratios wobble when unrelated code moves a shared branch, and a float-exact comparison would flap. Storing cc alongside lets the gate also flag "complexity grew even though CRAP did not" if we ever want that.

**Gating rules.**

1. Method over threshold, not in baseline, violation.
2. Method in baseline, current CRAP <= baselined CRAP + epsilon, pass with a warning ("baselined debt").
3. Method in baseline, current CRAP > baselined CRAP + epsilon, violation ("baselined debt got worse").
4. Baseline entry whose method no longer exists or is now under threshold, stale entry. Warn by default and suggest running `crapBaseline`. Offer a strict mode that fails on stale entries for teams who want the baseline to only shrink.

**Version and semantics stamps.** The file records the format version and the coverage-selection rule. If a future release changes scoring semantics, the tool detects the mismatch and asks for regeneration instead of emitting confusing violations.

**Workflow.** Adopt by running `crapBaseline` once and committing the file. From then on `crapCheck` gates PRs. Fixing a method leaves a stale entry that the next `crapBaseline` run removes. Regeneration is deliberate and shows up in code review as a diff to the baseline file.

**Precedent cross-check.** This design lines up with the linter baselines surveyed in section 4. The dedicated generate task follows detekt (`detektBaseline`). Warn-on-stale by default follows Android Lint, and the optional strict mode (`staleBaselineEntries = fail`) gives ratchet teams the PHPStan and ESLint behavior. Worth adding from ESLint, a prune option (`crapBaseline --prune` or a `crapBaselinePrune` task) that removes stale entries without admitting any new debt, since full regeneration would re-baseline anything that got worse since the last run. detekt's split between grandfathered debt and permanent false positives is not needed here, our exclusion flags already cover the false-positive case.

### 5.6 How CRAP fits with other gates

CRAP replaces per-method coverage rules. A flat per-method floor must pick one number for every method. That number is either too high for simple methods (noise, junk tests) or too low for complex ones (misses the risk). CRAP scales the demand with complexity, 0% at cc 3, about 37% at cc 6, about 83% at cc 14 with threshold 15. Same inputs, no noise. Teams adopting CRAP should drop per-method and per-class coverage minimums.

CRAP does not replace an overall coverage number. CRAP ignores simple methods on purpose, a cc 3 method with no tests scores 12 and passes. With CRAP alone, most of a codebase could slowly lose its tests and CRAP would never complain. Those tests still matter, they catch mistakes during refactoring. Keep a modest overall floor, around 80%, or gate coverage on changed lines only. Avoid very high floors like 95%, they force useless tests on trivial code.

A complexity cap alone does not solve the problem CRAP solves. A cap cannot ask for tests. Splitting a big untested method into small untested methods satisfies the cap while every untested branch survives the split.

The recommended stack, each gate owning one job:

1. Complexity cap, stops new complexity at the door
2. CRAP with a baseline, makes sure complex code is tested, old debt grandfathered
3. Modest overall or changed-lines coverage floor, stops the simple code from rotting

Shared blind spot to document honestly. Coverage only shows code ran, not that the test checks anything. A test with no asserts still counts. CRAP inherits this from the coverage data it reads. Mutation testing (pitest) is the tool for that gap, and the docs should say so rather than overclaim.

## 6. Open questions to discuss

- Should `crapCheck` attach to Gradle `check` by default or opt-in? (Draft says opt-in.)
- Should the baseline also grandfather complexity-cap violations, or only CRAP violations? Leaning both, same mechanism.
- Maven plugin timing. Requirements say "potentially." Suggest structuring core as a plain library (`core` + `cli` + `gradle-plugin` modules, like crap-java's layout) so a Maven mojo is a thin adapter later.
- Package/plugin id naming and license text (Apache-2.0 recommended, clean-room, no code from unclebob/crap4java or crap-java).

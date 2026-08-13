# JUnit XML Sidecar Spec

Locked 2026-08-13 by the crap-diy.9 grilling session. This resolves the
deferrals in research/gradle-plugin-spec.md, research/cli-spec.md, and the
sidecar mentions in research doc sections 5.1/5.4. File locations and flag
spellings were fixed by crap-diy.5 and crap-diy.4 and are unchanged here.

## Purpose and producers

The sidecar is a report in the JUnit XML format that CI systems (GitHub
Actions JUnit renderers, the GitLab Tests tab) display as test results with
no custom tooling. It renders the same analysis the JSON report records.
The JSON report stays the machine-faithful record, the sidecar is a display
artifact.

Producers: `crapCheck` and `crapReport` in Gradle (on by default via
`formats.junitXml`, written to `build/reports/crap4j/<taskName>/junit.xml`),
`crap4j check` and `crap4j report` in the CLI (opt-in via
`--junit-report <path>`). `baseline` and `tighten` never write one.

The file is written whenever it is enabled, pass or fail, empty or not. CI
wants the artifact of the failed run, and an empty suite distinguishes "ran,
nothing to score" from "never ran".

## Document shape

```xml
<?xml version="1.0" encoding="UTF-8"?>
<testsuites>
  <testsuite name="crap4j.crapCheck" tests="430" failures="2" errors="0" skipped="1" time="0">
    <testcase classname="com.contrastsecurity.mcp.SearchAppVulnerabilitiesTool"
              name="doExecute()" time="0">
      <failure type="crap-over-threshold"
               message="CRAP 18.52, cc 15, 75.0% branch, over the CRAP threshold 15.0"/>
    </testcase>
    ...
  </testsuite>
</testsuites>
```

- **Always the `<testsuites>` wrapper** around exactly one flat `<testsuite>`.
  One suite, not one per class, CI UIs group by `classname` themselves.
- **Suite name is `crap4j.<producer>`**, the Gradle task name
  (`crap4j.crapCheck`, `crap4j.crapReport`, custom-registered tasks use their
  own name) or the CLI verb (`crap4j.check`, `crap4j.report`). Unique names
  keep two ingested sidecars from colliding as duplicates in CI UIs.
- **Suite counts**: `tests` is the total row count, `failures` and `skipped`
  count their elements, `errors` is always `"0"`.
- **`time="0"`** on the suite and every testcase, for parser safety. **No
  `timestamp` attribute**, same rule as the JSON report, CI stamps everything
  already. Identical input produces a byte-identical file.
- **Only `<failure>`, never `<error>`.** Bad input is exit 1 and no file at
  all, nothing maps to the crash state.
- UTF-8, standard XML escaping (`<init>` becomes `&lt;init&gt;` in `name`).
- No `<properties>`, no `<system-out>`, no element bodies. `<failure>` and
  `<skipped>` are self-closing with `message` attributes.

## What is a testcase

**One `<testcase>` per scored method**, mirroring the JSON report's `methods`
array exactly. Excluded and folded-away methods are absent, same as there.
Additionally, in whole-repo mode, **one `<testcase>` per slack baseline
entry**, mirroring `slackBaselineEntries`.

Rows appear in the same order as their source arrays, `methods` first
(CRAP descending, then the identity tie-break), then `slackBaselineEntries`
(alphabetical).

## Status mapping

The palette rule: **red means it counts in `summary.violations`, gray means
it warns, green means clean.** The `<failure>` row count equals
`summary.violations` in every mode. That is the invariant implementers test.

| source | default (warn) mode | tight mode (`requireTightBaseline`) |
|---|---|---|
| method `ok` | pass (bare testcase) | pass |
| method `violation` | `<failure>` | `<failure>` |
| method `baselined` | `<skipped>` | `<skipped>` |
| slack entry | `<skipped>` | `<failure>` |

- `status: "fail"` in the JSON holds exactly when the suite has failures and
  the run is enforcing. In advisory runs failures can coexist with a passing
  build, see below.
- In changed-file mode (CLI only) slack detection is disabled, so no slack
  rows exist and the invariant still holds.

## Advisory mode

**Violations render as `<failure>` regardless of advisory.** The sidecar
reports measurement, the exit code and JSON `status` carry the gate. This
keeps `crapReport` (permanently advisory, sidecar on by default) useful, its
red rows are the advisory-rollout view of what enforcement would fail.

A CI setup that fails the build on any red row in an ingested JUnit file has
opted into enforcement by pointing CI at the file. The sidecar deliberately
lives outside `build/test-results/`, ingestion is always an explicit act.

## Naming

- **`classname`** is the dot form of the report's slash-form class name,
  `/` becomes `.`, the `$` of nested classes stays verbatim
  (`com.example.Foo$Bar`). This is the display-layer prettification the
  report spec allows, the JSON report keeps slash form.
- **`name`** is the method name verbatim (including `<init>`) plus a
  parenthesized, comma-separated list of simple parameter type names decoded
  from the JVM descriptor, arrays with `[]`, `parse(String)`,
  `of(int, String[])`, `doExecute()`. Return types never appear.
- **Uniqueness rule.** `(classname, name)` must be unique in the suite or CI
  UIs merge rows. Pretty parameter lists keep overloads apart. If two scored
  methods in one class still collide (bytecode-level duplicates such as
  bridge methods), every colliding row appends its JVM descriptor,
  `parse(String) [(Ljava/lang/String;)V]`.
- **Slack rows** take `classname` and `name` from the baseline entry by the
  same rules, with the suffix ` [slack]` on `name`, `doExecute() [slack]`.
  The suffix names what the row is and keeps an `excess-allowance` slack row
  from colliding with the same method's `baselined` row.

## Messages

One vocabulary everywhere, sidecar messages reuse the text report's numbers,
reasons, and remedy phrasing (research/text-report-spec.md). One line, no
second phrasebook. The `type` attribute on `<failure>` carries the primary
machine-readable reason from the JSON `reasons` vocabulary, or the slack
reason.

| row | element and message |
|---|---|
| violation, over threshold | `<failure type="crap-over-threshold" message="CRAP 38.50, cc 14, 50.0% branch, over the CRAP threshold 15.0"/>` |
| violation, over cap | `<failure type="complexity-over-cap" message="CRAP 22.51, cc 16, 70.6% branch, over the complexity cap 15"/>` |
| violation, regressed | `<failure type="crap-regressed" message="CRAP 22.51, cc 16, 70.6% branch, baselined at crap 18.52 cc 15, regressed: crap +3.99, complexity +1, over the cap"/>` |
| baselined | `<skipped message="baselined debt, allowance crap 18.52 cc 15, passing"/>` |
| slack, warn mode | `<skipped message="slack: under-limits, now passes on its own, run crapBaselineTighten"/>` |
| slack, tight mode | `<failure type="under-limits" message="slack: under-limits, now passes on its own, tight baseline required, run crapBaselineTighten"/>` |

`excess-allowance` messages carry the text report's detail, `allowance
18.52, scores 16.05 today`. `method-gone` reads `slack: method-gone, no such
method in the report, run crapBaselineTighten`. A violation with multiple
reasons uses the first reason for `type` and folds all reasons into the
message, same as the text report row does. CLI remedies name the CLI verbs
(`tighten`, `baseline`) instead of task names, matching the text report's
producer-aware wording.

## Edge cases

- **Empty analysis**: a valid file, wrapper present, `tests="0"`, no rows.
- **Changed-file mode**: rows are the methods analyzed in the slice, no
  slack rows, the outdated-report warning stays on stderr and never enters
  the file.
- **`line` and `sourceFile`** do not appear in the sidecar, CI UIs have no
  standard slot for them, the JSON report carries them.

## Relationship to other specs

- Reaffirmed here without change: `crapCheck` and `crapReport` both stay,
  the JaCoCo-style two-task split (gate task plus never-failing report task)
  questioned and upheld in this session.
- Amends nothing in the JSON report or baseline formats. The invariant
  (failures equal `summary.violations`) is noted in
  research/report-and-baseline-spec.md.

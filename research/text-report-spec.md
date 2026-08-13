# Text Report Spec

Locked 2026-08-13 by the crap-diy.3 prototype session. The scenarios below are
the spec, real numbers from research/mcp-contrast-case-study.md, vocabulary per
the crap-diy.5 amendments (slack, tighten, re-baseline). They tell one story in
order: first run, adopt a baseline, improve code, require tight, regress,
misconfigure, advisory, clean.

## Format rules

- **Grouped sections, no status column.** Violations, then baselined debt,
  then slack. `ok` methods never print. The status-column alternate at the
  bottom was considered and rejected as the default; worst-passing visibility
  is a possible CLI flag (crap-diy.4 decides).
- **Every section header explains itself.** One plain-language clause after
  the em dash states what the category means and whether it acts on the build.
  The same section reads differently when config changes its meaning (slack
  under requireTightBaseline, violations under advisory).
- **Explanatory headers and prose lines stay on one line, max 78 columns.**
  The report never wraps its own prose. Only data columns (long class names,
  file:line) may run wider.
- **Table columns** are CRAP (two decimals, exact), cc, coverage percent with
  kind, ShortClassName.method. Violation rows carry (SourceFile.java:line),
  baselined rows carry the stored allowance instead. Sorted CRAP descending.
- **The summary block** always prints: methods analyzed and excluded, then
  violations, baselined, slack counts, then a status line that names the
  remedy (tests, crapBaseline, crapBaselineTighten, re-baseline).
- **Slack counts as a violation in the summary when a tight baseline is
  required**, shown as `2 violations (2 slack)`. This amends the report spec:
  `summary.violations` in the JSON includes slack entries in that mode.
- **Config echo line** opens every run: threshold, complexity cap, coverage
  selection, baseline path or `no baseline`, plus ADVISORY or
  `tight baseline required` when set.

Scores are recomputed exactly for two-decimal display: doExecute at cc 15 and
75.0% branch coverage is 18.52 (the case study rounded to 18.5).

---

## S1 — First run, no baseline, 2 violations (crapCheck fails)

```
> Task :contrast-mcp-core:crapCheck

open-crap4j  threshold 15.0  complexity cap 15  coverage branch-preferred  no baseline

Violations (2) — over the limits, not excused, these fail the build

   CRAP   cc  coverage        method
  38.50   14  50.0% branch    RecommendationMarkdownRenderer.registerKnownTags   (RecommendationMarkdownRenderer.java:41)
  18.52   15  75.0% branch    SearchAppVulnerabilitiesTool.doExecute             (SearchAppVulnerabilitiesTool.java:88)

430 methods analyzed, 12 excluded
2 violations, 0 baselined, no baseline

FAIL: 2 methods over the CRAP threshold.
Fix with tests, or admit them as known debt with crapBaseline.

> Task :contrast-mcp-core:crapCheck FAILED
```

## S2 — After crapBaseline, gate passes with baselined debt

```
> Task :contrast-mcp-core:crapCheck

open-crap4j  threshold 15.0  complexity cap 15  coverage branch-preferred  baseline crap4j-baseline.json

Baselined debt (2) — over the limits but excused by the baseline, passing

   CRAP   cc  coverage        method                                              allowance
  38.50   14  50.0% branch    RecommendationMarkdownRenderer.registerKnownTags    crap 38.50  cc 14
  18.52   15  75.0% branch    SearchAppVulnerabilitiesTool.doExecute              crap 18.52  cc 15

430 methods analyzed, 12 excluded
0 violations, 2 baselined, 0 slack entries

PASS with 2 baselined methods carrying known debt.
These fail the build only if they grow past their stored allowance.
```

## S3 — Team improved the code, slack appears (warn by default)

registerKnownTags got tests and now passes on its own (under-limits).
doExecute improved but still fails alone, its allowance is now oversized
(excess-allowance).

```
> Task :contrast-mcp-core:crapCheck

open-crap4j  threshold 15.0  complexity cap 15  coverage branch-preferred  baseline crap4j-baseline.json

Baselined debt (1) — over the limits but excused by the baseline, passing

   CRAP   cc  coverage        method                                              allowance
  16.05   15  83.3% branch    SearchAppVulnerabilitiesTool.doExecute              crap 18.52  cc 15

Slack in the baseline (2) — more allowance than needed, informational

  under-limits      RecommendationMarkdownRenderer.registerKnownTags   now passes on its own
  excess-allowance  SearchAppVulnerabilitiesTool.doExecute             allowance 18.52, scores 16.05 today

430 methods analyzed, 12 excluded
0 violations, 1 baselined, 2 slack entries

PASS. The baseline has slack, lock in the progress with crapBaselineTighten.
```

## S4 — Same state, requireTightBaseline = true (crapCheck fails)

```
> Task :contrast-mcp-core:crapCheck

open-crap4j  threshold 15.0  complexity cap 15  coverage branch-preferred  baseline crap4j-baseline.json  tight baseline required

Baselined debt (1) — over the limits but excused by the baseline, passing

   CRAP   cc  coverage        method                                              allowance
  16.05   15  83.3% branch    SearchAppVulnerabilitiesTool.doExecute              crap 18.52  cc 15

Slack in the baseline (2) — tight baseline required, these fail the build

  under-limits      RecommendationMarkdownRenderer.registerKnownTags   now passes on its own
  excess-allowance  SearchAppVulnerabilitiesTool.doExecute             allowance 18.52, scores 16.05 today

430 methods analyzed, 12 excluded
2 violations (2 slack), 1 baselined, 2 slack entries

FAIL: baseline is not tight, 2 slack entries. Run crapBaselineTighten.

> Task :contrast-mcp-core:crapCheck FAILED
```

## S5 — Baselined method regressed (crapCheck fails)

doExecute grew a branch without a test, cc 16, coverage 70.6%.
Now over both its stored allowance and the complexity cap.

```
> Task :contrast-mcp-core:crapCheck

open-crap4j  threshold 15.0  complexity cap 15  coverage branch-preferred  baseline crap4j-baseline.json

Violations (1) — over the limits, not excused, these fail the build

   CRAP   cc  coverage        method
  22.51   16  70.6% branch    SearchAppVulnerabilitiesTool.doExecute             (SearchAppVulnerabilitiesTool.java:88)
                              baselined at crap 18.52 cc 15, regressed: crap +3.99, complexity +1, over the cap

Baselined debt (1) — over the limits but excused by the baseline, passing

   CRAP   cc  coverage        method                                              allowance
  38.50   14  50.0% branch    RecommendationMarkdownRenderer.registerKnownTags    crap 38.50  cc 14

430 methods analyzed, 12 excluded
1 violation, 1 baselined, 0 slack entries

FAIL: 1 baselined method regressed past its allowance.
Fix with tests, or re-admit the new debt with crapBaseline (reviewed, it raises the allowance).

> Task :contrast-mcp-core:crapCheck FAILED
```

## S6 — Coupled-threshold warnings (startup config checks)

Unreachable gate, T >= C² + C:

```
open-crap4j  threshold 250.0  complexity cap 15  coverage branch-preferred  no baseline

WARNING: threshold 250.0 is unreachable under complexity cap 15.
The worst score a cc 15 method can reach is 240 (fully uncovered).
The CRAP gate can never fire, this run is a plain complexity check.
Suggested pairing: threshold 15.0 (threshold = cap demands full coverage at the cap).
```

Hidden complexity cap, T < C:

```
open-crap4j  threshold 10.0  complexity cap 15  coverage branch-preferred  no baseline

WARNING: threshold 10.0 is below complexity cap 15.
CRAP >= cc always, so every method over cc 10 fails regardless of coverage.
The threshold is acting as a hidden complexity cap of 10.
Suggested pairing: complexity cap 10, or threshold 15.0.
```

## S7 — Advisory run (violations reported, build passes)

```
> Task :contrast-mcp-core:crapCheck

open-crap4j  threshold 15.0  complexity cap 15  coverage branch-preferred  no baseline  ADVISORY

Violations (2) — reported only, advisory mode passes the build

   CRAP   cc  coverage        method
  38.50   14  50.0% branch    RecommendationMarkdownRenderer.registerKnownTags   (RecommendationMarkdownRenderer.java:41)
  18.52   15  75.0% branch    SearchAppVulnerabilitiesTool.doExecute             (SearchAppVulnerabilitiesTool.java:88)

430 methods analyzed, 12 excluded
2 violations, 0 baselined, no baseline

ADVISORY: 2 violations reported, build allowed to pass.
```

## S8 — Clean pass after full cleanup and tighten (baseline empty, quiet)

Both methods got covered, crapBaselineTighten deleted both entries, the
committed baseline holds an empty entries array.

```
> Task :contrast-mcp-core:crapCheck

open-crap4j  threshold 15.0  complexity cap 15  coverage branch-preferred  baseline crap4j-baseline.json

430 methods analyzed, 12 excluded
0 violations, 0 baselined, 0 slack entries

PASS
```

---

## Alternate table shape (for comparison)

Same S1 data with a status column instead of grouped sections, closer to
classic crap4j output:

```
   CRAP   cc  coverage        status      method
  38.50   14  50.0% branch    VIOLATION   RecommendationMarkdownRenderer.registerKnownTags
  18.52   15  75.0% branch    VIOLATION   SearchAppVulnerabilitiesTool.doExecute
  12.11   12  90.9% branch    ok          RecommendationMarkdownRenderer.normalizeWhitespace
  12.00    3   0.0% instr     ok          RecommendationMarkdownRenderer.blockquote
  11.17   11  88.9% branch    ok          GetVulnerabilityTool.buildStackTraceAndLibraryData
  11.09   10  77.8% branch    ok          AttackFilterParams.toAttacksFilterBody
  11.00   11 100.0% branch    ok          PaginationParams.of
  ... 423 more under CRAP 11
```

The grouped-sections form above hides `ok` methods entirely. This alternate
shows the worst passers too, which reveals near-threshold methods but makes
every run noisy. Could be a flag (`--show-passing` / top-N) rather than the
default.

# JSON Report and Baseline File Spec

Locked 2026-08-12 by the crap-diy.1 grilling session. This supersedes research doc sections 5.1 (json parts) and 5.5. CSV output and CRAP-load metrics are out of scope for v1 per the map.

**Amended 2026-08-13 by crap-diy.5.** Vocabulary renames, prune becomes **tighten**, regenerate becomes **re-baseline**, stale becomes **slack** (JSON fields `staleEntries` and `staleBaselineEntries` renamed to `slackEntries` and `slackBaselineEntries`, safe because no code or readers exist yet, formatVersion stays 1). Slack gains a third case, `excess-allowance`, and strict mode is the Gradle property `requireTightBaseline`, which fails on every slack entry. Details in research/gradle-plugin-spec.md.

**Amended 2026-08-13 by crap-diy.3.** When a tight baseline is required, slack entries count as violations in `summary.violations` too, not only in the top-level `status`. The text report renders the count as `2 violations (2 slack)`. In the default warn mode `summary.violations` excludes slack, unchanged. Text output shape is locked at research/text-report-spec.md.

## Shared conventions

Both files follow these rules.

- **Class names use slash form**, copied verbatim from the JaCoCo XML, `com/example/OrderService`. No dot conversion anywhere in either file. Display layers (text report, error messages) may prettify.
- **Method identity is three separate fields**, `class`, `method`, `descriptor` (the JVM descriptor). Never a combined string in the files. The combined `class#method#descriptor` form is a display convention for logs only.
- **Field names are spelled out**, `complexity`, `coverage`, `descriptor`, `sourceFile`. No `cc`, `cov`, `desc`, `src`. Exception, `crap` is the product's own word and stays.
- **`crap` serializes to two decimals** in both files. **`coverage` is a ratio 0.0–1.0, four decimals.** Never percent.
- **`formatVersion` stamps both files.** It versions meaning, not shape. Adding a new optional field never bumps it, old readers ignore unknown fields. Changing or removing the meaning of an existing field bumps it. This deliberately leaves room for the Gradle ticket to add an optional per-method `module` field for multi-report aggregation without a bump.
- **`toolVersion`** (the version of open-crap4j that wrote the file) stamps both files.

## JSON report

Top-level shape is fixed. Every field below is always present with one exception, `baselineFile` is omitted when no baseline is configured. Arrays and counts sit empty or zero when unused.

```json
{
  "formatVersion": 1,
  "toolVersion": "1.0.0",
  "status": "fail",
  "advisory": false,
  "mode": "whole-repo",
  "threshold": 15.0,
  "complexityCap": 15,
  "coverageSelection": "branch-preferred",
  "baselineFile": "crap4j-baseline.json",
  "summary": {
    "methodsAnalyzed": 430,
    "violations": 1,
    "baselinedDebt": 1,
    "slackEntries": 1,
    "excluded": 12
  },
  "methods": [ ... ],
  "slackBaselineEntries": [ ... ]
}
```

- **`status`** is three-valued. `pass`, no violations. `fail`, violations found and the gate is enforcing. `advisory`, violations found but advisory mode let the build through. `fail` appears only when the build really failed, so consumers grepping for it never false-alarm on advisory runs. Baselined debt never moves status off `pass`. Slack entries move it only when a tight baseline is required (`requireTightBaseline`), where a slack entry counts as a violation for status purposes, so the same three-value logic applies. A never-failing report run (Gradle `crapReport`) is a permanently advisory run, `advisory: true`, status `pass` or `advisory`.
- **`advisory`** is the mode flag. A clean advisory run reads `status: "pass", advisory: true`.
- **`mode`** is `whole-repo` or `changed-file`.
- **No timestamp.** Reports are throwaway build outputs, CI stamps everything already.

### Method entries

The `methods` array holds every scored method. Unscored methods (excluded by flags or defaults, `<clinit>` and the lambdas folded into it, methods folded away by lambda folding) are absent entirely, counted only in `summary.excluded`. There is no `skipped` status.

Sorted by `crap` descending, worst first. Tie-break ascending by `class`, `method`, `descriptor`, so output is deterministic.

```json
{
  "class": "com/contrastsecurity/mcp/SearchAppVulnerabilitiesTool",
  "method": "doExecute",
  "descriptor": "()Ljava/lang/String;",
  "sourceFile": "SearchAppVulnerabilitiesTool.java",
  "line": 88,
  "complexity": 15,
  "coverage": 0.7500,
  "coverageKind": "branch",
  "crap": 18.50,
  "status": "baselined",
  "reasons": ["crap-over-threshold"],
  "baseline": { "crap": 18.50, "complexity": 15 }
}
```

- **`status`** is one of `ok`, `baselined`, `violation`.
- **`reasons`** appears only on non-`ok` methods (omitted on `ok`, not empty). Values, `crap-over-threshold`, `complexity-over-cap`, `crap-regressed`, `complexity-regressed`. The regressed pair means a baselined method grew past its stored allowance. On a `baselined` method, reasons say why it needed excusing.
- **`baseline`** appears only on `baselined` methods, showing the stored allowance the method is measured against, so a reader sees headroom without opening the baseline file.
- **`line`** is omitted when the class was compiled without debug info (`-g:none`). It is the first-instruction line JaCoCo reports, not the declaration line.
- **`coverageKind`** is `branch` when the merged BRANCH counter total is positive, else `instruction`.
- Omission rule, per-method fields that carry no information are omitted. Top-level fields never are.

### Slack baseline entries

`slackBaselineEntries` lists baseline entries with slack, the entries `crapBaselineTighten` would shrink or delete. Minimal on purpose, identity plus reason only. If the method still exists its current numbers are in `methods`, listing them twice invites disagreement.

```json
{
  "class": "com/contrastsecurity/mcp/OldParser",
  "method": "parse",
  "descriptor": "(Ljava/lang/String;)V",
  "reason": "method-gone"
}
```

`reason` is `method-gone` (no such method in the report), `under-limits` (method exists and now passes both gates on its own), or `excess-allowance` (method still fails a gate but its stored numbers sit meaningfully above today's, stored crap exceeds current by more than the epsilon, or stored complexity exceeds current).

## Baseline file

Checked into the repo, human-reviewable in diffs. Entries sort alphabetically by `class`, then `method`, then `descriptor`, for minimal stable diffs. Keeps a `generated` timestamp because re-baselining is a deliberate, reviewed act.

```json
{
  "formatVersion": 1,
  "toolVersion": "1.0.0",
  "generated": "2026-08-12T00:00:00Z",
  "coverageSelection": "branch-preferred",
  "threshold": 15.0,
  "complexityCap": 15,
  "entries": [
    {
      "class": "com/contrastsecurity/mcp/SearchAppVulnerabilitiesTool",
      "method": "doExecute",
      "descriptor": "()Ljava/lang/String;",
      "crap": 18.50,
      "complexity": 15
    }
  ]
}
```

Lambda folding runs before baselining, so entries always name real source methods. Line numbers are never part of identity.

## Gating rules

One mechanism grandfathers both gates. Each entry stores `crap` and `complexity`, and both ratchet independently.

1. Method over the CRAP threshold or the complexity cap, not in the baseline, **violation**.
2. Method in the baseline, current crap ≤ stored crap + epsilon AND current complexity ≤ stored complexity, **baselined**, passes with a warning.
3. Method in the baseline that exceeds either stored number, **violation**, with `crap-regressed` and/or `complexity-regressed` reasons. More complexity is new debt even when added tests keep CRAP flat.
4. Baseline entry with slack, warn by default and list it in `slackBaselineEntries`. Requiring a tight baseline (`requireTightBaseline` in Gradle, CLI spelling belongs to crap-diy.4) counts every slack entry as a violation, including for the top-level status, so `fail` still only appears when the build really failed. The failure message names the remedy, run `crapBaselineTighten`.

**Epsilon is a fixed constant, 0.05, not a setting.** It absorbs floating-point noise, it is not policy, the threshold is the policy knob. The gate compares the full-precision current score against the stored two-decimal value plus 0.05. Complexity compares exactly, it is an integer. The same epsilon guards slack detection in the other direction, an entry has `excess-allowance` slack only when the stored crap exceeds the current score by more than 0.05, so coverage wobble cannot flap the tight state.

## Config mismatch handling

- `formatVersion` or `coverageSelection` differs from the current tool, **hard fail**, demand a re-baseline. These change what the numbers mean, comparisons would be silently wrong.
- `threshold` or `complexityCap` differs, **warn and gate normally** against current config. Tightening the limits is supposed to surface new violations, one `crapBaseline` run re-admits them if desired.

## Tighten versus re-baseline

- **Re-baseline** rewrites the file from today's code (`crapBaseline`, first run or again). It re-admits debt that got worse since the last baseline. Deliberate, shows up in review as a diff.
- **Tighten** (`crapBaselineTighten`) never adds an entry and never raises a number. It deletes `method-gone` and `under-limits` entries and lowers `crap`/`complexity` on `excess-allowance` entries, locking in progress so a method baselined at 92 cannot drift from 50 back to 90 unnoticed. It lowers crap only past the epsilon and complexity on any decrease, so a tight baseline is exactly one tighten would not change.

## Changed-file mode

The baseline is a whole-repo artifact.

- Gating against the baseline works normally on the methods analyzed.
- Slack detection is disabled, an absent method may simply be outside the slice. `slackEntries` is 0 and `slackBaselineEntries` empty. Requiring a tight baseline is therefore inert in changed-file mode, whether that is a no-op or an error belongs to the CLI ticket.
- Baseline writes (re-baseline and tighten) refuse to run in changed-file mode, they would silently drop every entry outside the diff.

## Deliberately deferred

- Failures-only output is a CLI flag (crap-diy.4) that filters the `methods` array. No schema change.
- Multi-module aggregation was resolved by crap-diy.5, one analysis reads one report, combining coverage stays upstream in JaCoCo, and the optional per-method `module` field stays reserved and unwritten in v1. The version rules above already allow the addition.
- Exit codes and the CLI spelling of the require-tight-baseline switch belong to the CLI ticket (crap-diy.4). `status: fail` must align with the failing exit code.

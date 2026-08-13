# CLI Spec

Locked 2026-08-13 by the crap-diy.4 grilling session. This supersedes research doc section 5.3. It also resolves two questions other specs deferred here, the changed-file behavior of `requireTightBaseline` (report spec) and the worst-passing flag (text report spec).

**Amended 2026-08-13 by crap-diy.7.** `--exclude-annotation` is dropped from v1. JaCoCo XML carries only method names, descriptors, lines, and counters, no annotation data, so annotation-based exclusion is impossible under ADR 0001 (JaCoCo XML is the only input). JaCoCo's own filters catch annotations with CLASS or RUNTIME retention whose name ends in `Generated` (lombok, Dagger), but SOURCE-retention annotations like javax/jakarta `@Generated` are invisible to JaCoCo and to us. Path globs and class regexes are the exclusion tools. The built-in defaults are now, path glob `**/generated/**`, class-name regexes `.*MapperImpl$`, `^Dagger.*`, `^Hilt_.*`, `^AutoValue_.*` (applied to the simple class name), switched off together by `--use-default-exclusions=false`.

## Identity and shipping

- The command is **`crap4j`**. Usage and help text use that name. The "open-" brand appears only in repo and artifact names, matching the Gradle decision that users never type it.
- Ships as an **executable fat jar**, run with `java -jar`. Requires a Java 17+ runtime (bytecode target 17).
- **Zero dependencies, including the CLI.** Argument parsing is hand-rolled plain Java. Nothing is shaded.
- Distribution channel belongs to the publishing ticket (crap-diy.8). Module layout belongs to the build-layout ticket (crap-diy.6).

## Verbs

Four subcommands mirror the four Gradle tasks one-to-one.

| verb | Gradle task | gates? |
|---|---|---|
| `crap4j check` | `crapCheck` | yes, exit 2 on violations |
| `crap4j report` | `crapReport` | never fails |
| `crap4j baseline` | `crapBaseline` | writes the baseline, first run or re-baseline |
| `crap4j tighten` | `crapBaselineTighten` | shrinks the baseline, never adds or raises |

Invalid combinations are structurally impossible where the verb split allows it, `--advisory` exists only on `check`, `tighten` cannot take `--changed-files` at all.

## Exit codes

- **0**, pass, or advisory mode let violations through, or a clean no-op (empty changed set).
- **1**, usage or input error. Includes every refusal below.
- **2**, violations found and the gate is enforcing. Exit 2 appears exactly when the JSON report says `status: "fail"`. Slack entries under `--require-tight-baseline` count as violations, so they exit 2 too.

`report`, `baseline`, and `tighten` never gate, they use only 0 and 1. Warnings never change the exit code.

## Flags

Shared analysis inputs, accepted by all four verbs.

```
--report <path>                     required, single, the JaCoCo XML (see ADR 0003, one gate reads one report)
--threshold <double>                default 15.0
--complexity-cap <int>              default 15
--exclude <glob>                    repeatable
--exclude-class <regex>             repeatable
--use-default-exclusions=true|false default true
```

`--report` has **no default path**. A wrong-guess default that silently reads a report from an old build is the outdated-report trap. Every invocation names its input.

Per-verb flags.

| flag | check | report | baseline | tighten |
|---|---|---|---|---|
| `--changed-files <file\|->` | yes | yes | refused | refused |
| `--baseline <path>` | input | input | output | input and output |
| `--require-tight-baseline` | yes | yes | no | no |
| `--advisory` | yes | no | no | no |
| `--show-passing <N>` | yes | yes | no | no |
| `--json-report <path\|->` | yes | yes | no | no |
| `--junit-report <path>` | yes | yes | no | no |

- `report` keeps `--require-tight-baseline` because it changes the JSON `status` (pass versus advisory) and the violation counts. Dashboards may want the strict posture.
- `report` drops `--advisory`, it is inherently advisory (JSON gets `advisory: true`, status `pass` or `advisory`).
- `--junit-report` fixes the flag spelling and verb placement only. Sidecar semantics are locked at research/junit-sidecar-spec.md (crap-diy.9).

### Refusals, all usage errors, exit 1

- `--changed-files` on `baseline` or `tighten`. Baseline writes are whole-repo only (report spec), a sliced write would silently drop every entry outside the diff.
- `--require-tight-baseline` with `--changed-files`, on `check` **and** `report`. Slack detection is disabled in changed-file mode, so the switch asks for a guarantee the mode cannot deliver. Failing fast beats a silent no-op.
- An explicitly given `--baseline` path that does not exist, on `check` and `report`. Mirrors the Gradle rule, silence would hide a typo.
- `tighten` with no baseline file present, there is nothing to tighten.
- A duplicated scalar flag (see hygiene below).

## Baseline path convention

Mirrors Gradle exactly. Without `--baseline`, the conventional path `crap4j-baseline.json` in the working directory is used if it exists, and the gate runs baseline-less if it does not. An explicit path must exist (for `check`, `report`, `tighten`). For `baseline` the path, explicit or conventional, is the write target.

## Output channels

- The **text report always prints to stdout** (shape locked at research/text-report-spec.md), except when `--json-report -` is given.
- `--json-report <path>` writes the JSON report file. `--json-report -` writes JSON to **stdout and suppresses the text report**, for piping into jq. The `-` convention pairs with `--changed-files -`.
- `--junit-report <path>` writes the JUnit XML sidecar.
- **Diagnostics go to stderr**, the outdated-report warning, the coupled-threshold warnings (unreachable gate, hidden cap, research doc 5.2), and the baseline config-mismatch warning. Stdout stays parseable.
- Exit-code-only use is `>/dev/null`. There is no `--format none` and no `--quiet`.

## Changed-file mode

- `--changed-files <file>` takes one path per line, `-` reads the list from stdin. Built for `git diff --name-only origin/main... | crap4j check --report ... --changed-files -`.
- **Suffix match.** A listed path matches a class when it ends with the report-derived `<package>/<sourceFile>` string, so both `com/example/Foo.java` and `src/main/java/com/example/Foo.java` work verbatim. Two edges the implementer must honor.
  - **Path boundary.** The character before the matched suffix must be `/`, or the listed path equals the suffix exactly. `mycom/example/Foo.java` must not match `com/example/Foo.java`.
  - **Default package.** JaCoCo's package name is empty there, the match string is bare `Foo.java` with no leading slash. The equality arm of the boundary rule handles it, a bare `Foo.java` line matches only default-package classes.
- Backslashes normalize to slashes before matching.
- Lines that match nothing (README.md, build files, deleted classes) count as skipped, per the requirements. An **empty changed set is a clean no-op**, exit 0.

## Outdated-report warning

In changed-file mode only, warn on stderr when any listed file that matched is newer on disk than the report file. That catches the observed failure, editing a file and forgetting to re-run tests (seen in the mcp-contrast case study as an UP-TO-DATE module). Whole-repo mode gets no freshness check, the CLI trusts its input and Gradle owns freshness there. The term is **outdated report**, "stale" belongs to baseline slack and is not used here.

## Worst-passing visibility

`--show-passing <N>` appends one extra text section listing the N highest-CRAP passing methods. Text-only, no JSON change (the JSON `methods` array already holds every scored method). N is explicit with no default, so the default output stays exactly as crap-diy.3 locked it. This adopts the alternate at the bottom of research/text-report-spec.md as an opt-in section rather than the default shape.

## Hygiene

- Both `--flag value` and `--flag=value` are accepted.
- Only documented repeatable flags repeat. A duplicated scalar flag is a usage error, not last-one-wins, catching CI copy-paste bugs.
- `crap4j --version` prints the version. `crap4j` with no verb and `crap4j --help` print usage. `crap4j <verb> --help` prints per-verb help.

## Deliberately deferred

- Distribution channel and coordinates, crap-diy.8.
- Repo module layout and how the fat jar is built, crap-diy.6.
- ~~JUnit sidecar semantics, crap-diy.9~~, resolved at research/junit-sidecar-spec.md.
- CSV output, out of scope for v1 per the map.

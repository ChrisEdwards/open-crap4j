# Lambda Folding Spec

How open-crap4j folds synthetic lambda methods in JaCoCo XML reports into their enclosing source methods before computing CRAP scores. Every rule below was verified against primary sources. The sources section lists them. Verification used JDK 21 (Temurin 21.0.5), JaCoCo 0.8.12, and two real reports from the mcp-contrast project (547 method elements, 55 lambda methods, 12 overloaded fold targets).

## Verified naming grammar

javac names each synthetic lambda body method

```
lambda$<component>$<n>
```

where `<component>` is derived from the innermost enclosing member and `<n>` is a class-wide counter, not a per-method counter. Verified from javap output and from javac source (`LambdaToMethod.syntheticMethodNameComponent`).

| Enclosing member | `<component>` | Example (javap, JDK 21) |
|---|---|---|
| Ordinary method `foo` | `foo` | `lambda$ordinary$5` |
| Constructor | `new` | `lambda$new$3` |
| Instance field initializer | `new` | `lambda$new$0` |
| Instance initializer block | `new` | javac source, BLOCK non-static maps to "new" |
| Static field initializer | `static` | `lambda$static$1` |
| Static initializer block | `static` | `lambda$static$2` |
| Record compact constructor | `new` | `lambda$new$0` in `Rec` |
| Interface default method `doIt` | `doIt` | `lambda$doIt$0` |
| Interface static method `stat` | `stat` | `lambda$stat$1` |
| Method of an anonymous class | that method's name, inside the `Foo$N` class element | `Anon$1.lambda$run$0` |
| Lambda inside another lambda, JDK 9 and later | the enclosing source method's name | `lambda$nested$8` and `lambda$nested$9`, both named after `nested` |
| Lambda inside another lambda, JDK 8 | `null` | `lambda$null$N`, from the JDK 8 source's null-name fallback |

Facts that constrain the parser.

- The counter `<n>` is class-wide. `Sample` has ten lambdas numbered 0 through 9 across nine different enclosing members. Adding one lambda anywhere in a class renumbers every lambda translated after it.
- The counter reflects translation order, not source order. Field initializer lambdas got 0 through 2 before constructor body lambdas in the same class.
- `<component>` can itself contain `$` if the source method name does (legal but rare). Parse greedily, strip the `lambda$` prefix and the final `$<digits>`, everything between is the target name.
- A real source method can be named `lambda`. The mcp-contrast report contains one (`RecommendationMarkdownRenderer.lambda`, returns `Mustache$Lambda`). The pattern must anchor on the `lambda$` prefix plus a trailing `$<digits>` group, a bare `lambda` name must not match.
- The XML report carries no synthetic flag, so a source method literally named `lambda$foo$0` would be indistinguishable from a synthetic one. Accepted risk, `$` in identifiers is strongly discouraged and this never occurs in practice.
- Nested lambdas in JDK 9+ fold directly to the source method in one step. The `lambda$lambda$foo$0$1` chained pattern in the draft rules does not exist in any javac output examined. Keep transitive resolution as a cheap defensive rule only.
- Switch expressions with arrow cases generate no synthetic methods. `Sample.sw` compiles to inline bytecode, javap shows no `lambda$sw` entries.

## What JaCoCo puts in the report

- Lambda methods appear as separate `<method>` elements. JaCoCo's `SyntheticFilter` ignores synthetic methods except those whose name starts with `lambda$` (and Scala `$anonfun$` in Scala classes). Verified from filter source and from all three reports.
- Filtered synthetics never appear. The generated enum report contains only `go`, `lambda$go$0`, and `<clinit>`. `values`, `valueOf`, `$values`, and the implicit constructor are absent. The record report has no `equals`, `hashCode`, `toString`, or accessor methods. Bridge methods are removed by `BridgeFilter`.
- Kotlin classes use a different filter set (selected by the `Lkotlin/Metadata;` annotation) and Kotlin lambdas do not use `lambda$` naming. Out of scope for this spec.
- The `line` attribute on a method element is the first source line that holds instructions, not the declaration line. `ordinary()` declared on line 13 with its first statement on line 14 reports `line="14"`. Verified in JaCoCo source, `ReportElement.method` writes `coverage.getFirstLine()`.
- The `line` attribute is omitted when the class was compiled without debug info (`-g:none`). Verified empirically, every method element in that report lacks `line`. Both mcp-contrast reports have `line` on all 547 methods.
- Constructor line numbers are surprising. Instance field initializers compile into every constructor, so a constructor's first instruction line can be the field initializer's line, not the constructor declaration. Both `Sample` constructors (declared lines 10 and 11) report `line="6"`, the line of the first initialized field.

## Folding algorithm

Run per `<class>` element. Never fold across class elements.

1. Partition the class's methods. A method is a lambda if its name matches `^lambda\$(.+)\$(\d+)$`. Group 1 is the raw target name.
2. Map the raw target to a method name. `new` maps to `<init>`. `static` maps to `<clinit>`. Anything else maps to itself.
3. Resolve the target method among non-lambda methods of the same class with that name.
   - Zero candidates. Keep the lambda as a standalone method under its own name (fail open). This covers JDK 8 `lambda$null$N` and targets removed by JaCoCo filters.
   - One candidate. Fold into it.
   - Multiple candidates (overloads). Pick the candidate with the largest `line` that is less than or equal to the lambda's `line`. If several candidates share that line, pick the first in document order. If no candidate has `line` less than or equal to the lambda's, or any `line` attribute involved is missing, fall back to the first candidate in document order.
4. Transitive step, defensive only. If the resolved target is itself a lambda method, resolve again from that lambda's own target, with a visited set to stop cycles. No known javac output triggers this.
5. Merge counters by adding `missed` and `covered` per counter type, for COMPLEXITY, BRANCH, INSTRUCTION, and LINE. A counter type absent on one side is treated as 0/0. Drop the lambda's METHOD counter, the folded result is one method. Merge before computing any ratio, never average ratios.
6. Remove folded lambda elements from the method list. Then apply the `<clinit>` skip. Lambdas that folded into `<clinit>` (the `lambda$static$N` family) are skipped with it. The alternative of keeping them standalone was rejected, their execution is tied to class loading, the same reason `<clinit>` is skipped.
7. Compute CRAP from the merged counters. cc is merged COMPLEXITY missed plus covered. Coverage is merged BRANCH ratio when merged BRANCH total is positive, else merged INSTRUCTION ratio.

The overload rule ran against all 12 real overloaded targets in the mcp-contrast core report and resolved every one to the correct overload. In every case the correct overload is the one that delegates to a lambda-building helper, and the wrong overload sits a few lines later.

## Edge case table

| Case | Example | Rule | Confidence |
|---|---|---|---|
| Plain lambda in a method | `lambda$parseCommaSeparated$0` (real report) | Fold into same-name method | Verified, real data |
| Overloaded target | `SearchAttacksTool.searchAttacks` at lines 102 and 129, lambda at 106 (real report) | Largest line <= lambda line picks 102 | Verified, 12/12 real cases |
| Lambda on target's first line | `lambda$getScanProject$0` line 52, target line 52 (real report) | <= comparison makes equal lines match | Verified, real data |
| Constructor lambda | `lambda$new$3` (javap) | Target is `<init>` | Verified, javap and JaCoCo XML |
| Field initializer lambda | `lambda$new$0` at line 6, both `<init>` also at line 6 | Line tie, first `<init>` in document order. Any single-target choice is arbitrary, the initializer runs in every constructor | Verified tie exists. Tie-break is a convention, not verified against original crap4j |
| Static init lambda | `BaseTool.lambda$static$0` (real report) | Fold into `<clinit>`, then skip with it | Verified occurrence. Skip decision is a design choice |
| Nested lambda, JDK 9+ | `lambda$nested$8`, `lambda$nested$9` (javap, JDK 21) | Both fold directly into `nested`, no chain | Verified, javap and JaCoCo XML |
| Nested lambda, JDK 8 | `lambda$null$N` | Target `null` never exists, keep standalone | Naming verified from JDK 8 javac source. Not tested empirically, no JDK 8 available |
| Interface default and static methods | `Iface.lambda$doIt$0`, `Iface.lambda$stat$1` (javap and XML) | Normal fold, interfaces need no special case | Verified |
| Record compact constructor | `Rec.lambda$new$0` (javap and XML) | Folds into `<init>` like any constructor | Verified |
| Enum method lambda | `Color.lambda$go$0` (XML) | Normal fold. `values`/`valueOf` already filtered by JaCoCo | Verified |
| Anonymous class | `Anon$1` own class element with `run` and `lambda$run$0` | Lambda folds into `run` within `Anon$1`. The anon class's methods stay standalone under class `Anon$1` (crap-java punts the same way). Folding the whole anon class into `make` would need line heuristics the XML barely supports | Verified structure. Standalone is a design choice, options noted below |
| Real method named `lambda` | `RecommendationMarkdownRenderer.lambda` (real report) | Regex requires `lambda$...$<digits>`, no match, no fold | Verified, real data |
| Target filtered out of report | Possible with `@Generated`-annotated methods containing lambdas | Zero candidates, keep lambda standalone | Rule by construction, no real example found |
| No `line` attributes (`-g:none`) | Generated report, all methods lack `line` | Overload fallback to document order. Single candidate needs no line | Verified that attribute disappears |
| Switch arrow expressions | `Sample.sw` | No synthetic methods generated, nothing to fold | Verified, javap |
| Method name containing `$` | `foo$bar` with a lambda gives `lambda$foo$bar$0` | Greedy middle match recovers `foo$bar` | By grammar construction, not tested |
| Scala `$anonfun$` | Kept by SyntheticFilter in Scala classes | Out of scope, no fold rule | Verified in filter source only |

## Baseline key stability

The baseline identity key is class name plus method name plus JVM descriptor, for example `com/example/Foo#processOrder#(Ljava/lang/String;I)V`.

- Adding or removing lines above a method. Key unchanged, the key contains no line number. Lines are only used at fold time within a single report.
- Adding an overload. The descriptor disambiguates, both overloads get distinct keys.
- Nested classes. The class attribute contains `$`, for example `AttackSummary$ApplicationAttackInfo` in the real report. Keys work unchanged, folding stays within the class element.
- Lambdas themselves. Folding is what makes the baseline stable. Lambda numbering is class-wide, so adding one lambda renumbers all later ones in the class. Folded lambdas never enter the baseline, so the churn is invisible.
- Anonymous classes. `Foo$1` renumbers when an earlier anonymous class is added, so standalone anon-class methods have unstable keys. This is the honest cost of keeping them standalone. Alternatives are to fold the whole anon class into the enclosing method (needs sourcefilename plus line heuristics, fragile) or to exclude anon classes from baselines. Recommend documenting the instability and revisiting if it bites.
- Unfolded lambdas (missing target, JDK 8 `lambda$null`). Same instability as anon classes, for the same class-wide numbering reason. Rare enough to accept.

## Open questions

- Which exact JDK release changed nested lambda naming from `lambda$null$N` to the enclosing source method name. Both endpoints are verified (JDK 8 source has the null fallback, JDK 21 uses the method name). The precise changeover version does not affect the algorithm, both shapes are handled.
- Whether the original crap4j folded field initializer lambdas into a specific constructor. The document-order tie-break here is a fresh convention.
- Whether any build tool in scope emits JaCoCo XML from Kotlin-only classes into the same report. If so, unmatched Kotlin synthetics would flow through as standalone methods, which is safe but unaudited.

## Sources

Real reports (empirical, 12 August 2026)

- /Users/chrisedwards/projects/contrast/mcp-contrast/contrast-mcp-core/build/reports/jacoco/test/jacocoTestReport.xml (457 methods, 53 `lambda$` methods, 12 overloaded targets, 1 real method named `lambda`, 1 `lambda$static$0`)
- /Users/chrisedwards/projects/contrast/mcp-contrast/contrast-mcp-stdio-app/build/reports/jacoco/test/jacocoTestReport.xml (90 methods, 2 `lambda$` methods)

javac ground truth (Temurin OpenJDK 21.0.5, javap over classes compiled in /tmp/lambda-test/src)

- Sample.java (constructors, field and static initializers, overloads, nested lambda, switch expression), Iface.java (default and static interface methods), Rec.java (record compact constructor), Anon.java (anonymous class), Color.java (enum)

JaCoCo ground truth (agent and CLI 0.8.12 from Maven Central, XML reports generated in /tmp/lambda-test)

- report.xml (full run), nd.xml (compiled with -g:none, no line attributes), enum.xml (enum filtering)

JaCoCo source (github.com/jacoco/jacoco, master)

- org.jacoco.core/src/org/jacoco/core/internal/analysis/filter/SyntheticFilter.java (keeps `lambda$` and Scala `$anonfun$`, ignores other synthetics)
- org.jacoco.core/src/org/jacoco/core/internal/analysis/filter/Filters.java (full filter list, Kotlin filter selection via `Lkotlin/Metadata;`)
- org.jacoco.report/src/org/jacoco/report/internal/xml/ReportElement.java (`method()` writes `line` from `getFirstLine()`, omitted when -1)

javac source (OpenJDK)

- github.com/openjdk/jdk8u langtools LambdaToMethod.java, `syntheticMethodNameComponent` maps `<init>` to "new", `<clinit>` to "static", null name to "null"
- github.com/openjdk/jdk master LambdaToMethod.java, BLOCK owners map to "new" or "static" by the STATIC flag, constructors map to "new"

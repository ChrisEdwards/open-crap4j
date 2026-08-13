# Case study, CRAP metrics on mcp-contrast

Date 2026-08-12. Method, ran the repo's existing JaCoCo setup (`./gradlew test jacocoTestReport`, no test failures) and computed per-method CRAP scores from the XML with the open-crap4j rules, COMPLEXITY counter for cc, branch coverage preferred with instruction fallback, `lambda$...$N` methods folded into their enclosing method, `<clinit>` skipped. Modules analyzed, contrast-mcp-core and contrast-mcp-stdio-app (buildSrc excluded as build tooling).

## Headline numbers

| Module | Line | Instruction | Branch |
|---|---|---|---|
| contrast-mcp-core | 97.92% | 98.14% | 91.26% |
| contrast-mcp-stdio-app | 97.84% | 98.20% | 96.15% |

430 methods analyzed.

| CC range | Methods |
|---|---|
| 1-3 | 354 |
| 4-6 | 50 |
| 7-10 | 21 |
| 11-15 | 5 |
| 16+ | 0 |

Against the proposed defaults (CRAP threshold 15, complexity cap 15):

- Methods over the complexity cap, **0**
- Methods with CRAP > 15, **2**
- Methods with CRAP > 30, **1**
- Methods with cc >= 4 and branch coverage under 50%, **0**

## Top methods by CRAP

| Class | Method | CC | Cov % | Kind | CRAP |
|---|---|---|---|---|---|
| RecommendationMarkdownRenderer | registerKnownTags | 14 | 50.0 | branch | 38.5 |
| SearchAppVulnerabilitiesTool | doExecute | 15 | 75.0 | branch | 18.5 |
| RecommendationMarkdownRenderer | normalizeWhitespace | 12 | 90.9 | branch | 12.1 |
| RecommendationMarkdownRenderer | blockquote | 3 | 0.0 | instruction | 12.0 |
| GetVulnerabilityTool | buildStackTraceAndLibraryData | 11 | 88.9 | branch | 11.2 |
| AttackFilterParams | toAttacksFilterBody | 10 | 77.8 | branch | 11.1 |
| PaginationParams | of | 11 | 100.0 | branch | 11.0 |

(The rest of the top 15 are cc 9-10 methods at or near full branch coverage, CRAP 9-10, all passing.)

## Assessment, is CRAP valuable for this repo

This is a very healthy codebase, and the honest answer reflects that. The gate would find little to do day to day. But the exercise validated the tool's core claims on real code.

**The average hid the tail, exactly as predicted.** The repo reports 97.9% line coverage and 91.3% branch coverage, yet `registerKnownTags` sits at cc 14 with only 50% of its branches exercised, CRAP 38.5, over even the original crap4j threshold of 30. No aggregate coverage gate and no complexity cap (it is under 15) would ever surface this method. CRAP found it immediately. That is the intersection the tool exists for.

**The gate is what protects the cap boundary.** `doExecute` sits exactly at cc 15 with 75% branch coverage. The complexity cap passes it. Only the CRAP threshold demands the missing branch coverage on the single most complex method in the repo.

**Adoption cost is near zero and the baseline would be tiny.** JaCoCo XML is already produced by the existing build. The baseline file would hold two entries, or the team could just cover the two methods and run with an empty baseline. Either way the gate then prevents regression, which matters because this repo's discipline is currently cultural, nothing mechanical stops the next cc 14 method with half its branches untested.

**The coupled-threshold guidance applies here.** The codebase already keeps complexity low (nothing over 15, only 5 methods over 10). With cap 15 and threshold 15 the gate yields 2 findings. A team wanting a stricter ramp could drop to threshold 10, which would add roughly 4 borderline methods (cc 10-12 at 78-91% branch coverage) to the initial baseline.

Verdict. As a cleanup tool, marginal, there are only two findings. As a regression gate, cheap and worthwhile, it mechanically enforces the discipline the repo currently maintains by habit and catches the one class of defect (complex plus undertested) that its 98% coverage number is blind to.

# open-crap4j Requirements

## What this tool does

Compute CRAP (Change Risk Anti-Patterns) scores for Java methods by reading existing JaCoCo XML coverage reports. No re-running tests, no new instrumentation. The JaCoCo XML already contains everything needed.

## The CRAP formula

`CRAP(m) = cc(m)^2 * (1 - cov(m))^3 + cc(m)`

Where `cc` is cyclomatic complexity and `cov` is the branch coverage ratio (0.0 to 1.0) for method `m`.

Properties of the formula:
- A fully covered method (cov=1.0) has CRAP = cc (complexity alone)
- An uncovered method (cov=0.0) has CRAP = cc^2 + cc
- Coverage reduces CRAP cubically, so even partial coverage helps a lot
- CRAP >= cc always holds, so any method with cc >= threshold fails regardless of coverage

## Why this tool needs to exist

There is no clean, well-licensed, off-the-shelf tool for this:
- `unclebob/crap4java` has NO license file (all rights reserved)
- `fabian-barney/crap-java` derives from the unlicensed upstream, so its Apache-2.0 grant is legally questionable
- JaCoCo declined to add CRAP support (wontfix, "derived metrics are out of scope")
- SonarQube reports complexity and coverage separately but does not combine them into CRAP
- Jenkins Crap4J plugin depends on the unlicensed crap4j to produce data

## Input: JaCoCo XML report structure

JaCoCo XML reports contain per-method data inside `<class>` elements inside `<package>` elements. Each `<method>` has counters:

```xml
<package name="com/example/myapp">
  <class name="com/example/myapp/MyService" sourcefilename="MyService.java">
    <method name="processOrder" desc="(Ljava/lang/String;I)V" line="42">
      <counter type="INSTRUCTION" missed="5" covered="135"/>
      <counter type="BRANCH" missed="7" covered="21"/>
      <counter type="LINE" missed="2" covered="15"/>
      <counter type="COMPLEXITY" missed="3" covered="12"/>
      <counter type="METHOD" missed="0" covered="1"/>
    </method>
    <method name="lambda$processOrder$0" desc="(Ljava/lang/String;)Z" line="55">
      <counter type="INSTRUCTION" missed="0" covered="9"/>
      <counter type="COMPLEXITY" missed="0" covered="1"/>
      <counter type="METHOD" missed="0" covered="1"/>
    </method>
  </class>
</package>
```

Key facts about the data:
- **COMPLEXITY counter**: `missed + covered` = cyclomatic complexity for that method
- **BRANCH counter**: present only when the method has branches. `covered / (missed + covered)` = branch coverage ratio
- **INSTRUCTION counter**: always present. Use as coverage fallback when BRANCH counter is absent
- **Lambda methods**: JaCoCo reports lambda bodies as separate synthetic methods named `lambda$<enclosingMethod>$<n>`. Their complexity and coverage must be folded into the enclosing method.
- **`<clinit>` methods**: static initializers, should be skipped
- **`sourcefilename` attribute**: on the `<class>` element, maps back to the source file

## Core features needed

### 1. Per-method CRAP calculation
- Parse JaCoCo XML (with XXE protection)
- Extract per-method complexity from the COMPLEXITY counter
- Use branch coverage when BRANCH counter exists (missed+covered > 0), fall back to instruction coverage otherwise
- Record which coverage kind was used per method (BRANCH or INSTRUCTION)
- Compute CRAP score per method
- Sort by CRAP descending

### 2. Lambda folding
- Synthetic methods named `lambda$<methodName>$<n>` must be folded into their enclosing method
- Both complexity and coverage counters are merged
- When the enclosing method name has overloads (same name, different descriptors), fold into the overload whose start line is closest to (but not greater than) the lambda's start line
- Nested lambdas (lambda inside a lambda) should fold transitively

### 3. Whole-repo mode
- Process all classes in the report
- Print a human-readable table sorted by CRAP descending
- Write a machine-readable report (CSV or JSON)
- Flag methods above a configurable CRAP threshold
- Separately flag methods above a configurable complexity cap

### 4. Changed-file mode
- Accept a list of changed source file paths
- Restrict the report to methods in those files only
- Source paths are in the form `com/example/myapp/MyService.java` (package path + filename)
- Files absent from the JaCoCo report should be reported as skipped (not errored)
- An empty changed set is a clean no-op

### 5. Baseline-aware gating (for CI integration)
- Accept a baseline file listing known high-CRAP methods with their scores
- Methods in the baseline at the same or lower CRAP score pass with a warning
- Methods above the threshold NOT in the baseline, or whose CRAP score increased, are violations
- Generate/regenerate the baseline from a whole-repo run
- This enables gradual adoption: existing debt doesn't block PRs, but you can't add new debt

### 6. Exit codes for CI
- Exit 0: no violations (or advisory mode)
- Exit non-zero: violations found (new or worsened CRAP above threshold)

## Integration targets

This tool should work as:
- A standalone CLI (read a JaCoCo XML, print results)
- A Gradle plugin (register tasks that read the JaCoCo report the build already produces)
- Potentially a Maven plugin (same idea)

The primary consumer right now is a Gradle build that already runs JaCoCo and produces XML reports.

## Licensing

Must be clean open source. Apache-2.0 or MIT preferred. Must NOT derive from or include code from `unclebob/crap4java` (which has no license).

## Existing tools to research for feature ideas

Before designing, research what these tools offer and what we can learn from them:

1. **`fabian-barney/crap-java`** (on GitHub) - the most feature-complete existing tool. Look at its CLI options, output formats, threshold configuration, Gradle plugin API, and any features beyond basic CRAP calculation. It uses a `covKind` field to indicate branch vs instruction coverage. What else does it do well?

2. **The original CRAP4J concept** - what did the original academic/industry proposal include beyond the basic formula? Any refinements to the metric?

3. **SonarQube's complexity and coverage reporting** - how does it present per-method metrics? Any UX ideas for how to surface CRAP data?

4. **PIT (pitest) mutation testing** - different metric but similar "method-level quality signal" space. How does it handle baselines, thresholds, and gradual adoption?

5. **Checkstyle's CyclomaticComplexity check** - how does it configure thresholds and exclusions?

## What to prepare

Research the tools above and prepare to discuss:
- Feature gaps in existing tools that we should fill
- Features from existing tools worth adopting
- Output format preferences (CSV vs JSON vs both)
- Threshold defaults (the 8.0 default from crap-java is too low because cc >= 9 always fails regardless of coverage)
- Gradle plugin API design
- How baseline-aware gating should work in practice

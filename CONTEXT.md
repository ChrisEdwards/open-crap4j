# open-crap4j

A build gate that reads JaCoCo XML reports and fails when complex Java methods lack tests, scored by the CRAP metric.

## Language

**CRAP score**:
The change-risk score of a method, cc² × (1 − coverage)³ + cc.
_Avoid_: crappiness, risk score

**Complexity (cc)**:
Cyclomatic complexity of a method as JaCoCo counts it from bytecode. Runs higher than source-level counts from tools like Checkstyle.
_Avoid_: cognitive complexity

**Coverage kind**:
Which JaCoCo counter fed a method's coverage, branch when the method has branches, instruction otherwise.

**CRAP threshold**:
The CRAP score above which a method is a violation unless baselined. Written T in threshold math.

**Complexity cap**:
The complexity above which a method is a violation regardless of coverage, unless baselined. Written C in threshold math.
_Avoid_: complexity requirement, complexity limit

**Unreachable gate**:
A threshold and cap pair where C² + C ≤ T, so the CRAP threshold can never fire on any method the cap allows. The tool warns about it.

**Violation**:
A method over the CRAP threshold or the complexity cap that the baseline does not excuse. Violations fail the gate.

**Baseline**:
The committed file of known debt. A listed method passes with a warning at the same or lower CRAP score.
_Avoid_: suppression file, ignore list

**Baselined debt**:
A method the baseline excuses.
_Avoid_: grandfathered, legacy debt

**Slack entry**:
A baseline entry that tightening would shrink or delete. The method is gone, or it now passes on its own, or its stored numbers sit meaningfully above today's score. Warns by default.
_Avoid_: stale entry

**Tight baseline**:
A baseline with no slack entries. Requiring one makes any slack fail the gate.

**Epsilon**:
The fixed 0.05 allowance used when comparing CRAP scores, both for regression and for slack detection. Absorbs score noise between runs. A constant, not a setting.

**Tighten**:
Baseline cleanup that deletes slack entries and lowers stored numbers that sit meaningfully above today's scores. Never adds an entry, never raises a number.
_Avoid_: prune

**Re-baseline**:
Rewriting the baseline from today's code. Re-admits debt that got worse, so it is deliberate and reviewed.
_Avoid_: regenerate, refresh

**Method key**:
The identity of a method for baseline matching, class name plus method name plus JVM descriptor. Line numbers are never part of it.

**Lambda folding**:
Merging JaCoCo's synthetic lambda$ methods into their enclosing source method before scoring.

**Whole-repo mode**:
Scoring every method in the report.

**Changed-file mode**:
Restricting the report to methods in a supplied list of source files. The tool never computes the list itself.
_Avoid_: diff mode, changed mode

**Outdated report**:
A coverage report older than a changed source file it should describe. The tool warns, it does not fail.
_Avoid_: stale report

**Advisory mode**:
Reporting violations without failing the build, exit code 0.
_Avoid_: warn-only mode

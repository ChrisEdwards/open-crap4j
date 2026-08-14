package com.architester.crap4j.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class ChangedFileSelectorTest {
    @Test
    void appliesEveryLockedPathBoundaryRule() {
        JacocoReport report = report(
                new JacocoPackage("com/example", List.of(type("com/example/Foo", "Foo.java"))),
                new JacocoPackage("", List.of(type("DefaultFoo", "Foo.java"))));

        assertSelection(report, "com/example/Foo.java", "com/example/Foo");
        assertSelection(report, "src/main/java/com/example/Foo.java", "com/example/Foo");
        assertSelection(report, "src\\main\\java\\com\\example\\Foo.java", "com/example/Foo");
        assertSelection(report, "Foo.java", "DefaultFoo");

        assertSkipped(report, "mycom/example/Foo.java");
        assertSkipped(report, "src/main/java/Foo.java");
        assertSkipped(report, "README.md");
    }

    @Test
    void slicesTheRealFixtureToEveryClassFromOneSourceFile() throws Exception {
        JacocoReport report = new JacocoXmlParser().parse(
                Path.of("../test-fixtures/jacoco/report.xml"));

        ChangedFileSelection selection =
                new ChangedFileSelector().select(report, List.of("Anon.java", "README.md"));
        ScoringResult scoring = new ScoringEngine().score(selection.report(), Exclusions.none());

        assertThat(selection.skippedFiles()).isOne();
        assertThat(selection.report().packages())
                .flatExtracting(JacocoPackage::classes)
                .extracting(JacocoClass::name)
                .containsExactly("Anon", "Anon$1");
        assertThat(scoring.methods()).extracting(ScoredMethod::className)
                .containsOnly("Anon", "Anon$1");
    }

    @Test
    void emptyChangedSetIsACleanNoOpAndDisablesSlack() {
        JacocoReport report = report(new JacocoPackage(
                "com/example", List.of(type("com/example/Foo", "Foo.java"))));
        ChangedFileSelection selection = new ChangedFileSelector().select(report, List.of());
        ScoringResult scoring = new ScoringEngine().score(selection.report(), Exclusions.none());
        Baseline baseline = new Baseline(
                1, "1", "now", CoverageSelection.BRANCH_PREFERRED, 15, 15,
                List.of(new BaselineEntry(new MethodKey("com/example/Foo", "run", "()V"), 20, 4)));
        GateResult gate = new BaselineGate().evaluate(
                scoring,
                Optional.of(baseline),
                new GateConfig(15, 15, CoverageSelection.BRANCH_PREFERRED, false, true));

        assertThat(selection.report().packages()).isEmpty();
        assertThat(selection.skippedFiles()).isZero();
        assertThat(gate.methods()).isEmpty();
        assertThat(gate.violations()).isZero();
        assertThat(gate.slackEntries()).isEmpty();
    }

    private static void assertSelection(
            JacocoReport report, String changedPath, String expectedClass) {
        ChangedFileSelection selection =
                new ChangedFileSelector().select(report, List.of(changedPath));

        assertThat(selection.skippedFiles()).isZero();
        assertThat(selection.report().packages())
                .flatExtracting(JacocoPackage::classes)
                .extracting(JacocoClass::name)
                .containsExactly(expectedClass);
    }

    private static void assertSkipped(JacocoReport report, String changedPath) {
        ChangedFileSelection selection =
                new ChangedFileSelector().select(report, List.of(changedPath));

        assertThat(selection.skippedFiles()).isOne();
        assertThat(selection.report().packages()).isEmpty();
    }

    private static JacocoReport report(JacocoPackage... packages) {
        return new JacocoReport("test", List.of(packages));
    }

    private static JacocoClass type(String className, String sourceFile) {
        JacocoMethod method = new JacocoMethod(
                "run", "()V", OptionalInt.of(1),
                Map.of(
                        CounterType.COMPLEXITY, new Counter(0, 1),
                        CounterType.INSTRUCTION, new Counter(0, 1)));
        return new JacocoClass(className, Optional.of(sourceFile), List.of(method));
    }
}

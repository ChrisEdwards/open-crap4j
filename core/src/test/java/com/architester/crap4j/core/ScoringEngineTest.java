package com.architester.crap4j.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class ScoringEngineTest {
    @Test
    void scoresComplexityFifteenAtThreeQuarterBranchCoverage() {
        JacocoMethod method = method(
                "execute",
                "()V",
                Map.of(
                        CounterType.COMPLEXITY, new Counter(4, 11),
                        CounterType.BRANCH, new Counter(1, 3),
                        CounterType.INSTRUCTION, new Counter(20, 80)));

        ScoringResult result = new ScoringEngine().score(report(method), Exclusions.none());

        assertThat(result.methods()).singleElement().satisfies(scored -> {
            assertThat(scored.complexity()).isEqualTo(15);
            assertThat(scored.coverage()).isEqualTo(0.75);
            assertThat(scored.coverageKind()).isEqualTo(CoverageKind.BRANCH);
            assertThat(scored.crapScore()).isEqualTo(18.515625);
            assertThat(BigDecimal.valueOf(scored.crapScore()).setScale(2, RoundingMode.HALF_UP))
                    .isEqualByComparingTo("18.52");
        });
    }

    @Test
    void matchesTheRemainingGoldenScores() {
        ScoringResult result = new ScoringEngine().score(
                report(
                        method(
                                "halfCovered",
                                "()V",
                                Map.of(
                                        CounterType.COMPLEXITY, new Counter(7, 7),
                                        CounterType.BRANCH, new Counter(1, 1),
                                        CounterType.INSTRUCTION, new Counter(0, 10))),
                        method(
                                "uncovered",
                                "()V",
                                Map.of(
                                        CounterType.COMPLEXITY, new Counter(3, 0),
                                        CounterType.INSTRUCTION, new Counter(8, 0))),
                        method(
                                "covered",
                                "()V",
                                Map.of(
                                        CounterType.COMPLEXITY, new Counter(0, 8),
                                        CounterType.BRANCH, new Counter(0, 4),
                                        CounterType.INSTRUCTION, new Counter(0, 20)))),
                Exclusions.none());

        assertThat(result.methods())
                .extracting(
                        ScoredMethod::methodName,
                        ScoredMethod::crapScore,
                        ScoredMethod::coverageKind)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "halfCovered", 38.5, CoverageKind.BRANCH),
                        org.assertj.core.groups.Tuple.tuple(
                                "uncovered", 12.0, CoverageKind.INSTRUCTION),
                        org.assertj.core.groups.Tuple.tuple(
                                "covered", 8.0, CoverageKind.BRANCH));
    }

    @Test
    void appliesAllDefaultExclusionsAsOneSwitch() {
        JacocoReport report = report(
                jacocoClass("com/example/Ordinary", "Ordinary.java", "ordinary"),
                jacocoClass("com/example/generated/Factory", "Factory.java", "generated"),
                jacocoClass("com/example/OrderMapperImpl", "OrderMapperImpl.java", "mapper"),
                jacocoClass("com/example/DaggerGraph", "DaggerGraph.java", "dagger"),
                jacocoClass("com/example/Hilt_App", "Hilt_App.java", "hilt"),
                jacocoClass("com/example/AutoValue_Order", "AutoValue_Order.java", "autoValue"));

        ScoringResult withDefaults = new ScoringEngine().score(report, Exclusions.defaults());
        ScoringResult withoutDefaults = new ScoringEngine().score(report, Exclusions.none());

        assertThat(withDefaults.methods())
                .extracting(ScoredMethod::methodName)
                .containsExactly("ordinary");
        assertThat(withDefaults.excluded()).isEqualTo(5);
        assertThat(withoutDefaults.methods()).hasSize(6);
        assertThat(withoutDefaults.excluded()).isZero();
    }

    @Test
    void appliesCustomPathGlobsAndSimpleClassNameRegexesWithoutDefaults() {
        JacocoReport report = report(
                jacocoClass("com/example/internal/Helper", "Helper.java", "internal"),
                jacocoClass("com/example/ClientGenerated", "ClientGenerated.java", "generated"),
                jacocoClass("com/example/DaggerGraph", "DaggerGraph.java", "defaultWouldMatch"),
                jacocoClass("com/example/Ordinary", "Ordinary.java", "ordinary"));
        Exclusions exclusions = new Exclusions(
                List.of("**/internal/*.java"), List.of(".*Generated"), false);

        ScoringResult result = new ScoringEngine().score(report, exclusions);

        assertThat(result.methods())
                .extracting(ScoredMethod::methodName)
                .containsExactly("defaultWouldMatch", "ordinary");
        assertThat(result.excluded()).isEqualTo(2);
    }

    @Test
    void sortsByScoreDescendingThenIdentityAscending() {
        JacocoClass zClass = new JacocoClass(
                "com/example/Zed",
                Optional.of("Zed.java"),
                List.of(
                        scoredMethod("beta", "()V", 5, true),
                        scoredMethod("alpha", "(I)V", 5, true),
                        scoredMethod("alpha", "()V", 5, true),
                        scoredMethod("lowest", "()V", 2, true)));
        JacocoClass aClass = new JacocoClass(
                "com/example/Alpha",
                Optional.of("Alpha.java"),
                List.of(
                        scoredMethod("tie", "()V", 5, true),
                        scoredMethod("highest", "()V", 3, false)));

        ScoringResult result = new ScoringEngine().score(report(zClass, aClass), Exclusions.none());

        assertThat(result.methods())
                .extracting(ScoredMethod::className, ScoredMethod::methodName, ScoredMethod::descriptor)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("com/example/Alpha", "highest", "()V"),
                        org.assertj.core.groups.Tuple.tuple("com/example/Alpha", "tie", "()V"),
                        org.assertj.core.groups.Tuple.tuple("com/example/Zed", "alpha", "()V"),
                        org.assertj.core.groups.Tuple.tuple("com/example/Zed", "alpha", "(I)V"),
                        org.assertj.core.groups.Tuple.tuple("com/example/Zed", "beta", "()V"),
                        org.assertj.core.groups.Tuple.tuple("com/example/Zed", "lowest", "()V"));
    }

    @Test
    void treatsZeroTotalBranchesAsInstructionCoverageAndCountsStaticInitializers() {
        JacocoMethod ordinary = method(
                "ordinary",
                "()V",
                Map.of(
                        CounterType.COMPLEXITY, new Counter(0, 2),
                        CounterType.BRANCH, new Counter(0, 0),
                        CounterType.INSTRUCTION, new Counter(1, 3)));
        JacocoMethod staticInitializer = method(
                "<clinit>",
                "()V",
                Map.of(
                        CounterType.COMPLEXITY, new Counter(0, 1),
                        CounterType.INSTRUCTION, new Counter(0, 1)));

        ScoringResult result =
                new ScoringEngine().score(report(ordinary, staticInitializer), Exclusions.none());

        assertThat(result.methods()).singleElement().satisfies(scored -> {
            assertThat(scored.coverageKind()).isEqualTo(CoverageKind.INSTRUCTION);
            assertThat(scored.coverage()).isEqualTo(0.75);
        });
        assertThat(result.excluded()).isOne();
    }

    private static JacocoReport report(JacocoMethod... methods) {
        return report(new JacocoClass(
                "com/example/Service", Optional.of("Service.java"), List.of(methods)));
    }

    private static JacocoReport report(JacocoClass... classes) {
        return new JacocoReport(
                "test",
                java.util.Arrays.stream(classes)
                        .map(jacocoClass -> new JacocoPackage(
                                jacocoClass.name().substring(0, jacocoClass.name().lastIndexOf('/')),
                                List.of(jacocoClass)))
                        .toList());
    }

    private static JacocoClass jacocoClass(
            String className, String sourceFile, String methodName) {
        return new JacocoClass(
                className,
                Optional.of(sourceFile),
                List.of(method(
                        methodName,
                        "()V",
                        Map.of(
                                CounterType.COMPLEXITY, new Counter(0, 1),
                                CounterType.INSTRUCTION, new Counter(0, 1)))));
    }

    private static JacocoMethod scoredMethod(
            String name, String descriptor, int complexity, boolean covered) {
        return method(
                name,
                descriptor,
                Map.of(
                        CounterType.COMPLEXITY, new Counter(covered ? 0 : complexity, covered ? complexity : 0),
                        CounterType.INSTRUCTION, new Counter(covered ? 0 : 1, covered ? 1 : 0)));
    }

    private static JacocoMethod method(
            String name, String descriptor, Map<CounterType, Counter> counters) {
        return new JacocoMethod(name, descriptor, OptionalInt.of(42), counters);
    }
}

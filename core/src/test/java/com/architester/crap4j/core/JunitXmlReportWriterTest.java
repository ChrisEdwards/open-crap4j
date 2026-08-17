package com.architester.crap4j.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class JunitXmlReportWriterTest {
    @Test
    void write_should_matchGolden_when_fullScenarioWithStatusMapping() {
        BaselineEntry allowance = new BaselineEntry(
                new MethodKey("com/example/Debt", "old", "()V"), 18.52, 15);
        MethodAssessment violation = assessment(
                "com/example/Service$Nested", "<init>", "(Ljava/lang/String;[I)V",
                16, .706, 22.51, MethodStatus.VIOLATION,
                List.of(GateReason.COMPLEXITY_OVER_CAP), Optional.empty());
        MethodAssessment baselined = assessment(
                "com/example/Debt", "old", "()V", 15, .75, 18.515625,
                MethodStatus.BASELINED, List.of(GateReason.CRAP_OVER_THRESHOLD), Optional.of(allowance));
        MethodAssessment passing = assessment(
                "com/example/Clean", "ok", "()Z", 1, 1, 1,
                MethodStatus.OK, List.of(), Optional.empty());
        GateResult result = new GateResult(
                List.of(passing, baselined, violation),
                0,
                List.of(
                        new SlackBaselineEntry(allowance.key(), SlackReason.EXCESS_ALLOWANCE),
                        new SlackBaselineEntry(
                                new MethodKey("com/example/Gone", "gone", "()V"),
                                SlackReason.METHOD_GONE)),
                List.of(),
                false);

        String xml = new JunitXmlReportWriter().write(
                result,
                new GateConfig(15, 15, CoverageSelection.BRANCH_PREFERRED, false, false),
                "crapCheck",
                ReportProducer.GRADLE);

        assertThat(xml).isEqualTo("""
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuites>
                  <testsuite name="crap4j.crapCheck" tests="5" failures="1" errors="0" skipped="3" time="0">
                    <testcase classname="com.example.Service$Nested" name="&lt;init&gt;(String, int[])" time="0">
                      <failure type="complexity-over-cap" message="CRAP 22.51, cc 16, 70.6% branch, over the complexity cap 15"/>
                    </testcase>
                    <testcase classname="com.example.Debt" name="old()" time="0">
                      <skipped message="baselined debt, allowance crap 18.52 cc 15, passing"/>
                    </testcase>
                    <testcase classname="com.example.Clean" name="ok()" time="0"/>
                    <testcase classname="com.example.Debt" name="old() [slack]" time="0">
                      <skipped message="slack: excess-allowance, allowance 18.52, scores 18.52 today, run crapBaselineTighten"/>
                    </testcase>
                    <testcase classname="com.example.Gone" name="gone() [slack]" time="0">
                      <skipped message="slack: method-gone, no such method in the report, run crapBaselineTighten"/>
                    </testcase>
                  </testsuite>
                </testsuites>
                """);
    }

    @Test
    void parameterList_should_decodeAllShapes_when_variousDescriptors() {
        assertThat(JvmDescriptors.parameterList("(Ljava/lang/String;I)V"))
                .isEqualTo("String, int");
        assertThat(JvmDescriptors.parameterList("([[I[Ljava/lang/String;)Ljava/lang/Object;"))
                .isEqualTo("int[][], String[]");
        assertThat(JvmDescriptors.parameterList("(Lcom/example/Outer$Inner;ZBCSJFDC)V"))
                .isEqualTo("Outer.Inner, boolean, byte, char, short, long, float, double, char");
    }

    @Test
    void write_should_matchFailuresToViolations_when_allModes() {
        MethodAssessment violation = assessment(
                "A", "bad", "()V", 4, 0, 20,
                MethodStatus.VIOLATION, List.of(GateReason.CRAP_OVER_THRESHOLD), Optional.empty());
        MethodAssessment baselined = assessment(
                "B", "debt", "()V", 4, 0, 20,
                MethodStatus.BASELINED, List.of(GateReason.CRAP_OVER_THRESHOLD),
                Optional.of(new BaselineEntry(new MethodKey("B", "debt", "()V"), 20, 4)));
        SlackBaselineEntry slack = new SlackBaselineEntry(
                new MethodKey("Gone", "gone", "()V"), SlackReason.METHOD_GONE);
        List<GateResult> scenarios = List.of(
                new GateResult(List.of(), 0, List.of(), List.of(), false),
                new GateResult(List.of(violation), 0, List.of(), List.of(), false),
                new GateResult(List.of(violation), 0, List.of(), List.of(), false), // advisory is identical
                new GateResult(List.of(baselined), 0, List.of(slack), List.of(), false),
                new GateResult(List.of(baselined), 0, List.of(slack), List.of(), true),
                new GateResult(List.of(violation), 0, List.of(), List.of(), false)); // changed-file
        List<GateConfig> configs = List.of(
                config(false, false),
                config(false, false),
                config(false, false),
                config(false, false),
                config(true, false),
                config(false, true));

        for (int index = 0; index < scenarios.size(); index++) {
            GateResult scenario = scenarios.get(index);
            String xml = new JunitXmlReportWriter().write(
                    scenario, configs.get(index), "check", ReportProducer.CLI);

            assertThat(occurrences(xml, "<failure "))
                    .as("scenario %s", index)
                    .isEqualTo(scenario.violations());
            assertThat(xml).contains("failures=\"" + scenario.violations() + "\"");
        }
    }

    @Test
    void write_should_appendDescriptors_when_prettyNameCollisions() {
        GateResult result = new GateResult(
                List.of(
                        assessment("com/example/Api", "parse", "(I)V", 1, 1, 1,
                                MethodStatus.OK, List.of(), Optional.empty()),
                        assessment("com/example/Api", "parse", "(Ljava/lang/String;)V", 1, 1, 1,
                                MethodStatus.OK, List.of(), Optional.empty()),
                        assessment("com/example/Api", "bridge", "()Ljava/lang/String;", 1, 1, 1,
                                MethodStatus.OK, List.of(), Optional.empty()),
                        assessment("com/example/Api", "bridge", "()Ljava/lang/Object;", 1, 1, 1,
                                MethodStatus.OK, List.of(), Optional.empty())),
                0, List.of(), List.of(), false);

        String xml = new JunitXmlReportWriter().write(
                result, config(false, false), "check", ReportProducer.CLI);

        assertThat(xml)
                .contains("name=\"parse(int)\"", "name=\"parse(String)\"")
                .contains("name=\"bridge() [()Ljava/lang/Object;]\"")
                .contains("name=\"bridge() [()Ljava/lang/String;]\"")
                .doesNotContain("parse(int) [", "parse(String) [");
    }

    @Test
    void write_should_writeValidSuite_when_emptyAnalysis() {
        String xml = new JunitXmlReportWriter().write(
                new GateResult(List.of(), 0, List.of(), List.of(), false),
                config(false, false),
                "report",
                ReportProducer.CLI);

        assertThat(xml).isEqualTo("""
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuites>
                  <testsuite name="crap4j.report" tests="0" failures="0" errors="0" skipped="0" time="0">
                  </testsuite>
                </testsuites>
                """);
    }

    @Test
    void write_should_matchGoldens_when_allLockedScenarios() throws IOException {
        BaselineEntry debt = new BaselineEntry(new MethodKey("example/Debt", "work", "()V"), 20, 4);
        BaselineEntry improved =
                new BaselineEntry(new MethodKey("example/Improved", "better", "()V"), 20, 4);
        MethodAssessment violation = assessment(
                "example/Debt", "work", "()V", 4, 0, 20,
                MethodStatus.VIOLATION, List.of(GateReason.CRAP_OVER_THRESHOLD), Optional.empty());
        MethodAssessment baselined = assessment(
                "example/Debt", "work", "()V", 4, 0, 20,
                MethodStatus.BASELINED, List.of(GateReason.CRAP_OVER_THRESHOLD), Optional.of(debt));
        MethodAssessment better = assessment(
                "example/Improved", "better", "()V", 1, 1, 1,
                MethodStatus.OK, List.of(), Optional.of(improved));
        MethodAssessment regression = assessment(
                "example/Debt", "work", "()V", 5, 0, 30,
                MethodStatus.VIOLATION,
                List.of(GateReason.CRAP_REGRESSED, GateReason.COMPLEXITY_REGRESSED), Optional.of(debt));
        MethodAssessment clean = assessment(
                "example/Clean", "ok", "()V", 1, 1, 1,
                MethodStatus.OK, List.of(), Optional.empty());
        List<SlackBaselineEntry> slack = List.of(
                new SlackBaselineEntry(debt.key(), SlackReason.EXCESS_ALLOWANCE),
                new SlackBaselineEntry(improved.key(), SlackReason.UNDER_LIMITS));

        List<Scenario> scenarios = List.of(
                new Scenario("s1", gate(List.of(violation), List.of(), false), config(false, false), "crapCheck"),
                new Scenario("s2", gate(List.of(baselined), List.of(), false), config(false, false), "crapCheck"),
                new Scenario("s3", gate(List.of(baselined, better), slack, false), config(false, false), "crapCheck"),
                new Scenario("s4", gate(List.of(baselined, better), slack, true), config(true, false), "crapCheck"),
                new Scenario("s5", gate(List.of(regression), List.of(), false), config(false, false), "crapCheck"),
                new Scenario("s6", gate(List.of(), List.of(), false),
                        new GateConfig(250, 15, CoverageSelection.BRANCH_PREFERRED, false, false), "crapCheck"),
                new Scenario("s7", gate(List.of(violation), List.of(), false), config(false, false), "crapReport"),
                new Scenario("s8", gate(List.of(clean), List.of(), false), config(false, false), "crapCheck"),
                new Scenario("changed-file", gate(List.of(violation), List.of(), false), config(false, true), "check"));

        for (Scenario scenario : scenarios) {
            String xml = new JunitXmlReportWriter().write(
                    scenario.result(), scenario.config(), scenario.producerName(), ReportProducer.GRADLE);

            assertThat(xml).as(scenario.name()).isEqualTo(golden(scenario.name()));
            assertThat(occurrences(xml, "<failure ")).isEqualTo(scenario.result().violations());
        }
    }

    @Test
    void write_should_escapeXmlChars_when_specialCharactersInNames() {
        GateResult result = gate(List.of(assessment(
                "example/A&B", "say\"'", "()V", 1, 1, 1,
                MethodStatus.OK, List.of(), Optional.empty())), List.of(), false);

        String xml = new JunitXmlReportWriter().write(
                result, config(false, false), "check & \"quote\"", ReportProducer.CLI);

        assertThat(xml)
                .contains("name=\"crap4j.check &amp; &quot;quote&quot;\"")
                .contains("classname=\"example.A&amp;B\"")
                .contains("name=\"say&quot;&apos;()\"");
        assertThatThrownBy(() -> JvmDescriptors.parameterList("not-a-descriptor"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JvmDescriptors.parameterList("([V)V"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JvmDescriptors.parameterList("(Ljava/lang/String)V"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static GateResult gate(
            List<MethodAssessment> methods, List<SlackBaselineEntry> slack, boolean tight) {
        return new GateResult(methods, 0, slack, List.of(), tight);
    }

    private static String golden(String name) throws IOException {
        try (var input = JunitXmlReportWriterTest.class.getResourceAsStream(
                "/junit-report/" + name + ".xml")) {
            if (input == null) {
                throw new IOException("Missing golden " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static GateConfig config(boolean tight, boolean changedFile) {
        return new GateConfig(15, 15, CoverageSelection.BRANCH_PREFERRED, tight, changedFile);
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private record Scenario(
            String name, GateResult result, GateConfig config, String producerName) {}

    private static MethodAssessment assessment(
            String className,
            String method,
            String descriptor,
            int complexity,
            double coverage,
            double crap,
            MethodStatus status,
            List<GateReason> reasons,
            Optional<BaselineEntry> allowance) {
        return new MethodAssessment(
                new ScoredMethod(
                        className, method, descriptor, Optional.empty(), OptionalInt.empty(),
                        complexity, coverage, CoverageKind.BRANCH, crap),
                status, reasons, allowance);
    }
}

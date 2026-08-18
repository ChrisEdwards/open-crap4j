package com.architester.crap4j.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class CliArgumentsTest {
    @Test
    void parse_should_throw_when_emptyArgs() {
        assertThatThrownBy(() -> CliArguments.parse(new String[]{}))
                .isInstanceOf(ArrayIndexOutOfBoundsException.class);
    }

    @Test
    void parse_should_throw_when_unknownVerb() {
        assertThatThrownBy(() -> CliArguments.parse(new String[]{"invalid"}))
                .isInstanceOf(UsageException.class)
                .hasMessageContaining("Unknown verb");
    }

    @Test
    void parse_should_throw_when_showPassingNegative() {
        assertThatThrownBy(() -> CliArguments.parse(new String[]{
                "check", "--report", "r.xml", "--show-passing", "-1"}))
                .isInstanceOf(UsageException.class)
                .hasMessageContaining("non-negative");
    }

    @Test
    void parse_should_throw_when_useDefaultExclusionsInvalid() {
        assertThatThrownBy(() -> CliArguments.parse(new String[]{
                "check", "--report", "r.xml", "--use-default-exclusions", "maybe"}))
                .isInstanceOf(UsageException.class)
                .hasMessageContaining("Invalid value");
    }

    @Test
    void parse_should_throw_when_advisoryOnNonCheckVerb() {
        assertThatThrownBy(() -> CliArguments.parse(new String[]{
                "report", "--report", "r.xml", "--advisory"}))
                .isInstanceOf(UsageException.class)
                .hasMessageContaining("only allowed on check");
    }

    @Test
    void parse_should_parseReportName_when_provided() {
        CliArguments result = CliArguments.parse(new String[]{
                "check", "--report", "r.xml", "--report-name", "myreport"});

        assertThat(result.reportName()).isEqualTo(Optional.of("myreport"));
    }

    @Test
    void parse_should_parseSourceRoot_when_withGithubAnnotations() {
        CliArguments result = CliArguments.parse(new String[]{
                "check", "--report", "r.xml", "--github-annotations",
                "--source-root", "src/main/java"});

        assertThat(result.sourceRoot()).isEqualTo(Optional.of("src/main/java"));
    }
}

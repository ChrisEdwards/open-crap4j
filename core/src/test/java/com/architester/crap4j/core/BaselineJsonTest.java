package com.architester.crap4j.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class BaselineJsonTest {
    @Test
    void serializesSortedEntriesAndRoundTripsByteIdentically() {
        Baseline baseline = new Baseline(
                1,
                "0.1.0",
                "2026-08-12T00:00:00Z",
                CoverageSelection.BRANCH_PREFERRED,
                15.25,
                15,
                List.of(
                        new BaselineEntry(
                                new MethodKey("com/example/Zed", "run", "()V"), 18.5, 15),
                        new BaselineEntry(
                                new MethodKey("com/example/Alpha", "run", "(I)V"), 20.0, 16)));

        String json = BaselineJson.write(baseline);

        assertThat(json).isEqualTo("""
                {
                  "formatVersion": 1,
                  "toolVersion": "0.1.0",
                  "generated": "2026-08-12T00:00:00Z",
                  "coverageSelection": "branch-preferred",
                  "threshold": 15.25,
                  "complexityCap": 15,
                  "entries": [
                    {
                      "class": "com/example/Alpha",
                      "method": "run",
                      "descriptor": "(I)V",
                      "crap": 20.00,
                      "complexity": 16
                    },
                    {
                      "class": "com/example/Zed",
                      "method": "run",
                      "descriptor": "()V",
                      "crap": 18.50,
                      "complexity": 15
                    }
                  ]
                }
                """);
        assertThat(BaselineJson.write(BaselineJson.read(json))).isEqualTo(json);
    }

    @Test
    void serializesEmptyEntriesAndRoundTripsByteIdentically() {
        Baseline baseline = new Baseline(
                1,
                "0.1.0",
                "2026-08-12T00:00:00Z",
                CoverageSelection.BRANCH_PREFERRED,
                15.0,
                15,
                List.of());

        String json = BaselineJson.write(baseline);

        assertThat(json).isEqualTo("""
                {
                  "formatVersion": 1,
                  "toolVersion": "0.1.0",
                  "generated": "2026-08-12T00:00:00Z",
                  "coverageSelection": "branch-preferred",
                  "threshold": 15.0,
                  "complexityCap": 15,
                  "entries": [  ]
                }
                """);
        assertThat(BaselineJson.write(BaselineJson.read(json))).isEqualTo(json);
    }

    @Test
    void rejectsMalformedJson() {
        assertThatThrownBy(() -> BaselineJson.read("{"))
                .isInstanceOf(BaselineParseException.class)
                .hasMessageContaining("Expected an object key");
    }

    @Test
    void rejectsMissingRequiredFields() {
        String json = validEmptyBaselineJson().replace("  \"toolVersion\": \"0.1.0\",\n", "");

        assertThatThrownBy(() -> BaselineJson.read(json))
                .isInstanceOf(BaselineParseException.class)
                .hasMessage("Missing required field: toolVersion");
    }

    @Test
    void rejectsFieldsWithWrongTypes() {
        String json = validEmptyBaselineJson().replace("\"complexityCap\": 15", "\"complexityCap\": \"15\"");

        assertThatThrownBy(() -> BaselineJson.read(json))
                .isInstanceOf(BaselineParseException.class)
                .hasMessage("complexityCap must be a number");
    }

    @Test
    void roundTripsStringEscapes() {
        Baseline baseline = new Baseline(
                1,
                "v\"\\\b\f\n\r\t\u0001",
                "2026-08-12T00:00:00Z",
                CoverageSelection.BRANCH_PREFERRED,
                15.0,
                15,
                List.of());

        String json = BaselineJson.write(baseline);

        assertThat(json).contains("\"toolVersion\": \"v\\\"\\\\\\b\\f\\n\\r\\t\\u0001\"");
        Baseline parsed = BaselineJson.read(json);
        assertThat(parsed.toolVersion()).isEqualTo("v\"\\\b\f\n\r\t\u0001");
    }

    @Test
    void parsesSlashAndUnicodeEscapes() {
        String json = validEmptyBaselineJson().replace("\"0.1.0\"", "\"a\\/b\\u0041\"");

        Baseline parsed = BaselineJson.read(json);

        assertThat(parsed.toolVersion()).isEqualTo("a/bA");
    }

    @Test
    void rejectsUnterminatedString() {
        assertThatThrownBy(() -> BaselineJson.read("{\"a\": \"no end"))
                .isInstanceOf(BaselineParseException.class)
                .hasMessageContaining("Unterminated string");
    }

    @Test
    void rejectsUnterminatedEscape() {
        assertThatThrownBy(() -> BaselineJson.read("{\"a\": \"ab\\"))
                .isInstanceOf(BaselineParseException.class)
                .hasMessageContaining("Unterminated escape");
    }

    @Test
    void rejectsInvalidEscape() {
        assertThatThrownBy(() -> BaselineJson.read("{\"a\": \"\\x\"}"))
                .isInstanceOf(BaselineParseException.class)
                .hasMessageContaining("Invalid escape");
    }

    @Test
    void rejectsIncompleteUnicodeEscape() {
        assertThatThrownBy(() -> BaselineJson.read("{\"a\": \"\\u12\"}"))
                .isInstanceOf(BaselineParseException.class)
                .hasMessageContaining("unicode escape");
    }

    @Test
    void rejectsInvalidUnicodeEscape() {
        assertThatThrownBy(() -> BaselineJson.read("{\"a\": \"\\u00zz\"}"))
                .isInstanceOf(BaselineParseException.class)
                .hasMessageContaining("Invalid unicode escape");
    }

    @Test
    void rejectsTrailingContent() {
        assertThatThrownBy(() -> BaselineJson.read(validEmptyBaselineJson() + "extra"))
                .isInstanceOf(BaselineParseException.class)
                .hasMessageContaining("Unexpected trailing content");
    }

    @Test
    void rejectsPlusPrefixedNumbers() {
        String json = validEmptyBaselineJson().replace("\"threshold\": 15.0", "\"threshold\": +15.0");

        assertThatThrownBy(() -> BaselineJson.read(json))
                .isInstanceOf(BaselineParseException.class)
                .hasMessageContaining("Expected a number");
    }

    private static String validEmptyBaselineJson() {
        return """
                {
                  "formatVersion": 1,
                  "toolVersion": "0.1.0",
                  "generated": "2026-08-12T00:00:00Z",
                  "coverageSelection": "branch-preferred",
                  "threshold": 15.0,
                  "complexityCap": 15,
                  "entries": []
                }
                """;
    }
}

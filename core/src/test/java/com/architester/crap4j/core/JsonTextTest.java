package com.architester.crap4j.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JsonTextTest {
    @Test
    void quoted_should_wrapInQuotes_when_plainString() {
        assertThat(JsonText.quoted("hello")).isEqualTo("\"hello\"");
    }

    @Test
    void quoted_should_escapeQuoteAndBackslash_when_specialChars() {
        assertThat(JsonText.quoted("a\"b\\c")).isEqualTo("\"a\\\"b\\\\c\"");
    }

    @Test
    void quoted_should_escapeNamedSequences_when_controlChars() {
        assertThat(JsonText.quoted("\b\f\n\r\t"))
                .isEqualTo("\"\\b\\f\\n\\r\\t\"");
    }

    @Test
    void quoted_should_escapeAsUnicode_when_lowControlChars() {
        assertThat(JsonText.quoted(""))
                .isEqualTo("\"\\u0001\\u001f\"");
    }

    @Test
    void decimal_should_throw_when_infinity() {
        assertThatThrownBy(() -> JsonText.decimal(Double.POSITIVE_INFINITY, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decimal_should_throw_when_nan() {
        assertThatThrownBy(() -> JsonText.decimal(Double.NaN, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void policyDecimal_should_preserveTrailingZero_when_wholeNumber() {
        assertThat(JsonText.policyDecimal(15.0)).isEqualTo("15.0");
    }

    @Test
    void policyDecimal_should_stripTrailingZeros_when_extraPrecision() {
        assertThat(JsonText.policyDecimal(15.250)).isEqualTo("15.25");
    }
}

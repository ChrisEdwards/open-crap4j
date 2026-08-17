package com.architester.crap4j.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JsonTextTest {
    @Test
    void quotesPlainString() {
        assertThat(JsonText.quoted("hello")).isEqualTo("\"hello\"");
    }

    @Test
    void escapesSpecialCharacters() {
        assertThat(JsonText.quoted("a\"b\\c")).isEqualTo("\"a\\\"b\\\\c\"");
    }

    @Test
    void escapesControlCharacters() {
        assertThat(JsonText.quoted("\b\f\n\r\t"))
                .isEqualTo("\"\\b\\f\\n\\r\\t\"");
    }

    @Test
    void escapesLowControlCharsAsUnicode() {
        assertThat(JsonText.quoted(""))
                .isEqualTo("\"\\u0001\\u001f\"");
    }

    @Test
    void rejectsInfiniteDecimal() {
        assertThatThrownBy(() -> JsonText.decimal(Double.POSITIVE_INFINITY, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNaN() {
        assertThatThrownBy(() -> JsonText.decimal(Double.NaN, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void policyDecimalPreservesTrailingZero() {
        assertThat(JsonText.policyDecimal(15.0)).isEqualTo("15.0");
    }

    @Test
    void policyDecimalStripsExtraTrailingZeros() {
        assertThat(JsonText.policyDecimal(15.250)).isEqualTo("15.25");
    }
}

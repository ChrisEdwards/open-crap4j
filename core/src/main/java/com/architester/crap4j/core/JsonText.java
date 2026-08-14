package com.architester.crap4j.core;

import java.math.BigDecimal;
import java.util.Locale;

/** Shared JSON scalar formatting for the dependency-free report and baseline writers. */
final class JsonText {
    private JsonText() {}

    static String decimal(double value, int scale) {
        requireFinite(value);
        return String.format(Locale.ROOT, "%." + scale + "f", value);
    }

    static String policyDecimal(double value) {
        requireFinite(value);
        String decimal = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
        return decimal.contains(".") ? decimal : decimal + ".0";
    }

    static String quoted(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (character < 0x20) {
                        result.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
                    } else {
                        result.append(character);
                    }
                }
            }
        }
        return result.append('\"').toString();
    }

    private static void requireFinite(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("JSON numbers must be finite");
        }
    }
}

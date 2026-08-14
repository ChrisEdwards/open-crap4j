package com.architester.crap4j.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Reads and writes the dependency-free baseline JSON format. */
public final class BaselineJson {
    private BaselineJson() {}

    public static Baseline read(Path path) throws IOException {
        return read(Files.readString(path));
    }

    public static Baseline read(String json) {
        try {
            Map<String, Object> root = object(new Parser(json).parse(), "baseline");
            List<BaselineEntry> entries = new ArrayList<>();
            for (Object value : array(required(root, "entries"), "entries")) {
                Map<String, Object> entry = object(value, "entry");
                entries.add(new BaselineEntry(
                        new MethodKey(
                                string(required(entry, "class"), "class"),
                                string(required(entry, "method"), "method"),
                                string(required(entry, "descriptor"), "descriptor")),
                        number(required(entry, "crap"), "crap").doubleValue(),
                        integer(required(entry, "complexity"), "complexity")));
            }
            return new Baseline(
                    integer(required(root, "formatVersion"), "formatVersion"),
                    string(required(root, "toolVersion"), "toolVersion"),
                    string(required(root, "generated"), "generated"),
                    CoverageSelection.fromSerializedName(
                            string(required(root, "coverageSelection"), "coverageSelection")),
                    number(required(root, "threshold"), "threshold").doubleValue(),
                    integer(required(root, "complexityCap"), "complexityCap"),
                    entries);
        } catch (BaselineParseException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BaselineParseException("Invalid baseline: " + exception.getMessage(), exception);
        }
    }

    public static void write(Path path, Baseline baseline) throws IOException {
        Files.writeString(path, write(baseline));
    }

    public static String write(Baseline baseline) {
        StringBuilder json = new StringBuilder();
        json.append("{\n")
                .append("  \"formatVersion\": ").append(baseline.formatVersion()).append(",\n")
                .append("  \"toolVersion\": ").append(quoted(baseline.toolVersion())).append(",\n")
                .append("  \"generated\": ").append(quoted(baseline.generated())).append(",\n")
                .append("  \"coverageSelection\": ")
                .append(quoted(baseline.coverageSelection().serializedName())).append(",\n")
                .append("  \"threshold\": ").append(policyDecimal(baseline.threshold())).append(",\n")
                .append("  \"complexityCap\": ").append(baseline.complexityCap()).append(",\n")
                .append("  \"entries\": [");
        if (!baseline.entries().isEmpty()) {
            json.append('\n');
        }
        for (int index = 0; index < baseline.entries().size(); index++) {
            BaselineEntry entry = baseline.entries().get(index);
            json.append("    {\n")
                    .append("      \"class\": ").append(quoted(entry.key().className())).append(",\n")
                    .append("      \"method\": ").append(quoted(entry.key().methodName())).append(",\n")
                    .append("      \"descriptor\": ").append(quoted(entry.key().descriptor())).append(",\n")
                    .append("      \"crap\": ").append(decimal(entry.crapScore(), 2)).append(",\n")
                    .append("      \"complexity\": ").append(entry.complexity()).append("\n")
                    .append("    }");
            json.append(index + 1 < baseline.entries().size() ? ",\n" : "\n");
        }
        return json.append("  ]\n}\n").toString();
    }

    private static String decimal(double value, int scale) {
        return String.format(Locale.ROOT, "%." + scale + "f", value);
    }

    private static String policyDecimal(double value) {
        String decimal = java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
        return decimal.contains(".") ? decimal : decimal + ".0";
    }

    private static String quoted(String value) {
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

    private static Object required(Map<String, Object> object, String field) {
        if (!object.containsKey(field)) {
            throw new BaselineParseException("Missing required field: " + field);
        }
        return object.get(field);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String field) {
        if (!(value instanceof Map<?, ?>)) {
            throw new BaselineParseException(field + " must be an object");
        }
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> array(Object value, String field) {
        if (!(value instanceof List<?>)) {
            throw new BaselineParseException(field + " must be an array");
        }
        return (List<Object>) value;
    }

    private static String string(Object value, String field) {
        if (!(value instanceof String text)) {
            throw new BaselineParseException(field + " must be a string");
        }
        return text;
    }

    private static Number number(Object value, String field) {
        if (!(value instanceof Number number)) {
            throw new BaselineParseException(field + " must be a number");
        }
        return number;
    }

    private static int integer(Object value, String field) {
        Number number = number(value, field);
        int integer = number.intValue();
        if (number.doubleValue() != integer) {
            throw new BaselineParseException(field + " must be an integer");
        }
        return integer;
    }

    private static final class Parser {
        private final String input;
        private int position;

        private Parser(String input) {
            this.input = input;
        }

        private Object parse() {
            Object value = value();
            whitespace();
            if (position != input.length()) {
                fail("Unexpected trailing content");
            }
            return value;
        }

        private Object value() {
            whitespace();
            if (position >= input.length()) {
                fail("Expected a value");
            }
            return switch (input.charAt(position)) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            position++;
            Map<String, Object> result = new LinkedHashMap<>();
            whitespace();
            if (consume('}')) {
                return result;
            }
            do {
                whitespace();
                if (position >= input.length() || input.charAt(position) != '"') {
                    fail("Expected an object key");
                }
                String key = string();
                whitespace();
                expect(':');
                result.put(key, value());
                whitespace();
            } while (consume(','));
            expect('}');
            return result;
        }

        private List<Object> array() {
            position++;
            List<Object> result = new ArrayList<>();
            whitespace();
            if (consume(']')) {
                return result;
            }
            do {
                result.add(value());
                whitespace();
            } while (consume(','));
            expect(']');
            return result;
        }

        private String string() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (position < input.length()) {
                char character = input.charAt(position++);
                if (character == '"') {
                    return result.toString();
                }
                if (character != '\\') {
                    result.append(character);
                    continue;
                }
                if (position >= input.length()) {
                    fail("Unterminated escape");
                }
                char escape = input.charAt(position++);
                switch (escape) {
                    case '"', '\\', '/' -> result.append(escape);
                    case 'b' -> result.append('\b');
                    case 'f' -> result.append('\f');
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    case 'u' -> result.append(unicode());
                    default -> fail("Invalid escape");
                }
            }
            fail("Unterminated string");
            return "";
        }

        private char unicode() {
            if (position + 4 > input.length()) {
                fail("Incomplete unicode escape");
            }
            try {
                char value = (char) Integer.parseInt(input.substring(position, position + 4), 16);
                position += 4;
                return value;
            } catch (NumberFormatException exception) {
                fail("Invalid unicode escape");
                return 0;
            }
        }

        private Number number() {
            int start = position;
            while (position < input.length()
                    && "-0123456789.eE".indexOf(input.charAt(position)) >= 0) {
                position++;
            }
            if (start == position) {
                fail("Expected a number");
            }
            try {
                return Double.valueOf(input.substring(start, position));
            } catch (NumberFormatException exception) {
                fail("Invalid number");
                return 0;
            }
        }

        private Object literal(String literal, Object value) {
            if (!input.startsWith(literal, position)) {
                fail("Invalid literal");
            }
            position += literal.length();
            return value;
        }

        private void whitespace() {
            while (position < input.length() && Character.isWhitespace(input.charAt(position))) {
                position++;
            }
        }

        private boolean consume(char expected) {
            if (position < input.length() && input.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        private void expect(char expected) {
            if (!consume(expected)) {
                fail("Expected '" + expected + "'");
            }
        }

        private void fail(String message) {
            throw new BaselineParseException(message + " at character " + position);
        }
    }
}

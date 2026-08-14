package com.architester.crap4j.core;

import java.util.ArrayList;
import java.util.List;

/** Decodes JVM method descriptors for display surfaces. */
final class JvmDescriptors {
    private JvmDescriptors() {}

    static String parameterList(String descriptor) {
        if (descriptor == null || descriptor.isEmpty() || descriptor.charAt(0) != '(') {
            throw invalid(descriptor);
        }
        List<String> parameters = new ArrayList<>();
        int index = 1;
        while (index < descriptor.length() && descriptor.charAt(index) != ')') {
            DecodedType decoded = decodeType(descriptor, index);
            parameters.add(decoded.name());
            index = decoded.nextIndex();
        }
        if (index >= descriptor.length() || descriptor.charAt(index) != ')') {
            throw invalid(descriptor);
        }
        int returnIndex = index + 1;
        if (returnIndex >= descriptor.length()) {
            throw invalid(descriptor);
        }
        int end = descriptor.charAt(returnIndex) == 'V'
                ? returnIndex + 1
                : decodeType(descriptor, returnIndex).nextIndex();
        if (end != descriptor.length()) {
            throw invalid(descriptor);
        }
        return String.join(", ", parameters);
    }

    private static DecodedType decodeType(String descriptor, int start) {
        int index = start;
        int dimensions = 0;
        while (index < descriptor.length() && descriptor.charAt(index) == '[') {
            dimensions++;
            index++;
        }
        if (index >= descriptor.length()) {
            throw invalid(descriptor);
        }
        String name;
        switch (descriptor.charAt(index)) {
            case 'Z' -> name = "boolean";
            case 'B' -> name = "byte";
            case 'C' -> name = "char";
            case 'S' -> name = "short";
            case 'I' -> name = "int";
            case 'J' -> name = "long";
            case 'F' -> name = "float";
            case 'D' -> name = "double";
            case 'L' -> {
                int terminator = descriptor.indexOf(';', index);
                if (terminator < 0 || terminator == index + 1) {
                    throw invalid(descriptor);
                }
                String binaryName = descriptor.substring(index + 1, terminator);
                int separator = binaryName.lastIndexOf('/');
                name = binaryName.substring(separator + 1).replace('$', '.');
                index = terminator;
            }
            default -> throw invalid(descriptor);
        }
        StringBuilder display = new StringBuilder(name);
        display.append("[]".repeat(dimensions));
        return new DecodedType(display.toString(), index + 1);
    }

    private static IllegalArgumentException invalid(String descriptor) {
        return new IllegalArgumentException("Invalid JVM method descriptor: " + descriptor);
    }

    private record DecodedType(String name, int nextIndex) {}
}

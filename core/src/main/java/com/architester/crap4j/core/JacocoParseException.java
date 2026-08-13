package com.architester.crap4j.core;

/** Indicates that an input is not a readable JaCoCo XML report. */
public final class JacocoParseException extends Exception {
    public JacocoParseException(String message, Throwable cause) {
        super(message, cause);
    }

    public JacocoParseException(String message) {
        super(message);
    }
}

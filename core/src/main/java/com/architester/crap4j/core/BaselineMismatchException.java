package com.architester.crap4j.core;

/** A semantic baseline mismatch that makes stored scores unsafe to compare. */
public final class BaselineMismatchException extends IllegalArgumentException {
    public BaselineMismatchException(String message) {
        super(message);
    }
}

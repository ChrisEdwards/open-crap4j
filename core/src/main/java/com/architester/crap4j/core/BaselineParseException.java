package com.architester.crap4j.core;

/** Indicates that a baseline file is not valid baseline JSON. */
public final class BaselineParseException extends IllegalArgumentException {
    public BaselineParseException(String message) {
        super(message);
    }

    public BaselineParseException(String message, Throwable cause) {
        super(message, cause);
    }
}

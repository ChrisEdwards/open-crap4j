package com.architester.crap4j.core;

import java.util.Objects;

/** Stable method identity used for baseline matching. */
public record MethodKey(String className, String methodName, String descriptor)
        implements Comparable<MethodKey> {
    public MethodKey {
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(methodName, "methodName");
        Objects.requireNonNull(descriptor, "descriptor");
    }

    public static MethodKey of(ScoredMethod method) {
        return new MethodKey(method.className(), method.methodName(), method.descriptor());
    }

    @Override
    public int compareTo(MethodKey other) {
        int byClass = className.compareTo(other.className);
        if (byClass != 0) {
            return byClass;
        }
        int byMethod = methodName.compareTo(other.methodName);
        return byMethod != 0 ? byMethod : descriptor.compareTo(other.descriptor);
    }
}

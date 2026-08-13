package org.opencrap4j.core;

import java.util.List;
import java.util.Objects;

/** A package from a JaCoCo report. */
public record JacocoPackage(String name, List<JacocoClass> classes) {
    public JacocoPackage {
        Objects.requireNonNull(name, "name");
        classes = List.copyOf(classes);
    }
}

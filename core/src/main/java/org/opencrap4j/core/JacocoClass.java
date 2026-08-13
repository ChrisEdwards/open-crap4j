package org.opencrap4j.core;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A class from a JaCoCo package, retaining its slash-form name verbatim. */
public record JacocoClass(String name, Optional<String> sourceFile, List<JacocoMethod> methods) {
    public JacocoClass {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(sourceFile, "sourceFile");
        methods = List.copyOf(methods);
    }
}

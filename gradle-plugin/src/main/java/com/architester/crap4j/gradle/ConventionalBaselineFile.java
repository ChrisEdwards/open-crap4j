package com.architester.crap4j.gradle;

import java.io.File;
import java.io.Serializable;
import org.gradle.api.file.RegularFile;

/** Marks the extension's default baseline value so tasks can preserve explicit-path semantics. */
record ConventionalBaselineFile(File asFile) implements RegularFile, Serializable {
    @Override
    public File getAsFile() {
        return asFile;
    }
}

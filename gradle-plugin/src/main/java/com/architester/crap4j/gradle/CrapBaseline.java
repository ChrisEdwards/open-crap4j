package com.architester.crap4j.gradle;

import com.architester.crap4j.core.Baseline;
import com.architester.crap4j.core.BaselineJson;
import com.architester.crap4j.core.BaselineOperations;
import java.nio.file.Files;
import java.time.Instant;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

/** Writes a new baseline from the current report. */
public abstract class CrapBaseline extends AbstractCrapTask {
    @OutputFile
    public abstract RegularFileProperty getBaseline();

    @TaskAction
    public final void baseline() {
        try {
            Baseline created = new BaselineOperations().rebaseline(
                    score(), config(), Crap4jPlugin.toolVersion(), Instant.now().toString());
            Files.createDirectories(getBaseline().get().getAsFile().toPath().getParent());
            BaselineJson.write(getBaseline().get().getAsFile().toPath(), created);
            getLogger().lifecycle("Wrote baseline {}", getBaseline().get().getAsFile());
        } catch (Exception exception) {
            throw new GradleException("crap4j baseline failed: " + exception.getMessage(), exception);
        }
    }
}

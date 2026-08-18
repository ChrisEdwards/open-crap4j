package com.architester.crap4j.gradle;

import com.architester.crap4j.core.Baseline;
import com.architester.crap4j.core.BaselineGate;
import com.architester.crap4j.core.BaselineJson;
import com.architester.crap4j.core.BaselineOperations;
import com.architester.crap4j.core.ConfigWarning;
import com.architester.crap4j.core.GateConfig;
import com.architester.crap4j.core.GateResult;
import com.architester.crap4j.core.ScoringResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/** Shrinks an existing baseline without admitting new debt. */
@DisableCachingByDefault(because = "The task updates a source-controlled baseline in place")
public abstract class CrapBaselineTighten extends AbstractCrapTask {
    @OutputFile
    public abstract RegularFileProperty getBaseline();

    @TaskAction
    public final void tighten() {
        Path path = getBaseline().get().getAsFile().toPath();
        if (!Files.isRegularFile(path)) {
            throw new GradleException("Cannot tighten: no baseline file exists at " + path);
        }
        try {
            ScoringResult scoring = score();
            GateConfig gateConfig = config();
            Baseline existing = BaselineJson.read(path);
            GateResult preflight = new BaselineGate().evaluate(scoring, Optional.of(existing), gateConfig);
            for (ConfigWarning warning : preflight.configWarnings()) {
                getLogger().warn("Baseline {} differs from current configuration; tightening with current configuration",
                        warning == ConfigWarning.THRESHOLD_MISMATCH ? "threshold" : "complexity cap");
            }
            Baseline tightened = new BaselineOperations().tighten(
                    existing, scoring, gateConfig,
                    Crap4jPlugin.toolVersion(), Instant.now().toString());
            BaselineJson.write(path, tightened);
            getLogger().lifecycle("Wrote tightened baseline {}", path);
        } catch (Exception exception) {
            throw new GradleException("crap4j baseline tighten failed: " + exception.getMessage(), exception);
        }
    }
}

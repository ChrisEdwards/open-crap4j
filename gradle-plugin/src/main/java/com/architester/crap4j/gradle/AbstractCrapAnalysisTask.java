package com.architester.crap4j.gradle;

import com.architester.crap4j.core.Baseline;
import com.architester.crap4j.core.BaselineGate;
import com.architester.crap4j.core.BaselineJson;
import com.architester.crap4j.core.ConfigWarning;
import com.architester.crap4j.core.GateConfig;
import com.architester.crap4j.core.GateResult;
import com.architester.crap4j.core.JsonReportWriter;
import com.architester.crap4j.core.JunitXmlReportWriter;
import com.architester.crap4j.core.ReportProducer;
import com.architester.crap4j.core.ScoringResult;
import com.architester.crap4j.core.TextReportOutput;
import com.architester.crap4j.core.TextReportWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalInt;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

/** Thin Gradle wrapper around core gating and report writers. */
public abstract class AbstractCrapAnalysisTask extends AbstractCrapTask {
    @InputFile
    @org.gradle.api.tasks.Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getBaseline();

    @Internal
    public abstract RegularFileProperty getConventionalBaseline();

    @InputFile
    @org.gradle.api.tasks.Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public final Provider<RegularFile> getExistingConventionalBaseline() {
        return getConventionalBaseline().filter(file -> file.getAsFile().isFile());
    }

    @Input
    public abstract Property<Boolean> getAdvisory();

    @Input
    public abstract Property<Boolean> getJsonEnabled();

    @Input
    public abstract Property<Boolean> getJunitXmlEnabled();

    @Internal
    public abstract RegularFileProperty getJsonReport();

    @OutputFile
    @org.gradle.api.tasks.Optional
    public final Provider<RegularFile> getEnabledJsonReport() {
        return getJsonReport().filter(file -> getJsonEnabled().get());
    }

    @Internal
    public abstract RegularFileProperty getJunitXmlReport();

    @OutputFile
    @org.gradle.api.tasks.Optional
    public final Provider<RegularFile> getEnabledJunitXmlReport() {
        return getJunitXmlReport().filter(file -> getJunitXmlEnabled().get());
    }

    protected final GateResult analyze(boolean permanentlyAdvisory) {
        try {
            ScoringResult scoring = score();
            GateConfig config = config();
            Optional<Baseline> baseline = readBaseline();
            GateResult gate = new BaselineGate().evaluate(scoring, baseline, config);
            for (ConfigWarning warning : gate.configWarnings()) {
                getLogger().warn("Baseline {} differs from current configuration; gating with current configuration",
                        warning == ConfigWarning.THRESHOLD_MISMATCH ? "threshold" : "complexity cap");
            }

            boolean advisory = permanentlyAdvisory || getAdvisory().get();
            Optional<String> baselineDisplay = baseline.map(ignored -> displayBaseline());
            TextReportOutput text = new TextReportWriter().render(
                    gate, config, advisory, baselineDisplay, ReportProducer.GRADLE, OptionalInt.empty());
            if (!text.diagnostics().isBlank()) {
                getLogger().warn(text.diagnostics().stripTrailing());
            }
            getLogger().lifecycle(text.standardOutput().stripTrailing());

            if (getJsonEnabled().get()) {
                write(getJsonReport(), new JsonReportWriter().write(
                        gate, config, Crap4jPlugin.toolVersion(), advisory, baselineDisplay));
            }
            if (getJunitXmlEnabled().get()) {
                write(getJunitXmlReport(), new JunitXmlReportWriter().write(
                        gate, config, getName(), ReportProducer.GRADLE));
            }
            return gate;
        } catch (GradleException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new GradleException("crap4j analysis failed: " + exception.getMessage(), exception);
        }
    }

    private Optional<Baseline> readBaseline() throws IOException {
        Path path = effectiveBaselineFile().toPath();
        return Files.isRegularFile(path)
                ? Optional.of(BaselineJson.read(path))
                : Optional.empty();
    }

    private String displayBaseline() {
        return effectiveBaselineFile().getName();
    }

    private File effectiveBaselineFile() {
        RegularFile configured = getBaseline().getOrNull();
        return configured == null
                ? getConventionalBaseline().get().getAsFile()
                : configured.getAsFile();
    }

    private static void write(RegularFileProperty output, String contents) throws IOException {
        File file = output.get().getAsFile();
        Files.createDirectories(file.toPath().getParent());
        Files.writeString(file.toPath(), contents);
    }
}

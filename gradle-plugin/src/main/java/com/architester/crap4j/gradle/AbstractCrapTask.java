package com.architester.crap4j.gradle;

import com.architester.crap4j.core.CoverageSelection;
import com.architester.crap4j.core.Exclusions;
import com.architester.crap4j.core.GateConfig;
import com.architester.crap4j.core.JacocoReport;
import com.architester.crap4j.core.JacocoXmlParser;
import com.architester.crap4j.core.ScoringEngine;
import com.architester.crap4j.core.ScoringResult;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.work.DisableCachingByDefault;

/** Inputs and core analysis shared by all crap4j tasks. */
@DisableCachingByDefault(because = "Outputs may be user-selected or source-controlled baseline files")
public abstract class AbstractCrapTask extends DefaultTask {
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getJacocoXml();

    @Input
    public abstract Property<Double> getThreshold();

    @Input
    public abstract Property<Integer> getComplexityCap();

    @Input
    public abstract Property<Boolean> getRequireTightBaseline();

    @Input
    public abstract ListProperty<String> getExcludes();

    @Input
    public abstract ListProperty<String> getExcludeClasses();

    @Input
    public abstract Property<Boolean> getUseDefaultExclusions();

    protected final ScoringResult score() throws Exception {
        JacocoReport report = new JacocoXmlParser().parse(getJacocoXml().get().getAsFile().toPath());
        return new ScoringEngine().score(report, new Exclusions(
                getExcludes().get(), getExcludeClasses().get(), getUseDefaultExclusions().get()));
    }

    protected final GateConfig config() {
        return new GateConfig(
                getThreshold().get(),
                getComplexityCap().get(),
                CoverageSelection.BRANCH_PREFERRED,
                getRequireTightBaseline().get(),
                false);
    }
}

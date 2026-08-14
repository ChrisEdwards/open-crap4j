package com.architester.crap4j.gradle;

import com.architester.crap4j.core.GateResult;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;

/** Enforcing CRAP gate. */
public abstract class CrapCheck extends AbstractCrapAnalysisTask {
    @TaskAction
    public final void check() {
        GateResult result = analyze(false);
        if (!getAdvisory().get() && result.violations() > 0) {
            throw new GradleException("CRAP violations found");
        }
    }
}

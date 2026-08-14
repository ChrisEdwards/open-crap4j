package com.architester.crap4j.gradle;

import org.gradle.api.tasks.TaskAction;

/** Permanently advisory report task. */
public abstract class CrapReport extends AbstractCrapAnalysisTask {
    @TaskAction
    public final void report() {
        analyze(true);
    }
}

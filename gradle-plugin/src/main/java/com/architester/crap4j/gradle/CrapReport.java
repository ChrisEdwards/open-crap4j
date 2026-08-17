package com.architester.crap4j.gradle;

import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/** Permanently advisory report task. */
@DisableCachingByDefault(because = "Reports may be written to user-selected output files")
public abstract class CrapReport extends AbstractCrapAnalysisTask {
    @TaskAction
    public final void report() {
        analyze(true);
    }
}

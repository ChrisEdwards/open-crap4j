package com.architester.crap4j.gradle;

import java.util.List;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/** Lazy configuration for the crap4j Gradle plugin. */
public abstract class Crap4jExtension {
    private final Crap4jFormats formats;

    @Inject
    public Crap4jExtension(ObjectFactory objects) {
        formats = objects.newInstance(Crap4jFormats.class);
    }

    public abstract Property<Double> getThreshold();

    public abstract Property<Integer> getComplexityCap();

    public abstract RegularFileProperty getJacocoXml();

    public abstract RegularFileProperty getBaseline();

    public abstract Property<Boolean> getAdvisory();

    public abstract Property<Boolean> getAttachToCheck();

    public abstract Property<Boolean> getRequireTightBaseline();

    public abstract ListProperty<String> getExcludes();

    public abstract ListProperty<String> getExcludeClasses();

    public abstract Property<Boolean> getUseDefaultExclusions();

    public Crap4jFormats getFormats() {
        return formats;
    }

    public void formats(Action<? super Crap4jFormats> action) {
        action.execute(formats);
    }

    void conventions() {
        getThreshold().convention(15.0d);
        getComplexityCap().convention(15);
        getAdvisory().convention(false);
        getAttachToCheck().convention(false);
        getRequireTightBaseline().convention(false);
        getExcludes().convention(List.of());
        getExcludeClasses().convention(List.of());
        getUseDefaultExclusions().convention(true);
        formats.getJson().convention(true);
        formats.getJunitXml().convention(true);
    }
}

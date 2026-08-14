package com.architester.crap4j.gradle;

import org.gradle.api.provider.Property;

/** File formats emitted by analysis tasks. */
public abstract class Crap4jFormats {
    public abstract Property<Boolean> getJson();

    public abstract Property<Boolean> getJunitXml();
}

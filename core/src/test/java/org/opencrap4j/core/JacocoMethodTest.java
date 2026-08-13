package org.opencrap4j.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class JacocoMethodTest {
    @Test
    void acceptsAnEmptyCounterMap() {
        JacocoMethod method = new JacocoMethod("empty", "()V", OptionalInt.empty(), Map.of());

        assertThat(method.counters()).isEmpty();
    }
}

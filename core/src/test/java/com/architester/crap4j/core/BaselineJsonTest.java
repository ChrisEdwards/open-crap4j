package com.architester.crap4j.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class BaselineJsonTest {
    @Test
    void serializesSortedEntriesAndRoundTripsByteIdentically() {
        Baseline baseline = new Baseline(
                1,
                "0.1.0",
                "2026-08-12T00:00:00Z",
                CoverageSelection.BRANCH_PREFERRED,
                15.25,
                15,
                List.of(
                        new BaselineEntry(
                                new MethodKey("com/example/Zed", "run", "()V"), 18.5, 15),
                        new BaselineEntry(
                                new MethodKey("com/example/Alpha", "run", "(I)V"), 20.0, 16)));

        String json = BaselineJson.write(baseline);

        assertThat(json).isEqualTo("""
                {
                  "formatVersion": 1,
                  "toolVersion": "0.1.0",
                  "generated": "2026-08-12T00:00:00Z",
                  "coverageSelection": "branch-preferred",
                  "threshold": 15.25,
                  "complexityCap": 15,
                  "entries": [
                    {
                      "class": "com/example/Alpha",
                      "method": "run",
                      "descriptor": "(I)V",
                      "crap": 20.00,
                      "complexity": 16
                    },
                    {
                      "class": "com/example/Zed",
                      "method": "run",
                      "descriptor": "()V",
                      "crap": 18.50,
                      "complexity": 15
                    }
                  ]
                }
                """);
        assertThat(BaselineJson.write(BaselineJson.read(json))).isEqualTo(json);
    }
}

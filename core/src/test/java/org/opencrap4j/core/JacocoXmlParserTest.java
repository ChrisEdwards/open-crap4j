package org.opencrap4j.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class JacocoXmlParserTest {
    private static final Path FIXTURES = Path.of("src/test/resources/jacoco");

    @Test
    void parsesARealJacocoReportWithItsDoctype() throws Exception {
        JacocoReport report = new JacocoXmlParser().parse(FIXTURES.resolve("report.xml"));

        assertThat(report.name()).isEqualTo("JaCoCo Coverage Report");
        assertThat(report.packages()).hasSize(1);

        JacocoPackage defaultPackage = report.packages().get(0);
        assertThat(defaultPackage.name()).isEmpty();
        assertThat(defaultPackage.classes()).hasSize(7);

        JacocoClass sample = defaultPackage.classes().stream()
                .filter(candidate -> candidate.name().equals("Sample"))
                .findFirst()
                .orElseThrow();
        assertThat(sample.sourceFile()).isEqualTo("Sample.java");

        JacocoMethod switchMethod = sample.methods().stream()
                .filter(candidate -> candidate.name().equals("sw"))
                .findFirst()
                .orElseThrow();
        assertThat(switchMethod.descriptor()).isEqualTo("(I)I");
        assertThat(switchMethod.line()).hasValue(27);
        assertThat(switchMethod.counters())
                .containsEntry(CounterType.INSTRUCTION, new Counter(3, 5))
                .containsEntry(CounterType.BRANCH, new Counter(2, 1))
                .containsEntry(CounterType.LINE, new Counter(2, 2))
                .containsEntry(CounterType.COMPLEXITY, new Counter(2, 1))
                .containsEntry(CounterType.METHOD, new Counter(0, 1));
    }

    @Test
    void representsMissingMethodLinesAsAbsent() throws Exception {
        JacocoReport report = new JacocoXmlParser().parse(FIXTURES.resolve("nd.xml"));

        assertThat(report.packages())
                .flatExtracting(JacocoPackage::classes)
                .flatExtracting(JacocoClass::methods)
                .extracting(JacocoMethod::line)
                .containsOnly(OptionalInt.empty());
    }

    @Test
    void parsesTheEnumOnlyFixture() throws Exception {
        JacocoReport report = new JacocoXmlParser().parse(FIXTURES.resolve("enum.xml"));

        assertThat(report.packages())
                .flatExtracting(JacocoPackage::classes)
                .singleElement()
                .satisfies(enumClass -> {
                    assertThat(enumClass.name()).isEqualTo("Color");
                    assertThat(enumClass.methods())
                            .extracting(JacocoMethod::name)
                            .containsExactly("go", "lambda$go$0", "<clinit>");
                });
    }

    @Test
    void leavesExternalEntitiesUnresolvedAndClassNamesInSlashForm() throws Exception {
        JacocoReport report = new JacocoXmlParser().parse(FIXTURES.resolve("external-entity.xml"));

        JacocoClass parsedClass = report.packages().get(0).classes().get(0);
        assertThat(parsedClass.name()).isEqualTo("com/example/myapp/MyService");
        assertThat(parsedClass.methods().get(0).counters())
                .doesNotContainKey(CounterType.BRANCH);
    }
}

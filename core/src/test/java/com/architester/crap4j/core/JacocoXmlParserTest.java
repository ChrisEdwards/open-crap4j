package com.architester.crap4j.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
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
        assertThat(sample.sourceFile()).hasValue("Sample.java");

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

        JacocoClass sample = parsedClass("nd.xml", "Sample");
        assertThat(method(sample, "over", "(Ljava/lang/String;)V").counters())
                .containsEntry(CounterType.INSTRUCTION, new Counter(4, 9));
        assertThat(method(sample, "over", "(I)V").counters())
                .containsEntry(CounterType.INSTRUCTION, new Counter(5, 0));
    }

    @Test
    void representsMissingSourceFileAsAbsent() throws Exception {
        JacocoReport report = new JacocoXmlParser().parse(FIXTURES.resolve("nd.xml"));

        assertThat(report.packages().get(0).classes().get(0).sourceFile())
                .isEqualTo(Optional.empty());
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
                            .containsExactly("go");
                    assertThat(enumClass.methods().get(0).counters())
                            .containsEntry(CounterType.INSTRUCTION, new Counter(0, 6));
                });
    }

    @Test
    void foldsPlainLambdaCountersIntoItsEnclosingMethod() throws Exception {
        JacocoClass sample = parsedClass("report.xml", "Sample");

        JacocoMethod ordinary = method(sample, "ordinary", "()V");

        assertThat(sample.methods())
                .extracting(JacocoMethod::name)
                .doesNotContain("lambda$ordinary$5");
        assertThat(ordinary.counters())
                .containsEntry(CounterType.INSTRUCTION, new Counter(0, 9))
                .containsEntry(CounterType.LINE, new Counter(0, 4))
                .containsEntry(CounterType.COMPLEXITY, new Counter(0, 2))
                .containsEntry(CounterType.METHOD, new Counter(0, 1));
    }

    @Test
    void foldsOverloadedTargetsByLargestLineNotAfterTheLambda() throws Exception {
        JacocoClass sample = parsedClass("report.xml", "Sample");

        JacocoMethod stringOverload = method(sample, "over", "(Ljava/lang/String;)V");
        JacocoMethod intOverload = method(sample, "over", "(I)V");

        assertThat(stringOverload.counters())
                .containsEntry(CounterType.INSTRUCTION, new Counter(0, 9));
        assertThat(intOverload.counters())
                .containsEntry(CounterType.INSTRUCTION, new Counter(9, 0));
        assertThat(sample.methods())
                .extracting(JacocoMethod::name)
                .doesNotContain("lambda$over$6", "lambda$over$7");
    }

    @Test
    void foldsConstructorAndFieldInitializerLambdasIntoTheFirstLineTiedConstructor()
            throws Exception {
        JacocoClass sample = parsedClass("report.xml", "Sample");

        JacocoMethod firstConstructor = method(sample, "<init>", "()V");
        JacocoMethod secondConstructor = method(sample, "<init>", "(I)V");

        assertThat(firstConstructor.counters())
                .containsEntry(CounterType.INSTRUCTION, new Counter(8, 14))
                .containsEntry(CounterType.COMPLEXITY, new Counter(2, 2));
        assertThat(secondConstructor.counters())
                .containsEntry(CounterType.INSTRUCTION, new Counter(10, 0));
        assertThat(sample.methods())
                .extracting(JacocoMethod::name)
                .doesNotContain("lambda$new$0", "lambda$new$3", "lambda$new$4");
    }

    @Test
    void foldsStaticInitializerLambdasAndThenSkipsTheInitializer() throws Exception {
        JacocoClass sample = parsedClass("report.xml", "Sample");

        assertThat(sample.methods())
                .extracting(JacocoMethod::name)
                .doesNotContain("<clinit>", "lambda$static$1", "lambda$static$2");
    }

    @Test
    void foldsDefensiveLambdaChainsTransitively() throws Exception {
        JacocoClass parsedClass = parseClass("""
                <method name="source" desc="()V" line="10">
                  <counter type="INSTRUCTION" missed="0" covered="1"/>
                </method>
                <method name="lambda$source$0" desc="()V" line="11">
                  <counter type="INSTRUCTION" missed="0" covered="2"/>
                </method>
                <method name="lambda$lambda$source$0$1" desc="()V" line="12">
                  <counter type="INSTRUCTION" missed="3" covered="0"/>
                </method>
                """);

        assertThat(parsedClass.methods()).singleElement().satisfies(source -> {
            assertThat(source.name()).isEqualTo("source");
            assertThat(source.counters())
                    .containsEntry(CounterType.INSTRUCTION, new Counter(3, 3));
        });
    }

    @Test
    void foldsNestedLambdasDirectlyIntoTheirSourceMethod() throws Exception {
        JacocoClass sample = parsedClass("report.xml", "Sample");

        assertThat(method(sample, "nested", "()V").counters())
                .containsEntry(CounterType.INSTRUCTION, new Counter(0, 13));
        assertThat(sample.methods())
                .extracting(JacocoMethod::name)
                .doesNotContain("lambda$nested$8", "lambda$nested$9");
    }

    @Test
    void leavesSwitchArrowMethodAloneBecauseItHasNoSyntheticBody() throws Exception {
        JacocoClass sample = parsedClass("report.xml", "Sample");

        assertThat(method(sample, "sw", "(I)I").counters())
                .containsEntry(CounterType.INSTRUCTION, new Counter(3, 5));
        assertThat(sample.methods())
                .extracting(JacocoMethod::name)
                .doesNotContain("lambda$sw$0");
    }

    @Test
    void foldsInterfaceDefaultAndStaticMethodLambdasNormally() throws Exception {
        JacocoClass iface = parsedClass("report.xml", "Iface");

        assertThat(method(iface, "doIt", "()V").counters())
                .containsEntry(CounterType.INSTRUCTION, new Counter(0, 9));
        assertThat(method(iface, "stat", "()V").counters())
                .containsEntry(CounterType.INSTRUCTION, new Counter(9, 0));
    }

    @Test
    void foldsRecordCompactConstructorAndMethodLambdas() throws Exception {
        JacocoClass record = parsedClass("report.xml", "Rec");

        assertThat(method(record, "<init>", "(Ljava/util/List;)V").counters())
                .containsEntry(CounterType.INSTRUCTION, new Counter(0, 19));
        assertThat(method(record, "count", "()J").counters())
                .containsEntry(CounterType.INSTRUCTION, new Counter(0, 15))
                .containsEntry(CounterType.BRANCH, new Counter(0, 2));
    }

    @Test
    void foldsAnonymousClassLambdaOnlyWithinItsOwnClass() throws Exception {
        JacocoClass outer = parsedClass("report.xml", "Anon");
        JacocoClass anonymous = parsedClass("report.xml", "Anon$1");

        assertThat(method(outer, "make", "()Ljava/lang/Runnable;").counters())
                .containsEntry(CounterType.INSTRUCTION, new Counter(0, 5));
        assertThat(method(anonymous, "run", "()V").counters())
                .containsEntry(CounterType.INSTRUCTION, new Counter(0, 9));
    }

    @Test
    void keepsJdk8NullAndMissingTargetsStandalone() throws Exception {
        JacocoClass parsedClass = parseClass("""
                <method name="lambda$null$0" desc="()V" line="1"/>
                <method name="lambda$generated$1" desc="()V" line="2"/>
                """);

        assertThat(parsedClass.methods())
                .extracting(JacocoMethod::name)
                .containsExactly("lambda$null$0", "lambda$generated$1");
    }

    @Test
    void leavesRealLambdaAndScalaSyntheticNamesAlone() throws Exception {
        JacocoClass parsedClass = parseClass("""
                <method name="lambda" desc="()V" line="1"/>
                <method name="$anonfun$work$0" desc="()V" line="2"/>
                """);

        assertThat(parsedClass.methods())
                .extracting(JacocoMethod::name)
                .containsExactly("lambda", "$anonfun$work$0");
    }

    @Test
    void greedilyFindsMethodNamesContainingDollarSigns() throws Exception {
        JacocoClass parsedClass = parseClass("""
                <method name="foo$bar" desc="()V" line="1">
                  <counter type="INSTRUCTION" missed="2" covered="0"/>
                </method>
                <method name="lambda$foo$bar$0" desc="()V" line="2">
                  <counter type="BRANCH" missed="0" covered="3"/>
                </method>
                """);

        assertThat(parsedClass.methods()).singleElement().satisfies(method -> {
            assertThat(method.name()).isEqualTo("foo$bar");
            assertThat(method.counters())
                    .containsEntry(CounterType.INSTRUCTION, new Counter(2, 0))
                    .containsEntry(CounterType.BRANCH, new Counter(0, 3));
        });
    }

    @Test
    void fallsBackToFirstOverloadWhenNoLinePrecedesTheLambda() throws Exception {
        JacocoClass parsedClass = parseClass("""
                <method name="work" desc="(I)V" line="20"/>
                <method name="work" desc="(J)V" line="30"/>
                <method name="lambda$work$0" desc="()V" line="10">
                  <counter type="INSTRUCTION" missed="0" covered="1"/>
                </method>
                """);

        assertThat(method(parsedClass, "work", "(I)V").counters())
                .containsEntry(CounterType.INSTRUCTION, new Counter(0, 1));
        assertThat(method(parsedClass, "work", "(J)V").counters()).isEmpty();
    }

    @Test
    void neverFoldsAcrossClassElements() throws Exception {
        String xml = """
                <report name="Classes">
                  <package name="example">
                    <class name="example/One">
                      <method name="lambda$work$0" desc="()V" line="1"/>
                    </class>
                    <class name="example/Two">
                      <method name="work" desc="()V" line="1"/>
                    </class>
                  </package>
                </report>
                """;

        JacocoReport report = new JacocoXmlParser().parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        assertThat(report.packages().get(0).classes().get(0).methods())
                .extracting(JacocoMethod::name)
                .containsExactly("lambda$work$0");
    }

    @Test
    void leavesExternalEntitiesUnresolvedAndClassNamesInSlashForm() throws Exception {
        JacocoReport report = new JacocoXmlParser().parse(FIXTURES.resolve("external-entity.xml"));

        JacocoClass parsedClass = report.packages().get(0).classes().get(0);
        assertThat(parsedClass.name()).isEqualTo("com/example/myapp/MyService");
        assertThat(parsedClass.methods().get(0).counters())
                .doesNotContainKey(CounterType.BRANCH);
    }

    @Test
    void parsesPackagesNestedInGroups() throws Exception {
        String xml = """
                <report name="Grouped">
                  <group name="application">
                    <group name="feature">
                      <package name="com/example">
                        <class name="com/example/Example" sourcefilename="Example.java"/>
                      </package>
                    </group>
                  </group>
                </report>
                """;

        JacocoReport report = new JacocoXmlParser().parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        assertThat(report.packages())
                .extracting(JacocoPackage::name)
                .containsExactly("com/example");
    }

    private static JacocoClass parsedClass(String fixture, String className) throws Exception {
        return new JacocoXmlParser().parse(FIXTURES.resolve(fixture)).packages().stream()
                .flatMap(jacocoPackage -> jacocoPackage.classes().stream())
                .filter(candidate -> candidate.name().equals(className))
                .findFirst()
                .orElseThrow();
    }

    private static JacocoMethod method(JacocoClass jacocoClass, String name, String descriptor) {
        return jacocoClass.methods().stream()
                .filter(candidate -> candidate.name().equals(name)
                        && candidate.descriptor().equals(descriptor))
                .findFirst()
                .orElseThrow();
    }

    private static JacocoClass parseClass(String methods) throws Exception {
        String xml = """
                <report name="Handwritten">
                  <package name="example">
                    <class name="example/Example" sourcefilename="Example.java">
                """ + methods + """
                    </class>
                  </package>
                </report>
                """;
        return new JacocoXmlParser()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
                .packages()
                .get(0)
                .classes()
                .get(0);
    }
}

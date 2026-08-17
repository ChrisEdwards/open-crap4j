package com.architester.crap4j.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ExclusionsTest {
    @Test
    void excludes_should_matchNestedPaths_when_trailingDoubleStar() {
        Exclusions exclusions = new Exclusions(List.of("**/internal/**"), List.of(), false);
        JacocoClass clazz = jacocoClass("com/internal/Foo", "Foo.java");

        assertThat(exclusions.excludes("com/internal", clazz)).isTrue();
    }

    @Test
    void excludes_should_matchSingleChar_when_questionMark() {
        Exclusions exclusions = new Exclusions(List.of("???.java"), List.of(), false);
        JacocoClass match = jacocoClass("Foo", "Foo.java");
        JacocoClass noMatch = jacocoClass("FooBar", "FooBar.java");

        assertThat(exclusions.excludes("", match)).isTrue();
        assertThat(exclusions.excludes("", noMatch)).isFalse();
    }

    @Test
    void excludes_should_escapeRegexMetachars_when_dotInGlob() {
        Exclusions exclusions = new Exclusions(List.of("com.example.*"), List.of(), false);
        JacocoClass literal = jacocoClass("com/example/Foo", "com.example.foo");
        JacocoClass nonLiteral = jacocoClass("comXexample/Foo", "comXexampleXfoo");

        assertThat(exclusions.excludes("", literal)).isTrue();
        assertThat(exclusions.excludes("", nonLiteral)).isFalse();
    }

    @Test
    void excludes_should_notMatchPathGlob_when_noSourceFile() {
        Exclusions exclusions = new Exclusions(List.of("**"), List.of(), false);
        JacocoClass noSource = new JacocoClass("com/example/Foo", Optional.empty(), List.of());

        assertThat(exclusions.excludes("com/example", noSource)).isFalse();
    }

    @Test
    void excludes_should_matchClassName_when_regexPattern() {
        Exclusions exclusions = new Exclusions(List.of(), List.of(".*Impl$"), false);
        JacocoClass impl = jacocoClass("com/example/ServiceImpl", "ServiceImpl.java");

        assertThat(exclusions.excludes("com/example", impl)).isTrue();
    }

    @Test
    void excludes_should_matchDoubleStar_when_atEndOfGlob() {
        Exclusions exclusions = new Exclusions(List.of("src/**"), List.of(), false);
        JacocoClass clazz = jacocoClass("src/main/Foo", "Foo.java");

        assertThat(exclusions.excludes("src/main", clazz)).isTrue();
    }

    private static JacocoClass jacocoClass(String name, String sourceFile) {
        return new JacocoClass(name, Optional.of(sourceFile), List.of());
    }
}

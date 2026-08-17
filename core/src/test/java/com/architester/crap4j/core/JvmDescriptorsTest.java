package com.architester.crap4j.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JvmDescriptorsTest {
    @Test
    void decodesPrimitiveParameters() {
        assertThat(JvmDescriptors.parameterList("(ZBCSIJFD)V"))
                .isEqualTo("boolean, byte, char, short, int, long, float, double");
    }

    @Test
    void decodesObjectParameter() {
        assertThat(JvmDescriptors.parameterList("(Ljava/util/List;)V"))
                .isEqualTo("List");
    }

    @Test
    void decodesNestedClassParameter() {
        assertThat(JvmDescriptors.parameterList("(Ljava/util/Map$Entry;)V"))
                .isEqualTo("Map.Entry");
    }

    @Test
    void decodesArrayParameters() {
        assertThat(JvmDescriptors.parameterList("([[I)V")).isEqualTo("int[][]");
    }

    @Test
    void decodesNoParameters() {
        assertThat(JvmDescriptors.parameterList("()V")).isEmpty();
    }

    @Test
    void decodesNonVoidReturn() {
        assertThat(JvmDescriptors.parameterList("(I)I")).isEqualTo("int");
    }

    @Test
    void decodesObjectReturn() {
        assertThat(JvmDescriptors.parameterList("()Ljava/lang/String;")).isEmpty();
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> JvmDescriptors.parameterList(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEmpty() {
        assertThatThrownBy(() -> JvmDescriptors.parameterList(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingOpenParen() {
        assertThatThrownBy(() -> JvmDescriptors.parameterList("I)V"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTruncatedAfterArrayBrackets() {
        assertThatThrownBy(() -> JvmDescriptors.parameterList("(["))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingSemicolon() {
        assertThatThrownBy(() -> JvmDescriptors.parameterList("(Labc)V"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEmptyClassName() {
        assertThatThrownBy(() -> JvmDescriptors.parameterList("(L;)V"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingReturnType() {
        assertThatThrownBy(() -> JvmDescriptors.parameterList("(I)"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTrailingJunk() {
        assertThatThrownBy(() -> JvmDescriptors.parameterList("(I)VV"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnknownTypeChar() {
        assertThatThrownBy(() -> JvmDescriptors.parameterList("(X)V"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingCloseParen() {
        assertThatThrownBy(() -> JvmDescriptors.parameterList("(I"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

package com.architester.crap4j.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JvmDescriptorsTest {
    @Test
    void parameterList_should_decodePrimitives_when_allPrimitiveTypes() {
        assertThat(JvmDescriptors.parameterList("(ZBCSIJFD)V"))
                .isEqualTo("boolean, byte, char, short, int, long, float, double");
    }

    @Test
    void parameterList_should_decodeSimpleName_when_objectParameter() {
        assertThat(JvmDescriptors.parameterList("(Ljava/util/List;)V"))
                .isEqualTo("List");
    }

    @Test
    void parameterList_should_replaceDollarWithDot_when_nestedClass() {
        assertThat(JvmDescriptors.parameterList("(Ljava/util/Map$Entry;)V"))
                .isEqualTo("Map.Entry");
    }

    @Test
    void parameterList_should_appendBrackets_when_arrayParameter() {
        assertThat(JvmDescriptors.parameterList("([[I)V")).isEqualTo("int[][]");
    }

    @Test
    void parameterList_should_returnEmpty_when_noParameters() {
        assertThat(JvmDescriptors.parameterList("()V")).isEmpty();
    }

    @Test
    void parameterList_should_decodeParam_when_nonVoidReturn() {
        assertThat(JvmDescriptors.parameterList("(I)I")).isEqualTo("int");
    }

    @Test
    void parameterList_should_returnEmpty_when_objectReturn() {
        assertThat(JvmDescriptors.parameterList("()Ljava/lang/String;")).isEmpty();
    }

    @Test
    void parameterList_should_throw_when_null() {
        assertThatThrownBy(() -> JvmDescriptors.parameterList(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parameterList_should_throw_when_empty() {
        assertThatThrownBy(() -> JvmDescriptors.parameterList(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parameterList_should_throw_when_missingOpenParen() {
        assertThatThrownBy(() -> JvmDescriptors.parameterList("I)V"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parameterList_should_throw_when_truncatedAfterArrayBrackets() {
        assertThatThrownBy(() -> JvmDescriptors.parameterList("(["))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parameterList_should_throw_when_missingSemicolon() {
        assertThatThrownBy(() -> JvmDescriptors.parameterList("(Labc)V"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parameterList_should_throw_when_emptyClassName() {
        assertThatThrownBy(() -> JvmDescriptors.parameterList("(L;)V"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parameterList_should_throw_when_missingReturnType() {
        assertThatThrownBy(() -> JvmDescriptors.parameterList("(I)"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parameterList_should_throw_when_trailingJunk() {
        assertThatThrownBy(() -> JvmDescriptors.parameterList("(I)VV"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parameterList_should_throw_when_unknownTypeChar() {
        assertThatThrownBy(() -> JvmDescriptors.parameterList("(X)V"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parameterList_should_throw_when_missingCloseParen() {
        assertThatThrownBy(() -> JvmDescriptors.parameterList("(I"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

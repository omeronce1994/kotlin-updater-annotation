package com.thinkinglogic.example;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.BDDAssertions.then;

public class SimpleDataClassJavaTest {

    @Test
    void builderShouldRejectNullValueForRequiredFields() {
        // given
        SimpleDataClassBuilder builder = new SimpleDataClassBuilder();

    }

    @Test
    void staticBuilderMethodReturnsBuilder() {
        // given

        // when

        // then
    }
}

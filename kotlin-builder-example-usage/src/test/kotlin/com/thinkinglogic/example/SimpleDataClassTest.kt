package com.thinkinglogic.example

import assertk.assert
import assertk.assertions.contains
import assertk.assertions.isNotNull
import assertk.assertions.message
import assertk.catch
import com.thinkinglogic.example.SimpleDataClassBuilder.Companion.update
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class SimpleDataClassTest {

    @Test
    fun `builder should create object with correct properties`() {
        // given
        val expected = SimpleDataClass(
                notNullString = "foo",
                nullableString = null,
                notNullLong = 123,
                nullableLong = 345,
                date = LocalDate.now(),
                value = "valueProperty"
        )

        // when
        val actual = SimpleDataClassBuilder(notNullString = "test")
        val copy = expected.copy(notNullString = "test")
        val result = expected.update(actual)

        // then
        assertThat(result).isEqualTo(copy)
    }

}
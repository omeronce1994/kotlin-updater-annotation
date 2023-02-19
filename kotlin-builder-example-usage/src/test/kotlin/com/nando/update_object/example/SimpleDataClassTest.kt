package com.nando.update_object.example

import com.nando.update_object.example.SimpleDataClassUpdateObject.Companion.update
import com.nando.update_object.example.TestPartialObject.Companion.update
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
        val actual = SimpleDataClassUpdateObject(notNullString = "test")
        val copy = expected.copy(notNullString = "test")
        val result = expected.update(actual)

        // then
        assertThat(result).isEqualTo(copy)
    }

}
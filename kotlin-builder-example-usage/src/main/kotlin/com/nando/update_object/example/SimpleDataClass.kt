package com.nando.update_object.example

import com.nando.update_object.annotation.UpdateObject
import java.time.LocalDate

@UpdateObject
data class SimpleDataClass(
        val notNullString: String,
        val nullableString: String?,
        val notNullLong: Long,
        val nullableLong: Long?,
        val date: LocalDate,
        val value: String,
        val stringWithDefault: String = "withDefaultValue",
        val defaultDate: LocalDate = LocalDate.MIN
) {


}
package com.thinkinglogic.example

import com.thinkinglogic.builder.annotation.Builder
import com.thinkinglogic.builder.annotation.DefaultValue
import com.thinkinglogic.builder.annotation.UpdateObject
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import java.time.LocalDate

@UpdateObject
data class SimpleDataClass(
        val notNullString: String,
        val nullableString: String?,
        val notNullLong: Long,
        val nullableLong: Long?,
        val date: LocalDate,
        val value: String,
        @DefaultValue("withDefaultValue") val stringWithDefault: String = "withDefaultValue",
        @DefaultValue("LocalDate.MIN") val defaultDate: LocalDate = LocalDate.MIN
) {


}
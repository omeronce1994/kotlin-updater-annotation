package com.nando.update_object.example

import com.nando.update_object.annotation.PartOfPartialObjects
import com.nando.update_object.annotation.RequiredForUpdateObject
import com.nando.update_object.annotation.UpdateObject
import com.nando.update_object.annotation.modifiers.UpdateObjectModifier
import java.time.LocalDate

@UpdateObject
data class SimpleDataClass(
        @RequiredForUpdateObject
        val notNullString: String,
        val nullableString: String?,
        val notNullLong: Long,
        @PartOfPartialObjects(partialClassesName = ["TestPartialObject", "TestTwo"])
        val nullableLong: Long?,
        val date: LocalDate,
        @PartOfPartialObjects(partialClassesName = ["TestPartialObject", "TestTwo"])
        val value: String,
        val stringWithDefault: String = "withDefaultValue",
        val defaultDate: LocalDate = LocalDate.MIN
) {


}
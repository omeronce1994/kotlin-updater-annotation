package com.thinkinglogic.example

import com.thinkinglogic.builder.annotation.Builder
import com.thinkinglogic.builder.annotation.DefaultValue
import java.time.LocalDate


data class DataClassWithLongPropertyNames(
        @DefaultValue("myDefault") val stringWithAVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryLongNameThatWouldCauseLineWrappingInTheGeneratedFile: String = "myDefault",
        val nullableString: String?
) {

}
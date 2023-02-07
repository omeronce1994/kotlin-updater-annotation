package com.thinkinglogic.example

import com.thinkinglogic.builder.annotation.Builder


data class GenericTypeDataClass(
        val myTypedObject: TypedObject<String>
)

class TypedObject<T>(val value: T)
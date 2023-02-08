package com.nando.update_object.annotation

import com.nando.update_object.annotation.modifiers.UpdateObjectModifier
import kotlin.reflect.KClass

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS, AnnotationTarget.CONSTRUCTOR)
annotation class UpdateObject(
    val visibilityModifiers: Array<KClass<out UpdateObjectModifier.VisibilityModifier>> = []
)
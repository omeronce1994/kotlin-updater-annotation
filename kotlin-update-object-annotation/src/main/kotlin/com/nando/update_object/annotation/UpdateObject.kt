package com.nando.update_object.annotation

import com.nando.update_object.annotation.modifiers.UpdateObjectModifier
import kotlin.reflect.KClass

/**
 * Will generate matching update object class to the annotated class with update function to update the annotated class object using
 * generated update object for example for class
 * ```
 * @UpdateObject
 * data class SimpleDataClass(val field: String)
 * ```
 * will be generated the following update object with update function
 * ```
 * data class SimpleDataClassUpdateObject(val field: String? = null) {
 *      companion object {
 *          fun SimpleDataClass.update(updateObject: SimpleDataClassUpdateObject): SimpleDataClass {
 *              val field = updateObject.field ?: this.field
 *              val result = SimpleDataClass(field = field)
 *              return result
 *          }
 *      }
 * }
 * ```
 * @property className - class name for generated update object. If left empty default name will be <annotated-class-name>UpdateObject
 * @property visibilityModifier - visibility modifier for the generated class. default is [UpdateObjectModifier.VisibilityModifier.Public]
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS, AnnotationTarget.CONSTRUCTOR)
annotation class UpdateObject(
    val className: String = "",
    val visibilityModifier: KClass<out UpdateObjectModifier.VisibilityModifier> = UpdateObjectModifier.VisibilityModifier.Public::class
)
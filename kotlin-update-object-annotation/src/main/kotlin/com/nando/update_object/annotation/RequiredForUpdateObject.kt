package com.nando.update_object.annotation

/**
 * Annotate field with this annotation in case you want it to be required for update object instantiation and not set it's default value to null.
 * Note that field that is generated with this annotation will always be generated with non null type.
 * For example for the following class annotated with [UpdateObject] annotation and it's field is annotated with [RequiredForUpdateObject]
 * ```
 * @UpdateObject
 * data class SimpleDataClass(@RequiredForUpdateObject val field: String)
 * ```
 * will be generated the following update object with update function
 * ```
 * data class SimpleDataClassUpdateObject(val field: String) {
 *      companion object {
 *          fun SimpleDataClass.update(updateObject: SimpleDataClassUpdateObject): SimpleDataClass {
 *              val field = updateObject.field ?: this.field
 *              val result = SimpleDataClass(field = field)
 *              return result
 *          }
 *      }
 * }
 * ```
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
annotation class RequiredForUpdateObject

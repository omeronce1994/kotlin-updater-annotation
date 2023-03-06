package com.nando.update_object.processor

import com.nando.update_object.annotation.PartOfPartialObjects
import com.nando.update_object.annotation.RequiredForUpdateObject
import com.nando.update_object.annotation.UpdateObject
import com.nando.update_object.annotation.modifiers.UpdateObjectModifier
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import java.io.File
import java.util.*
import java.util.stream.Collectors
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.*
import javax.lang.model.type.ArrayType
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.MirroredTypeException
import javax.lang.model.type.MirroredTypesException
import javax.lang.model.type.PrimitiveType
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.ElementFilter.*
import javax.tools.Diagnostic.Kind.ERROR
import javax.tools.Diagnostic.Kind.NOTE

/**
 * Kapt processor for the @Builder annotation.
 * Constructs a Builder for the annotated class.
 */
@SupportedAnnotationTypes("com.nando.update_object.annotation.UpdateObject")
@SupportedSourceVersion(SourceVersion.RELEASE_11)
@SupportedOptions(BuilderProcessor.KAPT_KOTLIN_GENERATED_OPTION_NAME)
class BuilderProcessor : AbstractProcessor() {

    companion object {
        const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"
        const val CHECK_REQUIRED_FIELDS_FUNCTION_NAME = "checkRequiredFields"
        val MUTABLE_COLLECTIONS = mapOf(
            List::class.asClassName() to ClassName("kotlin.collections", "MutableList"),
            Set::class.asClassName() to ClassName("kotlin.collections", "MutableSet"),
            Collection::class.asClassName() to ClassName("kotlin.collections", "MutableCollection"),
            Map::class.asClassName() to ClassName("kotlin.collections", "MutableMap"),
            Iterator::class.asClassName() to ClassName("kotlin.collections", "MutableIterator")
        )
    }

    override fun process(
        annotations: MutableSet<out TypeElement>?,
        roundEnv: RoundEnvironment
    ): Boolean {
        val annotatedElements = roundEnv.getElementsAnnotatedWith(UpdateObject::class.java)
        if (annotatedElements.isEmpty()) {
            processingEnv.noteMessage { "No classes annotated with @${UpdateObject::class.java.simpleName} in this round ($roundEnv)" }
            return false
        }

        val generatedSourcesRoot = processingEnv.options[KAPT_KOTLIN_GENERATED_OPTION_NAME] ?: run {
            processingEnv.errorMessage { "Can't find the target directory for generated Kotlin files." }
            return false
        }

        processingEnv.noteMessage { "Generating Builders in $roundEnv" }

        processingEnv.noteMessage { "Generating Builders for ${annotatedElements.size} classes in $generatedSourcesRoot" }

        val sourceRootFile = File(generatedSourcesRoot)
        sourceRootFile.mkdir()

        annotatedElements.forEach { annotatedElement ->
            when (annotatedElement.kind) {
                ElementKind.CLASS -> writeBuilderForClass(
                    annotatedElement as TypeElement,
                    sourceRootFile
                )
                ElementKind.CONSTRUCTOR -> writeBuilderForConstructor(
                    annotatedElement as ExecutableElement,
                    sourceRootFile
                )
                else -> annotatedElement.errorMessage { "Invalid element type, expected a class or constructor" }
            }
        }

        return true
    }

    /** Invokes [writeBuilder] to create a builder for the given [classElement]. */
    private fun writeBuilderForClass(classElement: TypeElement, sourceRootFile: File) {
        writeBuilder(classElement, classElement.fieldsForBuilder(), sourceRootFile)
    }

    /** Invokes [writeBuilder] to create a builder for the given [constructor]. */
    private fun writeBuilderForConstructor(constructor: ExecutableElement, sourceRootFile: File) {
        writeBuilder(
            constructor.enclosingElement as TypeElement,
            constructor.parameters,
            sourceRootFile
        )
    }

    /** Writes the source code to create a builder for [classToBuild] within the [sourceRoot] directory. */
    private fun writeBuilder(
        classToBuild: TypeElement,
        fields: List<VariableElement>,
        sourceRoot: File
    ) {
        val packageName = processingEnv.elementUtils.getPackageOf(classToBuild).toString()
        val builderClassName = classToBuild.getUpdateObjectClassName()

        processingEnv.noteMessage { "Writing $packageName.$builderClassName" }

        val builderClass = ClassName(packageName, builderClassName)
        processingEnv.noteMessage { "Adding modifiers" }
        val visibilityModifier = classToBuild.getUpdateObjectVisibilityModifiers().toKModifier()
        val modifiersSet = setOf(visibilityModifier, KModifier.DATA)
        processingEnv.noteMessage { "Added modifiers $modifiersSet" }
        val builderSpec =
            TypeSpec.classBuilder(builderClassName).addModifiers(*modifiersSet.toTypedArray())
        builderSpec.primaryConstructor(*fields.map { it.asProperty() }.toTypedArray())
        builderSpec.addType(createCompanionObject(classToBuild, builderClass, fields, visibilityModifiers = setOf(visibilityModifier)))
        val partialObjectClassNameToFieldsMap = mutableMapOf<String, Set<VariableElement>>()
        fields.forEach {
            if (it.hasAnnotation(PartOfPartialObjects::class.java)) {
                val annotation = it.findAnnotation(PartOfPartialObjects::class.java)
                val classes = annotation.partialClassesName
                classes.forEach { className ->
                    val partialObjectFields = partialObjectClassNameToFieldsMap[className]?.toMutableSet() ?: mutableSetOf()
                    partialObjectFields.add(it)
                    partialObjectClassNameToFieldsMap[className] = partialObjectFields
                }
            }
        }
        partialObjectClassNameToFieldsMap.entries.forEach {
            val className = it.key
            val classFields = it.value
            buildPartialObjectsClasses(classToBuild, classFields.toList(), className, sourceRoot)
        }

//        fields.forEach { field ->
//            processingEnv.noteMessage { "Adding field: $field" }
//            builderSpec.addProperty(field.asProperty())
//            builderSpec.primaryConstructor()
//        }
        //builderSpec.primaryConstructor(constructorBuilder.build())

        FileSpec.builder(packageName, builderClassName)
            .addType(builderSpec.build())
            .build()
            .writeTo(sourceRoot)
    }

    private fun buildPartialObjectsClasses(
        fullClass: TypeElement,
        fields: List<VariableElement>,
        partialClassName: String,
        sourceRoot: File
    ) {
        val packageName = processingEnv.elementUtils.getPackageOf(fullClass).toString()
        processingEnv.noteMessage { "Writing $packageName.$partialClassName" }
        val partialClass = ClassName(packageName, partialClassName)
        val modifiersSet = setOf(KModifier.DATA)
        val builderSpec =
            TypeSpec.classBuilder(partialClass).addModifiers(*modifiersSet.toTypedArray())
        builderSpec.primaryConstructor(*fields.map { it.asPartialClassProperty() }.toTypedArray())
        builderSpec.addType(createPartialObjectCompanionObject(fullClass, partialClass, fields, visibilityModifiers = setOf()))
        FileSpec.builder(packageName, partialClassName)
            .addType(builderSpec.build())
            .build()
            .writeTo(sourceRoot)
    }

    private fun TypeElement.getUpdateObjectClassName(fallbackName: String = "${simpleName}UpdateObject"): String = if (hasAnnotation(UpdateObject::class.java)) {
        val annotation = this.findAnnotation(UpdateObject::class.java)
        val classNameAnnotation = annotation.className
        classNameAnnotation.ifEmpty {
            fallbackName
        }
    }
    else {
        fallbackName
    }

    private fun TypeElement.getUpdateObjectVisibilityModifiers(): UpdateObjectModifier.VisibilityModifier {
        return if (hasAnnotation(UpdateObject::class.java)) {
            // make sure that strings are wrapped in quotes
            val annotation = this.findAnnotation(UpdateObject::class.java)
            val modifier = try {
                annotation.visibilityModifier as TypeMirror
            } catch (e: MirroredTypeException) {
                e.typeMirror
            } catch (e: Exception) {
                errorMessage { "failed to get modifiers. message: ${e.message}" }
                throw e
            }
            processingEnv.noteMessage { "Processing modifiers $modifier" }
            val name = modifier.toString()
            val result : UpdateObjectModifier.VisibilityModifier= when (name) {
                UpdateObjectModifier.VisibilityModifier.Internal::class.qualifiedName -> UpdateObjectModifier.VisibilityModifier.Internal
                UpdateObjectModifier.VisibilityModifier.Private::class.qualifiedName -> UpdateObjectModifier.VisibilityModifier.Private
                UpdateObjectModifier.VisibilityModifier.Protected::class.qualifiedName -> UpdateObjectModifier.VisibilityModifier.Protected
                UpdateObjectModifier.VisibilityModifier.Public::class.qualifiedName -> UpdateObjectModifier.VisibilityModifier.Public
                else -> {
                    val error = "Provided modifier ${modifier.asTypeElement().simpleName} is does not implement ${UpdateObjectModifier::class.java.simpleName}"
                    errorMessage {
                        error
                    }
                    throw IllegalArgumentException(error)
                }
            }
            return result
        } else {
            throw IllegalStateException("Tried to get update visibility modifiers for class which is not annotated with ${UpdateObject::class.simpleName}")
        }
    }

    private fun UpdateObjectModifier.VisibilityModifier.toKModifier() = when (this) {
        is UpdateObjectModifier.VisibilityModifier.Internal -> KModifier.INTERNAL
        is UpdateObjectModifier.VisibilityModifier.Private -> KModifier.PRIVATE
        is UpdateObjectModifier.VisibilityModifier.Protected -> KModifier.PROTECTED
        UpdateObjectModifier.VisibilityModifier.Public -> KModifier.PUBLIC
    }

    /** Returns all fields in this type that also appear as a constructor parameter. */
    private fun TypeElement.fieldsForBuilder(): List<VariableElement> {
        val allMembers = processingEnv.elementUtils.getAllMembers(this)
        val fields = fieldsIn(allMembers)
        val constructors = constructorsIn(allMembers)
        val constructorParamNames = constructors
            .flatMap { it.parameters }
            .map { it.simpleName.toString() }
            .toSet()
        return fields.filter { constructorParamNames.contains(it.simpleName.toString()) }
    }

    private fun TypeSpec.Builder.primaryConstructor(vararg properties: PropertySpec): TypeSpec.Builder {
        val propertySpecs = properties.map { p -> p.toBuilder().initializer(p.name).build() }
        val parameters = propertySpecs.map {
            val builder = ParameterSpec.builder(it.name, it.type)
            if (it.type.isNullable) {
                builder.defaultValue("null")
            }
            builder.build()
        }
        val constructor = FunSpec.constructorBuilder()
            .addParameters(parameters)
            .build()

        return this
            .primaryConstructor(constructor)
            .addProperties(propertySpecs)
    }

    /** Creates a constructor for [classType] that accepts an instance of the class to build, from which default values are obtained. */
    private fun createConstructor(fields: List<Element>, classType: TypeElement): FunSpec {
        val source = "source"
        val sourceParameter = ParameterSpec.builder(source, classType.asKotlinTypeName()).build()
        val getterFieldNames = classType.getterFieldNames()
        val code = StringBuilder()
        fields.forEach { field ->
            if (getterFieldNames.contains(field.simpleName.toString())) {
                code.append("    this.${field.simpleName}·=·$source.${field.simpleName}")
                    .appendLine()
            }
        }
        return FunSpec.constructorBuilder()
            .addParameter(sourceParameter)
            .callThisConstructor()
            .addCode(code.toString())
            .build()
    }

    /** Returns a set of the names of fields with getters (actually the names of getter methods with 'get' removed and decapitalised). */
    private fun TypeElement.getterFieldNames(): Set<String> {
        val allMembers = processingEnv.elementUtils.getAllMembers(this)
        return methodsIn(allMembers)
            .filter { it.simpleName.startsWith("get") && it.parameters.isEmpty() }
            .map {
                it.simpleName.toString().substringAfter("get")
                    .replaceFirstChar { ch -> ch.lowercase(Locale.getDefault()) }
            }
            .toSet()
    }

    /** Creates a 'build()' function that will invoke a constructor for [returnType], passing [fields] as arguments and returning the new instance. */
    private fun createBuildFunction(fields: List<Element>, returnType: TypeElement): FunSpec {
        val code = StringBuilder()
        code.append("return·${returnType.simpleName}(")
        val iterator = fields.listIterator()
        while (iterator.hasNext()) {
            val field = iterator.next()
            code.appendLine().append("    ${field.simpleName}·=·${field.simpleName}")
            if (!field.isNullable()) {
                code.append("!!")
            }
            if (iterator.hasNext()) {
                code.append(",")
            }
        }
        code.appendLine().append(")").appendLine()

        return FunSpec.builder("build")
            .returns(returnType.asClassName())
            .addCode(code.toString())
            .build()
    }

    private fun createCompanionObject(
        sourceType: TypeElement,
        classToBuild: ClassName,
        fields: List<VariableElement>,
        visibilityModifiers: Set<KModifier>
    ) = TypeSpec.companionObjectBuilder()
        .addModifiers(visibilityModifiers)
        .addFunction(createUpdateFunction(sourceType, classToBuild, fields, visibilityModifiers))
        .addFunction(createToUpdateObjectFunction(sourceType, classToBuild, fields, visibilityModifiers)).build()

    private fun createUpdateFunction(
        sourceType: TypeElement,
        classToBuild: ClassName,
        fields: List<VariableElement>,
        visibilityModifiers: Set<KModifier>,
        updateObjectParameterName: String = "updateObject"
    ): FunSpec {
        val code = StringBuilder()
        fields.forEach {
            code.appendLine("val ${it.simpleName} = $updateObjectParameterName.${it.simpleName} ?: this.${it.simpleName}")
        }
        code.appendLine("val result = ${sourceType.simpleName}(")
        fields.forEachIndexed { index, variableElement ->
            val end = if (index < fields.size - 1) "," else ""
            code.appendLine("\t${variableElement.simpleName} = ${variableElement.simpleName}$end")
        }
        code.appendLine(")")
        code.appendLine("return result")
        return FunSpec.builder("update")
            .addModifiers(visibilityModifiers)
            .addParameter(updateObjectParameterName, classToBuild)
            .receiver(sourceType.asKotlinClassName())
            .returns(sourceType.asKotlinTypeName())
            .addCode(code.toString())
            .build()
    }

    private fun createToUpdateObjectFunction(
        sourceType: TypeElement,
        classToBuild: ClassName,
        fields: List<VariableElement>,
        visibilityModifiers: Set<KModifier>
    ): FunSpec {
        val code = StringBuilder()
        code.appendLine("val result = ${classToBuild.simpleName}(")
        fields.forEachIndexed { index, variableElement ->
            val end = if (index < fields.size - 1) "," else ""
            code.appendLine("\t${variableElement.simpleName} = ${variableElement.simpleName}$end")
        }
        code.appendLine(")")
        code.appendLine("return result")
        return FunSpec.builder("to${classToBuild.simpleName}")
            .addModifiers(visibilityModifiers)
            .receiver(sourceType.asKotlinClassName())
            .returns(classToBuild)
            .addCode(code.toString())
            .build()
    }

    private fun createPartialObjectCompanionObject(
        sourceType: TypeElement,
        classToBuild: ClassName,
        fields: List<VariableElement>,
        visibilityModifiers: Set<KModifier>
    ) = TypeSpec.companionObjectBuilder()
        .addModifiers(visibilityModifiers)
        .addFunction(createPartialObjectUpdateFunction(sourceType, classToBuild, fields, visibilityModifiers))
        .addFunction(createMapToFunction(sourceType, classToBuild, fields, visibilityModifiers)).build()

    private fun createPartialObjectUpdateFunction(
        sourceType: TypeElement,
        classToBuild: ClassName,
        fields: List<VariableElement>,
        visibilityModifiers: Set<KModifier>,
        updateObjectParameterName: String = "updateObject"
    ): FunSpec {
        val code = StringBuilder()
        fields.forEach {
            code.appendLine("val ${it.simpleName} = $updateObjectParameterName.${it.simpleName}")
        }
        code.appendLine("val result = copy(")
        fields.forEachIndexed { index, variableElement ->
            val end = if (index < fields.size - 1) "," else ""
            code.appendLine("\t${variableElement.simpleName} = ${variableElement.simpleName}$end")
        }
        code.appendLine(")")
        code.appendLine("return result")
        return FunSpec.builder("update")
            .addModifiers(visibilityModifiers)
            .addParameter(updateObjectParameterName, classToBuild)
            .receiver(sourceType.asKotlinClassName())
            .returns(sourceType.asKotlinTypeName())
            .addCode(code.toString())
            .build()
    }

    private fun createMapToFunction(
        sourceType: TypeElement,
        classToBuild: ClassName,
        fields: List<VariableElement>,
        visibilityModifiers: Set<KModifier>
    ): FunSpec {
        val code = StringBuilder()
        code.appendLine("val result = ${classToBuild.simpleName}(")
        fields.forEachIndexed { index, variableElement ->
            val end = if (index < fields.size - 1) "," else ""
            code.appendLine("\t${variableElement.simpleName} = ${variableElement.simpleName}$end")
        }
        code.appendLine(")")
        code.appendLine("return result")
        return FunSpec.builder("mapTo${classToBuild.simpleName}")
            .addModifiers(visibilityModifiers)
            .receiver(sourceType.asKotlinClassName())
            .returns(classToBuild)
            .addCode(code.toString())
            .build()
    }

    /** Creates a function that will invoke [check] to confirm that each required field is populated. */
    private fun createCheckRequiredFieldsFunction(fields: List<Element>): FunSpec {
        val code = StringBuilder()
        fields.filterNot { it.isNullable() }
            .forEach { field ->
                code.append("    check(${field.simpleName}·!=·null, {\"${field.simpleName}·must·not·be·null\"})")
                    .appendLine()
            }

        return FunSpec.builder(CHECK_REQUIRED_FIELDS_FUNCTION_NAME)
            .addCode(code.toString())
            .addModifiers(KModifier.PRIVATE)
            .build()
    }

    private fun Element.asPartialClassProperty(): PropertySpec =
        PropertySpec.builder(
            simpleName.toString(),
            asKotlinTypeName().copy(nullable = isNullable()),
            KModifier.PUBLIC
        )
            //filter Nullable and NotNull annotations that are generated by Kotlin automatically for java compatibility
            .addAnnotations(this.annotationMirrors.filter {
                it.annotationType.asElement().simpleName.toString() != Nullable::class.simpleName
            }.filter {
                it.annotationType.asElement().simpleName.toString() != NotNull::class.simpleName
            }.map { AnnotationSpec.get(it) })
            .build()

    /** Creates a property for the field identified by this element. */
    private fun Element.asProperty(nullable: Boolean = !isMarkedWithRequiredAnnotation()): PropertySpec =
        PropertySpec.builder(
            simpleName.toString(),
            type = asKotlinTypeName().copy(nullable = nullable),
            KModifier.PUBLIC
        )
            .initializer(CodeBlock.of("null"))
                //filter Nullable and NotNull annotations that are generated by Kotlin automatically for java compatibility
            .addAnnotations(this.annotationMirrors.filter {
                it.annotationType.asElement().simpleName.toString() != Nullable::class.simpleName
            }.filter {
                it.annotationType.asElement().simpleName.toString() != NotNull::class.simpleName
            }.map { AnnotationSpec.get(it) })
            .build()

    private fun Element.isMarkedWithRequiredAnnotation() = hasAnnotation(RequiredForUpdateObject::class.java)

    private fun Element.asParameter(): ParameterSpec =
        ParameterSpec.builder(
            simpleName.toString(),
            asKotlinTypeName().copy(nullable = true),
            KModifier.PUBLIC
        )
            .defaultValue("null")
            .build()

    /** Creates a function that sets the property identified by this element, and returns the [builder]. */
    private fun Element.asSetterFunctionReturning(builder: ClassName): FunSpec {
        val fieldType = asKotlinTypeName()
        val parameterClass = if (isNullable()) {
            fieldType.copy(nullable = true)
        } else {
            fieldType
        }
        return FunSpec.builder(simpleName.toString())
            .addParameter(ParameterSpec.builder("value", parameterClass).build())
            .returns(builder)
            .addCode("return apply·{ this.$simpleName·=·value }\n")
            .build()
    }

    /**
     * Converts this element to a [TypeName], ensuring that java types such as [java.lang.String] are converted to their Kotlin equivalent,
     * also converting the TypeName according to any [NullableType] and [Mutable] annotations.
     */
    private fun Element.asKotlinTypeName(): TypeName {
        var typeName = asType().asKotlinTypeName()
        return typeName
    }

    /**
     * Converts this type to one containing nullable elements.
     *
     * For instance `List<String>` is converted to `List<String?>`, `Map<String, String>` to `Map<String, String?>`).
     * @throws NoSuchElementException if [this.typeArguments] is empty.
     */
    private fun ParameterizedTypeName.withNullableType(): ParameterizedTypeName {
        val lastType = this.typeArguments.last().copy(nullable = true)
        val typeArguments = ArrayList<TypeName>()
        typeArguments.addAll(this.typeArguments.dropLast(1))
        typeArguments.add(lastType)
        return this.rawType.parameterizedBy(*typeArguments.toTypedArray())
    }

    /**
     * Converts this type to its mutable equivalent.
     *
     * For instance `List<String>` is converted to `MutableList<String>`.
     * @throws NullPointerException if [this.rawType] cannot be mapped to a mutable collection
     */
    private fun ParameterizedTypeName.asMutableCollection(): ParameterizedTypeName {
        val mutable = MUTABLE_COLLECTIONS[rawType]!!
            .parameterizedBy(*this.typeArguments.toTypedArray())
            .copy(annotations = this.annotations) as ParameterizedTypeName
        return if (isNullable) {
            mutable.copy(nullable = true) as ParameterizedTypeName
        } else {
            mutable
        }
    }

    /** Converts this TypeMirror to a [TypeName], ensuring that java types such as [java.lang.String] are converted to their Kotlin equivalent. */
    private fun TypeMirror.asKotlinTypeName(): TypeName {
        return when (this) {
            is PrimitiveType -> processingEnv.typeUtils.boxedClass(this as PrimitiveType?)
                .asKotlinClassName()
            is ArrayType -> {
                val arrayClass = ClassName("kotlin", "Array")
                return arrayClass.parameterizedBy(this.componentType.asKotlinTypeName())
            }
            is DeclaredType -> {
                val typeName = this.asTypeElement().asKotlinClassName()
                if (!this.typeArguments.isEmpty()) {
                    val kotlinTypeArguments = typeArguments.stream()
                        .map { it.asKotlinTypeName() }
                        .collect(Collectors.toList())
                        .toTypedArray()
                    return typeName.parameterizedBy(*kotlinTypeArguments)
                }
                return typeName
            }
            else -> this.asTypeElement().asKotlinClassName()
        }
    }

    /** Converts this element to a [ClassName], ensuring that java types such as [java.lang.String] are converted to their Kotlin equivalent. */
    private fun TypeElement.asKotlinClassName(): ClassName {
        val className = asClassName()
        return try {
            // ensure that java.lang.* and java.util.* etc classes are converted to their kotlin equivalents
            Class.forName(className.canonicalName).kotlin.asClassName()
        } catch (e: ClassNotFoundException) {
            // probably part of the same source tree as the annotated class
            className
        }
    }

    /** Returns the [TypeElement] represented by this [TypeMirror]. */
    private fun TypeMirror.asTypeElement() = processingEnv.typeUtils.asElement(this) as TypeElement

    /** Returns true as long as this [Element] is not a [PrimitiveType] and does not have the [NotNull] annotation. */
    private fun Element.isNullable(): Boolean {
        if (this.asType() is PrimitiveType) {
            return false
        }
        return !hasAnnotation(NotNull::class.java)
    }

    /**
     * Returns true if this element has the specified [annotation], or if the parent class has a matching constructor parameter with the annotation.
     * (This is necessary because builder annotations can be applied to both fields and constructor parameters - and constructor parameters take precedence.
     * Rather than require clients to specify, for instance, `@field:NullableType`, this method also checks for annotations of constructor parameters
     * when this element is a field).
     */
    private fun Element.hasAnnotation(annotation: Class<*>): Boolean {
        return hasAnnotationDirectly(annotation) || hasAnnotationViaConstructorParameter(annotation)
    }

    /** Return true if this element has the specified [annotation]. */
    private fun Element.hasAnnotationDirectly(annotation: Class<*>): Boolean {
        return this.annotationMirrors
            .map { it.annotationType.toString() }
            .toSet()
            .contains(annotation.name)
    }

    /** Return true if there is a constructor parameter with the same name as this element that has the specified [annotation]. */
    private fun Element.hasAnnotationViaConstructorParameter(annotation: Class<*>): Boolean {
        val parameterAnnotations = getConstructorParameter()?.annotationMirrors ?: listOf()
        return parameterAnnotations
            .map { it.annotationType.toString() }
            .toSet()
            .contains(annotation.name)
    }

    /** Returns the first constructor parameter with the same name as this element, if any such exists. */
    private fun Element.getConstructorParameter(): VariableElement? {
        val enclosingElement = this.enclosingElement
        return if (enclosingElement is TypeElement) {
            val allMembers = processingEnv.elementUtils.getAllMembers(enclosingElement)
            constructorsIn(allMembers)
                .flatMap { it.parameters }
                .firstOrNull { it.simpleName == this.simpleName }
        } else {
            null
        }
    }

    /**
     * Returns the given annotation, retrieved from this element directly, or from the corresponding constructor parameter.
     *
     * @throws NullPointerException if no such annotation can be found - use [hasAnnotation] before calling this method.
     */
    private fun <A : Annotation> Element.findAnnotation(annotation: Class<A>): A {
        return if (hasAnnotationDirectly(annotation)) {
            getAnnotation(annotation)
        } else {
            getConstructorParameter()!!.getAnnotation(annotation)
        }
    }

    /** Returns the given [assertion], logging an error message if it is not true. */
    private fun Element.assert(assertion: Boolean, message: String): Boolean {
        if (!assertion) {
            this.errorMessage { message }
        }
        return assertion
    }

    /** Prints an error message using this element as a position hint. */
    private fun Element.errorMessage(message: () -> String) {
        processingEnv.messager.printMessage(ERROR, message(), this)
    }
}

private fun ProcessingEnvironment.errorMessage(message: () -> String) {
    this.messager.printMessage(ERROR, message())
}

private fun ProcessingEnvironment.noteMessage(message: () -> String) {
    this.messager.printMessage(NOTE, message())
}

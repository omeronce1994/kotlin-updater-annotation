package com.nando.update_object.annotation.ktx

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.properties.Properties
import kotlinx.serialization.properties.encodeToMap

object JsonKtx {
    inline fun <reified T> T.toJson(converter: (T) -> String = JsonKtx::toJsonConverter) = converter(this)

    inline fun <reified T> String.toDataClass(converter: (String) -> T = JsonKtx::fromJsonConverter) = converter(this)

    inline fun <reified T> T.toFieldsMap(
        jsonConverter: (T) -> String = JsonKtx::toJsonConverter,
        fromJsonToMapConverter: (json: String) -> Map<String, String> = JsonKtx::fromJsonConverter
    ): Map<String, String> {
        val json = toJson(jsonConverter)
        return json.toDataClass(fromJsonToMapConverter)
    }

    @OptIn(ExperimentalSerializationApi::class)
    inline fun <reified T> T.toAnyMap(): Map<String, Any?> {
        return Properties(EmptySerializersModule).encodeToMap(this)
    }

    inline fun <reified T> toJsonConverter(value: T): String  {
        val serializer: KSerializer<T> = Json.serializersModule.serializer()
        return Json.encodeToString(serializer, value)
    }

    inline fun <reified T> fromJsonConverter(json: String): T  {
        val serializer: KSerializer<T> = Json.serializersModule.serializer()
        return Json.decodeFromString(serializer, json)
    }

    fun Collection<*>.toJsonElement(): JsonElement = JsonArray(mapNotNull { it.toJsonElement() })

    fun Map<*, *>.toJsonElement(): JsonElement = JsonObject(
        mapNotNull {
            (it.key as? String ?: return@mapNotNull null) to it.value.toJsonElement()
        }.toMap(),
    )

    fun Any?.toJsonElement(): JsonElement = when (this) {
        null -> JsonNull
        is Map<*, *> -> toJsonElement()
        is Collection<*> -> toJsonElement()
        else -> JsonPrimitive(toString())
    }
}


package dev.logtide.sdk.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

/**
 * Custom serializer for Any type in metadata maps
 * Handles primitive types, lists, and nested maps
 */
object AnyValueSerializer : KSerializer<Any> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("AnyValue", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Any) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw IllegalStateException("This serializer can only be used with Json")

        val element = when (value) {
            is Number -> JsonPrimitive(value)
            is String -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Map<*, *> -> buildJsonObject {
                value.forEach { (k, v) ->
                    if (k is String && v != null) {
                        put(k, serializeValue(v))
                    }
                }
            }
            is List<*> -> buildJsonArray {
                value.forEach { item ->
                    if (item != null) {
                        add(serializeValue(item))
                    }
                }
            }
            else -> JsonPrimitive(value.toString())
        }

        jsonEncoder.encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): Any {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw IllegalStateException("This serializer can only be used with Json")

        return deserializeJsonElement(jsonDecoder.decodeJsonElement())
    }

    private fun serializeValue(value: Any): JsonElement = when (value) {
        is Number -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Map<*, *> -> buildJsonObject {
            value.forEach { (k, v) ->
                if (k is String && v != null) {
                    put(k, serializeValue(v))
                }
            }
        }
        is List<*> -> buildJsonArray {
            value.forEach { item ->
                if (item != null) {
                    add(serializeValue(item))
                }
            }
        }
        else -> JsonPrimitive(value.toString())
    }

    private fun deserializeJsonElement(element: JsonElement): Any = when (element) {
        is JsonPrimitive -> {
            when {
                element.isString -> element.content
                element.booleanOrNull != null -> element.boolean
                element.longOrNull != null -> element.long
                element.doubleOrNull != null -> element.double
                else -> element.content
            }
        }
        is JsonObject -> element.mapValues { deserializeJsonElement(it.value) }
        is JsonArray -> element.map { deserializeJsonElement(it) }
    }
}

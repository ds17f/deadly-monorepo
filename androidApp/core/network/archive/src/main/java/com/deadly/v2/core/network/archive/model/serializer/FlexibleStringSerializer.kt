package com.deadly.v2.core.network.archive.model.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

/**
 * Custom serializer that can handle fields that may be either String or Array<String>
 * Returns the first string if it's an array, or the string itself if it's a string
 */
object FlexibleStringSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexibleString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String?) {
        encoder.encodeString(value ?: "")
    }

    override fun deserialize(decoder: Decoder): String? {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("This serializer only works with JSON")
        
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> {
                when {
                    element.isString -> element.content
                    else -> element.content // Handle numbers as strings
                }
            }
            is JsonArray -> {
                // If it's an array, take the first non-empty element
                element.firstOrNull { it is JsonPrimitive && !it.content.isBlank() }
                    ?.let { (it as JsonPrimitive).content }
            }
            is JsonNull -> null
            else -> null
        }
    }
}

/**
 * Custom serializer for fields that may be String, Array<String>, or Int
 * Converts everything to String for consistency
 */
object FlexibleStringOrIntSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexibleStringOrInt", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String?) {
        encoder.encodeString(value ?: "")
    }

    override fun deserialize(decoder: Decoder): String? {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("This serializer only works with JSON")
        
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> element.content
            is JsonArray -> {
                element.firstOrNull { it is JsonPrimitive }
                    ?.let { (it as JsonPrimitive).content }
            }
            is JsonNull -> null
            else -> null
        }
    }
}

/**
 * Custom serializer for fields that can be String or Array<String>
 * Returns all strings joined with newlines
 */
object FlexibleStringListSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexibleStringList", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String?) {
        encoder.encodeString(value ?: "")
    }

    override fun deserialize(decoder: Decoder): String? {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("This serializer only works with JSON")
        
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> {
                if (element.isString) element.content else null
            }
            is JsonArray -> {
                element.filterIsInstance<JsonPrimitive>()
                    .map { it.content }
                    .filter { it.isNotBlank() }
                    .joinToString("\n")
                    .takeIf { it.isNotBlank() }
            }
            is JsonNull -> null
            else -> null
        }
    }
}
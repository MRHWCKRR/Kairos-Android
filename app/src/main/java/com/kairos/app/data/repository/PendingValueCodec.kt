package com.kairos.app.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.GeoPoint
import kotlinx.serialization.json.*
import java.util.Base64
import java.util.Date

/** Tagged values preserve Firestore types, including fields unknown to this client. */
class PendingValueCodec(
    private val referenceResolver: ((String) -> DocumentReference)? = null,
    private val referenceProjectId: String? = null
) {
    fun encode(edit: PendingEdit): String = buildJsonObject {
        put("id", edit.id)
        put("userId", edit.userId)
        put("kind", edit.kind)
        put("documentId", edit.documentId)
        put("documentExisted", edit.documentExisted)
        put("base", encodeValue(edit.base, 0))
        put("edited", encodeValue(edit.edited, 0))
        put("rawBaseline", encodeValue(edit.rawBaseline, 0))
    }.toString()

    fun decode(text: String): PendingEdit {
        val obj = Json.parseToJsonElement(text).jsonObject
        require(obj.keys == setOf("id", "userId", "kind", "documentId", "documentExisted", "base", "edited", "rawBaseline")) { "Invalid pending edit fields" }
        fun map(name: String): Map<String, Any?> {
            val decoded = decodeValue(obj.getValue(name), 0)
            require(decoded is Map<*, *>) { "Invalid pending map: $name" }
            @Suppress("UNCHECKED_CAST")
            return decoded as Map<String, Any?>
        }
        return PendingEdit(obj.string("id"), obj.string("userId"), obj.string("kind"),
            obj.string("documentId"), obj.getValue("documentExisted").jsonPrimitive.boolean,
            map("base"), map("edited"), map("rawBaseline"))
    }

    private fun encodeValue(value: Any?, depth: Int): JsonElement {
        require(depth <= 100) { "Pending value nesting is too deep" }
        var type: String
        val data: JsonElement = when (value) {
            null -> { type = "null"; JsonNull }
            is Boolean -> { type = "bool"; JsonPrimitive(value) }
            is String -> { type = "string"; JsonPrimitive(value) }
            is Int -> { type = "int"; JsonPrimitive(value) }
            is Long -> { type = "long"; JsonPrimitive(value) }
            is Double -> { require(value.isFinite()) { "Non-finite pending number" }; type = "double"; JsonPrimitive(value) }
            is Float -> { require(value.isFinite()) { "Non-finite pending number" }; type = "float"; JsonPrimitive(value) }
            is Short -> { type = "short"; JsonPrimitive(value.toInt()) }
            is Byte -> { type = "byte"; JsonPrimitive(value.toInt()) }
            is Timestamp -> { type = "timestamp"; JsonArray(listOf(JsonPrimitive(value.seconds), JsonPrimitive(value.nanoseconds))) }
            is GeoPoint -> {
                require(value.latitude.isFinite() && value.longitude.isFinite()) { "Non-finite pending coordinates" }
                type = "geopoint"; JsonArray(listOf(JsonPrimitive(value.latitude), JsonPrimitive(value.longitude)))
            }
            is Blob -> { type = "blob"; JsonPrimitive(Base64.getEncoder().encodeToString(value.toBytes())) }
            is Date -> { type = "date"; JsonPrimitive(value.time) }
            is DocumentReference -> {
                require(referenceResolver != null && referenceProjectId != null) { "Pending document references require a project and resolver" }
                require(value.firestore.app.options.projectId == referenceProjectId) { "Pending reference belongs to a different Firebase project" }
                require(value == referenceResolver.invoke(value.path)) { "Pending reference belongs to a different Firestore database" }
                type = "reference"; buildJsonObject { put("project", referenceProjectId); put("path", value.path) }
            }
            is Map<*, *> -> {
                type = "map"
                buildJsonObject { value.entries.sortedBy {
                    it.key as? String ?: throw IllegalArgumentException("Pending map keys must be strings")
                }.forEach { (key, item) ->
                    require(key is String) { "Pending map keys must be strings" }
                    put(key, encodeValue(item, depth + 1))
                } }
            }
            is List<*> -> { type = "list"; JsonArray(value.map { encodeValue(it, depth + 1) }) }
            else -> throw IllegalArgumentException("Unsupported pending value type: ${value.javaClass.name}")
        }
        return buildJsonObject { put("type", type); put("value", data) }
    }

    private fun decodeValue(element: JsonElement, depth: Int): Any? {
        require(depth <= 100) { "Pending value nesting is too deep" }
        val obj = element.jsonObject
        require(obj.keys == setOf("type", "value")) { "Invalid tagged pending value" }
        val value = obj.getValue("value")
        fun finite(): Double = value.jsonPrimitive.double.also { require(it.isFinite()) { "Non-finite pending number" } }
        return when (val type = obj.string("type")) {
            "null" -> { require(value == JsonNull); null }
            "bool" -> value.jsonPrimitive.boolean
            "string" -> value.jsonPrimitive.also { require(it.isString) }.content
            "int" -> value.jsonPrimitive.int
            "long" -> value.jsonPrimitive.long
            "double" -> finite()
            "float" -> finite().toFloat().also { require(it.isFinite()) }
            "short" -> value.jsonPrimitive.int.also { require(it in Short.MIN_VALUE..Short.MAX_VALUE) }.toShort()
            "byte" -> value.jsonPrimitive.int.also { require(it in Byte.MIN_VALUE..Byte.MAX_VALUE) }.toByte()
            "timestamp" -> value.jsonArray.also { require(it.size == 2) }.let { Timestamp(it[0].jsonPrimitive.long, it[1].jsonPrimitive.int) }
            "geopoint" -> value.jsonArray.also { require(it.size == 2) }.let {
                val latitude = it[0].jsonPrimitive.double
                val longitude = it[1].jsonPrimitive.double
                require(latitude.isFinite() && longitude.isFinite()) { "Non-finite pending coordinates" }
                GeoPoint(latitude, longitude)
            }
            "blob" -> Blob.fromBytes(Base64.getDecoder().decode(value.jsonPrimitive.content))
            "date" -> Date(value.jsonPrimitive.long)
            "reference" -> {
                val reference = value.jsonObject
                require(referenceProjectId != null && reference.string("project") == referenceProjectId) { "Pending reference Firebase project mismatch" }
                requireNotNull(referenceResolver) { "Pending document references require a resolver" }(reference.string("path")).also {
                    require(it.firestore.app.options.projectId == referenceProjectId) { "Pending reference resolver project mismatch" }
                }
            }
            "map" -> value.jsonObject.mapValues { decodeValue(it.value, depth + 1) }
            "list" -> value.jsonArray.map { decodeValue(it, depth + 1) }
            else -> throw IllegalArgumentException("Unknown pending value tag: $type")
        }
    }

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.also { require(it.isString) }.content
}

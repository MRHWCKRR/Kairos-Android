package com.kairos.app.data.repository

/** A concurrent edit cannot be applied without discarding somebody else's data. */
class SharedDataConflict(message: String) : IllegalStateException(message)

/**
 * Applies changes between complete known-model projections to a raw shared document.
 * Unknown fields and remote collection order survive. Record IDs must be unique across the
 * document within each collection kind, including after merging concurrent additions.
 * Missing raw fields are equivalent to
 * projection defaults; explicit null is never treated as a missing field.
 *
 * With projections alone, deletion can only check known fields. Supply [baselineRaw] from the
 * original snapshot to also reject deletion of a record whose unknown fields changed remotely.
 */
object SharedDataMerge {
    private val collections = setOf("boards", "sections", "tasks", "scheduleEvents", "notifications")
    private object Missing

    fun merge(
        base: Map<String, Any?>,
        edited: Map<String, Any?>,
        remote: Map<String, Any?>,
        baselineRaw: Map<String, Any?>? = null
    ): Map<String, Any?> {
        // Actions address IDs globally, so duplicates in separate parents are ambiguous too.
        validateIdentities(base)
        validateIdentities(edited)
        validateIdentities(remote)
        val merged = mergeMap(base, edited, remote, baselineRaw, "document")
        // Separate valid concurrent additions can collide only after the merge.
        validateIdentities(merged)
        return merged
    }

    private fun validateIdentities(document: Map<String, Any?>) {
        val seen = collections.associateWith { mutableSetOf<String>() }
        fun visit(map: Map<String, Any?>, path: String) {
            for ((key, value) in map) {
                if (key in collections && value is List<*>) {
                    val records = records(value, "$path.$key")
                    for ((id, record) in records) {
                        if (!seen.getValue(key).add(id)) conflict("$path.$key[$id] (ambiguous global ID)")
                        visit(record, "$path.$key[$id]")
                    }
                } else if (value is Map<*, *>) {
                    visit(stringMap(value, "$path.$key"), "$path.$key")
                }
            }
        }
        visit(document, "document")
    }

    private fun mergeMap(
        base: Map<String, Any?>,
        edited: Map<String, Any?>,
        remote: Map<String, Any?>,
        raw: Map<String, Any?>?,
        path: String
    ): Map<String, Any?> {
        val result = remote.toMutableMap()
        for (key in base.keys + edited.keys) {
            val merged = mergeValue(
                base.value(key), edited.value(key), remote.value(key), if (raw != null) raw.value(key) else Missing,
                "$path.$key", key in collections &&
                    (base[key] is List<*> || edited[key] is List<*> || remote[key] is List<*>)
            )
            if (merged === Missing) result.remove(key) else result[key] = merged
        }
        return result
    }

    private fun mergeValue(
        base: Any?, edited: Any?, remote: Any?, raw: Any?, path: String, collection: Boolean
    ): Any? {
        if (collection) {
            // Validate identities even when the local collection did not change.
            val b = records(base, path)
            val e = records(edited, path)
            val r = records(remote, path)
            if (same(base, edited)) return remote
            return mergeRecords(b, e, r, raw, path)
        }
        if (same(base, edited)) return remote
        if (base is Map<*, *> && edited is Map<*, *>) {
            if (remote !== Missing && remote !is Map<*, *>) conflict(path)
            if (remote === Missing && raw !== Missing) conflict(path)
            return mergeMap(
                stringMap(base, path), stringMap(edited, path),
                if (remote is Map<*, *>) stringMap(remote, path) else emptyMap(),
                if (raw is Map<*, *>) stringMap(raw, path) else null, path
            )
        }
        if (same(remote, edited)) return remote // Retried write already applied.
        if ((remote === Missing && raw === Missing) || same(remote, base)) return edited
        conflict(path)
    }

    private fun mergeRecords(
        base: LinkedHashMap<String, Map<String, Any?>>,
        edited: LinkedHashMap<String, Map<String, Any?>>,
        remote: LinkedHashMap<String, Map<String, Any?>>,
        raw: Any?, path: String
    ): List<Map<String, Any?>> {
        val rawRecords = if (raw is List<*>) records(raw, path) else null
        val result = remote.toMutableMap()
        for ((id, old) in base) {
            val next = edited[id]
            val current = remote[id]
            if (next == null) {
                if (current != null) {
                    val originalRaw = rawRecords?.get(id)
                    val unchanged = if (originalRaw != null) same(originalRaw, current)
                        else matchesProjection(old, current)
                    if (!unchanged) conflict("$path[$id]")
                    result.remove(id)
                }
            } else if (!same(old, next)) {
                if (current == null) conflict("$path[$id] (removed remotely)")
                result[id] = mergeMap(old, next, current, rawRecords?.get(id), "$path[$id]")
            }
        }
        for ((id, next) in edited) {
            if (id !in base) {
                val current = remote[id]
                if (current != null && !same(current, next)) conflict("$path[$id] (ID already exists)")
                if (current == null) result[id] = next
            }
        }
        return result.values.toList()
    }

    private fun records(value: Any?, path: String): LinkedHashMap<String, Map<String, Any?>> {
        if (value === Missing) return linkedMapOf()
        if (value !is List<*>) conflict("$path (expected a collection)")
        val result = linkedMapOf<String, Map<String, Any?>>()
        for (item in value) {
            if (item !is Map<*, *>) conflict("$path (expected a record)")
            val record = stringMap(item, path)
            val id = record["id"] as? String
            if (id.isNullOrBlank() || result.containsKey(id)) conflict("$path (missing or duplicate ID)")
            result[id] = record
        }
        return result
    }

    /** Compares only represented fields, allowing omitted Firestore defaults. */
    private fun matchesProjection(base: Any?, remote: Any?): Boolean {
        if (remote === Missing) return true
        if (base is Map<*, *> && remote is Map<*, *>) {
            return base.all { (key, value) ->
                matchesProjection(value, if (remote.containsKey(key)) remote[key] else Missing)
            }
        }
        if (base is List<*> && remote is List<*>) {
            return base.size == remote.size && base.indices.all { matchesProjection(base[it], remote[it]) }
        }
        return same(base, remote)
    }

    private fun same(a: Any?, b: Any?): Boolean = when {
        a === Missing || b === Missing -> a === b
        a is Number && b is Number -> {
            // Firestore integers are Long even when a model emits Int. Do not round large integers.
            if (a is Byte || a is Short || a is Int || a is Long) {
                if (b is Byte || b is Short || b is Int || b is Long) a.toLong() == b.toLong()
                else a.toDouble() == b.toDouble()
            } else a.toDouble() == b.toDouble()
        }
        a is Map<*, *> && b is Map<*, *> ->
            a.keys == b.keys && a.all { (key, value) -> same(value, b[key]) }
        a is List<*> && b is List<*> -> a.size == b.size && a.indices.all { same(a[it], b[it]) }
        else -> a == b
    }

    private fun stringMap(value: Map<*, *>, path: String): Map<String, Any?> {
        if (value.keys.any { it !is String }) conflict("$path (invalid field name)")
        @Suppress("UNCHECKED_CAST")
        return value as Map<String, Any?>
    }

    private fun Map<String, Any?>.value(key: String): Any? = if (containsKey(key)) get(key) else Missing
    private fun conflict(path: String): Nothing = throw SharedDataConflict("Shared data changed at $path; reload and try again.")
}

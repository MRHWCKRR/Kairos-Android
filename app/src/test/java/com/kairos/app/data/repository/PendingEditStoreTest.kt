package com.kairos.app.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.GeoPoint
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ConcurrentLinkedQueue

class PendingEditStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun edit(user: String = "alice", id: String = "op-1", raw: Map<String, Any?> = emptyMap()) = PendingEdit(
        id, user, "plan", "document", true,
        mapOf("title" to "old"), mapOf("title" to "new"), raw)

    @Test fun restartPreservesOriginalPayloadAndFirestoreTypes() {
        val root = temporary.newFolder()
        val raw = mapOf("timestamp" to Timestamp(1_700_000_000L, 123456789),
            "blob" to Blob.fromBytes(byteArrayOf(0, -1, 3)), "point" to GeoPoint(12.5, -32.0),
            "date" to Date(172899L), "nested" to listOf(null, true, "value", 42, 42L, 0.5))
        val original = edit(raw = raw)
        PendingEditStore(root).put(original)
        assertEquals(listOf(original), PendingEditStore(root).list("alice"))
    }

    @Test fun accountsAndAcknowledgementsAreIsolated() {
        val root = temporary.newFolder()
        val first = PendingEditStore(root)
        val second = PendingEditStore(root)
        first.put(edit())
        second.put(edit("bob"))
        second.put(edit(id = "op-2"))
        first.remove("bob", "op-2")
        first.remove("alice", "op-1")
        assertEquals(listOf("op-2"), second.list("alice").map { it.id })
        assertEquals(listOf("op-1"), first.list("bob").map { it.id })
        assertEquals(first.changes.value, second.changes.value)
    }

    @Test fun immutableIdsAndFailedStagingPreserveOldRecord() {
        val root = temporary.newFolder()
        val store = PendingEditStore(root)
        val original = edit()
        store.put(original)
        store.put(original)
        assertEquals(1L, store.changes.value)
        assertThrows(IllegalArgumentException::class.java) { store.put(original.copy(edited = mapOf("title" to "different"))) }
        val bounded = PendingEditStore(root, maxRecordBytes = 512)
        assertThrows(IllegalArgumentException::class.java) { bounded.put(edit(id = "op-2", raw = mapOf("big" to "x".repeat(1000)))) }
        assertEquals(listOf(original), PendingEditStore(root).list("alice"))
    }

    @Test fun malformedVersionAndChecksumFailVisiblyAndCanBeDiscarded() {
        listOf("version" to "\"version\":7", "checksum" to "\"checksum\":\"wrong\"").forEach { (field, replacement) ->
            val root = temporary.newFolder()
            val store = PendingEditStore(root)
            store.put(edit())
            store.put(edit("bob"))
            val file = root.walkTopDown().first { it.isFile && it.readText().contains("alice") }
            file.writeText(file.readText().replace(Regex("\"$field\":(?:1|\"[a-f0-9]+\")"), replacement))
            assertThrows(IOException::class.java) { store.list("alice") }
            store.clear("alice")
            assertTrue(store.list("alice").isEmpty())
            assertEquals(1, store.list("bob").size)
        }
    }

    @Test fun unknownTypesAndNonFiniteNumbersCannotBeStaged() {
        val store = PendingEditStore(temporary.newFolder())
        listOf(Any(), Double.NaN, Double.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { store.put(edit(raw = mapOf("unknown" to value))) }
        }
        assertTrue(store.list("alice").isEmpty())
    }

    @Test fun numericTagsPreserveFiniteTypesAndUnknownTagsFail() {
        val codec = PendingValueCodec()
        val original = edit(raw = mapOf("int" to Int.MAX_VALUE, "long" to Long.MAX_VALUE,
            "double" to -0.0, "float" to 0.125f, "short" to 3.toShort(), "byte" to 1.toByte()))
        assertEquals(original, codec.decode(codec.encode(original)))
        assertThrows(IllegalArgumentException::class.java) { codec.decode(codec.encode(original).replace("\"long\"", "\"alien\"")) }
    }

    @Test fun interruptedStageIsNeverReplayed() {
        val root = temporary.newFolder()
        val store = PendingEditStore(root)
        store.put(edit())
        File(root.listFiles()!!.single(), "stage-interrupted.tmp").writeText("incomplete")
        assertEquals(listOf(edit()), PendingEditStore(root).list("alice"))
    }

    @Test fun simultaneousInstancesDoNotOverwriteOperations() {
        val root = temporary.newFolder()
        val stores = listOf(PendingEditStore(root), PendingEditStore(root))
        val start = CountDownLatch(1)
        val failures = ConcurrentLinkedQueue<Throwable>()
        val threads = stores.mapIndexed { index, store -> Thread {
            try {
                start.await()
                repeat(5) { store.put(edit(id = "operation-$index-$it")) }
            } catch (error: Throwable) { failures.add(error) }
        }.also { it.start() } }
        start.countDown()
        threads.forEach { it.join() }
        assertTrue(failures.toString(), failures.isEmpty())
        assertEquals(10, PendingEditStore(root).list("alice").size)
        assertEquals(10L, stores.first().changes.value)
    }
}

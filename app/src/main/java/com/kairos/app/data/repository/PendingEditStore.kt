package com.kairos.app.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Account-scoped, bounded, immutable operation journal in an app-private no-backup directory. */
class PendingEditStore(
    directory: File,
    private val codec: PendingValueCodec = PendingValueCodec(),
    private val maxRecordBytes: Long = 4L * 1024 * 1024,
    private val maxAccountBytes: Long = 32L * 1024 * 1024
) {
    private val directory = directory.canonicalFile
    private val state = states.getOrPut(this.directory.path) { StoreState() }
    val changes: StateFlow<Long> = state.changes.asStateFlow()

    fun put(edit: PendingEdit) = synchronized(state) {
        require(edit.userId.isNotBlank() && edit.id.isNotBlank() && edit.documentId.isNotBlank()) { "Pending edit identity must not be blank" }
        require(edit.kind in setOf("plan", "profile", "ai")) { "Unsupported pending edit kind" }
        val payload = codec.encode(edit)
        val bytes = buildJsonObject {
            put("version", 1)
            put("checksum", hash(payload))
            put("payload", payload)
        }.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= maxRecordBytes) { "Pending edit exceeds record size limit" }
        val account = account(edit.userId, create = true)
        // Reading the complete journal first prevents accepting new writes over corruption.
        val existing = listLocked(edit.userId)
        val target = File(account, "${hash(edit.id)}.json")
        if (existing.any { it.id == edit.id }) {
            require(read(target, edit.userId).let(codec::encode) == payload) { "Pending operation ID already has a different payload" }
            return@synchronized
        }
        val used = records(account).sumOf { it.length() }
        require(used + bytes.size <= maxAccountBytes) { "Pending edits exceed account storage limit" }
        // A previous process may have stopped before its atomic rename. Reclaim only
        // validated uncommitted staging files while holding the shared store lock.
        account.listFiles()?.filter { it.name.matches(STAGING_NAME) }?.forEach {
            require(!Files.isSymbolicLink(it.toPath()) && it.isFile) { "Unsafe pending staging file" }
            Files.delete(it.toPath())
        }
        val staging = File.createTempFile("stage-", ".tmp", account)
        try {
            FileOutputStream(staging).use { stream -> stream.write(bytes); stream.fd.sync() }
            // No fallback to a non-atomic copy: a failed stage must leave the journal intact.
            Files.move(staging.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            state.changes.value += 1
        } finally {
            staging.delete()
        }
    }

    fun list(userId: String): List<PendingEdit> = synchronized(state) { listLocked(userId) }

    fun remove(userId: String, id: String) = synchronized(state) {
        require(id.isNotBlank()) { "Pending operation ID must not be blank" }
        val account = account(userId, create = false)
        val target = File(account, "${hash(id)}.json")
        if (target.exists()) {
            val edit = read(target, userId)
            require(edit.id == id) { "Pending operation identity mismatch" }
            Files.delete(target.toPath())
            state.changes.value += 1
        }
    }

    /** Explicit user-requested discard, including corrupt records belonging to this account. */
    fun clear(userId: String) = synchronized(state) {
        val account = account(userId, create = false)
        if (account.exists()) {
            val files = account.listFiles() ?: throw IOException("Cannot read pending edits directory")
            files.forEach { file ->
                require(file.name.matches(RECORD_NAME) || file.name.matches(STAGING_NAME)) { "Unexpected pending journal entry" }
                require(!Files.isSymbolicLink(file.toPath()) && file.isFile) { "Unsafe pending journal entry" }
            }
            files.forEach { Files.delete(it.toPath()) }
            state.changes.value += 1
        }
    }

    private fun listLocked(userId: String): List<PendingEdit> {
        val account = account(userId, create = false)
        if (!account.exists()) return emptyList()
        return records(account).sortedWith(compareBy<File> { it.lastModified() }.thenBy { it.name })
            .map { read(it, userId) }
    }

    private fun records(account: File): List<File> = (account.listFiles()
        ?: throw IOException("Cannot read pending edits directory")).mapNotNull { file ->
        require(!Files.isSymbolicLink(file.toPath()) && file.isFile) { "Unsafe pending journal entry" }
        when {
            file.name.matches(RECORD_NAME) -> file
            file.name.matches(STAGING_NAME) -> null // An interrupted uncommitted stage is never replayed.
            else -> throw IOException("Unexpected pending journal entry")
        }
    }

    private fun read(file: File, userId: String): PendingEdit = try {
        require(!Files.isSymbolicLink(file.toPath()) && file.isFile) { "Unsafe pending edit file" }
        require(file.length() <= maxRecordBytes) { "Pending edit exceeds record size limit" }
        val envelope = Json.parseToJsonElement(file.readText(Charsets.UTF_8)).jsonObject
        require(envelope.keys == setOf("version", "checksum", "payload")) { "Invalid pending edit envelope" }
        require(envelope.getValue("version").jsonPrimitive.int == 1) { "Unsupported pending edit version" }
        val payload = envelope.getValue("payload").jsonPrimitive.also { require(it.isString) }.content
        require(envelope.getValue("checksum").jsonPrimitive.content == hash(payload)) { "Pending edit checksum mismatch" }
        codec.decode(payload).also { edit ->
            require(edit.userId == userId && file.name == "${hash(edit.id)}.json") { "Pending edit identity mismatch" }
            require(edit.kind in setOf("plan", "profile", "ai")) { "Unsupported pending edit kind" }
        }
    } catch (error: Exception) {
        throw IOException("Cannot read pending edit ${file.name}: ${error.message}", error)
    }

    private fun account(userId: String, create: Boolean): File {
        require(userId.isNotBlank()) { "Pending account ID must not be blank" }
        require(!Files.isSymbolicLink(directory.toPath())) { "Unsafe pending journal directory" }
        val account = File(directory, hash(userId))
        require(!Files.isSymbolicLink(account.toPath())) { "Unsafe pending account directory" }
        if (create && !account.isDirectory && !account.mkdirs()) throw IOException("Cannot create pending edits directory")
        if (Files.exists(account.toPath(), LinkOption.NOFOLLOW_LINKS)) require(account.isDirectory) { "Invalid pending account directory" }
        return account
    }

    private class StoreState { val changes = MutableStateFlow(0L) }

    companion object {
        private val states = ConcurrentHashMap<String, StoreState>()
        private val RECORD_NAME = Regex("[0-9a-f]{64}\\.json")
        private val STAGING_NAME = Regex("stage-[A-Za-z0-9-]+\\.tmp")
        private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

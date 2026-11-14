package com.kairos.app.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.kairos.app.data.models.ChatMessage
import com.kairos.app.data.repository.*
import com.kairos.app.ui.screens.ai.AiHelperViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** All credentials, storage and provider replies belong to local demo emulators. */
@RunWith(AndroidJUnit4::class)
class AiWorkspaceSessionTest {
    private var app: FirebaseApp? = null
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var repository: FirebaseRepository
    private lateinit var uid: String
    private lateinit var directory: File
    private lateinit var vm: AiHelperViewModel
    private val store = ViewModelStore()
    private val provider = ControlledAi()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private class ControlledAi : AiRepository() {
        data class Call(val messages: List<ChatMessage>, val reply: CompletableDeferred<String> = CompletableDeferred())
        val calls = Channel<Call>(Channel.UNLIMITED)
        var count = 0
        override suspend fun sendChatRequest(messages: List<ChatMessage>): String {
            count++
            val call = Call(messages); calls.send(call)
            return call.reply.await()
        }
    }
    @Before fun setUp() = runBlocking {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("firestoreEmulator") == "true")
        val name = "ai-session-${UUID.randomUUID()}"
        val value = FirebaseApp.initializeApp(instrumentation.targetContext, FirebaseOptions.Builder()
            .setProjectId("demo-kairos-transactions").setApplicationId("1:123456789:android:aisession")
            .setApiKey("emulator-only-not-a-real-key").build(), name)
        app = value
        auth = FirebaseAuth.getInstance(value); auth.useEmulator("10.0.2.2", 9099)
        uid = auth.signInAnonymously().await().user!!.uid
        db = FirebaseFirestore.getInstance(value)
        db.firestoreSettings = FirebaseFirestoreSettings.Builder().setPersistenceEnabled(false).build()
        db.useEmulator("10.0.2.2", 8080)
        directory = File(instrumentation.targetContext.noBackupFilesDir, name)
        repository = FirebaseRepository(db, { auth.currentUser?.uid }, PendingEditStore(directory))
        seed(uid)
        instrumentation.runOnMainSync {
            vm = ViewModelProvider(store, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = AiHelperViewModel(
                    instrumentation.targetContext.applicationContext as Application, provider, repository, AuthRepository(auth)) as T
            })[AiHelperViewModel::class.java]
        }
        eventually { vm.uiState.workspace?.chats?.size == 2 && !vm.uiState.loading }
    }
    @After fun tearDown() = runBlocking {
        if (app != null) {
            instrumentation.runOnMainSync { store.clear() }
            if (::auth.isInitialized) auth.signOut()
            if (::db.isInitialized) { db.terminate().await(); db.clearPersistence().await() }
            app?.delete()
            if (::directory.isInitialized) directory.deleteRecursively()
        }
    }
    private suspend fun seed(owner: String) {
        db.collection("users").document(owner).set(mapOf("aiWorkspace" to mapOf("version" to 2, "activeChatId" to "a", "projects" to emptyList<Any>(),
            "chats" to listOf(mapOf("id" to "a", "title" to "First chat", "messages" to emptyList<Any>()),
                mapOf("id" to "b", "title" to "Second chat", "messages" to emptyList<Any>()))))).await()
        db.collection("study_plans").document("plan-$owner").set(mapOf("userID" to owner, "createdAt" to Timestamp.now(), "boards" to emptyList<Any>(), "scheduleEvents" to emptyList<Any>())).await()
    }
    private suspend fun eventually(condition: () -> Boolean) = withTimeout(20_000) {
        while (true) {
            var ready = false
            instrumentation.runOnMainSync { ready = condition() }
            if (ready) break
            delay(25)
        }
    }
    private suspend fun next() = withTimeout(20_000) { provider.calls.receive() }
    private fun send(chat: String, text: String) = instrumentation.runOnMainSync { vm.selectChat(chat); vm.setDraft(text); vm.handleSend() }
    private fun reply(text: String) = "{\"reply\":\"$text\",\"actions\":[]}"

    @Test fun switchingChatsKeepsBothRepliesAndIndependentDrafts() = runBlocking {
        send("a", "First question"); val first = next()
        send("b", "Second question"); val second = next()
        instrumentation.runOnMainSync { assertEquals(setOf("a", "b"), vm.uiState.pendingChatIds); vm.setDraft("Next draft") }
        second.reply.complete(reply("Second answer"))
        eventually { vm.uiState.workspace!!.chats.first { it.id == "b" }.messages.lastOrNull()?.content == "Second answer" }
        first.reply.complete(reply("First answer"))
        eventually { vm.uiState.pendingChatIds.isEmpty() }
        instrumentation.runOnMainSync {
            assertEquals("b", vm.uiState.activeChatId); assertEquals("Next draft", vm.userInput)
            assertEquals("First answer", vm.uiState.workspace!!.chats.first { it.id == "a" }.messages.last().content)
        }
    }
    @Test fun typingDuringUserMessageSaveSurvivesAcknowledgement() = runBlocking {
        instrumentation.runOnMainSync { vm.setDraft("Send this"); vm.handleSend(); vm.setDraft("Keep typing this") }
        val call = next()
        instrumentation.runOnMainSync { assertEquals("Keep typing this", vm.userInput) }
        call.reply.complete(reply("Answer")); eventually { vm.uiState.pendingChatIds.isEmpty() }
        instrumentation.runOnMainSync { assertEquals("Keep typing this", vm.userInput) }
    }
    @Test fun accountSwitchClearsDraftsAndRefusesLateReplyPersistence() = runBlocking {
        send("a", "Old account question"); val call = next()
        instrumentation.runOnMainSync { vm.setDraft("Private draft") }
        auth.signOut()
        val nextUid = auth.signInAnonymously().await().user!!.uid
        seed(nextUid)
        eventually { vm.ownerId == nextUid && vm.uiState.workspace != null && !vm.uiState.loading }
        call.reply.complete(reply("Old account answer"))
        instrumentation.runOnMainSync {
            assertEquals("", vm.userInput); assertTrue(vm.uiState.pendingChatIds.isEmpty()); assertNull(vm.uiState.proposal)
        }
        val old = db.collection("users").document(uid).get().await().data.orEmpty()
        assertTrue(AiWorkspace.read(old, uid).chats.first { it.id == "a" }.messages.none { it.role == "assistant" })
    }
    @Test fun discardedFailedReplyCannotBeRecreatedByStaleRetry() = runBlocking {
        send("a", "Question"); val call = next()
        db.disableNetwork().await()
        call.reply.complete(reply("Retained answer"))
        eventually { vm.uiState.retainedChanges && vm.uiState.pendingChatIds.isEmpty() }
        db.enableNetwork().await()
        val pending = repository.observePendingEdits(uid).firstAi()
        repository.discardPendingEdit(uid, pending.id)
        eventually { !vm.uiState.retainedChanges }
        instrumentation.runOnMainSync { vm.retryReply() }
        eventually { vm.uiState.pendingChatIds.isEmpty() && !vm.uiState.saving }
        assertEquals(1, provider.count)
        val saved = repository.getAiUserOnce(uid)
        assertTrue(AiWorkspace.read(saved, uid).chats.first { it.id == "a" }.messages.none { it.role == "assistant" })
    }
    private suspend fun kotlinx.coroutines.flow.Flow<List<PendingEdit>>.firstAi(): PendingEdit =
        first { list -> list.any { it.kind == "ai" } }.first { it.kind == "ai" }
}

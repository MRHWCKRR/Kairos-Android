package com.kairos.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.Source
import com.kairos.app.data.models.KairosPlan
import com.kairos.app.data.models.KairosTask
import com.kairos.app.data.models.ChatMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** Only runs against the explicitly opted-in local demo emulator, never a configured app backend. */
@RunWith(AndroidJUnit4::class)
class FirebaseRepositoryTransactionTest {
    private var app: FirebaseApp? = null
    private lateinit var db: FirebaseFirestore
    private lateinit var repository: FirebaseRepository
    private lateinit var uid: String
    private lateinit var planId: String
    private var owner: String? = null

    @Before
    fun setUp() = runBlocking {
        assumeTrue(
            "Requires tools/verify-firestore.sh and the local Firestore emulator",
            InstrumentationRegistry.getArguments().getString("firestoreEmulator") == "true"
        )
        uid = "transaction-${UUID.randomUUID()}"
        planId = "plan-$uid"
        owner = uid
        val testApp = FirebaseApp.initializeApp(
            InstrumentationRegistry.getInstrumentation().targetContext,
            FirebaseOptions.Builder()
                .setProjectId("demo-kairos-transactions")
                .setApplicationId("1:123456789:android:transactiontests")
                .setApiKey("emulator-only-not-a-real-api-key")
                .build(),
            uid
        )
        app = testApp
        db = FirebaseFirestore.getInstance(testApp)
        db.firestoreSettings = FirebaseFirestoreSettings.Builder().setPersistenceEnabled(false).build()
        db.useEmulator("10.0.2.2", 8080)
        repository = FirebaseRepository(db, { owner })
        bounded {
            db.collection("study_plans").document(planId).set(richPlan()).await()
            db.collection("users").document(uid).set(richProfile()).await()
        }
    }

    @After
    fun tearDown() = runBlocking {
        // A skipped test has no Firebase app. Never touch the default Firebase instance.
        if (app != null) {
            try {
                bounded { db.terminate().await(); db.clearPersistence().await() }
            } finally {
                app?.delete()
                app = null
            }
        }
    }

    @Test
    fun renameTaskPreservesUnknownFieldsAtEveryLevel() = runBlocking {
        bounded {
            val before = loadPlan()
            repository.updatePlan(uid, before, renameTask(before, "Renamed"))
            val expected = richPlan().toMutableMap()
            val board = (expected["boards"] as List<*>).single() as Map<*, *>
            val section = (board["sections"] as List<*>).single() as Map<*, *>
            val task = (section["tasks"] as List<*>).single() as Map<*, *>
            expected["boards"] = listOf(board + ("sections" to listOf(section + ("tasks" to listOf(task + ("title" to "Renamed"))))))
            assertEquals(expected, planData())
        }
    }

    @Test
    fun independentEditsFromTwoLoadedSnapshotsMerge() = runBlocking {
        bounded {
            val first = loadPlan()
            val second = loadPlan()
            repository.updatePlan(uid, first, renameTask(first, "First editor"))
            val board = second.boards.single()
            repository.updatePlan(uid, second, second.copy(boards = listOf(board.copy(title = "Second editor"))))
            val saved = loadPlan()
            assertEquals("Second editor", saved.boards.single().title)
            assertEquals("First editor", saved.boards.single().sections.single().tasks.single().title)
            assertEquals("ios-plan", planData()["iosOnly"])
        }
    }

    @Test
    fun competingEditsToSameFieldRejectWithoutOverwritingWinner() = runBlocking {
        bounded {
            val first = loadPlan()
            val second = loadPlan()
            repository.updatePlan(uid, first, renameTask(first, "Winner"))
            val winner = planData()
            expectRejected { repository.updatePlan(uid, second, renameTask(second, "Loser")) }
            assertEquals(winner, planData())
        }
    }

    @Test
    fun saveTargetsLoadedDocumentEvenWhenNewerPlanAppears() = runBlocking {
        bounded {
            val before = loadPlan()
            val newer = db.collection("study_plans").document("newer-$uid")
            val newerData = richPlan() + ("createdAt" to Timestamp(2000, 0))
            newer.set(newerData).await()
            repository.updatePlan(uid, before, renameTask(before, "Original edited"))
            assertEquals(newerData, newer.get(Source.SERVER).await().data)
            assertEquals("Original edited", taskData(planData())["title"])
        }
    }

    @Test
    fun newPlanUsesDeterministicOwnerDocument() = runBlocking {
        bounded {
            val template = loadPlan()
            val newUid = "new-$uid"
            owner = newUid
            assertNull(repository.getPlanOnce(newUid))
            val before = KairosPlan(userID = newUid)
            repository.updatePlan(newUid, before, before.copy(boards = template.boards))
            val created = db.collection("study_plans").document(newUid).get(Source.SERVER).await()
            assertTrue(created.exists())
            assertEquals(newUid, created.getString("userID"))
            assertNotNull(created.getTimestamp("createdAt"))
            assertEquals(newUid, repository.getPlanOnce(newUid)!!.documentId)
            assertFalse(created.contains("documentId"))
            assertFalse(created.contains("sourceFields"))
        }
    }

    @Test
    fun deletedPlanIsNotRecreated() = runBlocking {
        bounded {
            val before = loadPlan()
            val ref = db.collection("study_plans").document(planId)
            ref.delete().await()
            expectRejected { repository.updatePlan(uid, before, renameTask(before, "Resurrected")) }
            assertFalse(ref.get(Source.SERVER).await().exists())
        }
    }

    @Test
    fun profileEditsMergeAndPreserveUnmodeledData() = runBlocking {
        bounded {
            val first = repository.getUserProfile(uid).first()!!
            val second = repository.getUserProfile(uid).first()!!
            repository.updateUserProfile(uid, first, mapOf("settings.profile.displayName" to "Edited name"))
            repository.updateUserProfile(uid, second, mapOf("settings.appearance.theme" to "poseidon"))
            val saved = db.collection("users").document(uid).get(Source.SERVER).await()
            assertEquals("Edited name", saved.get("settings.profile.displayName"))
            assertEquals("poseidon", saved.get("settings.appearance.theme"))
            assertEquals("premium", saved.get("settings.profile.iosBadge"))
            assertEquals("violet", saved.get("settings.appearance.iosAccent"))
            assertEquals("ios-user", saved.get("iosOnly"))
            assertEquals(mapOf("streak" to 42L), saved.get("futureStats"))
            val settings = richProfile()["settings"] as Map<*, *>
            val expectedSettings = settings + mapOf(
                "profile" to ((settings["profile"] as Map<*, *>) + ("displayName" to "Edited name")),
                "appearance" to ((settings["appearance"] as Map<*, *>) + ("theme" to "poseidon"))
            )
            assertEquals(richProfile() + ("settings" to expectedSettings), saved.data)
            val beforeConflict = saved.data
            expectRejected {
                repository.updateUserProfile(uid, second, mapOf("settings.profile.displayName" to "Stale name"))
            }
            assertEquals(beforeConflict, db.collection("users").document(uid).get(Source.SERVER).await().data)
        }
    }

    @Test
    fun deletedProfileIsNotRecreated() = runBlocking {
        bounded {
            val before = repository.getUserProfile(uid).first()!!
            val ref = db.collection("users").document(uid)
            ref.delete().await()
            expectRejected { repository.updateUserProfile(uid, before, mapOf("onboardingCompleted" to true)) }
            assertFalse(ref.get(Source.SERVER).await().exists())
        }
    }

    @Test
    fun deletedPreviouslyEmptyProfileIsNotRecreated() = runBlocking {
        bounded {
            val ref = db.collection("users").document(uid)
            ref.set(emptyMap<String, Any>()).await()
            val before = repository.getUserProfile(uid).first()!!
            assertTrue(before.documentExists)
            assertTrue(before.sourceFields.isEmpty())
            ref.delete().await()
            expectRejected { repository.updateUserProfile(uid, before, mapOf("onboardingCompleted" to true)) }
            assertFalse(ref.get(Source.SERVER).await().exists())
        }
    }

    @Test
    fun chatAppendPreservesUnknownMessageFieldsAndClearSucceeds() = runBlocking {
        bounded {
            val before = repository.getUserProfile(uid).first()!!
            val message = ChatMessage(role = "assistant", content = "Appended answer", timestamp = 2L)
            repository.updateUserProfile(uid, before, mapOf("aiChatHistory" to before.aiChatHistory + message))
            val ref = db.collection("users").document(uid)
            val oldHistory = richProfile()["aiChatHistory"] as List<*>
            val expected = richProfile() + ("aiChatHistory" to (oldHistory + mapOf(
                "role" to message.role, "content" to message.content, "timestamp" to message.timestamp
            )))
            assertEquals(expected, ref.get(Source.SERVER).await().data)
            val afterAppend = repository.getUserProfile(uid).first()!!
            repository.updateUserProfile(uid, afterAppend, mapOf("aiChatHistory" to emptyList<ChatMessage>()))
            assertEquals(richProfile() + ("aiChatHistory" to emptyList<Any>()), ref.get(Source.SERVER).await().data)
        }
    }

    @Test
    fun staleChatAppendAndClearRejectConcurrentRemoteChanges() = runBlocking {
        bounded {
            val before = repository.getUserProfile(uid).first()!!
            val ref = db.collection("users").document(uid)
            val oldHistory = richProfile()["aiChatHistory"] as List<*>
            val originalMessage = oldHistory.single() as Map<*, *>
            // A second client changes a captured message and adds its own message.
            val remoteHistory = listOf(
                originalMessage + ("content" to "Edited remotely"),
                mapOf("role" to "assistant", "content" to "Remote answer", "timestamp" to 3L, "iosModel" to "remote-model")
            )
            ref.update("aiChatHistory", remoteHistory).await()
            val remote = richProfile() + ("aiChatHistory" to remoteHistory)
            val localMessage = ChatMessage(role = "user", content = "Stale append", timestamp = 4L)
            expectRejected {
                repository.updateUserProfile(uid, before, mapOf("aiChatHistory" to before.aiChatHistory + localMessage))
            }
            assertEquals(remote, ref.get(Source.SERVER).await().data)
            expectRejected {
                repository.updateUserProfile(uid, before, mapOf("aiChatHistory" to emptyList<ChatMessage>()))
            }
            assertEquals(remote, ref.get(Source.SERVER).await().data)
        }
    }

    @Test
    fun changedAccountRejectsBothPlanAndProfileWrites() = runBlocking {
        bounded {
            val plan = loadPlan()
            val profile = repository.getUserProfile(uid).first()!!
            val oldPlan = planData()
            val oldProfile = db.collection("users").document(uid).get(Source.SERVER).await().data
            owner = "different-account"
            expectRejected { repository.updatePlan(uid, plan, renameTask(plan, "Wrong account")) }
            expectRejected { repository.updateUserProfile(uid, profile, mapOf("onboardingCompleted" to true)) }
            assertEquals(oldPlan, planData())
            assertEquals(oldProfile, db.collection("users").document(uid).get(Source.SERVER).await().data)
        }
    }

    @Test
    fun offlineFailureThenRetrySameCapturedEditDoesNotDuplicateTask() = runBlocking {
        // The same captured objects are used for every attempt, as in the UI retry action.
        val before = boundedPlan()
        val board = before.boards.single()
        val section = board.sections.single()
        val added = KairosTask(id = "offline-created", title = "Captured before disconnect")
        val after = before.copy(boards = listOf(board.copy(sections = listOf(section.copy(
            tasks = section.tasks + added
        )))))
        bounded { db.disableNetwork().await() }
        try {
            var failure: FirebaseFirestoreException? = null
            try {
                withTimeout(25_000) { repository.updatePlan(uid, before, after) }
            } catch (error: FirebaseFirestoreException) {
                failure = error
            }
            assertNotNull("An offline transaction must fail instead of queueing a write", failure)
            assertEquals(FirebaseFirestoreException.Code.UNAVAILABLE, failure!!.code)
        } finally {
            bounded { db.enableNetwork().await() }
        }
        bounded {
            assertEquals("The failed transaction must leave the server unchanged", richPlan(), planData())
            repository.updatePlan(uid, before, after)
            val original = richPlan()
            val rawBoard = (original["boards"] as List<*>).single() as Map<*, *>
            val rawSection = (rawBoard["sections"] as List<*>).single() as Map<*, *>
            val expectedTasks = (rawSection["tasks"] as List<*>) + mapOf(
                "id" to added.id, "title" to added.title, "completed" to false,
                "date" to null, "archived" to false
            )
            val expected = original + ("boards" to listOf(rawBoard + ("sections" to listOf(
                rawSection + ("tasks" to expectedTasks)
            ))))
            assertEquals(expected, planData())
            repository.updatePlan(uid, before, after)
            assertEquals("Retrying the same captured insertion must be idempotent", expected, planData())
        }
    }

    @Test
    fun accountChangeAfterInitialCheckRejectsInsideTransaction() = runBlocking {
        bounded {
            val before = loadPlan()
            val reads = AtomicInteger()
            val switchingRepository = FirebaseRepository(db, {
                if (reads.incrementAndGet() == 1) uid else "account-changed-in-transaction"
            })
            expectRejected { switchingRepository.updatePlan(uid, before, renameTask(before, "Wrong account")) }
            assertTrue("Owner must be checked again after method entry", reads.get() >= 2)
            assertEquals(richPlan(), planData())

            val profile = repository.getUserProfile(uid).first()!!
            reads.set(0)
            expectRejected {
                switchingRepository.updateUserProfile(uid, profile, mapOf("onboardingCompleted" to true))
            }
            assertTrue("Profile transaction must recheck the owner", reads.get() >= 2)
            assertEquals(richProfile(), db.collection("users").document(uid).get(Source.SERVER).await().data)
        }
    }

    private suspend fun boundedPlan(): KairosPlan = withTimeout(30_000) { loadPlan() }

    private suspend fun loadPlan(): KairosPlan = repository.getPlanOnce(uid)!!

    private suspend fun planData(): Map<String, Any> =
        db.collection("study_plans").document(planId).get(Source.SERVER).await().data!!

    private fun renameTask(plan: KairosPlan, title: String): KairosPlan {
        val board = plan.boards.single()
        val section = board.sections.single()
        return plan.copy(boards = listOf(board.copy(sections = listOf(section.copy(
            tasks = listOf(section.tasks.single().copy(title = title))
        )))))
    }

    private fun taskData(plan: Map<String, Any>): Map<*, *> {
        val board = (plan["boards"] as List<*>).single() as Map<*, *>
        val section = (board["sections"] as List<*>).single() as Map<*, *>
        return (section["tasks"] as List<*>).single() as Map<*, *>
    }

    private suspend fun expectRejected(action: suspend () -> Unit) {
        var rejection: Exception? = null
        try { action() } catch (error: Exception) { rejection = error }
        assertNotNull("Expected a stale edit or ownership rejection", rejection)
        // Connectivity failures must fail the test instead of masquerading as conflict handling.
        var cause: Throwable? = rejection
        var deliberateRejection = false
        while (cause != null) {
            if (cause is IllegalStateException || cause is SecurityException || cause is IllegalArgumentException) {
                deliberateRejection = true
            }
            cause = cause.cause
        }
        assertTrue("Expected application rejection, got $rejection", deliberateRejection)
    }

    private suspend fun bounded(action: suspend () -> Unit) = withTimeout(30_000) { action() }

    private fun richPlan(): Map<String, Any> = mapOf(
        "userID" to uid,
        "createdAt" to Timestamp(1000, 0),
        "iosOnly" to "ios-plan",
        "calendarSync" to mapOf("provider" to "apple", "calendarId" to "private-calendar", "revision" to 8L),
        "aiMetadata" to mapOf("model" to "ios-model", "generationId" to "generation", "tokens" to 123L),
        "dayInsights" to mapOf("monday" to "Existing insight"),
        "scheduleEvents" to listOf(mapOf("id" to "event", "title" to "Class", "iosColor" to "blue")),
        "boards" to listOf(mapOf(
            "id" to "board", "title" to "Board", "iosColor" to "purple",
            "sections" to listOf(mapOf(
                "id" to "section", "title" to "Section", "iosLayout" to "grid",
                "tasks" to listOf(mapOf(
                    "id" to "task", "title" to "Original", "completed" to false,
                    "iosPriority" to 3L,
                    "notes" to "Retain the complete iOS notes",
                    "checklist" to listOf(mapOf("id" to "check", "title" to "Review", "completed" to true)),
                    "recurrence" to mapOf("enabled" to false, "rule" to "weekly", "occurrenceIds" to listOf("occurrence")),
                    "timing" to mapOf("estimateMinutes" to 45L, "startedAt" to Timestamp(900, 0), "elapsedSeconds" to 70L),
                    "subtasks" to listOf(mapOf("id" to "subtask", "done" to true))
                ))
            ))
        ))
    )

    private fun richProfile(): Map<String, Any> = mapOf(
        "iosOnly" to "ios-user",
        "futureStats" to mapOf("streak" to 42L),
        "achievements" to mapOf("lifetimeTasksCompleted" to 17L, "iosLevel" to "gold"),
        "focusData" to mapOf("totalSeconds" to 3600L, "iosSessions" to listOf("session")),
        "notifications" to listOf(mapOf("id" to "notice", "title" to "Notice", "iosRoute" to "/focus")),
        "aiChatHistory" to listOf(mapOf("role" to "user", "content" to "Hello", "timestamp" to 1L, "iosModel" to "custom")),
        "settings" to mapOf(
            "profile" to mapOf("displayName" to "Original name", "iosBadge" to "premium"),
            "appearance" to mapOf("theme" to "default", "iosAccent" to "violet")
        )
    )
}

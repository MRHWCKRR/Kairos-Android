package com.kairos.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
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
import java.io.File
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
    private lateinit var journalDirectory: File
    private lateinit var pendingStore: PendingEditStore

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
        journalDirectory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.noBackupFilesDir,
            "transaction-test-$uid"
        )
        restartRepository()
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
                if (::journalDirectory.isInitialized) journalDirectory.deleteRecursively()
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
    fun staleFullSettingsDraftRejectsRemotelyDeletedProfileField() = runBlocking {
        bounded {
            val original = repository.getUserProfile(uid).first()!!
            val ref = db.collection("users").document(uid)
            ref.update("settings.profile.displayName", FieldValue.delete()).await()
            val deletedSnapshot = ref.get(Source.SERVER).await()
            val remote = deletedSnapshot.data
            assertFalse(deletedSnapshot.contains("settings.profile.displayName"))
            val editedSettings = original.settings.copy(
                profile = original.settings.profile.copy(displayName = "Stale draft name")
            )
            expectRejected {
                repository.updateUserProfile(uid, original, mapOf("settings" to editedSettings))
            }
            assertEquals(remote, ref.get(Source.SERVER).await().data)
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
        val operationId = "offline-${UUID.randomUUID()}"
        bounded { db.disableNetwork().await() }
        try {
            var failure: FirebaseFirestoreException? = null
            try {
                withTimeout(25_000) { repository.updatePlan(uid, before, after, operationId) }
            } catch (error: FirebaseFirestoreException) {
                failure = error
            }
            assertNotNull("An offline transaction must fail instead of queueing a write", failure)
            assertEquals(FirebaseFirestoreException.Code.UNAVAILABLE, failure!!.code)
            restartRepository()
            val recovered = repository.observePendingEdits(uid).first().single()
            assertEquals(operationId, recovered.id)
            assertEquals(planId, recovered.documentId)
            assertEquals(before.sourceFields, recovered.rawBaseline)
            assertEquals(before.sharedFields(), recovered.base)
            assertEquals(after.sharedFields(), recovered.edited)
        } finally {
            bounded { db.enableNetwork().await() }
        }
        bounded {
            assertEquals("The failed transaction must leave the server unchanged", richPlan(), planData())
            repository.retryPendingEdit(uid, operationId)
            assertTrue(repository.observePendingEdits(uid).first().isEmpty())
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
            repository.retryPendingEdit(uid, operationId)
            assertEquals("Retrying the same captured insertion must be idempotent", expected, planData())
        }
    }

    @Test
    fun accountChangeAfterInitialCheckRejectsInsideTransaction() = runBlocking {
        bounded {
            val before = loadPlan()
            val reads = AtomicInteger()
            val switchingRepository = FirebaseRepository(db, {
                if (reads.incrementAndGet() <= 7) uid else "account-changed-in-transaction"
            }, pendingStore)
            expectRejected { switchingRepository.updatePlan(uid, before, renameTask(before, "Wrong account")) }
            assertTrue("Owner must be checked before the transaction commits", reads.get() >= 8)
            assertEquals(richPlan(), planData())

            val profile = repository.getUserProfile(uid).first()!!
            reads.set(0)
            expectRejected {
                switchingRepository.updateUserProfile(uid, profile, mapOf("onboardingCompleted" to true))
            }
            assertTrue("Profile transaction must recheck the owner before writing", reads.get() >= 8)
            assertEquals(richProfile(), db.collection("users").document(uid).get(Source.SERVER).await().data)
        }
    }

    @Test
    fun interruptedPlanAcknowledgementRetriesWithoutDuplicatingTask() = runBlocking {
        bounded {
            val before = loadPlan()
            val board = before.boards.single()
            val section = board.sections.single()
            val added = KairosTask(id = "interrupted-task", title = "Added once")
            val after = before.copy(boards = listOf(board.copy(sections = listOf(
                section.copy(tasks = section.tasks + added)
            ))))
            val original = planEdit(before, after)
            repository.updatePlan(uid, before, after, original.id)
            val acknowledged = planData()
            // Simulate process death after the server committed but before local acknowledgement.
            pendingStore.put(original)
            restartRepository()
            repository.retryPendingEdit(uid, original.id)
            assertEquals(acknowledged, planData())
            assertTrue(repository.observePendingEdits(uid).first().isEmpty())
            repository.retryPendingEdit(uid, original.id)
            assertEquals(acknowledged, planData())
        }
    }

    @Test
    fun restartedPlanConflictPreservesOriginalIntentAndRemoteDocument() = runBlocking {
        bounded {
            val before = loadPlan()
            val original = planEdit(before, renameTask(before, "Pending title"))
            pendingStore.put(original)
            repository.updatePlan(uid, before, renameTask(before, "Remote winner"))
            val winner = planData()
            restartRepository()
            expectRejected { repository.retryPendingEdit(uid, original.id) }
            assertEquals(winner, planData())
            assertEquals(original, repository.observePendingEdits(uid).first().single())
            restartRepository()
            assertEquals(original, repository.observePendingEdits(uid).first().single())
        }
    }

    @Test
    fun restartedProfileRetryPreservesUnknownFieldsAndAcknowledgesOnce() = runBlocking {
        bounded {
            val before = repository.getUserProfile(uid).first()!!
            val original = PendingEdit(
                userId = uid, kind = "profile", documentId = uid,
                documentExisted = before.documentExists, base = before.sharedFields(),
                edited = profilePatch(before, mapOf("settings.profile.displayName" to "Recovered name")),
                rawBaseline = before.sourceFields
            )
            pendingStore.put(original)
            restartRepository()
            assertEquals(original, repository.observePendingEdits(uid).first().single())
            repository.retryPendingEdit(uid, original.id)
            val ref = db.collection("users").document(uid)
            val saved = ref.get(Source.SERVER).await().data!!
            assertEquals("Recovered name", ref.get(Source.SERVER).await().get("settings.profile.displayName"))
            assertEquals("ios-user", saved["iosOnly"])
            assertEquals(mapOf("streak" to 42L), saved["futureStats"])
            assertTrue(repository.observePendingEdits(uid).first().isEmpty())
            pendingStore.put(original)
            restartRepository()
            repository.retryPendingEdit(uid, original.id)
            assertEquals(saved, ref.get(Source.SERVER).await().data)
            assertTrue(repository.observePendingEdits(uid).first().isEmpty())
        }
    }

    @Test
    fun restartedProfileConflictPreservesOriginalIntentAndRemoteDocument() = runBlocking {
        bounded {
            val before = repository.getUserProfile(uid).first()!!
            val original = PendingEdit(
                userId = uid, kind = "profile", documentId = uid,
                documentExisted = before.documentExists, base = before.sharedFields(),
                edited = profilePatch(before, mapOf("settings.profile.displayName" to "Pending name")),
                rawBaseline = before.sourceFields
            )
            pendingStore.put(original)
            val ref = db.collection("users").document(uid)
            ref.update("settings.profile.displayName", "Remote name").await()
            val winner = ref.get(Source.SERVER).await().data
            restartRepository()
            expectRejected { repository.retryPendingEdit(uid, original.id) }
            assertEquals(winner, ref.get(Source.SERVER).await().data)
            assertEquals(original, repository.observePendingEdits(uid).first().single())
        }
    }

    @Test
    fun restartedPendingEditRemainsBoundToOriginalPlanWhenNewerPlanAppears() = runBlocking {
        bounded {
            val before = loadPlan()
            val original = planEdit(before, renameTask(before, "Recovered original"))
            pendingStore.put(original)
            val newer = db.collection("study_plans").document("newer-$uid")
            val newerData = richPlan() + ("createdAt" to Timestamp(2000, 0))
            newer.set(newerData).await()
            restartRepository()
            repository.retryPendingEdit(uid, original.id)
            assertEquals("Recovered original", taskData(planData())["title"])
            assertEquals(newerData, newer.get(Source.SERVER).await().data)
            assertTrue(repository.observePendingEdits(uid).first().isEmpty())
        }
    }

    @Test
    fun restartedPendingEditDoesNotRecreateDeletedPlanOrProfile() = runBlocking {
        bounded {
            val before = loadPlan()
            val plan = planEdit(before, renameTask(before, "Must stay deleted"))
            val profile = repository.getUserProfile(uid).first()!!
            val profileEdit = PendingEdit(
                userId = uid, kind = "profile", documentId = uid,
                documentExisted = profile.documentExists, base = profile.sharedFields(),
                edited = profilePatch(profile, mapOf("onboardingCompleted" to true)),
                rawBaseline = profile.sourceFields
            )
            pendingStore.put(plan)
            pendingStore.put(profileEdit)
            val planRef = db.collection("study_plans").document(planId)
            val profileRef = db.collection("users").document(uid)
            planRef.delete().await()
            profileRef.delete().await()
            restartRepository()
            expectRejected { repository.retryPendingEdit(uid, plan.id) }
            expectRejected { repository.retryPendingEdit(uid, profileEdit.id) }
            assertFalse(planRef.get(Source.SERVER).await().exists())
            assertFalse(profileRef.get(Source.SERVER).await().exists())
            assertEquals(setOf(plan, profileEdit), repository.observePendingEdits(uid).first().toSet())
        }
    }

    @Test
    fun changedAccountCannotReadRetryOrDiscardOriginalAccountsPendingEdit() = runBlocking {
        bounded {
            val before = loadPlan()
            val original = planEdit(before, renameTask(before, "Account A draft"))
            pendingStore.put(original)
            owner = "different-$uid"
            restartRepository()
            expectRejected { repository.observePendingEdits(uid).first() }
            expectRejected { repository.retryPendingEdit(uid, original.id) }
            expectRejected { repository.discardPendingEdit(uid, original.id) }
            expectRejected { repository.discardPendingEdits(uid) }
            assertTrue(repository.observePendingEdits(owner!!).first().isEmpty())
            repository.retryPendingEdit(owner!!, original.id)
            repository.discardPendingEdit(owner!!, original.id)
            repository.discardPendingEdits(owner!!)
            assertEquals(richPlan(), planData())
            owner = uid
            restartRepository()
            assertEquals(original, repository.observePendingEdits(uid).first().single())
            repository.discardPendingEdit(uid, original.id)
            restartRepository()
            assertTrue(repository.observePendingEdits(uid).first().isEmpty())
            assertEquals(richPlan(), planData())
        }
    }

    @Test
    fun failedDurableStagingPreventsPlanAndProfileServerWrites() = runBlocking {
        bounded {
            val before = loadPlan()
            val profile = repository.getUserProfile(uid).first()!!
            repository = FirebaseRepository(db, { owner }, PendingEditStore(journalDirectory, maxRecordBytes = 1))
            expectRejected { repository.updatePlan(uid, before, renameTask(before, "Must not be sent")) }
            expectRejected { repository.updateUserProfile(uid, profile, mapOf("onboardingCompleted" to true)) }
            assertEquals(richPlan(), planData())
            assertEquals(richProfile(), db.collection("users").document(uid).get(Source.SERVER).await().data)
            restartRepository()
            assertTrue(repository.observePendingEdits(uid).first().isEmpty())
        }
    }

    private fun aiCommand(command: com.kairos.app.data.repository.AiWorkspace.Command) = mapOf<String, Any?>(
        "operation" to "workspace", "createdAt" to System.currentTimeMillis(),
        "command" to com.kairos.app.data.repository.AiWorkspace.encodeCommand(command))

    @Test fun aiWorkspaceFreshCommandsPreserveOtherChatsAndMetadata() = runBlocking {
        bounded {
            val reference = db.collection("users").document(uid)
            reference.update("aiWorkspace", mapOf("version" to 2, "webMetadata" to "keep", "projects" to emptyList<Any>(),
                "chats" to listOf(mapOf("id" to "first", "messages" to emptyList<Any>(), "summary" to "Memory"),
                    mapOf("id" to "second", "messages" to emptyList<Any>())))).await()
            val append = aiCommand(com.kairos.app.data.repository.AiWorkspace.Command.Append("first", listOf(
                com.kairos.app.data.repository.AiWorkspace.Message("one", "user", "First message", 1L))))
            repository.saveAiOperation(uid, append, "first-append")
            repository.saveAiOperation(uid, aiCommand(com.kairos.app.data.repository.AiWorkspace.Command.Append("second", listOf(
                com.kairos.app.data.repository.AiWorkspace.Message("two", "user", "Other message", 2L)))), "second-append")
            repository.saveAiOperation(uid, append, "first-append") // lost acknowledgement replay
            val user = repository.getAiUserOnce(uid)
            val workspace = com.kairos.app.data.repository.AiWorkspace.read(user, uid)
            assertEquals(1, workspace.chats.first { it.id == "first" }.messages.size)
            assertEquals("Other message", workspace.chats.first { it.id == "second" }.messages.single().content)
            assertEquals("Memory", workspace.chats.first { it.id == "first" }.summary)
            assertEquals("keep", (user["aiWorkspace"] as Map<*, *>)["webMetadata"])
            assertEquals(richProfile()["settings"], user["settings"])
            assertTrue(repository.observePendingEdits(uid).first().isEmpty())
        }
    }

    @Test fun aiWorkspaceOfflineJournalRestartsAndDiscardDoesNotResurrect() = runBlocking {
        bounded {
            db.disableNetwork().await()
            val command = aiCommand(com.kairos.app.data.repository.AiWorkspace.Command.RenameChat("legacy-$uid", "Recovered chat"))
            try { repository.saveAiOperation(uid, command, "offline-ai"); fail("Offline save must remain retained") }
            catch (_: FirebaseFirestoreException) { }
            assertEquals("ai", pendingStore.list(uid).single().kind)
            db.enableNetwork().await()
            restartRepository()
            repository.retryPendingEdit(uid, "offline-ai")
            assertEquals("Recovered chat", com.kairos.app.data.repository.AiWorkspace.read(repository.getAiUserOnce(uid), uid).chats.single().title)
            val discarded = PendingEdit("discarded-ai", uid, "ai", uid, true, emptyMap(),
                aiCommand(com.kairos.app.data.repository.AiWorkspace.Command.DeleteChat("legacy-$uid")), emptyMap())
            pendingStore.put(discarded)
            repository.discardPendingEdit(uid, discarded.id)
            repository.retryPendingEdit(uid, discarded.id)
            assertEquals(1, com.kairos.app.data.repository.AiWorkspace.read(repository.getAiUserOnce(uid), uid).chats.size)
        }
    }

    @Test fun aiPreviewAppliesAtomicallyOnceAndRetiresSavedReview() = runBlocking {
        bounded {
            val source = richPlan() + ("scheduleEvents" to emptyList<Any>())
            db.collection("study_plans").document(planId).set(source).await()
            val proposal = com.kairos.app.data.repository.AiActions.make(listOf(
                com.kairos.app.data.repository.AiActions.Action("complete_task", mapOf("taskId" to "task"))),
                source, uid, "legacy-$uid", planId, "UTC", "Complete Original")
            val reply = mapOf<String, Any?>("operation" to "reply", "createdAt" to System.currentTimeMillis(),
                "proposal" to com.kairos.app.data.repository.AiActions.toMap(proposal),
                "command" to com.kairos.app.data.repository.AiWorkspace.encodeCommand(com.kairos.app.data.repository.AiWorkspace.Command.Append("legacy-$uid",
                    listOf(com.kairos.app.data.repository.AiWorkspace.Message("saved-reply", "assistant", "Review this change.")))))
            repository.saveAiOperation(uid, reply, "reply-op")
            assertEquals(false, taskData(planData())["completed"])
            val apply = mapOf<String, Any?>("operation" to "apply", "createdAt" to System.currentTimeMillis(), "planId" to planId,
                "proposal" to com.kairos.app.data.repository.AiActions.toMap(proposal))
            repository.saveAiOperation(uid, apply, "apply-op")
            repository.saveAiOperation(uid, apply, "apply-op")
            val changed = planData()
            assertEquals(true, taskData(changed)["completed"])
            assertEquals("ios-plan", changed["iosOnly"])
            assertEquals("Retain the complete iOS notes", taskData(changed)["notes"])
            val profile = repository.getAiUserOnce(uid)
            assertTrue((profile["aiNativeProposals"] as Map<*, *>).isEmpty())
            assertEquals(18L, (profile["achievements"] as Map<*, *>)["lifetimeTasksCompleted"])
            assertEquals(1, (changed["aiNativeActionReceipts"] as List<*>).size)
            repository.saveAiOperation(uid, reply, "late-reply-new-op")
            assertTrue((repository.getAiUserOnce(uid)["aiNativeProposals"] as Map<*, *>).isEmpty())
        }
    }

    private fun restartRepository() {
        pendingStore = PendingEditStore(journalDirectory)
        repository = FirebaseRepository(db, { owner }, pendingStore)
    }

    private fun planEdit(before: KairosPlan, after: KairosPlan) = PendingEdit(
        userId = uid, kind = "plan", documentId = before.documentId,
        documentExisted = before.documentId.isNotBlank(), base = before.sharedFields(),
        edited = after.sharedFields(), rawBaseline = before.sourceFields
    )

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

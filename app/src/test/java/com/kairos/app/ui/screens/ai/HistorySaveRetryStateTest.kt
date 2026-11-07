package com.kairos.app.ui.screens.ai

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

class HistorySaveRetryStateTest {
    private class StagingFailure : IOException("Disk unavailable")

    @Test
    fun missedJournalNotificationThenDiscardDoesNotResurrectFailedSave() = runImmediately {
        val state = HistorySaveRetryState()
        val operationId = "original-chat-save"
        val journal = mutableSetOf<String>()
        var puts = 0
        var retries = 0
        val stage: suspend () -> Unit = {
            puts++
            journal += operationId
            // Fail after durable staging. No observer notification is delivered to the screen.
            throw IOException("Network unavailable")
        }
        try {
            state.execute(stage, {}, { it is StagingFailure })
            fail("Initial network failure expected")
        } catch (_: IOException) { }
        assertFalse(state.observedInJournal)
        assertTrue(operationId in journal)

        // The global banner discards it before this screen ever observes the staged record.
        journal.remove(operationId)
        val usedRetainedRoute = state.execute(stage, {
            retries++
            // The repository's retained retry is a no-op when the immutable ID is absent.
            journal.firstOrNull { it == operationId }
            Unit
        }, { it is StagingFailure })

        assertTrue(usedRetainedRoute)
        assertEquals(1, puts)
        assertEquals(1, retries)
        assertTrue(journal.isEmpty())
    }

    @Test
    fun onlyFailureBeforeStagingAllowsAnotherPut() = runImmediately {
        val state = HistorySaveRetryState()
        var puts = 0
        var retries = 0
        try {
            state.execute({ puts++; throw StagingFailure() }, { retries++ }, { it is StagingFailure })
            fail("Staging failure expected")
        } catch (_: StagingFailure) { }

        val usedRetainedRoute = state.execute({ puts++ }, { retries++ }, { it is StagingFailure })
        assertFalse(usedRetainedRoute)
        assertEquals(2, puts)
        assertEquals(0, retries)
    }

    @Test
    fun retainedRetryFailureCannotReenableStaging() = runImmediately {
        val state = HistorySaveRetryState()
        state.observeInJournal()
        var puts = 0
        var retries = 0
        try {
            state.execute({ puts++ }, { retries++; throw IOException("Still offline") }, { it is StagingFailure })
            fail("Retry failure expected")
        } catch (_: IOException) { }
        assertTrue(state.execute({ puts++ }, { retries++ }, { it is StagingFailure }))
        assertEquals(0, puts)
        assertEquals(2, retries)
    }

    private fun runImmediately(block: suspend () -> Unit) {
        var completed = false
        var failure: Throwable? = null
        block.startCoroutine(object : Continuation<Unit> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) {
                completed = true
                failure = result.exceptionOrNull()
            }
        })
        check(completed) { "Test operations must complete synchronously" }
        failure?.let { throw it }
    }
}

package com.kairos.app.ui.screens.ai

import java.util.concurrent.CancellationException

/** One save's retry route survives missed/conflated journal notifications. */
internal class HistorySaveRetryState {
    private var staged = false
    var observedInJournal = false
        private set

    fun observeInJournal() {
        staged = true
        observedInJournal = true
    }

    /** Returns whether the retained-operation route was used, including its missing-record no-op. */
    suspend fun execute(
        stage: suspend () -> Unit,
        retryRetained: suspend () -> Unit,
        failedBeforeStaging: (Exception) -> Boolean
    ): Boolean {
        val retryingRetained = staged
        try {
            if (retryingRetained) retryRetained() else stage()
            staged = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Only a typed failure from the staging boundary permits another put.
            // Every later failure must use the immutable ID, even if its record was discarded.
            if (!retryingRetained && !failedBeforeStaging(e)) staged = true
            throw e
        }
        return retryingRetained
    }
}

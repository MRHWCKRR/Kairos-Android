package com.kairos.app.data.repository

import java.util.UUID

/** Immutable original intent and server baseline for one acknowledged operation. */
data class PendingEdit(
    val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val kind: String,
    val documentId: String,
    val documentExisted: Boolean,
    val base: Map<String, Any?>,
    val edited: Map<String, Any?>,
    val rawBaseline: Map<String, Any?>
)

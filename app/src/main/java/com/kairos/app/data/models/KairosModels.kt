package com.kairos.app.data.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class KairosTask(
    val id: String = "",
    val title: String = "",
    var completed: Boolean = false,
    val date: String? = null,
    val archived: Boolean = false
)

@IgnoreExtraProperties
data class KairosSection(
    val id: String = "",
    val title: String = "",
    val tasks: List<KairosTask> = emptyList(),
    val archived: Boolean = false
)

@IgnoreExtraProperties
data class KairosBoard(
    val id: String = "",
    val title: String = "",
    val sections: List<KairosSection> = emptyList(),
    val archived: Boolean = false
)

@IgnoreExtraProperties
data class KairosScheduleEvent(
    val id: String = "",
    val title: String = "",
    val category: String = "other",
    val day: Int = 0,
    val start: String = "",
    val end: String = ""
)

@IgnoreExtraProperties
data class KairosPlan(
    val boards: List<KairosBoard> = emptyList(),
    val dayInsights: Map<String, String> = emptyMap(),
    val scheduleEvents: List<KairosScheduleEvent> = emptyList(),
    val userID: String = "",
    val createdAt: Timestamp? = null
)

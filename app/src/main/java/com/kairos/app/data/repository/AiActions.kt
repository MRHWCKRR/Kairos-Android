package com.kairos.app.data.repository

import kotlinx.serialization.json.*
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID

/** Untrusted model output becomes a durable preview before any planner write. */
object AiActions {
    data class Action(val type: String, val args: Map<String, Any?>)
    data class Response(val reply: String, val actions: List<Action>)
    data class Row(val id: String, val title: String, val action: String, val before: String, val after: String, val destructive: Boolean)
    data class Proposal(
        val id: String, val ownerId: String, val chatId: String, val planId: String,
        val timezone: String, val createdAt: Double, val beforeFingerprint: String, val request: String,
        val actions: List<Action>, val rows: List<Row>, val allowCompletedRescheduling: Boolean = false
    )
    data class Prepared(val before: Map<String, Any?>, val after: Map<String, Any?>, val rows: List<Row>, val completedTaskIds: List<String>)

    private val allowed = mapOf(
        "create_task" to setOf("sectionId", "title", "date", "startTime", "endTime"),
        "update_task" to setOf("taskId", "newTitle", "date", "startTime", "endTime", "completed"),
        "complete_task" to setOf("taskId"), "delete_task" to setOf("taskId"),
        "create_board" to setOf("title"), "update_board" to setOf("boardId", "newTitle"), "delete_board" to setOf("boardId"),
        "create_section" to setOf("boardId", "title"), "update_section" to setOf("sectionId", "newTitle"), "delete_section" to setOf("boardId", "sectionId"),
        "set_task_reminder" to setOf("taskId", "minutesBefore"), "remove_task_reminder" to setOf("taskId"),
        "update_task_occurrence" to setOf("taskId", "occurrenceDate", "date", "startTime", "endTime", "status"),
        "create_schedule_event" to setOf("title", "date", "day", "start", "end", "category"),
        "update_schedule_event" to setOf("eventId", "occurrenceDate", "newTitle", "date", "start", "end", "category"),
        "delete_schedule_event" to setOf("eventId", "occurrenceDate")
    )
    val instructions = """
        Return ONLY JSON {"reply":"Markdown response","actions":[]}. Omit unspecified arguments; never use null.
        Propose only these action types with exactly these args:
        create_task {sectionId,title,date?,startTime?,endTime?}; update_task {taskId,newTitle?,date?,startTime?,endTime?,completed?}; complete_task {taskId}; delete_task {taskId};
        create_board {title}; update_board {boardId,newTitle}; delete_board {boardId}; create_section {boardId,title}; update_section {sectionId,newTitle}; delete_section {boardId,sectionId};
        set_task_reminder {taskId,minutesBefore}; remove_task_reminder {taskId}; update_task_occurrence {taskId,occurrenceDate,date?,startTime?,endTime?,status?};
        create_schedule_event {title,date?,day?,start,end,category?}; update_schedule_event {eventId,occurrenceDate?,newTitle?,date?,start?,end?,category?}; delete_schedule_event {eventId,occurrenceDate?}.
        Use exact current planner IDs, YYYY-MM-DD dates, HH:MM times, at most 10 actions, and no repeated target. Actions await review: do not claim success.
        Recurring tasks require update_task_occurrence with the original occurrenceDate. Status: pending/completed/skipped. A series reminder applies to every occurrence; minutesBefore: 1–10080.
        Weekly event update/delete requires the original occurrenceDate; never silently change a series. New events require exactly one of date/day (0=Sunday,6=Saturday); weekly events start today. Categories: sleep,class,tutoring,training,other.
        Whole-series edits, event imports/feeds, automatic multi-assignment planning remain web-only. Ask for clarification when required fields or exact targets are unclear.
    """.trimIndent()

    fun parse(text: String): Response {
        require(text.length <= 128_000) { "The AI response is too large." }
        val trimmed = text.trim()
        val json = if (trimmed.startsWith("```")) {
            require(trimmed.endsWith("```") && trimmed.contains('\n')) { "Invalid JSON response." }
            trimmed.substringAfter('\n').substringBeforeLast("```").trim()
        } else trimmed
        val root = stringMap(decode(Json.parseToJsonElement(json.also(::rejectDuplicateKeys))))
        require(root.keys == setOf("reply", "actions")) { "Expected reply and actions only." }
        val reply = root["reply"] as? String ?: invalid("Expected a reply.")
        require(root["actions"] is List<*>) { "Expected an actions array." }
        val actions = records(root["actions"]).map { item ->
            require(item.keys == setOf("type", "args")) { "Unknown action fields." }
            Action(item["type"] as? String ?: invalid("Missing action type."), stringMap(item["args"]))
        }
        validateActions(actions, allowEmpty = true)
        return Response(reply, actions)
    }

    fun make(actions: List<Action>, plan: Map<String, Any?>, ownerId: String, chatId: String, planId: String, timezone: String, request: String, allowCompletedRescheduling: Boolean = false): Proposal {
        validateActions(actions)
        val initial = Proposal("proposal-${UUID.randomUUID()}", ownerId, chatId, planId, timezone, System.currentTimeMillis().toDouble(), fingerprint(plan), request.take(4000), actions.map { it.copy(args = copyMap(it.args)) }, emptyList(), allowCompletedRescheduling)
        val proposal = initial.copy(rows = transform(initial, plan).rows)
        validate(proposal)
        return proposal
    }

    fun prepare(proposal: Proposal, plan: Map<String, Any?>): Prepared {
        validate(proposal)
        require(fingerprint(plan) == proposal.beforeFingerprint) { "Your planner changed since this preview. Discard it and request an updated preview." }
        val result = transform(proposal, plan)
        require(result.rows == proposal.rows) { "This saved preview cannot be verified safely. Discard it and request a new preview." }
        return result
    }

    fun toMap(proposal: Proposal): Map<String, Any?> = mapOf(
        "version" to 1, "id" to proposal.id, "ownerID" to proposal.ownerId, "chatID" to proposal.chatId, "planID" to proposal.planId,
        "timezone" to proposal.timezone, "createdAt" to proposal.createdAt, "beforeFingerprint" to proposal.beforeFingerprint, "request" to proposal.request,
        "actions" to proposal.actions.map { mapOf("type" to it.type, "args" to copyMap(it.args)) },
        "rows" to proposal.rows.map { mapOf("id" to it.id, "title" to it.title, "action" to it.action, "before" to it.before, "after" to it.after, "destructive" to it.destructive) },
        "allowCompletedRescheduling" to proposal.allowCompletedRescheduling
    )

    fun fromMap(map: Map<String, Any?>): Proposal {
        require(integer(map["version"]) == 1L) { "Unsupported saved preview version." }
        fun text(key: String) = map[key] as? String ?: invalid("Unreadable saved preview: $key.")
        val actions = records(map["actions"]).map {
            require(it.keys == setOf("type", "args")) { "Unreadable saved action." }
            Action(it["type"] as? String ?: invalid("Unreadable action type."), copyMap(stringMap(it["args"])))
        }
        val rows = records(map["rows"]).map { row ->
            fun field(key: String) = row[key] as? String ?: invalid("Unreadable preview row.")
            Row(field("id"), field("title"), field("action"), field("before"), field("after"), row["destructive"] as? Boolean ?: invalid("Unreadable preview row."))
        }
        val proposal = Proposal(text("id"), text("ownerID"), text("chatID"), text("planID"), text("timezone"), number(map["createdAt"]) ?: invalid("Invalid preview timestamp."), text("beforeFingerprint"), text("request"), actions, rows, map["allowCompletedRescheduling"] as? Boolean ?: invalid("Unreadable planning preference."))
        validate(proposal)
        return proposal
    }

    fun payloadFingerprint(proposal: Proposal): String = digest(toMap(proposal))
    fun receipt(proposal: Proposal): Map<String, Any?> = mapOf("id" to proposal.id, "createdAt" to proposal.createdAt, "fingerprint" to payloadFingerprint(proposal))
    fun wasApplied(proposal: Proposal, plan: Map<String, Any?>): Boolean {
        validate(proposal)
        val receipts = records(plan["aiNativeActionReceipts"])
        require(receipts.map { it["id"] }.distinct().size == receipts.size) { "Ambiguous action receipts." }
        val saved = receipts.firstOrNull { it["id"] == proposal.id }
        if (saved != null) {
            require(saved["fingerprint"] == payloadFingerprint(proposal)) { "This operation identity belongs to another preview." }
            return true
        }
        val floor = if (plan.containsKey("aiNativeActionReceiptFloor")) number(plan["aiNativeActionReceiptFloor"]) ?: invalid("Unreadable receipt history.") else 0.0
        require(proposal.createdAt > floor) { "This preview is too old to retry safely. Check your planner before requesting a new change." }
        return false
    }

    fun undo(before: Map<String, Any?>, after: Map<String, Any?>, current: Map<String, Any?>): Map<String, Any?> =
        SharedDataMerge.merge(after, before, current, baselineRaw = after)

    private fun validate(proposal: Proposal) {
        require(listOf(proposal.id, proposal.ownerId, proposal.chatId, proposal.planId).all { it.isNotBlank() } && proposal.createdAt.isFinite() && proposal.createdAt > 0 && proposal.request.length <= 4000) { "Unreadable saved preview." }
        ZoneId.of(proposal.timezone)
        require(proposal.beforeFingerprint.matches(Regex("[a-f0-9]{64}")) && proposal.rows.size == proposal.actions.size) { "Unreadable preview baseline." }
        validateActions(proposal.actions)
        require(proposal.rows.map { it.id } == proposal.actions.indices.map { "${proposal.id}-$it" }) { "Unreadable preview row identities." }
    }

    private fun validateActions(actions: List<Action>, allowEmpty: Boolean = false) {
        require(actions.size <= 10 && (allowEmpty || actions.isNotEmpty())) { "Review from 1 to 10 changes at a time." }
        val targets = mutableSetOf<String>()
        for (action in actions) {
            val a = action.args
            val fields = allowed[action.type] ?: invalid("Unsupported action: ${action.type}.")
            require(a.keys.all { it in fields } && a.values.none { it == null }) { "Unknown or null action arguments." }
            for ((key, value) in a) when (key) {
                "completed" -> require(value is Boolean) { "Expected a completion value." }
                "minutesBefore" -> require(integer(value) in 1L..10080L) { "Choose a reminder from 1 to 10080 minutes." }
                "day" -> require(integer(value) in 0L..6L) { "Choose a weekday from 0 to 6." }
                else -> {
                    require(value is String && value.isNotBlank()) { "Invalid $key." }
                    if (key in setOf("date", "occurrenceDate")) date(value)
                    if (key in setOf("start", "end", "startTime", "endTime")) minutes(value)
                    if (key in setOf("title", "newTitle")) require(value.length <= 180) { "Titles may contain at most 180 characters." }
                    if (key == "status") require(value in setOf("pending", "completed", "skipped")) { "Unsupported occurrence status." }
                    if (key == "category") require(value in setOf("sleep", "class", "tutoring", "training", "other")) { "Unsupported event category." }
                }
            }
            val required = when (action.type) {
                "create_task" -> setOf("sectionId", "title")
                "create_board" -> setOf("title")
                "create_section" -> setOf("boardId", "title")
                "update_board" -> setOf("boardId", "newTitle")
                "update_section" -> setOf("sectionId", "newTitle")
                "delete_board" -> setOf("boardId")
                "delete_section" -> setOf("boardId", "sectionId")
                "set_task_reminder" -> setOf("taskId", "minutesBefore")
                "update_task_occurrence" -> setOf("taskId", "occurrenceDate")
                "create_schedule_event" -> setOf("title", "start", "end")
                "update_schedule_event", "delete_schedule_event" -> setOf("eventId")
                else -> setOf("taskId")
            }
            require(a.keys.containsAll(required)) { "Missing required arguments for ${action.type}." }
            if (action.type == "create_schedule_event") require(a.containsKey("date") != a.containsKey("day")) { "Choose either a date or a weekly weekday." }
            if (action.type in setOf("update_task", "update_schedule_event", "update_task_occurrence")) require(a.keys.any { it !in setOf("taskId", "eventId", "occurrenceDate") }) { "Choose a change to preview." }
            if (!action.type.startsWith("create_")) {
                val kind = when { "taskId" in a -> "taskId"; "eventId" in a -> "eventId"; "sectionId" in a -> "sectionId"; else -> "boardId" }
                require(targets.add("$kind:${a[kind]}")) { "Propose only one change per target." }
            }
        }
    }

    private fun transform(proposal: Proposal, plan: Map<String, Any?>): Prepared {
        identities(plan)
        var next = copyMap(plan)
        val rows = mutableListOf<Row>()
        val completed = mutableListOf<String>()
        for ((index, action) in proposal.actions.withIndex()) {
            val before = next
            val identity = "${proposal.id}-$index"
            next = if (action.type.endsWith("schedule_event")) editEvent(action, next, proposal, identity) else editBoards(action, next, proposal, identity)
            val target = findTarget(action, before)
            val title = target?.get("title") as? String ?: action.args["newTitle"] as? String ?: action.args["title"] as? String ?: "Planner change"
            val afterText = if (action.type.startsWith("create_")) {
                val added = entities(action.type, next).first { item -> entities(action.type, before).none { it["id"] == item["id"] } }
                describeItem(added, action)
            } else describe(action, next)
            rows += Row(identity, title, action.type.split('_').joinToString(" ") { it.replaceFirstChar { c -> c.titlecase(Locale.ROOT) } }, if (action.type.startsWith("create_")) "Not created" else describe(action, before), afterText, action.type.startsWith("delete_"))
            if (action.type == "complete_task" || action.args["completed"] == true) {
                val id = action.args["taskId"] as String
                if (target?.get("completed") != true) completed += id
            }
        }
        identities(next)
        return Prepared(copyMap(plan), next, rows, completed)
    }

    private fun editBoards(action: Action, plan: Map<String, Any?>, proposal: Proposal, identity: String): Map<String, Any?> {
        val a = action.args
        val boards = records(plan["boards"]).map { it.toMutableMap() }.toMutableList()
        fun board(id: Any?): MutableMap<String, Any?> = boards.singleOrNull { it["id"] == id && it["archived"] != true } ?: invalid("The exact board is unavailable.")
        fun activeSections(): List<Pair<MutableMap<String, Any?>, Map<String, Any?>>> = boards.filter { it["archived"] != true }.flatMap { b -> records(b["sections"]).filter { it["archived"] != true }.map { b to it } }
        when (action.type) {
            "create_board" -> boards += mutableMapOf("id" to "board-$identity-0", "title" to a["title"], "archived" to false, "sections" to emptyList<Any>())
            "update_board" -> board(a["boardId"])["title"] = a["newTitle"]
            "delete_board" -> boards.remove(board(a["boardId"]))
            "create_section" -> {
                val b = board(a["boardId"])
                b["sections"] = records(b["sections"]) + mapOf("id" to "sec-$identity-0", "title" to a["title"], "archived" to false, "tasks" to emptyList<Any>())
            }
            "update_section", "delete_section", "create_task" -> {
                val (b, old) = activeSections().singleOrNull { it.second["id"] == a["sectionId"] } ?: invalid("The exact section is unavailable.")
                val section = old.toMutableMap()
                when (action.type) {
                    "update_section" -> section["title"] = a["newTitle"]
                    "delete_section" -> require(b["id"] == a["boardId"]) { "The section does not belong to that board." }
                    else -> {
                        val task = mutableMapOf<String, Any?>("id" to "task-$identity-0", "title" to (a["title"] as String).trim(), "completed" to false, "archived" to false)
                        schedule(task, a)
                        section["tasks"] = records(section["tasks"]) + task
                    }
                }
                b["sections"] = records(b["sections"]).mapNotNull { if (it["id"] != old["id"]) it else if (action.type == "delete_section") null else section }
            }
            else -> {
                val matches = activeSections().flatMap { (b, s) -> records(s["tasks"]).filter { it["id"] == a["taskId"] && it["archived"] != true }.map { Triple(b, s, it) } }
                val (b, s, old) = matches.singleOrNull() ?: invalid("The exact task is missing or archived.")
                val task = old.toMutableMap()
                require(task["readOnly"] != true && task["calendarSourceId"] == null) { "This task is read-only." }
                var tasks = records(s["tasks"])
                when (action.type) {
                    "set_task_reminder", "remove_task_reminder" -> {
                        task["reminderMinutes"] = if (action.type == "set_task_reminder") integer(a["minutesBefore"]) else null
                        if (task["recurrence"] != null) {
                            val rule = stringMap(task["recurrence"]).toMutableMap()
                            rule["exceptions"] = optionalMap(rule, "exceptions").mapValues { (_, raw) ->
                                val exception = stringMap(raw).toMutableMap()
                                if (exception["override"] != null) exception["override"] = stringMap(exception["override"]).toMutableMap().also { it.remove("reminderMinutes") }
                                exception
                            }
                            task["recurrence"] = rule
                        }
                    }
                    "update_task_occurrence" -> task.putAll(editOccurrence(task, a, proposal))
                    "update_task", "complete_task", "delete_task" -> {
                        require(optionalMap(task, "recurrence")["enabled"] != true) { "Choose a recurring occurrence; whole-series edits are unavailable." }
                        if (a.keys.any { it in setOf("date", "startTime", "endTime") }) {
                            require(task["scheduleLocked"] != true) { "This task's schedule is locked." }
                            require(task["completed"] != true || proposal.allowCompletedRescheduling) { "Completed-task rescheduling is disabled in planning settings." }
                        }
                        if (action.type == "delete_task") {
                            tasks = tasks.filter { it["id"] != task["id"] }.map { child -> if (child["parentTaskID"] == task["id"]) child.toMutableMap().also { it["parentTaskID"] = null } else child }
                        } else {
                            a["newTitle"]?.let { task["title"] = it }
                            schedule(task, a)
                            val done = if (action.type == "complete_task") true else a["completed"] as? Boolean
                            if (done != null) task["completed"] = done
                        }
                    }
                    else -> invalid("Unsupported task action.")
                }
                if (action.type != "delete_task") tasks = tasks.map { if (it["id"] == task["id"]) task else it }
                if (action.type == "complete_task" || "completed" in a) tasks = reconcile(tasks, task["parentTaskID"] as? String, task["completed"] == true)
                val section = s.toMutableMap().also { it["tasks"] = tasks }
                b["sections"] = records(b["sections"]).map { if (it["id"] == s["id"]) section else it }
            }
        }
        return plan.toMutableMap().also { it["boards"] = boards }
    }

    private fun reconcile(source: List<Map<String, Any?>>, parentId: String?, complete: Boolean): List<Map<String, Any?>> {
        val tasks = source.toMutableList()
        var id = parentId
        val seen = mutableSetOf<String>()
        while (id != null) {
            require(seen.add(id)) { "The task has cyclic parent links." }
            val index = tasks.indexOfFirst { it["id"] == id }
            require(index >= 0 && optionalMap(tasks[index], "recurrence")["enabled"] != true) { "The parent task is unavailable." }
            val children = tasks.filter { it["parentTaskID"] == id && it["archived"] != true }
            tasks[index] = tasks[index].toMutableMap().also { it["completed"] = complete && children.isNotEmpty() && children.all { child -> child["completed"] == true } }
            id = tasks[index]["parentTaskID"] as? String
        }
        return tasks
    }

    private fun schedule(task: MutableMap<String, Any?>, args: Map<String, Any?>) {
        val oldDuration = duration(task)
        for (key in listOf("date", "startTime", "endTime")) if (key in args) task[key] = args[key]
        if ("startTime" in args && "endTime" !in args) task["endTime"] = clock(minutes(args["startTime"] as String) + oldDuration)
    }

    private fun editOccurrence(task: Map<String, Any?>, a: Map<String, Any?>, proposal: Proposal): Map<String, Any?> {
        require(task["scheduleLocked"] != true) { "This task's schedule is locked." }
        val rule = stringMap(task["recurrence"]).toMutableMap()
        val original = a["occurrenceDate"] as String
        val exceptions = optionalMap(rule, "exceptions").toMutableMap()
        val exception = (exceptions[original]?.let(::stringMap) ?: emptyMap()).toMutableMap()
        val override = optionalMap(exception, "override").toMutableMap()
        val generated = recurringDate(task, rule, original)
        val preserved = override.keys.any { it in setOf("date", "startTime", "endTime", "durationMinutes") } || exception["status"] in setOf("completed", "skipped")
        require(exception["status"] != "deleted" && (generated || preserved)) { "This recurring occurrence no longer exists." }
        val effective = task + override
        val fixed = (rule["mode"] ?: "flexible") == "fixed"
        val oldStart = if (fixed || "startTime" in override) effective["startTime"] as? String else null
        var oldEnd = if (fixed || "startTime" in override) effective["endTime"] as? String else null
        if (oldStart != null) {
            minutes(oldStart)
            if (oldEnd == null) oldEnd = clock(minutes(oldStart) + duration(effective)) else minutes(oldEnd)
        }
        val scheduling = a.keys.any { it in setOf("date", "startTime", "endTime") }
        if (scheduling) {
            require(exception["status"] != "completed" || proposal.allowCompletedRescheduling) { "Completed-task rescheduling is disabled in planning settings." }
            val newDate = a["date"] as? String ?: override["date"] as? String ?: original
            date(newDate)
            val newStart = a["startTime"] as? String ?: oldStart
            val newEnd = a["endTime"] as? String ?: if ("startTime" in a) clock(minutes(newStart!!) + duration(effective)) else oldEnd
            require((newStart == null && newEnd == null) || (newStart != null && newEnd != null)) { "Choose both occurrence start and end times." }
            if (newStart != null) { minutes(newStart); minutes(newEnd!!) }
            for ((key, value) in mapOf("date" to newDate, "startTime" to newStart, "endTime" to newEnd)) {
                val baseline = if (key == "date") original else task[key]
                if (value == baseline) override.remove(key) else override[key] = value
            }
            override.remove("durationMinutes")
        }
        if (a["status"] == "pending" && !generated) {
            override.putIfAbsent("date", original)
            if (!override.containsKey("startTime")) override["startTime"] = oldStart
            if (!override.containsKey("endTime")) override["endTime"] = oldEnd
            if (!override.containsKey("durationMinutes")) override["durationMinutes"] = duration(effective)
        }
        if ("status" in a) {
            exception["status"] = a["status"]
            exception.remove("completedAt"); exception.remove("skippedAt")
            if (a["status"] in setOf("completed", "skipped")) exception[if (a["status"] == "completed") "completedAt" else "skippedAt"] = Instant.ofEpochMilli(proposal.createdAt.toLong()).toString()
        }
        if (override.isEmpty()) exception.remove("override") else exception["override"] = override
        exceptions[original] = exception; rule["exceptions"] = exceptions
        return task.toMutableMap().also { it["recurrence"] = rule }
    }

    /** Same candidate semantics as the shared recurrence engine; malformed rules fail closed. */
    private fun recurringDate(task: Map<String, Any?>, rule: Map<String, Any?>, original: String): Boolean {
        require(rule["enabled"] == true) { "This task is not recurring." }
        for ((key, values) in mapOf("frequency" to setOf("daily", "weekly", "monthly", "yearly"), "mode" to setOf("fixed", "flexible"), "endType" to setOf("never", "date", "count"))) {
            require(!rule.containsKey(key) || rule[key] in values) { "Invalid recurrence $key." }
        }
        if (rule["count"] != null) require(integer(rule["count"])?.let { it > 0 } == true) { "Invalid recurrence count." }
        for (key in listOf("startDate", "endDate")) rule[key]?.let { date(it as? String ?: invalid("Invalid recurrence date.")) }
        if (rule.containsKey("weekdays")) {
            val days = rule["weekdays"] as? List<*> ?: invalid("Invalid repeat weekdays.")
            require(days.all { integer(it) in 0L..6L }) { "Invalid repeat weekday." }
        }
        require(rule["monthlyMode"] == null || rule["monthlyMode"] in setOf("date", "weekdayPosition")) { "Invalid monthly repeat mode." }
        require(rule["weekdayPosition"] == null || rule["weekdayPosition"] in setOf("first", "second", "third", "fourth", "last")) { "Invalid weekday position." }
        val start = date((rule["startDate"] ?: task["date"] ?: original) as? String ?: invalid("Invalid recurrence start."))
        val target = date(original)
        require(kotlin.math.abs(target.year - start.year) <= 200 && optionalMap(rule, "exceptions").size <= 10000) { "This recurring task exceeds native limits." }
        val preset = (rule["preset"] as? String)?.lowercase(Locale.ROOT)
        val frequency = if (preset == "weekdays") "weekly" else rule["frequency"] as? String ?: "daily"
        require(frequency in setOf("daily", "weekly", "monthly", "yearly") && (rule["mode"] ?: "flexible") in setOf("fixed", "flexible")) { "Unsupported repeat rule." }
        val interval = if (rule.containsKey("interval")) integer(rule["interval"]) ?: invalid("Invalid repeat interval.") else 1L
        require(interval in 1..Int.MAX_VALUE.toLong()) { "Invalid repeat interval." }
        val endType = rule["endType"] ?: "never"
        require(endType in setOf("never", "date", "count")) { "Invalid recurrence end." }
        val count = if (endType == "count") integer(rule["count"])?.also { require(it > 0) } ?: invalid("Missing recurrence count.") else Long.MAX_VALUE
        val end = if (endType == "date") date(rule["endDate"] as? String ?: invalid("Missing recurrence end date.")) else target
        val weekdays = if (preset == "weekdays") listOf(1L, 2L, 3L, 4L, 5L) else (rule["weekdays"] as? List<*>)?.map { integer(it)?.also { n -> require(n in 0..6) } ?: invalid("Invalid repeat weekday.") } ?: emptyList()
        if (rule.containsKey("weekdays")) require(rule["weekdays"] is List<*>) { "Invalid repeat weekdays." }
        val selectedWeekdays = weekdays.ifEmpty { listOf((start.dayOfWeek.value % 7).toLong()) }
        val monthDay = if (rule["monthDay"] != null) integer(rule["monthDay"]) ?: invalid("Invalid monthly date.") else start.dayOfMonth.toLong()
        val month = if (rule["month"] != null) integer(rule["month"]) ?: invalid("Invalid repeat month.") else start.monthValue.toLong()
        val weekday = if (rule["weekday"] != null) integer(rule["weekday"]) ?: invalid("Invalid repeat weekday.") else (start.dayOfWeek.value % 7).toLong()
        require(monthDay in 1..31 && month in 1..12 && weekday in 0..6) { "Invalid repeat date." }
        val monthlyMode = rule["monthlyMode"] ?: "date"
        require(monthlyMode in setOf("date", "weekdayPosition")) { "Invalid monthly repeat mode." }
        val position = rule["weekdayPosition"] as? String
        if (frequency == "monthly" && monthlyMode == "weekdayPosition") require(position in setOf("first", "second", "third", "fourth", "last")) { "Choose a weekday position." }
        for (raw in optionalMap(rule, "exceptions").values) {
            val exception = stringMap(raw)
            require(exception["status"] == null || exception["status"] in setOf("pending", "completed", "skipped", "deleted")) { "Unsupported occurrence status." }
        }
        if (target < start || target > end) return false
        var emitted = 0L
        var candidate = start
        while (candidate <= target) {
            val days = ChronoUnit.DAYS.between(start, candidate)
            val matches = when (frequency) {
                "daily" -> days % interval == 0L
                "weekly" -> (days / 7) % interval == 0L && (candidate.dayOfWeek.value % 7).toLong() in selectedWeekdays
                "monthly" -> {
                    val months = ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(candidate))
                    months % interval == 0L && if (monthlyMode == "date") candidate.dayOfMonth.toLong() == monthDay else {
                        val ordinal = (candidate.dayOfMonth - 1) / 7
                        (candidate.dayOfWeek.value % 7).toLong() == weekday && if (position == "last") candidate.plusWeeks(1).month != candidate.month else ordinal == listOf("first", "second", "third", "fourth").indexOf(position)
                    }
                }
                else -> (candidate.year - start.year).toLong() % interval == 0L && candidate.monthValue.toLong() == month && candidate.dayOfMonth.toLong() == monthDay
            }
            if (matches) { emitted++; if (emitted > count) return false; if (candidate == target) return true }
            candidate = candidate.plusDays(1)
        }
        return false
    }

    private fun editEvent(action: Action, plan: Map<String, Any?>, proposal: Proposal, identity: String): Map<String, Any?> {
        val a = action.args
        val events = records(plan["scheduleEvents"]).toMutableList()
        val creating = action.type == "create_schedule_event"
        val index = if (creating) -1 else events.indexOfFirst { it["id"] == a["eventId"] }.also { require(it >= 0) { "The exact event is unavailable." } }
        val event = if (creating) mutableMapOf<String, Any?>() else events[index].toMutableMap()
        require(event["archived"] != true && event["readOnly"] != true && event["scheduleLocked"] != true && event["locked"] != true && event["calendarSourceId"] == null) { "This event is archived, locked, or read-only." }
        val original = a["occurrenceDate"] as? String
        val exceptions = optionalMap(event, "exceptions").toMutableMap()
        val exception = (original?.let { exceptions[it]?.let(::stringMap) } ?: emptyMap()).toMutableMap()
        val override = optionalMap(exception, "override")
        if (!creating) {
            if (event["date"] == null) {
                require(original != null) { "Choose one original weekly occurrence date; series edits are unavailable." }
                val day = integer(event["day"])
                val selected = date(original)
                require(day in 0L..6L && selected.dayOfWeek.value % 7 == day!!.toInt() && original >= "1900-01-01" && original <= "2200-12-30") { "Choose an existing original weekly occurrence." }
                for (key in listOf("startDate", "endDate")) event[key]?.let { date(it as? String ?: invalid("Invalid event bounds.")) }
                require((event["startDate"] == null || original >= event["startDate"] as String) && (event["endDate"] == null || original <= event["endDate"] as String)) { "This weekly occurrence is outside the event dates." }
                val interval = if (event.containsKey("intervalWeeks")) integer(event["intervalWeeks"]) else 1L
                require(interval == 1L || interval == 2L) { "Unsupported weekly interval." }
                if (interval == 2L) {
                    val anchor = date(event["anchorDate"] as? String ?: invalid("Missing alternating-week anchor."))
                    require(anchor.dayOfWeek == selected.dayOfWeek && ChronoUnit.DAYS.between(anchor, selected) % 14 == 0L) { "This is not an event occurrence." }
                }
                require(exception["skipped"] != true) { "This weekly occurrence is already skipped." }
            } else require(original == null) { "A dated event has no weekly occurrence identity." }
        }
        if (action.type == "delete_schedule_event") {
            if (original == null) events.removeAt(index) else { exception["skipped"] = true; exceptions[original] = exception; event["exceptions"] = exceptions; events[index] = event }
        } else {
            val effective = event + override
            val today = Instant.ofEpochMilli(proposal.createdAt.toLong()).atZone(ZoneId.of(proposal.timezone)).toLocalDate().toString()
            val chosenDate = a["date"] as? String ?: if (original != null) override["date"] as? String ?: original else effective["date"] as? String ?: today
            date(chosenDate)
            val title = a["newTitle"] as? String ?: a["title"] as? String ?: effective["title"] as? String ?: invalid("Missing event title.")
            require(title.isNotBlank() && title.length <= 180) { "Invalid event title." }
            val start = a["start"] as? String ?: effective["start"] as? String ?: invalid("Missing event start.")
            var end = a["end"] as? String ?: effective["end"] as? String ?: invalid("Missing event end.")
            if (!creating && "start" in a && "end" !in a) end = clock(minutes(start) + eventDuration(effective))
            minutes(start); minutes(end)
            val category = a["category"] as? String ?: effective["category"] as? String ?: "other"
            require(category in setOf("sleep", "class", "tutoring", "training", "other")) { "Unsupported event category." }
            val fields = mapOf("title" to title, "date" to chosenDate, "start" to start, "end" to end, "category" to category)
            if (original != null) {
                exception["skipped"] = false; exception["override"] = override + fields
                exceptions[original] = exception; event["exceptions"] = exceptions; events[index] = event
            } else {
                event.putAll(fields)
                if (creating) {
                    event["id"] = "event-$identity"
                    if ("day" in a) { event.remove("date"); event["day"] = integer(a["day"]); event["startDate"] = today }
                    events += event
                } else events[index] = event
            }
        }
        return plan.toMutableMap().also { it["scheduleEvents"] = events }
    }

    private fun entities(type: String, plan: Map<String, Any?>): List<Map<String, Any?>> {
        if (type.contains("schedule_event")) return records(plan["scheduleEvents"])
        val boards = records(plan["boards"])
        if (type.endsWith("board")) return boards
        val sections = boards.flatMap { records(it["sections"]) }
        return if (type.endsWith("section")) sections else sections.flatMap { records(it["tasks"]) }
    }
    private fun findTarget(action: Action, plan: Map<String, Any?>): Map<String, Any?>? {
        val id = action.args["taskId"] ?: action.args["eventId"] ?: action.args["sectionId"] ?: action.args["boardId"] ?: return null
        val kind = when { "taskId" in action.args -> "task"; "eventId" in action.args -> "schedule_event"; "sectionId" in action.args -> "section"; else -> "board" }
        return entities(kind, plan).singleOrNull { it["id"] == id }
    }
    private fun describe(action: Action, plan: Map<String, Any?>): String {
        var item = findTarget(action, plan) ?: return if (action.type.startsWith("delete_")) "Removed" else action.args["newTitle"] as? String ?: action.args["title"] as? String ?: "New item"
        val original = action.args["occurrenceDate"] as? String
        if ("eventId" in action.args && original != null) {
            val exception = optionalMap(item, "exceptions")[original]?.let(::stringMap) ?: emptyMap()
            if (exception["skipped"] == true) return "Skipped occurrence $original"
            item = (item + optionalMap(exception, "override")).toMutableMap().also { it["date"] = it["date"] as? String ?: original; it.remove("day") }
        }
        return describeItem(item, action)
    }
    private fun describeItem(item: Map<String, Any?>, action: Action): String {
        if (action.type == "update_task_occurrence") {
            val original = action.args["occurrenceDate"] as String
            val exception = optionalMap(optionalMap(item, "recurrence"), "exceptions")[original]?.let(::stringMap) ?: emptyMap()
            val override = optionalMap(exception, "override")
            return listOf("Occurrence $original", override["date"] as? String ?: original, override["startTime"] as? String ?: item["startTime"] as? String ?: "Flexible", override["endTime"] as? String ?: item["endTime"] as? String ?: "", exception["status"] as? String ?: "pending").filter { it.isNotEmpty() }.joinToString(" · ")
        }
        val parts = mutableListOf(item["title"] as? String ?: "")
        (item["date"] as? String)?.let(parts::add)
        integer(item["day"])?.let { parts += "Weekly weekday $it" }
        parts += listOfNotNull(item["startTime"] as? String ?: item["start"] as? String, item["endTime"] as? String ?: item["end"] as? String).joinToString("–")
        if (action.type.contains("reminder")) parts += integer(item["reminderMinutes"])?.let { "Reminder: $it min before" } ?: "No task reminder"
        if (action.type == "complete_task" || "completed" in action.args) parts += if (item["completed"] == true) "Completed" else "Pending"
        if (!action.type.startsWith("create_") && (action.type.contains("board") || action.type.contains("section"))) {
            val sections = if ("sections" in item) records(item["sections"]) else listOf(item)
            parts += "${sections.sumOf { records(it["tasks"]).size }} tasks"
        }
        return parts.filter { it.isNotEmpty() }.joinToString(" · ")
    }

    private fun identities(plan: Map<String, Any?>) {
        fun collection(map: Map<String, Any?>, key: String) {
            require(!map.containsKey(key) || map[key] is List<*>) { "Unreadable planner collection: $key." }
        }
        collection(plan, "boards"); collection(plan, "scheduleEvents")
        for (board in records(plan["boards"])) {
            collection(board, "sections")
            for (section in records(board["sections"])) collection(section, "tasks")
        }
        for (kind in listOf("board", "section", "task", "schedule_event")) {
            val ids = entities(kind, plan).map { it["id"] as? String ?: invalid("A planner item has no identity.") }
            require(ids.all { it.isNotBlank() } && ids.distinct().size == ids.size) { "The planner contains missing or duplicate identities." }
        }
    }

    // iOS v1 reviewState projection. Opaque web metadata stays in raw records, outside JSON hashing.
    private fun fingerprint(plan: Map<String, Any?>): String {
        fun pick(map: Map<String, Any?>, fields: Set<String>) = map.filterKeys { it in fields }
        val boards = records(plan["boards"]).map { b -> pick(b, setOf("id", "title", "archived", "boardDescription")) + ("sections" to records(b["sections"]).map { s ->
            pick(s, setOf("id", "title", "archived")) + ("tasks" to records(s["tasks"]).map { t ->
                val selected = pick(t, setOf("id", "title", "archived", "completed", "date", "startTime", "endTime", "dueTime", "dueDate", "taskDescription", "notes", "priority", "estimatedMinutes", "lastScheduledMinutes", "parentTaskID", "checklist", "reminderMinutes", "scheduleLocked", "schedulingPreference")).toMutableMap()
                if (t.containsKey("recurrence") && t["recurrence"] != null) {
                    val rule = stringMap(t["recurrence"])
                    val projected = pick(rule, setOf("enabled", "preset", "frequency", "interval", "weekdays", "monthlyMode", "monthDay", "weekdayPosition", "weekday", "month", "startDate", "endType", "endDate", "count", "mode")).toMutableMap()
                    projected["exceptions"] = optionalMap(rule, "exceptions").mapValues { (_, raw) ->
                        val exception = stringMap(raw)
                        val chosen = pick(exception, setOf("status", "completedAt", "skippedAt", "focusLoggedSeconds", "remainingMinutes")).toMutableMap()
                        if (exception["override"] != null) chosen["override"] = pick(stringMap(exception["override"]), setOf("date", "startTime", "endTime", "durationMinutes", "title", "notes", "estimatedMinutes", "checklist", "reminderMinutes"))
                        chosen
                    }
                    selected["recurrence"] = projected
                }
                selected
            })
        }) }
        val events = records(plan["scheduleEvents"]).map { event ->
            val selected = pick(event, setOf("id", "title", "category", "date", "day", "start", "end", "archived", "readOnly", "calendarSourceId", "transparent", "allDay", "startDate", "endDate", "intervalWeeks", "anchorDate")).toMutableMap()
            selected["exceptions"] = optionalMap(event, "exceptions").mapValues { (_, raw) ->
                val exception = stringMap(raw)
                val chosen = pick(exception, setOf("skipped")).toMutableMap()
                if (exception["override"] != null) chosen["override"] = pick(stringMap(exception["override"]), setOf("date", "title", "start", "end", "category", "allDay", "transparent"))
                chosen
            }
            selected
        }
        return digest(mapOf("boards" to boards, "scheduleEvents" to events))
    }

    private fun digest(value: Any?): String = MessageDigest.getInstance("SHA-256").digest(canonical(value).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun canonical(value: Any?): String = when (value) {
        null -> "null"
        is String -> JsonPrimitive(value).toString()
        is Boolean -> value.toString()
        is Number -> {
            require(value.toDouble().isFinite()) { "Cannot fingerprint a non-finite value." }
            BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
        }
        is Map<*, *> -> stringMap(value).toSortedMap().entries.joinToString(",", "{", "}") { canonical(it.key) + ":" + canonical(it.value) }
        is List<*> -> value.joinToString(",", "[", "]") { canonical(it) }
        else -> invalid("This preview contains data that cannot be fingerprinted safely.")
    }
    private fun rejectDuplicateKeys(json: String) {
        val frames = mutableListOf<MutableSet<String>?>()
        var index = 0
        while (index < json.length) {
            when (json[index]) {
                '{' -> frames.add(mutableSetOf())
                '[' -> frames.add(null)
                '}', ']' -> if (frames.isNotEmpty()) frames.removeAt(frames.lastIndex)
                '"' -> {
                    val start = index++
                    while (index < json.length && json[index] != '"') { if (json[index] == '\\') index++; index++ }
                    val end = ++index
                    while (index < json.length && json[index].isWhitespace()) index++
                    if (index < json.length && json[index] == ':') {
                        val key = Json.parseToJsonElement(json.substring(start, end)).jsonPrimitive.content
                        require(frames.lastOrNull()?.add(key) == true) { "Duplicate JSON field: $key." }
                    }
                    continue
                }
            }
            index++
        }
    }
    private fun number(value: Any?): Double? = (value as? Number)?.toDouble()?.takeIf { it.isFinite() }
    private fun decode(element: JsonElement): Any? = when (element) {
        is JsonObject -> element.mapValues { decode(it.value) }
        is JsonArray -> element.map(::decode)
        JsonNull -> null
        is JsonPrimitive -> if (element.isString) element.content else element.booleanOrNull ?: element.longOrNull ?: element.doubleOrNull ?: invalid("Invalid JSON value.")
    }
    private fun stringMap(value: Any?): Map<String, Any?> {
        require(value is Map<*, *> && value.keys.all { it is String }) { "This planner contains unreadable data. Review it on the web." }
        @Suppress("UNCHECKED_CAST")
        return value as Map<String, Any?>
    }
    private fun records(value: Any?): List<Map<String, Any?>> = if (value == null) emptyList() else (value as? List<*>)?.map(::stringMap) ?: invalid("This planner contains an unreadable collection.")
    private fun optionalMap(map: Map<String, Any?>, key: String): Map<String, Any?> = if (map[key] == null) emptyMap() else stringMap(map[key])
    private fun copyMap(map: Map<String, Any?>): Map<String, Any?> = map.mapValues { (_, value) -> copy(value) }
    private fun copy(value: Any?): Any? = when (value) { is Map<*, *> -> copyMap(stringMap(value)); is List<*> -> value.map(::copy); else -> value }
    private fun integer(value: Any?): Long? = when (value) {
        is Byte, is Short, is Int, is Long -> (value as Number).toLong()
        is Double, is Float -> (value as Number).toDouble().takeIf { it.isFinite() && it >= Long.MIN_VALUE.toDouble() && it < Long.MAX_VALUE.toDouble() && it % 1.0 == 0.0 }?.toLong()
        else -> null
    }
    private fun date(value: String): LocalDate {
        require(value.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) { "Dates must use YYYY-MM-DD." }
        return LocalDate.parse(value).also { require(it.year in 1..9999 && it.toString() == value) { "Invalid date." } }
    }
    private fun minutes(value: String): Int {
        require(value.matches(Regex("([01]\\d|2[0-3]):[0-5]\\d"))) { "Times must use HH:MM." }
        return value.take(2).toInt() * 60 + value.takeLast(2).toInt()
    }
    private fun clock(value: Int): String = String.format(Locale.ROOT, "%02d:%02d", Math.floorMod(value, 1440) / 60, Math.floorMod(value, 1440) % 60)
    private fun duration(task: Map<String, Any?>): Int {
        val start = task["startTime"] as? String
        val end = task["endTime"] as? String
        if (start != null && end != null) return Math.floorMod(minutes(end) - minutes(start), 1440).takeIf { it > 0 } ?: 1440
        return (integer(task["estimatedMinutes"]) ?: 60L).coerceIn(1, 1440).toInt()
    }
    private fun eventDuration(event: Map<String, Any?>): Int = Math.floorMod(minutes(event["end"] as? String ?: invalid("Missing event end.")) - minutes(event["start"] as? String ?: invalid("Missing event start.")), 1440)
    private fun invalid(message: String): Nothing = throw IllegalArgumentException(message)
}

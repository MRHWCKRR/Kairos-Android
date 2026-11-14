package com.kairos.app.ui.screens.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kairos.app.data.models.ChatMessage
import com.kairos.app.data.repository.AiActions
import com.kairos.app.data.repository.AiWorkspace
import com.kairos.app.ui.components.KairosOutlinedButton
import com.kairos.app.ui.components.KairosOverflowMenu
import com.kairos.app.ui.components.KairosTextButton
import com.kairos.app.ui.icons.KairosIcons
import java.util.UUID

data class AiWorkspaceUiState(
    val workspace: AiWorkspace.Snapshot? = null,
    val activeChatId: String? = null,
    val activeProjectId: String? = null,
    val showingAllChats: Boolean = false,
    val draft: String = "",
    val pendingChatIds: Set<String> = emptySet(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val workspaceError: String? = null,
    val error: String? = null,
    val replyError: String? = null,
    val proposal: AiActions.Proposal? = null,
    val proposals: List<AiActions.Proposal> = emptyList(),
    val canUndo: Boolean = false,
    val retainedChanges: Boolean = false
)

data class AiWorkspaceUiActions(
    val newChat: (String) -> Unit,
    val selectChat: (String) -> Unit,
    val selectProject: (String) -> Unit,
    val allChats: () -> Unit,
    val edit: (AiWorkspace.Command) -> Unit,
    val draft: (String) -> Unit,
    val send: () -> Unit,
    val retry: () -> Unit,
    val reload: () -> Unit,
    val createPlan: () -> Unit,
    val apply: () -> Unit,
    val discard: () -> Unit,
    val undo: () -> Unit
)

/** Authentication and writes stay in the coordinator; only sheet/editor presentation is local. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiWorkspaceScreen(state: AiWorkspaceUiState, actions: AiWorkspaceUiActions, modifier: Modifier = Modifier) {
    var history by remember { mutableStateOf(false) }
    var editingProject by remember { mutableStateOf<AiWorkspace.Project?>(null) }
    var creatingProject by remember { mutableStateOf(false) }
    var renamingChat by remember { mutableStateOf<AiWorkspace.Chat?>(null) }
    var deletingChat by remember { mutableStateOf<AiWorkspace.Chat?>(null) }
    var movingChat by remember { mutableStateOf<AiWorkspace.Chat?>(null) }
    var reviewing by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val chats = state.workspace?.chats.orEmpty()
    val projects = state.workspace?.projects.orEmpty()
    val chat = chats.firstOrNull { it.id == state.activeChatId }
    val project = projects.firstOrNull { it.id == state.activeProjectId }
    val editable = state.workspace != null && !state.loading && state.workspaceError == null && !state.saving && !state.retainedChanges
    LaunchedEffect(state.workspace == null, state.loading) {
        if (state.workspace == null) {
            history = false; editingProject = null; creatingProject = false
            renamingChat = null; deletingChat = null; movingChat = null; reviewing = false
        }
    }
    LaunchedEffect(state.proposal?.id) { reviewing = false }

    Surface(modifier.fillMaxSize().testTag("ai-workspace")) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { focus.clearFocus(); history = true }, enabled = state.workspace != null) {
                    Icon(KairosIcons.symbol("sidebar.left"), "Chats and projects")
                }
                Text("Kairos AI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = { actions.newChat("") }, enabled = editable) {
                    Icon(KairosIcons.symbol("square.and.pencil"), "New chat")
                }
                KairosOverflowMenu("Chat actions") { dismiss ->
                    DropdownMenuItem(
                        text = { Text("Create plan from chat") },
                        enabled = editable && chat?.messages?.isNotEmpty() == true && chat.id !in state.pendingChatIds && !chat.hasUnfinishedWebActions,
                        onClick = { dismiss(); actions.createPlan() }
                    )
                }
            }
            when {
                state.loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text("Loading workspace…")
                    }
                }
                state.workspaceError != null || state.workspace == null -> Box(Modifier.weight(1f).fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(KairosIcons.symbol("exclamationmark.triangle"), null)
                        Text("Workspace unavailable", style = MaterialTheme.typography.titleLarge)
                        Text(state.workspaceError ?: "Your AI workspace could not be loaded.")
                        KairosTextButton(onClick = actions.reload, enabled = !state.saving) { Text("Reload workspace") }
                    }
                }
                state.showingAllChats -> WorkspaceChatList(chats, projects, state, actions, Modifier.weight(1f))
                project != null -> ProjectHome(project, chats, projects, state, actions, { editingProject = project }, Modifier.weight(1f))
                else -> WorkspaceConversation(chat, state, actions, { reviewing = true }, Modifier.weight(1f))
            }
            if (!state.loading && state.workspace != null && (state.showingAllChats || project != null)) {
                WorkspaceFeedback(state, actions)
            }
        }
    }

    if (history) ModalBottomSheet(onDismissRequest = { history = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        val uri = LocalUriHandler.current
        Column(Modifier.fillMaxWidth().testTag("ai-history")) {
            SheetHeading("Chats and projects", "Done", { history = false })
            LazyColumn(Modifier.weight(1f, fill = false), contentPadding = PaddingValues(bottom = 12.dp)) {
                item { WorkspaceLink("New chat", "square.and.pencil", editable) { history = false; actions.newChat("") } }
                item { WorkspaceLink("All chats", "bubble.left.and.bubble.right") { history = false; actions.allChats() } }
                item { WorkspaceSection("Projects") }
                items(projects, key = { "project-${it.id}" }) { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WorkspaceLink(item.title, "folder", modifier = Modifier.weight(1f)) { history = false; actions.selectProject(item.id) }
                        KairosOverflowMenu("Project actions: ${item.title}") { dismiss ->
                            DropdownMenuItem(text = { Text("Edit project") }, enabled = editable, onClick = { dismiss(); editingProject = item })
                        }
                    }
                }
                item { WorkspaceLink("New project", "folder.badge.plus", editable) { creatingProject = true } }
                item { WorkspaceSection("Chats") }
                if (chats.isEmpty()) item { Text("No chats yet.", Modifier.padding(horizontal = 20.dp, vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(chats, key = { "chat-${it.id}" }) { item ->
                    WorkspaceChatRow(item, projects, state, { history = false; actions.selectChat(item.id) }) {
                        KairosOverflowMenu("Chat actions: ${item.title}") { dismiss ->
                            DropdownMenuItem(text = { Text("Rename") }, enabled = editable, onClick = { dismiss(); renamingChat = item })
                            DropdownMenuItem(text = { Text("Move to project") }, enabled = editable, onClick = { dismiss(); movingChat = item })
                            DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, enabled = editable, onClick = { dismiss(); deletingChat = item })
                        }
                    }
                }
                val orphaned = state.proposals.filter { saved -> chats.none { it.id == saved.chatId } }
                if (orphaned.isNotEmpty()) {
                    item { WorkspaceSection("Previews from deleted chats") }
                    items(orphaned, key = { "proposal-${it.id}" }) { saved ->
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                            Text(saved.rows.firstOrNull()?.title ?: "Saved changes")
                            Text("This conversation was removed on another device.", style = MaterialTheme.typography.bodySmall)
                            KairosTextButton(onClick = { history = false; actions.selectChat(saved.chatId) }) { Text("Review saved preview") }
                        }
                    }
                }
                state.error?.let { message -> item { WorkspaceError(message) } }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                Text("AI workspace", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                KairosTextButton(onClick = { uri.openUri("https://kairos-web.kirosapp.workers.dev/plans.html") }) { Text("View plans") }
            }
        }
    }
    if (creatingProject || editingProject != null) ProjectEditor(editingProject, state, actions) { creatingProject = false; editingProject = null }
    renamingChat?.let { RenameChatEditor(it, state, actions) { renamingChat = null } }
    deletingChat?.let { target ->
        var submitted by remember(target.id) { mutableStateOf(false) }
        LaunchedEffect(chats, state.saving, state.error) { if (submitted && !state.saving && state.error == null && chats.none { it.id == target.id }) deletingChat = null }
        AlertDialog(
            onDismissRequest = { if (!state.saving) deletingChat = null },
            title = { Text("Delete this chat?") },
            text = { Column { Text("This also removes the conversation from your web workspace."); state.error?.let { WorkspaceError(it) }; WorkspaceSaveRecovery(state, actions) } },
            confirmButton = { KairosTextButton(onClick = { submitted = true; actions.edit(AiWorkspace.Command.DeleteChat(target.id)) }, enabled = editable) { Text("Delete chat", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { KairosTextButton(onClick = { deletingChat = null }, enabled = !state.saving) { Text("Cancel") } }
        )
    }
    movingChat?.let { target ->
        var destination by remember(target.id) { mutableStateOf<String?>(null) }
        LaunchedEffect(chats, state.saving, state.error) { if (destination != null && !state.saving && state.error == null && chats.firstOrNull { it.id == target.id }?.projectId == destination) movingChat = null }
        AlertDialog(
            onDismissRequest = { if (!state.saving) movingChat = null },
            title = { Text("Move to project") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    (listOf("" to "No project") + projects.map { it.id to it.title }).forEach { (id, title) ->
                        KairosTextButton(onClick = { destination = id; actions.edit(AiWorkspace.Command.MoveChat(target.id, id)) }, enabled = editable) { Text(title) }
                    }
                    state.error?.let { WorkspaceError(it) }
                    WorkspaceSaveRecovery(state, actions)
                }
            },
            confirmButton = { KairosTextButton(onClick = { movingChat = null }, enabled = !state.saving) { Text("Cancel") } }
        )
    }
    if (reviewing) state.proposal?.let { saved -> ProposalReview(saved, state, actions) { reviewing = false } }
}

@Composable
private fun WorkspaceConversation(chat: AiWorkspace.Chat?, state: AiWorkspaceUiState, actions: AiWorkspaceUiActions, review: () -> Unit, modifier: Modifier) {
    val list = rememberLazyListState()
    val messages = chat?.messages.orEmpty()
    val pending = state.activeChatId in state.pendingChatIds
    LaunchedEffect(chat?.id, messages.size, pending) {
        if (messages.isNotEmpty() || pending) list.animateScrollToItem(messages.size + if (pending) 1 else 0)
    }
    Column(modifier.fillMaxWidth()) {
        if (chat?.hasUnfinishedWebActions == true) Text("Finish or cancel this chat’s pending change on the web before continuing here.", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
        if (messages.isNotEmpty()) Text(chat?.title.orEmpty(), Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth().testTag("ai-conversation"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            if (messages.isEmpty()) item { ChatEmptyState() }
            items(messages, key = { it.id }) { ChatBubble(ChatMessage(it.role, it.content, it.timestamp)) }
            if (pending) item { TypingIndicator() }
            item { Spacer(Modifier.height(1.dp)) }
        }
        state.replyError?.let {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(it, Modifier.weight(1f), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                KairosTextButton(onClick = actions.retry, enabled = !pending && !state.saving) { Text("Retry reply") }
            }
        }
        WorkspaceFeedback(state, actions)
        state.proposal?.let { proposal ->
            KairosOutlinedButton(onClick = review, enabled = !state.saving, modifier = Modifier.padding(horizontal = 14.dp).fillMaxWidth()) {
                Text("Review ${proposal.rows.size} proposed change${if (proposal.rows.size == 1) "" else "s"}")
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp).testTag("ai-composer")) {
            ChatInputBar(state.draft, actions.draft, actions.send, pending || state.saving || state.replyError != null || state.retainedChanges || state.proposal != null || chat?.hasUnfinishedWebActions == true)
        }
    }
}

@Composable
private fun WorkspaceFeedback(state: AiWorkspaceUiState, actions: AiWorkspaceUiActions) {
    if (state.saving) LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Saving workspace" })
    state.error?.let { message ->
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
            WorkspaceError(message)
        }
    }
    WorkspaceSaveRecovery(state, actions)
    if (state.canUndo) KairosTextButton(onClick = actions.undo, enabled = !state.saving && !state.retainedChanges, modifier = Modifier.padding(horizontal = 8.dp)) {
        Icon(KairosIcons.symbol("arrow.counterclockwise"), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Undo last changes")
    }
}

@Composable
private fun WorkspaceSaveRecovery(state: AiWorkspaceUiState, actions: AiWorkspaceUiActions) {
    if (state.retainedChanges) Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
        Text("Unsaved changes are retained on this device.", style = MaterialTheme.typography.bodySmall)
        Row {
            KairosTextButton(onClick = actions.retry, enabled = !state.saving) { Text("Retry save") }
            KairosTextButton(onClick = actions.reload, enabled = !state.saving) { Text("Reload saved workspace") }
        }
    } else if (state.error != null) {
        KairosTextButton(onClick = actions.reload, enabled = !state.saving, modifier = Modifier.padding(horizontal = 14.dp)) { Text("Reload saved workspace") }
    }
}

@Composable
private fun WorkspaceChatList(chats: List<AiWorkspace.Chat>, projects: List<AiWorkspace.Project>, state: AiWorkspaceUiState, actions: AiWorkspaceUiActions, modifier: Modifier) {
    LazyColumn(modifier.fillMaxWidth().testTag("ai-all-chats")) {
        item { WorkspaceSection("All chats") }
        if (chats.isEmpty()) item { Text("No chats yet. Start a new conversation.", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(chats, key = { it.id }) { chat -> WorkspaceChatRow(chat, projects, state, { actions.selectChat(chat.id) }, preview = true) }
    }
}

@Composable
private fun ProjectHome(project: AiWorkspace.Project, chats: List<AiWorkspace.Chat>, projects: List<AiWorkspace.Project>, state: AiWorkspaceUiState, actions: AiWorkspaceUiActions, edit: () -> Unit, modifier: Modifier) {
    LazyColumn(modifier.fillMaxWidth().testTag("ai-project-home"), contentPadding = PaddingValues(bottom = 20.dp)) {
        item {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(project.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                if (project.description.isNotEmpty()) Text(project.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                KairosTextButton(onClick = edit, enabled = !state.saving && !state.retainedChanges) { Text("Edit project") }
            }
        }
        item { WorkspaceSection("Shared context") }
        item { Text(project.sharedContext.ifEmpty { "Add project context from Chats and projects." }, Modifier.padding(horizontal = 20.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyMedium) }
        item { WorkspaceSection("Chats") }
        item { WorkspaceLink("New chat in project", "square.and.pencil", !state.saving && !state.retainedChanges) { actions.newChat(project.id) } }
        val filtered = chats.filter { it.projectId == project.id }
        if (filtered.isEmpty()) item { Text("No chats in this project yet.", Modifier.padding(horizontal = 20.dp, vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(filtered, key = { it.id }) { chat -> WorkspaceChatRow(chat, projects, state, { actions.selectChat(chat.id) }) }
    }
}

@Composable
private fun WorkspaceChatRow(chat: AiWorkspace.Chat, projects: List<AiWorkspace.Project>, state: AiWorkspaceUiState, select: () -> Unit, preview: Boolean = false, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).clickable(onClick = select).padding(horizontal = 20.dp, vertical = 12.dp).testTag("ai-chat-${chat.id}"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(chat.title, style = if (preview) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge)
            val subtitle = if (preview) chat.messages.lastOrNull()?.content ?: "New conversation" else projects.firstOrNull { it.id == chat.projectId }?.title
            if (!subtitle.isNullOrEmpty()) Text(subtitle, maxLines = 2, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (chat.id in state.pendingChatIds) CircularProgressIndicator(Modifier.padding(end = 12.dp).size(16.dp).semantics { contentDescription = "Reply pending: ${chat.title}" }, strokeWidth = 2.dp)
        else if (chat.id == state.activeChatId) Icon(KairosIcons.Check, "Selected chat", Modifier.padding(end = 12.dp).size(18.dp), tint = MaterialTheme.colorScheme.secondary)
        trailing()
    }
}

@Composable
private fun WorkspaceLink(label: String, icon: String, enabled: Boolean = true, modifier: Modifier = Modifier, click: () -> Unit) {
    Row(modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(enabled = enabled, onClick = click).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(KairosIcons.symbol(icon), null, Modifier.size(22.dp), tint = if (enabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WorkspaceSection(title: String) {
    Text(title, Modifier.fillMaxWidth().padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 4.dp).semantics { heading() }, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun WorkspaceError(message: String) { Text(message, Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

@Composable
private fun SheetHeading(title: String, closeLabel: String, close: () -> Unit, enabled: Boolean = true, save: (() -> Unit)? = null, saveEnabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f).padding(start = 8.dp).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        KairosTextButton(onClick = close, enabled = enabled) { Text(closeLabel) }
        save?.let { KairosTextButton(onClick = it, enabled = enabled && saveEnabled) { Text("Save") } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectEditor(project: AiWorkspace.Project?, state: AiWorkspaceUiState, actions: AiWorkspaceUiActions, close: () -> Unit) {
    val id = remember(project?.id) { project?.id ?: "project-${UUID.randomUUID()}" }
    var title by remember(id) { mutableStateOf(project?.title.orEmpty()) }
    var description by remember(id) { mutableStateOf(project?.description.orEmpty()) }
    var context by remember(id) { mutableStateOf(project?.sharedContext.orEmpty()) }
    var submitted by remember(id) { mutableStateOf<AiWorkspace.Command?>(null) }
    var confirmingDelete by remember(id) { mutableStateOf(false) }
    LaunchedEffect(state.workspace, state.saving, state.error) {
        val command = submitted
        if (command != null && !state.saving && state.error == null) {
            val saved = state.workspace?.projects?.firstOrNull { it.id == id }
            if (command is AiWorkspace.Command.DeleteProject && state.workspace != null && saved == null || command is AiWorkspace.Command.SaveProject && saved?.title == command.title.trim() && saved.description == command.description && saved.sharedContext == command.context) close()
        }
    }
    ModalBottomSheet(onDismissRequest = { if (!state.saving) close() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().testTag("ai-project-editor")) {
            SheetHeading(if (project == null) "New project" else "Edit project", "Cancel", close, !state.saving, save = {
                val command = AiWorkspace.Command.SaveProject(id, title.trim(), description, context, project == null)
                submitted = command; actions.edit(command)
            }, saveEnabled = title.isNotBlank() && !state.retainedChanges)
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth().testTag("ai-project-title"), enabled = !state.saving && !state.retainedChanges, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth().testTag("ai-project-description"), enabled = !state.saving && !state.retainedChanges, label = { Text("Description") }, minLines = 2, maxLines = 5)
                OutlinedTextField(context, { context = it }, Modifier.fillMaxWidth().testTag("ai-project-context"), enabled = !state.saving && !state.retainedChanges, label = { Text("Shared context") }, minLines = 5)
                Text("Used across chats in this project on mobile and web.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.error?.let { WorkspaceError(it) }
                WorkspaceSaveRecovery(state, actions)
                if (state.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (project != null) KairosTextButton(onClick = { confirmingDelete = true }, enabled = !state.saving && !state.retainedChanges) { Text("Delete project", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (confirmingDelete) AlertDialog(
        onDismissRequest = { if (!state.saving) confirmingDelete = false },
        title = { Text("Delete project?") },
        text = { Text("Chats will be kept in All chats.") },
        confirmButton = { KairosTextButton(onClick = { confirmingDelete = false; val command = AiWorkspace.Command.DeleteProject(id); submitted = command; actions.edit(command) }, enabled = !state.saving) { Text("Delete project", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { KairosTextButton(onClick = { confirmingDelete = false }, enabled = !state.saving) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RenameChatEditor(chat: AiWorkspace.Chat, state: AiWorkspaceUiState, actions: AiWorkspaceUiActions, close: () -> Unit) {
    var title by remember(chat.id) { mutableStateOf(chat.title) }
    var submitted by remember(chat.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(state.workspace, state.saving, state.error) {
        if (submitted != null && !state.saving && state.error == null && state.workspace?.chats?.firstOrNull { it.id == chat.id }?.title == submitted?.trim()) close()
    }
    ModalBottomSheet(onDismissRequest = { if (!state.saving) close() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().padding(bottom = 20.dp)) {
            SheetHeading("Rename chat", "Cancel", close, !state.saving, save = { submitted = title; actions.edit(AiWorkspace.Command.RenameChat(chat.id, title)) }, saveEnabled = title.isNotBlank() && !state.retainedChanges)
            OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth().padding(20.dp).testTag("ai-chat-name"), enabled = !state.saving && !state.retainedChanges, label = { Text("Name") }, singleLine = true)
            state.error?.let { WorkspaceError(it) }
            WorkspaceSaveRecovery(state, actions)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProposalReview(proposal: AiActions.Proposal, state: AiWorkspaceUiState, actions: AiWorkspaceUiActions, close: () -> Unit) {
    ModalBottomSheet(onDismissRequest = { if (!state.saving) close() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().testTag("ai-proposal-review")) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Review changes", Modifier.weight(1f).padding(start = 8.dp).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                KairosTextButton(onClick = close, enabled = !state.saving) { Text("Close") }
                KairosTextButton(onClick = actions.apply, enabled = !state.saving && !state.retainedChanges) { Text("Apply") }
            }
            LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { Text("Proposed changes", style = MaterialTheme.typography.titleSmall) }
                items(proposal.rows, key = { it.id }) { row ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(row.action, style = MaterialTheme.typography.titleSmall)
                        Text(row.title)
                        Text("Before", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(row.before)
                        Text("After", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(row.after)
                        if (row.destructive) Text("This removes the item or selected occurrence from your planner.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        HorizontalDivider(Modifier.padding(top = 10.dp))
                    }
                }
                item { Text("This preview is saved to your account. If the planner changes, request a fresh preview before applying.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                state.error?.let { message -> item { WorkspaceError(message) } }
                item { WorkspaceSaveRecovery(state, actions) }
                item { KairosTextButton(onClick = actions.discard, enabled = !state.saving) { Text("Discard changes", color = MaterialTheme.colorScheme.error) } }
                if (state.saving) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
        }
    }
}

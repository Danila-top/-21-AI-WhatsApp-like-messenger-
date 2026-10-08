package com.danilatop.aimessenger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.danilatop.aimessenger.AIMessengerViewModel
import com.danilatop.aimessenger.ai.DefaultAgents
import com.danilatop.aimessenger.data.ActivityLogEntity
import com.danilatop.aimessenger.data.MessageEntity
import com.danilatop.aimessenger.data.ToolApprovalEntity
import com.danilatop.aimessenger.data.WorkspaceFileEntity
import com.danilatop.aimessenger.data.ScheduledTaskEntity

private enum class Panel(val title: String) {
    CHAT("Chat"),
    ACTIVITY("Activity"),
    FILES("Files"),
    MEMORY("Memory"),
    TOOLS("Tools"),
    TASKS("Tasks"),
    APPROVALS("Approvals"),
    MEMBERS("Members")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatWorkspaceScreen(
    conversationId: String,
    vm: AIMessengerViewModel,
    onBack: () -> Unit
) {
    val conversation = vm.conversationById(conversationId)
    var selectedPanel by remember { mutableStateOf(Panel.CHAT) }
    var input by remember { mutableStateOf("") }
    var agentId by remember { mutableStateOf("coordinator") }

    val messages by vm.messages(conversationId).collectAsState()
    val activity by vm.activityForConversation(conversationId).collectAsState()
    val files by vm.files(conversationId).collectAsState()
    val tasks by vm.tasksForConversation(conversationId).collectAsState()
    val approvals by vm.approvalsForConversation(conversationId).collectAsState()
    val memories by vm.memories.collectAsState()
    val streaming by vm.streamingText.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            conversation?.title ?: "AI Workspace",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "@" + agentId +
                                if (conversation?.autonomous == true) " · AUTONOMOUS" else "",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            agentId = if (agentId == "coordinator") "deepseek"
                            else if (agentId == "deepseek") "claude"
                            else "coordinator"
                        }
                    ) {
                        Icon(Icons.Default.Groups, contentDescription = "Сменить агента")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedPanel.ordinal,
                edgePadding = 8.dp
            ) {
                Panel.entries.forEach { panel ->
                    Tab(
                        selected = selectedPanel == panel,
                        onClick = { selectedPanel = panel },
                        text = {
                            Text(
                                when (panel) {
                                    Panel.APPROVALS -> {
                                        if (approvals.any { it.status == "PENDING" }) {
                                            "Approvals*"
                                        } else panel.title
                                    }
                                    else -> panel.title
                                }
                            )
                        }
                    )
                }
            }

            when (selectedPanel) {
                Panel.CHAT -> ChatPanel(
                    conversationId = conversationId,
                    vm = vm,
                    messages = messages,
                    streaming = streaming,
                    input = input,
                    onInput = { input = it },
                    agentId = agentId,
                    autonomous = conversation?.autonomous == true,
                    onSend = {
                        vm.send(conversationId, input, agentId)
                        input = ""
                    },
                    onStream = {
                        vm.sendStreaming(conversationId, input, agentId)
                        input = ""
                    },
                    onTeam = { input = "/team " },
                    onRemember = { input = "/remember " },
                    onCalc = { input = "/calc " },
                    onToggleAutonomous = {
                        vm.setAutonomous(
                            conversationId,
                            !(conversation?.autonomous ?: false)
                        )
                    }
                )
                Panel.ACTIVITY -> ActivityPanel(activity)
                Panel.FILES -> FilesPanel(files)
                Panel.MEMORY -> MemoryPanel(memories)
                Panel.TOOLS -> ToolsPanel(vm, conversationId)
                Panel.TASKS -> TasksPanel(tasks)
                Panel.APPROVALS -> ApprovalsPanel(
                    approvals = approvals,
                    onApprove = { vm.approveTool(conversationId, it) },
                    onDeny = { vm.denyTool(conversationId, it) }
                )
                Panel.MEMBERS -> MembersPanel(conversation?.participantAgentIds.orEmpty())
            }
        }
    }
}

@Composable
private fun ChatPanel(
    conversationId: String,
    vm: AIMessengerViewModel,
    messages: List<MessageEntity>,
    streaming: String,
    input: String,
    onInput: (String) -> Unit,
    agentId: String,
    autonomous: Boolean,
    onSend: () -> Unit,
    onStream: () -> Unit,
    onTeam: () -> Unit,
    onRemember: () -> Unit,
    onCalc: () -> Unit,
    onToggleAutonomous: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth().padding(12.dp)) {
                        Column(Modifier.padding(18.dp)) {
                            Text("AI Workspace", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Обычный чат, /team для команды, native tool-calling " +
                                    "для инструментов и MCP для внешнего мира."
                            )
                        }
                    }
                }
            }
            items(messages, key = { it.id }) { MessageBubble(message = it) }
            if (streaming.isNotBlank()) {
                item {
                    Surface(
                        tonalElevation = 4.dp,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(0.94f).padding(horizontal = 12.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "streaming…",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(streaming)
                        }
                    }
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = onTeam, label = { Text("TEAM") })
                AssistChip(onClick = onCalc, label = { Text("CALC") })
                AssistChip(onClick = onRemember, label = { Text("MEMORY") })
                AssistChip(
                    onClick = onToggleAutonomous,
                    label = { Text(if (autonomous) "AUTO ON" else "AUTO") }
                )
                AssistChip(
                    onClick = onStream,
                    label = { Text("STREAM") }
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = onInput,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Сообщение агенту…") },
                    maxLines = 6
                )
                IconButton(onClick = onSend) {
                    Icon(Icons.Default.Send, contentDescription = "Отправить")
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: MessageEntity) {
    val human = message.senderType == "human"
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = if (human) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            tonalElevation = if (human) 2.dp else 5.dp,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    if (human) "Вы" else message.senderId,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(message.content)
            }
        }
    }
}

@Composable
private fun ActivityPanel(items: List<ActivityLogEntity>) {
    LazyColumn(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items, key = { it.id }) { item ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(item.actor + " · " + item.action, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(item.details)
                }
            }
        }
    }
}

@Composable
private fun FilesPanel(files: List<WorkspaceFileEntity>) {
    LazyColumn(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(files, key = { it.id }) { file ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(file.name, fontWeight = FontWeight.SemiBold)
                    Text(file.mimeType, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(file.content.take(800))
                }
            }
        }
        if (files.isEmpty()) {
            item { Text("Workspace пока пуст.") }
        }
    }
}

@Composable
private fun MemoryPanel(memories: List<com.danilatop.aimessenger.data.MemoryEntity>) {
    LazyColumn(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(memories, key = { it.id }) { memory ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(memory.key, fontWeight = FontWeight.SemiBold)
                    Text(memory.value)
                    Text(
                        "importance=" + memory.importance,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        if (memories.isEmpty()) {
            item { Text("Память пуста.") }
        }
    }
}

@Composable
private fun ToolsPanel(vm: AIMessengerViewModel, conversationId: String) {
    val servers by vm.mcpServers.collectAsState()
    val permissions by vm.toolPermissions(conversationId).collectAsState()

    val localTools = listOf(
        "calculator",
        "remember",
        "workspace_write",
        "schedule_task",
        "request_external_action"
    )

    fun defaultMode(tool: String): String =
        if (tool == "request_external_action") "CONFIRM" else "AUTO"

    fun mode(tool: String): String =
        permissions.firstOrNull { it.toolName == tool }?.mode ?: defaultMode(tool)

    fun nextMode(current: String): String =
        when (current) {
            "AUTO" -> "CONFIRM"
            "CONFIRM" -> "DENY"
            else -> "AUTO"
        }

    LazyColumn(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { Text("Local tools", fontWeight = FontWeight.Bold) }

        items(localTools) { tool ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(tool, fontWeight = FontWeight.SemiBold)
                        Text(
                            when (tool) {
                                "calculator" -> "Локальная арифметика"
                                "remember" -> "Долговременная память"
                                "workspace_write" -> "Запись файла в workspace"
                                "schedule_task" -> "Постановка фоновой AI-задачи"
                                else -> "Внешнее действие с подтверждением"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    AssistChip(
                        onClick = {
                            vm.setToolPermission(
                                conversationId,
                                tool,
                                nextMode(mode(tool))
                            )
                        },
                        label = { Text(mode(tool)) }
                    )
                }
            }
        }

        item {
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))
            Text("MCP connections", fontWeight = FontWeight.Bold)
        }

        items(servers, key = { it.id }) { server ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(server.name, fontWeight = FontWeight.SemiBold)
                            Text(server.endpoint, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = server.enabled,
                            onCheckedChange = {
                                vm.setMcpServerEnabled(server.id, it)
                            }
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Обнаруженные MCP tools требуют CONFIRM по умолчанию.",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        item {
            Text(
                "Политика инструментов хранится отдельно для этой комнаты.",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun TasksPanel(tasks: List<ScheduledTaskEntity>) {
    LazyColumn(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(tasks, key = { it.id }) { task ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(task.title, fontWeight = FontWeight.SemiBold)
                    Text(task.prompt)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "nextRunAt=" + task.nextRunAt +
                            " · enabled=" + task.enabled,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        if (tasks.isEmpty()) item { Text("Запланированных задач нет.") }
    }
}

@Composable
private fun ApprovalsPanel(
    approvals: List<ToolApprovalEntity>,
    onApprove: (String) -> Unit,
    onDeny: (String) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (approvals.isEmpty()) {
            item { Text("Нет ожидающих approvals.") }
        }
        items(approvals, key = { it.id }) { approval ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(approval.toolName, fontWeight = FontWeight.SemiBold)
                    Text("status=" + approval.status)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        approval.arguments,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = { onApprove(approval.id) },
                            enabled = approval.status == "PENDING"
                        ) {
                            Text("Одобрить")
                        }
                        FilledTonalButton(
                            onClick = { onDeny(approval.id) },
                            enabled = approval.status == "PENDING"
                        ) {
                            Text("Отклонить")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MembersPanel(participants: String) {
    val ids = participants.split(",").map { it.trim() }.filter { it.isNotBlank() }

    LazyColumn(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("Участники комнаты", fontWeight = FontWeight.Bold)
        }
        items(ids) { id ->
            val name = when (id) {
                "coordinator" -> DefaultAgents.coordinator.name
                "deepseek" -> DefaultAgents.deepseek.name
                "claude" -> DefaultAgents.claude.name
                "gemini" -> DefaultAgents.gemini.name
                else -> id
            }
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Groups, contentDescription = null)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(name, fontWeight = FontWeight.SemiBold)
                        Text("@" + id, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

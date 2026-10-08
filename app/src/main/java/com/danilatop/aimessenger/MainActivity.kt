package com.danilatop.aimessenger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.danilatop.aimessenger.data.MessageEntity

class MainActivity : ComponentActivity() {
    private val vm by viewModels<AIMessengerViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MessengerApp(vm) }
    }
}

@Composable
fun MessengerApp(vm: AIMessengerViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.ensureSeed() }
    var screen by remember { mutableStateOf("home") }
    var selected by remember { mutableStateOf<String?>(null) }

    MaterialTheme {
        when {
            screen == "settings" -> SettingsScreen(vm) { screen = "home" }
            selected != null -> ChatScreen(selected!!, vm) { selected = null }
            else -> HomeScreen(vm, { selected = it }) { screen = "settings" }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    vm: AIMessengerViewModel,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit
) {
    val conversations by vm.conversations.collectAsState()
    var createOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("AI Messenger", fontWeight = FontWeight.Bold)
                        Text(
                            conversations.size.toString() + " рабочих чатов",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {}) { Icon(Icons.Default.Menu, contentDescription = null) }
                },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Настройки")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { createOpen = true }) {
                Icon(Icons.Default.Add, contentDescription = "Новый чат")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(conversations, key = { it.id }) { c ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                    onClick = { onOpen(c.id) }
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            Modifier.size(46.dp),
                            shape = MaterialTheme.shapes.large,
                            tonalElevation = 2.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Groups, contentDescription = null)
                            }
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(c.title, fontWeight = FontWeight.SemiBold)
                            Text(
                                c.participantAgentIds.split(",").joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }

    if (createOpen) {
        NewChatDialog(
            onDismiss = { createOpen = false },
            onCreate = { title, participants ->
                vm.newChat(title.ifBlank { "Новый AI-чат" }, participants)
                createOpen = false
            }
        )
    }
}

@Composable
private fun NewChatDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var participants by remember { mutableStateOf("coordinator") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый AI-чат") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = participants,
                    onValueChange = { participants = it },
                    label = { Text("Агенты через запятую") },
                    supportingText = {
                        Text("coordinator, deepseek, claude, gemini")
                    }
                )
                Text(
                    "Self-chat, команда агентов или отдельная исследовательская комната."
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(title, participants) }) { Text("Создать") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(
    conversationId: String,
    vm: AIMessengerViewModel,
    onBack: () -> Unit
) {
    val messages by vm.messages(conversationId).collectAsState()
    val streaming by vm.streamingText.collectAsState()
    val conversation = vm.conversationById(conversationId)
    var input by remember { mutableStateOf("") }
    var agent by remember { mutableStateOf("coordinator") }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            conversation?.title ?: "AI Chat",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "@" + agent,
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
                            agent = if (agent == "coordinator") "deepseek" else "coordinator"
                        }
                    ) {
                        Icon(Icons.Default.Groups, contentDescription = "Сменить агента")
                    }
                }
            )
        },
        bottomBar = {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(
                        onClick = { input = "/team " },
                        label = { Text("TEAM") }
                    )
                    AssistChip(
                        onClick = { input = "/calc " },
                        label = { Text("CALC") }
                    )
                    AssistChip(
                        onClick = { input = "/remember " },
                        label = { Text("MEMORY") }
                    )
                    AssistChip(
                        onClick = {
                            vm.setAutonomous(
                                conversationId,
                                !(conversation?.autonomous ?: false)
                            )
                        },
                        label = {
                            Text(if (conversation?.autonomous == true) "AUTO ON" else "AUTO")
                        }
                    )
                    AssistChip(
                        onClick = {
                            if (input.isNotBlank()) {
                                vm.sendStreaming(conversationId, input, agent)
                                input = ""
                            }
                        },
                        label = { Text("STREAM") }
                    )
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("Задача для агента…") },
                        maxLines = 6
                    )
                    IconButton(
                        onClick = {
                            vm.send(conversationId, input, agent)
                            input = ""
                        }
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Отправить")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth().padding(12.dp)) {
                        Column(Modifier.padding(18.dp)) {
                            Text(
                                "Рабочее пространство AI",
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "TEAM — несколько агентов + синтезатор. " +
                                    "MEMORY — долговременная память. " +
                                    "CALC — безопасный калькулятор."
                            )
                        }
                    }
                }
            }
            items(messages, key = { it.id }) { MessageBubble(it) }
            if (streaming.isNotBlank()) {
                item {
                    Surface(
                        tonalElevation = 5.dp,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(0.90f).padding(horizontal = 12.dp)
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
            modifier = Modifier.fillMaxWidth(0.90f)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    vm: AIMessengerViewModel,
    onBack: () -> Unit
) {
    var openAi by remember { mutableStateOf("") }
    var deepSeek by remember { mutableStateOf("") }
    var claude by remember { mutableStateOf("") }
    var gemini by remember { mutableStateOf("") }
    var mcpName by remember { mutableStateOf("") }
    var mcpEndpoint by remember { mutableStateOf("") }
    var mcpToken by remember { mutableStateOf("") }
    var memoryKey by remember { mutableStateOf("") }
    var memoryValue by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Провайдеры",
                    Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.titleLarge
                )
            }
            item {
                ProviderField(
                    "OpenAI API key",
                    openAi,
                    { openAi = it }
                ) {
                    vm.setApiKey("OpenAI", openAi)
                    openAi = ""
                }
            }
            item {
                ProviderField(
                    "DeepSeek API key",
                    deepSeek,
                    { deepSeek = it }
                ) {
                    vm.setApiKey("DeepSeek", deepSeek)
                    deepSeek = ""
                }
            }
            item {
                ProviderField(
                    "Anthropic API key",
                    claude,
                    { claude = it }
                ) {
                    vm.setApiKey("Claude", claude)
                    claude = ""
                }
            }
            item {
                ProviderField(
                    "Gemini API key",
                    gemini,
                    { gemini = it }
                ) {
                    vm.setApiKey("Gemini", gemini)
                    gemini = ""
                }
            }
            item { HorizontalDivider() }
            item {
                Text(
                    "MCP-серверы",
                    Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.titleLarge
                )
            }
            item {
                OutlinedTextField(
                    mcpName,
                    { mcpName = it },
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    label = { Text("Название сервера") },
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    mcpEndpoint,
                    { mcpEndpoint = it },
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    label = { Text("Streamable HTTP endpoint") },
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    mcpToken,
                    { mcpToken = it },
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    label = { Text("Bearer token (необязательно)") },
                    singleLine = true
                )
            }
            item {
                FilledTonalButton(
                    onClick = {
                        vm.connectMcpServer(mcpName, mcpEndpoint, mcpToken)
                        mcpName = ""
                        mcpEndpoint = ""
                        mcpToken = ""
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text("Подключить MCP")
                }
            }
            val mcpServers by vm.mcpServers.collectAsState()
            items(mcpServers, key = { it.id }) { server ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(server.name, fontWeight = FontWeight.SemiBold)
                        Text(server.endpoint, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = server.enabled,
                        onCheckedChange = { vm.setMcpServerEnabled(server.id, it) }
                    )
                }
            }
            item { HorizontalDivider() }
            item {
                Text(
                    "Долгосрочная память",
                    Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.titleLarge
                )
            }
            item {
                OutlinedTextField(
                    value = memoryKey,
                    onValueChange = { memoryKey = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    label = { Text("Ключ") }
                )
            }
            item {
                OutlinedTextField(
                    value = memoryValue,
                    onValueChange = { memoryValue = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    label = { Text("Значение") }
                )
            }
            item {
                FilledTonalButton(
                    onClick = {
                        vm.addMemory(memoryKey, memoryValue)
                        memoryKey = ""
                        memoryValue = ""
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Icon(Icons.Default.Memory, contentDescription = null)
                    Text("  Сохранить")
                }
            }
            item {
                Text(
                    "Ключи хранятся зашифрованными через Android Keystore и не должны попадать в Git.",
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun ProviderField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    save: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            singleLine = true
        )
        TextButton(
            onClick = save,
            modifier = Modifier.padding(start = 16.dp)
        ) {
            Text("Сохранить ключ")
        }
    }
}

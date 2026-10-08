package com.danilatop.aimessenger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.danilatop.aimessenger.AIMessengerViewModel
import com.danilatop.aimessenger.data.AgentProfileEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentProfilesScreen(
    vm: AIMessengerViewModel,
    onBack: () -> Unit
) {
    val profiles by vm.agentProfiles.collectAsStateCompat()
    var editing by remember { mutableStateOf<AgentProfileEntity?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Agent Profiles") },
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(profiles, key = { it.id }) { profile ->
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text(profile.name, fontWeight = FontWeight.Bold)
                                Text("@" + profile.id)
                                Text(profile.provider + " · " + profile.model)
                            }
                            Switch(
                                checked = profile.enabled,
                                onCheckedChange = {
                                    vm.setAgentProfileEnabled(profile.id, it)
                                }
                            )
                        }
                        Text(profile.baseUrl, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                        Button(onClick = { editing = profile }) {
                            Text("Редактировать")
                        }
                    }
                }
            }
        }
    }

    editing?.let { profile ->
        AgentEditor(
            profile = profile,
            onDismiss = { editing = null },
            onSave = {
                vm.saveAgentProfile(it)
                editing = null
            }
        )
    }
}

@Composable
private fun AgentEditor(
    profile: AgentProfileEntity,
    onDismiss: () -> Unit,
    onSave: (AgentProfileEntity) -> Unit
) {
    var name by remember(profile.id) { mutableStateOf(profile.name) }
    var model by remember(profile.id) { mutableStateOf(profile.model) }
    var baseUrl by remember(profile.id) { mutableStateOf(profile.baseUrl) }
    var prompt by remember(profile.id) { mutableStateOf(profile.systemPrompt) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agent: " + profile.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Имя") })
                OutlinedTextField(model, { model = it }, label = { Text("Модель") })
                OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("Endpoint") })
                OutlinedTextField(prompt, { prompt = it }, label = { Text("System prompt") })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        profile.copy(
                            name = name.trim().ifBlank { profile.name },
                            model = model.trim().ifBlank { profile.model },
                            baseUrl = baseUrl.trim().ifBlank { profile.baseUrl },
                            systemPrompt = prompt.trim().ifBlank { profile.systemPrompt }
                        )
                    )
                }
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectAsStateCompat(): androidx.compose.runtime.State<T> =
    androidx.compose.runtime.collectAsState()

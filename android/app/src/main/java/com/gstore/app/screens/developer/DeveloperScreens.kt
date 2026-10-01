package com.gstore.app.screens.developer

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Publish Game: o developer preenche nome/descrição/categoria/versão/notes,
 * seleciona o APK, ícone e screenshots — e o app envia tudo para a API,
 * que publica no GitHub Releases e registra no Neon.
 *
 * Para o developer: "Publicar" -> "Enviando APK..." -> "Processando..." -> "Publicado".
 * Nenhum contato manual com o GitHub é necessário.
 */
@Composable
fun PublishGameScreen(
    viewModel: DeveloperViewModel,
    onPublished: (String) -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var version by remember { mutableStateOf("1.0.0") }
    var versionCode by remember { mutableStateOf("1") }
    var releaseNotes by remember { mutableStateOf("") }
    var apkUri by remember { mutableStateOf<Uri?>(null) }

    val apkPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { apkUri = it }
    val publishState by viewModel.publishState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text("Publicar jogo", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nome do jogo") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Descrição") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = category,
            onValueChange = { category = it },
            label = { Text("Categoria (Arcade, Puzzle, Corrida...)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Row {
            OutlinedTextField(
                value = version,
                onValueChange = { version = it },
                label = { Text("Versão") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            OutlinedTextField(
                value = versionCode,
                onValueChange = { versionCode = it },
                label = { Text("Version code") },
                singleLine = true,
                modifier = Modifier.width(140.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = releaseNotes,
            onValueChange = { releaseNotes = it },
            label = { Text("Release notes") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = { apkPicker.launch("*/*") },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            Text(apkUri?.let { "APK selecionado ✓" } ?: "Selecionar arquivo APK")
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                viewModel.publish(
                    context = context,
                    name = name.trim(),
                    description = description.trim(),
                    category = category.trim(),
                    version = version.trim(),
                    versionCode = versionCode.toIntOrNull(),
                    releaseNotes = releaseNotes.trim(),
                    apkUri = apkUri,
                )
            },
            enabled = apkUri != null && name.isNotBlank() && publishState !is PublishState.Busy,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            when (val s = publishState) {
                is PublishState.Uploading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Enviando APK... ${s.progress}%")
                    LinearProgressIndicator(
                        progress = { s.progress / 100f },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                    )
                }
                is PublishState.Processing -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.height(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Processando...")
                }
                else -> Text("Publicar", style = MaterialTheme.typography.titleMedium)
            }
        }

        when (val s = publishState) {
            is PublishState.Error -> {
                Spacer(Modifier.height(12.dp))
                Text(
                    s.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            is PublishState.Done -> {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Publicado! O jogo já está disponível na G Store.",
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            else -> Unit
        }
    }
}

/** Lista de jogos do developer (dashboard). */
@Composable
fun DashboardScreen(
    viewModel: DeveloperViewModel,
    onOpenVersions: (String, String) -> Unit,
    onOpenEdit: (String) -> Unit,
    onOpenPublish: () -> Unit,
) {
    val myGames by viewModel.myGames.collectAsState()
    val loading by viewModel.loading.collectAsState()

    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.loadMyGames() }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Developer Dashboard",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        )
        Button(
            onClick = onOpenPublish,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(52.dp),
        ) {
            Text("Publicar novo jogo")
        }
        Spacer(Modifier.height(16.dp))
        if (loading) {
            LazyColumn {
                items(3) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        com.gstore.app.ui.components.ShimmerBox(
                            modifier = Modifier.fillMaxWidth().height(64.dp),
                        )
                    }
                }
            }
        } else if (myGames.isEmpty()) {
            com.gstore.app.ui.components.EmptyState(
                title = "Nenhum jogo publicado ainda",
                subtitle = "Publique seu primeiro jogo: nome, APK e release notes.",
            )
        } else {
            LazyColumn {
                items(myGames, key = { it.id }) { game ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(game.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "v${game.version ?: "—"} • ${game.status} • ${game.downloads} downloads",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                androidx.compose.material3.TextButton(onClick = { onOpenVersions(game.id, game.name) }) {
                                    Text("Versões")
                                }
                                androidx.compose.material3.TextButton(onClick = { onOpenEdit(game.id) }) {
                                    Text("Editar")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

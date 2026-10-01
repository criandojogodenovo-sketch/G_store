package com.gstore.app.screens.developer

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gstore.app.ui.components.EmptyState
import com.gstore.app.ui.components.MetaChip
import com.gstore.app.ui.components.formatDownloads

/** Versões de um jogo (admin) com estatísticas e formulário de nova versão. */
@Composable
fun GameVersionsScreen(
    gameId: String,
    gameName: String,
    viewModel: DeveloperViewModel,
    onBack: () -> Unit,
) {
    LaunchedEffect(gameId) { viewModel.loadVersions(gameId) }
    val versions by viewModel.versions.collectAsState()
    val stats by viewModel.stats.collectAsState()

    // Formulário de nova versão
    var showForm by remember { mutableStateOf(false) }
    var version by remember { mutableStateOf("") }
    var versionCode by remember { mutableStateOf("") }
    var releaseNotes by remember { mutableStateOf("") }
    var apkUrl by remember { mutableStateOf("") }
    var erro by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
            }
            Column {
                Text(gameName, style = MaterialTheme.typography.titleLarge)
                stats?.let {
                    Text(
                        "${formatDownloads(it.total)} downloads • ${it.last7Days} nos últimos 7 dias • ${it.last30Days} nos últimos 30 dias",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Button(
            onClick = { showForm = !showForm },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(48.dp),
        ) {
            Text(if (showForm) "Fechar" else "Adicionar versão")
        }

        if (showForm) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row {
                        OutlinedTextField(
                            value = version,
                            onValueChange = { version = it },
                            label = { Text("Versão (ex.: 1.1.0)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(10.dp))
                        OutlinedTextField(
                            value = versionCode,
                            onValueChange = { versionCode = it },
                            label = { Text("Version code") },
                            singleLine = true,
                            modifier = Modifier.width(120.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = releaseNotes,
                        onValueChange = { releaseNotes = it },
                        label = { Text("Release notes") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = apkUrl,
                        onValueChange = { apkUrl = it },
                        label = { Text("Link do APK no GitHub Releases") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    erro?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            viewModel.addVersion(
                                gameId = gameId,
                                version = version.trim(),
                                versionCode = versionCode.toLongOrNull(),
                                releaseNotes = releaseNotes.trim(),
                                apkUrl = apkUrl.trim(),
                            ) { err ->
                                if (err == null) {
                                    showForm = false
                                    version = ""; versionCode = ""; releaseNotes = ""; apkUrl = ""
                                    erro = null
                                } else {
                                    erro = err
                                }
                            }
                        },
                        enabled = version.isNotBlank() && apkUrl.isNotBlank(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                    ) {
                        Text("Guardar versão")
                    }
                }
            }
        }

        if (versions.isEmpty()) {
            EmptyState(
                title = "Nenhuma versão publicada",
                subtitle = "Adicione a primeira versão com o link do APK no GitHub Releases.",
            )
        } else {
            LazyColumn {
                items(versions, key = { it.id }) { v ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MetaChip("v${v.version}")
                                Spacer(Modifier.padding(4.dp))
                                v.versionCode?.let { MetaChip("code $it") }
                                Spacer(Modifier.padding(4.dp))
                                v.apkSizeBytes?.let {
                                    Text(
                                        "%.1f MB".format(it / 1024f / 1024f),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                v.releaseNotes ?: "Sem release notes",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                v.apkUrl,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                            )
                            v.releaseTag?.let {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "GitHub release: $it",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

package com.gstore.app.screens.developer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gstore.app.ui.components.EmptyState
import com.gstore.app.ui.components.MetaChip
import com.gstore.app.ui.components.formatDownloads

/** Histórico de versões de um jogo do developer, com estatísticas. */
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
                        "${formatDownloads(it.counter)} downloads • ${it.last7Days} nos últimos 7 dias",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (versions.isEmpty()) {
            EmptyState(
                title = "Nenhuma versão publicada",
                subtitle = "Publique uma nova versão com o APK pelo botão Publicar.",
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

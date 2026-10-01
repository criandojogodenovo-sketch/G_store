package com.gstore.app.screens.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gstore.app.ui.components.EmptyState

/**
 * Biblioteca / Downloads: lista os APKs baixados via G Store com progresso,
 * sucesso e estados vazios. (Feedback nativo do DownloadManager.)
 */
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    appContext: android.content.Context,
) {
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.observe(appContext) }
    val items by viewModel.items.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Biblioteca",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        )
        if (items.isEmpty()) {
            EmptyState(
                title = "Sua biblioteca está vazia",
                subtitle = "Os jogos que você baixar aparecem aqui, com o progresso em tempo real.",
            )
        } else {
            LazyColumn {
                items(items, key = { it.downloadId }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            when (item.status) {
                                android.app.DownloadManager.STATUS_SUCCESSFUL ->
                                    Text(
                                        "Concluído — pronto para instalar",
                                        color = MaterialTheme.colorScheme.tertiary,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                android.app.DownloadManager.STATUS_FAILED ->
                                    Text("Falhou", color = MaterialTheme.colorScheme.error)
                                else -> {
                                    LinearProgressIndicator(
                                        progress = { item.progress / 100f },
                                        modifier = Modifier.fillMaxWidth().height(6.dp),
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "Baixando... ${item.progress}%",
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
}

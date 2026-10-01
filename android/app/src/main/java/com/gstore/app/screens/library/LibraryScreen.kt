package com.gstore.app.screens.library

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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.gstore.app.ui.components.GameListItem

/**
 * Biblioteca: jogos baixados (com progresso em tempo real) e favoritos
 * do utilizador (conta Neon).
 */
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    appContext: android.content.Context,
    onOpenGame: (com.gstore.app.data.repo.GameDto) -> Unit = {},
) {
    LaunchedEffect(Unit) { viewModel.observe(appContext) }
    val state by viewModel.state.collectAsState()
    var aba by remember { mutableStateOf("baixados") }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Biblioteca",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        )
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = aba == "baixados",
                onClick = { aba = "baixados" },
                label = { Text("Baixados") },
            )
            Spacer(Modifier.padding(4.dp))
            FilterChip(
                selected = aba == "favoritos",
                onClick = { aba = "favoritos" },
                label = { Text("Favoritos") },
            )
        }

        if (aba == "baixados") {
            if (state.downloads.isEmpty()) {
                EmptyState(
                    title = "Nenhum download ainda",
                    subtitle = "Os jogos que você baixar aparecem aqui, com o progresso em tempo real.",
                )
            } else {
                LazyColumn {
                    items(state.downloads, key = { it.downloadId }) { item ->
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
        } else {
            // ── Favoritos (conta) ──────────────────────────────
            if (!state.loggedIn) {
                EmptyState(
                    title = "Favoritos precisam de conta",
                    subtitle = "Entre na sua conta para guardar jogos favoritos na nuvem.",
                )
            } else if (state.loadingFavorites) {
                Text(
                    "Carregando favoritos...",
                    modifier = Modifier.padding(20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (state.favorites.isEmpty()) {
                EmptyState(
                    title = "Nenhum favorito ainda",
                    subtitle = "Toque no coração na página de um jogo para o guardar aqui.",
                )
            } else {
                LazyColumn {
                    items(state.favorites, key = { it.id }) { game ->
                        GameListItem(
                            game = game,
                            onClick = { onOpenGame(game) },
                            trailing = {
                                IconButton(onClick = { viewModel.removeFavorite(game.id) }) {
                                    Icon(
                                        Icons.Filled.Favorite,
                                        contentDescription = "Remover dos favoritos",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

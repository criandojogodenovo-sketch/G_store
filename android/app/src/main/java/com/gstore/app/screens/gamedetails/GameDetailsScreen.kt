package com.gstore.app.screens.gamedetails

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.gstore.app.ui.components.ErrorState
import com.gstore.app.ui.components.GameIconPlaceholder
import com.gstore.app.ui.components.MetaChip
import com.gstore.app.ui.components.formatDownloads

/**
 * Tela de detalhes do jogo: ícone, título, developer, versão, screenshots,
 * descrição, downloads e o botão Download com feedback visual completo
 * (preparando -> progresso -> sucesso/erro -> tentar novamente).
 */
@Composable
fun GameDetailsScreen(
    viewModel: GameDetailsViewModel,
    onBack: () -> Unit,
) {
    val game by viewModel.game.collectAsState()
    val versions by viewModel.versions.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

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
            Text("Detalhes", style = MaterialTheme.typography.titleLarge)
        }

        when {
            loading -> DetailsSkeleton()
            error != null -> ErrorState(message = error ?: "", onRetry = { viewModel.load() })
            game != null -> {
                val g = game!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            AsyncImage(
                                model = g.iconUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(g.name, style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                g.developer ?: "Desenvolvedor",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "v${g.version ?: "—"} • ${formatDownloads(g.downloads)} downloads",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        g.categories.take(3).forEach { MetaChip(it) }
                    }

                    Spacer(Modifier.height(20.dp))
                    DownloadButton(downloadState) { viewModel.startDownload() }

                    if (g.screenshots.isNotEmpty()) {
                        Spacer(Modifier.height(20.dp))
                        Text("Capturas de tela", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(g.screenshots) { shot ->
                                AsyncImage(
                                    model = shot,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .width(220.dp)
                                        .height(130.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                )
                            }
                        }
                    }

                    if (g.description != null) {
                        Spacer(Modifier.height(20.dp))
                        Text("Sobre este jogo", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            g.description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (versions.isNotEmpty()) {
                        Spacer(Modifier.height(20.dp))
                        Text("Versões", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        versions.forEach { v ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                MetaChip("v${v.version}")
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        v.releaseNotes ?: "Release notes não informadas",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                    )
                                    v.apkSizeBytes?.let {
                                        Text(
                                            "${"%.1f".format(it / 1024f / 1024f)} MB",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun DownloadButton(state: DownloadState, onDownload: () -> Unit) {
    Button(
        onClick = onDownload,
        enabled = state is DownloadState.Idle || state is DownloadState.Failed,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        when (state) {
            is DownloadState.Idle -> Text("Baixar", style = MaterialTheme.typography.titleMedium)
            is DownloadState.Preparing -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Preparando download...")
            }
            is DownloadState.Downloading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Baixando... ${state.progress}%")
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { state.progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                )
            }
            is DownloadState.Success -> Text("Download concluído ✓", style = MaterialTheme.typography.titleMedium)
            is DownloadState.Failed -> Text("Falhou — tentar novamente", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun DetailsSkeleton() {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.gstore.app.ui.components.ShimmerBox(modifier = Modifier.size(88.dp), cornerRadius = 18)
            Spacer(Modifier.width(16.dp))
            Column {
                com.gstore.app.ui.components.ShimmerBox(modifier = Modifier.width(180.dp).height(24.dp))
                Spacer(Modifier.height(8.dp))
                com.gstore.app.ui.components.ShimmerBox(modifier = Modifier.width(120.dp).height(14.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
        com.gstore.app.ui.components.ShimmerBox(modifier = Modifier.fillMaxWidth().height(52.dp), cornerRadius = 14)
        Spacer(Modifier.height(20.dp))
        com.gstore.app.ui.components.ShimmerBox(modifier = Modifier.fillMaxWidth().height(130.dp))
        Spacer(Modifier.height(16.dp))
        com.gstore.app.ui.components.ShimmerBox(modifier = Modifier.fillMaxWidth().height(80.dp))
    }
}

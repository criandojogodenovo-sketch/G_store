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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.gstore.app.data.repo.ReviewDto
import com.gstore.app.ui.components.ErrorState
import com.gstore.app.ui.components.MetaChip
import com.gstore.app.ui.components.formatDownloads

/**
 * Tela de detalhes do jogo: ícone, título, developer, versão, tamanho,
 * categoria, screenshots, descrição, notas e comentários + botão Baixar
 * (GitHub Releases) e favoritos.
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
    val favorite by viewModel.favorite.collectAsState()
    val reviews by viewModel.reviews.collectAsState()
    val myReview by viewModel.myReview.collectAsState()
    val loggedIn by viewModel.loggedIn.collectAsState()
    val averageRating by viewModel.averageRating.collectAsState()

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
                                buildString {
                                    append("v${g.version ?: "—"}")
                                    append(" • ${formatDownloads(g.downloads)} downloads")
                                    viewModel.latestVersion()?.apkSizeBytes?.let {
                                        append(" • ${"%.1f".format(it / 1024f / 1024f)} MB")
                                    }
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = viewModel::toggleFavorite) {
                            Icon(
                                if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = if (favorite) "Remover dos favoritos" else "Adicionar aos favoritos",
                                tint = if (favorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        averageRating?.let {
                            MetaChip("★ %.1f".format(it))
                        }
                        MetaChip("${reviews.size} avaliações")
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

                    // ── Avaliações e comentários ──────────────────────
                    Spacer(Modifier.height(24.dp))
                    Text("Avaliações e comentários", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    if (loggedIn) {
                        ReviewForm(myReview, onSubmit = { rating, comment, done ->
                            viewModel.submitReview(rating, comment, done)
                        })
                    } else {
                        Text(
                            "Entre na sua conta para avaliar este jogo.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    if (reviews.isEmpty()) {
                        Text(
                            "Ainda não há avaliações — seja o primeiro!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        reviews.forEach { r -> ReviewItem(r) }
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

/** Formulário de avaliação (1–5 estrelas + comentário). */
@Composable
private fun ReviewForm(
    myReview: MyReview?,
    onSubmit: (rating: Int, comment: String, onDone: (Boolean) -> Unit) -> Unit,
) {
    var rating by remember(myReview) { mutableIntStateOf(myReview?.rating ?: 0) }
    var comment by remember(myReview) { mutableStateOf(myReview?.comment ?: "") }
    var sending by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Sua nota: ", style = MaterialTheme.typography.bodyMedium)
            (1..5).forEach { estrela ->
                IconButton(
                    onClick = { rating = estrela },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = "$estrela estrelas",
                        tint = if (estrela <= rating) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = comment,
            onValueChange = { comment = it },
            label = { Text(if (myReview != null) "Editar comentário" else "Comentário (opcional)") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        feedback?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                if (rating < 1) {
                    feedback = "Escolha de 1 a 5 estrelas."
                    return@OutlinedButton
                }
                sending = true
                feedback = null
                onSubmit(rating, comment) { ok ->
                    sending = false
                    feedback = if (ok) null else "Não foi possível guardar a avaliação."
                }
            },
            enabled = !sending,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(46.dp),
        ) {
            if (sending) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            else Text(if (myReview != null) "Atualizar avaliação" else "Enviar avaliação")
        }
    }
}

@Composable
private fun ReviewItem(r: ReviewDto) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            repeat(r.rating) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                r.authorName ?: "Jogador",
                style = MaterialTheme.typography.titleSmall,
            )
        }
        if (!r.comment.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                r.comment,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

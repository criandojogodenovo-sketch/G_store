package com.gstore.app.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gstore.app.data.repo.GameDto
import com.gstore.app.ui.components.ErrorState
import com.gstore.app.ui.components.FeaturedGameCard
import com.gstore.app.ui.components.FeaturedSkeleton
import com.gstore.app.ui.components.GameCard
import com.gstore.app.ui.components.GameCardSkeleton
import com.gstore.app.ui.components.MetaChip

/**
 * Home da G Store: destaques, recentes, categorias e grid de jogos.
 * Skeleton loading, puxar-para-atualizar e fallback offline (cache).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenGame: (GameDto) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenCategory: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Cabeçalho com saudação e acesso à busca
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("G Store", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Sua loja de jogos Android",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                onClick = onOpenSearch,
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = "Pesquisar",
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { viewModel.refresh() },
        ) {
            when (val s = state) {
                is HomeUiState.Loading -> HomeSkeleton()
                is HomeUiState.Error -> ErrorState(message = s.message, onRetry = { viewModel.load() })
                is HomeUiState.Ready -> HomeContent(
                    state = s,
                    onOpenGame = onOpenGame,
                    onOpenCategory = onOpenCategory,
                )
            }
        }
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState.Ready,
    onOpenGame: (GameDto) -> Unit,
    onOpenCategory: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        if (state.fromCache) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.WifiOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Sem ligação — a mostrar o último catálogo guardado.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }

        if (state.featured.isNotEmpty()) {
            item {
                SectionTitle("Destaques")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.featured) { game ->
                        FeaturedGameCard(game = game, onClick = { onOpenGame(game) })
                    }
                }
            }
        }

        item {
            SectionTitle("Categorias")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.categories) { category ->
                    MetaChip(
                        text = "${category.name} (${category.gamesCount})",
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }

        item {
            SectionTitle("Recentes")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.recent, key = { it.id }) { game ->
                    GameCard(game = game, onClick = { onOpenGame(game) }, modifier = Modifier.width(150.dp))
                }
            }
        }

        item { SectionTitle("Todos os jogos") }

        // Grid embutido na LazyColumn (jogos limitados para performance)
        items(state.grid, key = { it.id }) { game ->
            Box(modifier = Modifier.padding(horizontal = 10.dp)) {
                GameListItemInline(game, onOpenGame)
            }
        }
    }
}

@Composable
private fun GameListItemInline(game: GameDto, onOpenGame: (GameDto) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        GameCard(game = game, onClick = { onOpenGame(game) })
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

@Composable
private fun HomeSkeleton() {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Text(
                "Carregando...",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(3) { FeaturedSkeleton() }
            }
        }
        items(4) {
            Row(modifier = Modifier.padding(horizontal = 12.dp)) {
                repeat(2) {
                    GameCardSkeleton(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

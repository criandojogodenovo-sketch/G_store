package com.gstore.app.screens.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Rocket
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.gstore.app.data.repo.CategoryDto
import com.gstore.app.data.repo.countLabel
import com.gstore.app.ui.components.ErrorState

@Composable
fun CategoriesScreen(
    viewModel: CategoriesViewModel,
    onOpenCategory: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Categorias",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        )
        when (val s = state) {
            is CategoriesUiState.Loading -> LazyColumn {
                items(6) {
                    CategoryRowSkeleton()
                }
            }
            is CategoriesUiState.Error -> ErrorState(message = s.message, onRetry = { viewModel.load() })
            is CategoriesUiState.Ready -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val jogos = s.categories.filter { it.type == "game" }
                val apps = s.categories.filter { it.type == "app" }
                if (jogos.isNotEmpty()) {
                    item(key = "hdr-jogos") { SectionHeader("Jogos") }
                    items(jogos, key = { it.id }) { category ->
                        CategoryCard(
                            name = category.name,
                            subtitle = category.description
                                ?: countLabel(category.gamesCount, "jogo", "jogos"),
                        ) { onOpenCategory(category.slug) }
                    }
                }
                if (apps.isNotEmpty()) {
                    item(key = "hdr-apps") { SectionHeader("Apps") }
                    items(apps, key = { it.id }) { category ->
                        CategoryCard(
                            name = category.name,
                            subtitle = category.description
                                ?: countLabel(category.gamesCount, "app", "apps"),
                        ) { onOpenCategory(category.slug) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
    )
}

@Composable
private fun CategoryCard(name: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageFor(name),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            androidx.compose.foundation.layout.Spacer(Modifier.size(14.dp))
            Column {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CategoryRowSkeleton() {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.gstore.app.ui.components.ShimmerBox(modifier = Modifier.size(28.dp), cornerRadius = 8)
            androidx.compose.foundation.layout.Spacer(Modifier.size(14.dp))
            Column {
                com.gstore.app.ui.components.ShimmerBox(modifier = Modifier.fillMaxWidth(0.4f).size(height = 16.dp, width = 100.dp))
                androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
                com.gstore.app.ui.components.ShimmerBox(modifier = Modifier.fillMaxWidth(0.6f).size(height = 12.dp, width = 140.dp))
            }
        }
    }
}

private fun imageFor(name: String): ImageVector = when (name.lowercase()) {
    "arcade" -> Icons.Filled.Rocket
    "puzzle" -> Icons.Filled.Extension
    "esportes" -> Icons.Filled.Star
    // categorias de APPS
    "ferramentas" -> Icons.Filled.Construction
    "produtividade" -> Icons.Filled.TaskAlt
    "social" -> Icons.Filled.People
    "educação" -> Icons.Filled.School
    "entretenimento" -> Icons.Filled.Movie
    "utilitários" -> Icons.Filled.Build
    "comunicação" -> Icons.Filled.Forum
    else -> Icons.Filled.SportsEsports
}

package com.gstore.app.screens.search

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gstore.app.ui.components.EmptyState
import com.gstore.app.ui.components.ErrorState
import com.gstore.app.ui.components.GameListItem
import com.gstore.app.ui.components.GameCardSkeleton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onOpenGame: (com.gstore.app.data.remote.GameDto) -> Unit,
    onBack: () -> Unit,
) {
    val query by viewModel.query.collectAsState()
    val state by viewModel.state.collectAsState()

    androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
            }
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text("Buscar jogos...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Limpar")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 16.dp),
            )
        }

        when (val s = state) {
            is SearchUiState.Idle -> EmptyState(
                title = "O que você quer jogar?",
                subtitle = "Digite o nome de um jogo, categoria ou desenvolvedor.",
            )
            is SearchUiState.Loading -> LazyColumn {
                items(4) { GameCardSkeleton(modifier = Modifier.fillMaxWidth()) }
            }
            is SearchUiState.Empty -> EmptyState(
                title = "Nada encontrado",
                subtitle = "Tente outra palavra-chave ou explore as categorias.",
            )
            is SearchUiState.Error -> ErrorState(message = s.message, onRetry = { viewModel.onQueryChange(query) })
            is SearchUiState.Results -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(s.games, key = { it.id }) { game ->
                    GameListItem(game = game, onClick = { onOpenGame(game) })
                }
            }
        }
    }
}

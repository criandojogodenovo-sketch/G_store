package com.gstore.app.screens.developer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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

/** Editar um app/jogo do catálogo (admin): tipo, nome, descrições, ícone, categoria, estado. */
@Composable
fun EditGameScreen(
    gameId: String,
    viewModel: DeveloperViewModel,
    onSaved: () -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(gameId) { viewModel.loadMyGames() }
    val myGames by viewModel.myGames.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val game = myGames.firstOrNull { it.id == gameId }

    var type by remember(game) { mutableStateOf(game?.type ?: "game") }
    var name by remember(game) { mutableStateOf(game?.name ?: "") }
    var description by remember(game) { mutableStateOf(game?.description ?: "") }
    var shortDescription by remember(game) { mutableStateOf(game?.shortDescription ?: "") }
    var iconUrl by remember(game) { mutableStateOf(game?.iconUrl ?: "") }
    var status by remember(game) { mutableStateOf(game?.status ?: "published") }
    var categoryId by remember(game) {
        mutableStateOf(categories.firstOrNull { c -> game?.categories?.contains(c.slug) == true }?.id ?: "")
    }
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
            Text("Editar item", style = MaterialTheme.typography.titleLarge)
        }

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text("Tipo", style = MaterialTheme.typography.labelMedium)
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                androidx.compose.material3.FilterChip(
                    selected = type == "game",
                    onClick = { type = "game"; categoryId = "" },
                    label = { Text("Jogo") },
                )
                Spacer(Modifier.padding(4.dp))
                androidx.compose.material3.FilterChip(
                    selected = type == "app",
                    onClick = { type = "app"; categoryId = "" },
                    label = { Text("App") },
                )
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome") },
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
                value = shortDescription,
                onValueChange = { shortDescription = it },
                label = { Text("Descrição curta") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = iconUrl,
                onValueChange = { iconUrl = it },
                label = { Text("URL do ícone") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))

            Text("Categoria", style = MaterialTheme.typography.labelMedium)
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                // Só categorias do tipo escolhido acima.
                CategorySelector(categories.filter { it.type == type }, categoryId) { categoryId = it }
            }

            Text("Estado", style = MaterialTheme.typography.labelMedium)
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                androidx.compose.material3.FilterChip(
                    selected = status == "published",
                    onClick = { status = "published" },
                    label = { Text("Publicado") },
                )
                Spacer(Modifier.padding(4.dp))
                androidx.compose.material3.FilterChip(
                    selected = status == "draft",
                    onClick = { status = "draft" },
                    label = { Text("Rascunho") },
                )
                androidx.compose.material3.FilterChip(
                    selected = status == "unpublished",
                    onClick = { status = "unpublished" },
                    label = { Text("Despublicado") },
                )
            }

            erro?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    viewModel.updateGame(
                        gameId = gameId,
                        name = name.trim(),
                        type = type,
                        description = description.trim(),
                        shortDescription = shortDescription.trim(),
                        iconUrl = iconUrl.trim(),
                        status = status,
                        categoryId = categoryId,
                    ) { err ->
                        if (err == null) onSaved() else erro = err
                    }
                },
                enabled = name.isNotBlank(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("Salvar alterações")
            }
        }
    }
}

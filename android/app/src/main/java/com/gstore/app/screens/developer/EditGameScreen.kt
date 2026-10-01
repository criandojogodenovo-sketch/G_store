package com.gstore.app.screens.developer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Editar informações de um jogo publicado (nome/descrição/categoria). */
@Composable
fun EditGameScreen(
    gameId: String,
    viewModel: DeveloperViewModel,
    onSaved: () -> Unit,
    onBack: () -> Unit,
) {
    // Busca o jogo atual na lista carregada do dashboard.
    LaunchedEffect(gameId) { viewModel.loadMyGames() }
    val myGames by viewModel.myGames.collectAsState()
    val game = myGames.firstOrNull { it.id == gameId }

    var name by remember(game) { mutableStateOf(game?.name ?: "") }
    var description by remember(game) { mutableStateOf(game?.description ?: "") }
    var category by remember(game) { mutableStateOf(game?.category ?: "") }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
            }
            Text("Editar jogo", style = MaterialTheme.typography.titleLarge)
        }

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
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
                value = category,
                onValueChange = { category = it },
                label = { Text("Categoria") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    viewModel.updateGame(gameId, name.trim(), description.trim(), category.trim()) { onSaved() }
                },
                enabled = name.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("Salvar alterações")
            }
        }
    }
}

package com.gstore.app.screens.developer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.gstore.app.data.repo.CategoryDto

/**
 * Publicar app ou jogo (admin): tipo, nome, descrições, categoria, ícone,
 * screenshots, versão, version_code e o LINK do APK no GitHub Releases.
 */
@Composable
fun PublishGameScreen(
    viewModel: DeveloperViewModel,
    onPublished: (String) -> Unit,
) {
    var type by remember { mutableStateOf("game") }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var shortDescription by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf("") }
    var iconUrl by remember { mutableStateOf("") }
    var screenshots by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("published") }
    var version by remember { mutableStateOf("1.0.0") }
    var versionCode by remember { mutableStateOf("1") }
    var releaseNotes by remember { mutableStateOf("") }
    var apkUrl by remember { mutableStateOf("") }
    var slug by remember { mutableStateOf("") }
    var slugEditado by remember { mutableStateOf(false) }

    val publishState by viewModel.publishState.collectAsState()
    val categories by viewModel.categories.collectAsState()

    LaunchedEffect(Unit) {
        if (categories.isEmpty()) viewModel.loadMyGames()
    }
    // Slug automático a partir do nome (editável).
    if (!slugEditado && name.isNotBlank() && slug.isBlank()) {
        slug = slugify(name)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text("Adicionar app ou jogo", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        Text("Tipo", style = MaterialTheme.typography.labelMedium)
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            androidx.compose.material3.FilterChip(
                selected = type == "game",
                onClick = { type = "game"; categoryId = "" },
                label = { Text("Jogo") },
            )
            Spacer(Modifier.width(8.dp))
            androidx.compose.material3.FilterChip(
                selected = type == "app",
                onClick = { type = "app"; categoryId = "" },
                label = { Text("App") },
            )
        }
        Spacer(Modifier.height(10.dp))

        AdminTextField(name, { name = it; if (!slugEditado) slug = slugify(it) }, "Nome do jogo ou app")
        AdminTextField(slug, { slug = it; slugEditado = true }, "Slug (identificador na URL)")
        AdminTextField(description, { description = it }, "Descrição", minLines = 3)
        AdminTextField(shortDescription, { shortDescription = it }, "Descrição curta", minLines = 1)

        Spacer(Modifier.height(10.dp))
        Text("Categoria", style = MaterialTheme.typography.labelMedium)
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            // Só faz sentido escolher categorias do tipo escolhido acima.
            CategorySelector(categories.filter { it.type == type }, categoryId) { categoryId = it }
        }

        AdminTextField(iconUrl, { iconUrl = it }, "URL do ícone (https://...)")
        AdminTextField(screenshots, { screenshots = it }, "Screenshots (URLs separadas por vírgula)", minLines = 2)

        Spacer(Modifier.height(10.dp))
        Text("Estado", style = MaterialTheme.typography.labelMedium)
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            androidx.compose.material3.FilterChip(
                selected = status == "published",
                onClick = { status = "published" },
                label = { Text("Publicado") },
            )
            Spacer(Modifier.width(8.dp))
            androidx.compose.material3.FilterChip(
                selected = status == "draft",
                onClick = { status = "draft" },
                label = { Text("Rascunho") },
            )
        }

        Spacer(Modifier.height(10.dp))
        Row {
            OutlinedTextField(
                value = version,
                onValueChange = { version = it },
                label = { Text("Versão") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            OutlinedTextField(
                value = versionCode,
                onValueChange = { versionCode = it },
                label = { Text("Version code") },
                singleLine = true,
                modifier = Modifier.width(140.dp),
            )
        }
        AdminTextField(releaseNotes, { releaseNotes = it }, "Release notes", minLines = 2)
        AdminTextField(apkUrl, { apkUrl = it }, "Link do APK no GitHub Releases (https://.../jogo.apk)")

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                viewModel.publish(
                    name = name.trim(),
                    slug = slug.trim(),
                    type = type,
                    description = description.trim(),
                    shortDescription = shortDescription.trim(),
                    categoryId = categoryId,
                    iconUrl = iconUrl.trim(),
                    screenshots = screenshots.split(',').map { it.trim() }.filter { it.isNotBlank() },
                    status = status,
                    version = version.trim(),
                    versionCode = versionCode.toLongOrNull(),
                    releaseNotes = releaseNotes.trim(),
                    apkUrl = apkUrl.trim(),
                )
            },
            enabled = name.isNotBlank() && slug.isNotBlank() &&
                (version.isBlank() || apkUrl.isNotBlank()) &&
                publishState !is PublishState.Saving,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            when (val s = publishState) {
                is PublishState.Saving -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.height(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(s.passo)
                }
                else -> Text("Guardar", style = MaterialTheme.typography.titleMedium)
            }
        }

        when (val s = publishState) {
            is PublishState.Error -> {
                Spacer(Modifier.height(12.dp))
                Text(s.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            is PublishState.Done -> {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Guardado! Já está disponível na G Store.",
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                LaunchedEffect(Unit) { onPublished("") }
            }
            else -> Unit
        }
    }
}

@Composable
private fun AdminTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        minLines = minLines,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    )
}

@Composable
fun CategorySelector(categories: List<CategoryDto>, selected: String, onSelect: (String) -> Unit) {
    val scroll = androidx.compose.foundation.rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll),
    ) {
        categories.forEach { c ->
            androidx.compose.material3.FilterChip(
                selected = selected == c.id,
                onClick = { onSelect(if (selected == c.id) "" else c.id) },
                label = { Text(c.name) },
            )
            Spacer(Modifier.width(6.dp))
        }
    }
}

/** Dashboard do administrador: todos os jogos (incl. rascunhos). */
@Composable
fun DashboardScreen(
    viewModel: DeveloperViewModel,
    onOpenVersions: (String, String) -> Unit,
    onOpenEdit: (String) -> Unit,
    onOpenPublish: () -> Unit,
) {
    val myGames by viewModel.myGames.collectAsState()
    val loading by viewModel.loading.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadMyGames() }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Área de administração",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        )
        Button(
            onClick = onOpenPublish,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(52.dp),
        ) {
            Text("Adicionar app ou jogo")
        }
        Spacer(Modifier.height(16.dp))
        if (loading) {
            LazyColumn {
                items(3) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        com.gstore.app.ui.components.ShimmerBox(
                            modifier = Modifier.fillMaxWidth().height(64.dp),
                        )
                    }
                }
            }
        } else if (myGames.isEmpty()) {
            com.gstore.app.ui.components.EmptyState(
                title = "Nada no catálogo ainda",
                subtitle = "Adicione o primeiro app ou jogo: nome, versão e o link do APK no GitHub Releases.",
            )
        } else {
            LazyColumn {
                items(myGames, key = { it.id }) { game ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(game.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${if (game.type == "app") "App" else "Jogo"} • v${game.version ?: "—"} • ${game.status} • " +
                                            com.gstore.app.data.repo.countLabel(game.downloads, "download", "downloads"),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                androidx.compose.material3.TextButton(onClick = { onOpenVersions(game.id, game.name) }) {
                                    Text("Versões")
                                }
                                androidx.compose.material3.TextButton(onClick = { onOpenEdit(game.id) }) {
                                    Text("Editar")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

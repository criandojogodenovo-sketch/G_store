package com.gstore.app.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gstore.app.data.remote.Role

/** Tela de Perfil: dados da conta, papel (role) e atalho ao dashboard. */
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onOpenDeveloperDashboard: () -> Unit,
    onOpenLogin: () -> Unit,
) {
    val profile by viewModel.profile.collectAsState()
    val loggedOut by viewModel.loggedOut.collectAsState()
    val busy by viewModel.busy.collectAsState()
    var renaming by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    androidx.compose.runtime.LaunchedEffect(loggedOut) {
        if (loggedOut) onOpenLogin()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text("Perfil", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    (profile?.displayName ?: profile?.email ?: "G").take(1).uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    profile?.displayName ?: "Visitante",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    profile?.email ?: "Entre para acessar sua conta",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        if (profile != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Papel na plataforma", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when (profile!!.role) {
                            Role.ADMIN -> "Administrador"
                            Role.DEVELOPER -> "Desenvolvedor"
                            Role.USER -> "Usuário"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (profile!!.role == Role.USER) {
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.becomeDeveloper() },
                            enabled = !busy,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (busy) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            else Text("Quero publicar jogos (virar developer)")
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            if (profile!!.role != Role.USER) {
                OutlinedButton(
                    onClick = onOpenDeveloperDashboard,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    Icon(Icons.Filled.Storefront, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Developer Dashboard")
                }
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { viewModel.logout() },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Sair da conta")
            }
        } else {
            Button(
                onClick = onOpenLogin,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("Entrar / Criar conta")
            }
        }
    }
}

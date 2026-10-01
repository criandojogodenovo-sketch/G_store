package com.gstore.app.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Ecrã de erro exibido no arranque quando a configuração pública do
 * Appwrite está em falta ou inválida. O objetivo é que o problema seja
 * IMEDIATAMENTE visível (em vez de "login falhou" genérico).
 */
@Composable
fun ConfigErrorScreen(missing: List<String>) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(72.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Configuração incompleta",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "O app não consegue falar com o serviço de contas " +
                    "(Appwrite) porque faltam estes valores:",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))

            missing.forEach { valor ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text = valor,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = when (valor) {
                                "APPWRITE_ENDPOINT" ->
                                    "Endereço do Appwrite em falta (tem de começar por https://)."
                                "APPWRITE_PROJECT_ID" ->
                                    "ID do projeto Appwrite em falta."
                                else -> "Valor de configuração em falta."
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Como resolver: defina APPWRITE_ENDPOINT e APPWRITE_PROJECT_ID " +
                    "em android/gradle.properties (ou via -P... no comando de build). " +
                    "Estes valores são públicos. A APPWRITE_API_KEY é um SEGREDO e nunca " +
                    "vai no app — fica apenas no backend.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(24.dp))

            val context = LocalContext.current
            Button(onClick = { (context as? android.app.Activity)?.finish() }) {
                Text("Fechar aplicação")
            }
        }
    }
}

package dev.ygo.app.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.ygo.app.core.l10n.t
import dev.ygo.app.core.state.AppState
import kotlinx.coroutines.launch

/**
 * L'adresse de l'instance, demandée avant tout le reste. L'app est faite pour des serveurs
 * auto-hébergés : il n'y a pas de serveur par défaut, et en inventer un donnerait des erreurs
 * réseau incompréhensibles à qui n'a pas encore déployé le sien.
 */
@Composable
fun ServerSetupScreen(app: AppState) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }

    // On normalise ici plutôt qu'à chaque requête : « monserveur.local:4000 » tapé sans
    // schéma est le cas courant, et échouer dessus serait absurde.
    val normalized = url.trim().trimEnd('/').let {
        when {
            it.isEmpty() -> ""
            it.startsWith("http://") || it.startsWith("https://") -> it
            else -> "https://$it"
        }
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, androidx.compose.ui.Alignment.CenterVertically),
    ) {
        Text(t("android.server.title"), style = MaterialTheme.typography.headlineSmall)
        Text(
            t("android.server.hint"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text(t("android.server.current")) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { scope.launch { app.setServer(normalized) } },
            enabled = normalized.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(t("common.actions.save"))
        }
    }
}

package dev.ygo.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ygo.app.core.l10n.t
import dev.ygo.app.core.state.AppState
import kotlinx.coroutines.launch

/**
 * L'accueil, volontairement minuscule pour l'instant : il n'existe que pour atteindre le
 * scanner et vérifier que la session tient. Les onglets Collection, Decks, Suggestions et
 * le reste viendront remplacer ce contenu par la suite.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(app: AppState, onScan: () -> Unit) {
    val scope = rememberCoroutineScope()

    Scaffold(topBar = { TopAppBar(title = { Text(t("layout.appName")) }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                t("android.settings.account"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(app.user?.username ?: "—", style = MaterialTheme.typography.headlineSmall)

            Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Text(t("android.scan.title"), modifier = Modifier.padding(start = 8.dp))
            }

            OutlinedButton(
                onClick = { scope.launch { app.signOut() } },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(t("layout.logout"))
            }
        }
    }
}

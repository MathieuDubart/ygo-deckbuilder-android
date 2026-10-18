package dev.ygo.app.feature.scanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.ygo.app.core.l10n.t
import dev.ygo.app.core.state.AppState
import dev.ygo.app.ui.theme.Theme
import kotlinx.coroutines.launch

/**
 * La relecture du lot avant envoi : corriger les quantités, retirer ce qui a mal été lu, et
 * tout ranger d'un coup. C'est le filet du scan — on scanne vite et sans réfléchir, on
 * vérifie une fois, ici.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanReviewScreen(
    app: AppState,
    batch: ScanBatch,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("android.scan.review.title")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("common.actions.back"))
                    }
                },
            )
        },
        bottomBar = {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (batch.failures.isNotEmpty()) {
                    // On nomme ce qui a échoué : après vingt cartes, « erreur » tout court
                    // obligerait à tout revérifier à la main.
                    Text(
                        t("android.scan.review.failed", mapOf("names" to batch.failures.joinToString(", "))),
                        style = MaterialTheme.typography.bodySmall,
                        color = Theme.danger,
                    )
                }
                Button(
                    onClick = {
                        scope.launch {
                            if (batch.submit(app.endpoints)) {
                                app.collectionChanged()
                                onDone()
                            }
                        }
                    },
                    enabled = !batch.isEmpty && !batch.saving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(t("android.scan.review.save", mapOf("count" to batch.totalCopies)))
                }
            }
        },
    ) { padding ->
        if (batch.isEmpty) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(t("android.scan.review.empty"), style = MaterialTheme.typography.bodyMedium)
            }
            return@Scaffold
        }

        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(batch.drafts, key = { it.id }) { draft ->
                DraftRow(draft = draft, onRemove = { batch.remove(draft) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun DraftRow(draft: ScanDraft, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(draft.card.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
            Text(
                draft.subtitle,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { if (draft.quantity > 1) draft.quantity -= 1 }) {
            Icon(Icons.Default.Remove, contentDescription = null)
        }
        Text("×${draft.quantity}", style = MaterialTheme.typography.titleSmall)
        IconButton(onClick = { draft.quantity += 1 }) {
            Icon(Icons.Default.Add, contentDescription = null)
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Delete, contentDescription = t("common.actions.remove"))
        }
    }
}

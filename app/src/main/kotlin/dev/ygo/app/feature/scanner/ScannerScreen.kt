package dev.ygo.app.feature.scanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.ygo.app.core.l10n.t
import dev.ygo.app.core.net.PrintLookup
import dev.ygo.app.core.state.AppState
import dev.ygo.app.ui.theme.Theme
import dev.ygo.domain.PrintCode
import kotlinx.coroutines.launch

/**
 * Scan des cartes par leur code imprimé (« SDBE-FR001 », sous l'illustration) : la caméra lit
 * les codes à la chaîne et chaque carte reconnue rejoint une file. On enchaîne les cartes sans
 * rien valider, puis on relit la file pour corriger les éditions, les quantités, et tout
 * envoyer en collection d'un coup.
 *
 * Port de `CardScannerView.swift`, y compris ses deux garde-fous, qui sont tout le secret
 * d'un scan utilisable (voir [lookup]).
 */
private const val ABSENCE_COOLDOWN_MS = 1_500L
private const val SCAN_COOLDOWN_MS = 5_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(app: AppState, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val batch = remember { ScanBatch() }

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val askCamera = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }
    LaunchedEffect(Unit) { if (!granted) askCamera.launch(Manifest.permission.CAMERA) }

    var cameraFailed by remember { mutableStateOf(false) }
    var reviewing by remember { mutableStateOf(false) }
    var lookingUp by remember { mutableStateOf<String?>(null) }
    var notFound by remember { mutableStateOf<String?>(null) }

    /** Dernière fois qu'un code a été vu, et ceux déjà comptés (anti-rafale, voir plus bas). */
    val lastSeen = remember { mutableMapOf<String, Long>() }
    val counted = remember { mutableSetOf<String>() }
    var cooldownUntil by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Fait avancer la jauge du délai, et seulement tant qu'il y en a un.
    LaunchedEffect(cooldownUntil) {
        while (cooldownUntil > System.currentTimeMillis()) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(100)
        }
        now = System.currentTimeMillis()
    }

    /**
     * Premier code pas encore compté parmi ceux lus → carte correspondante, ajoutée à la file.
     *
     * Deux garde-fous se cumulent. Par code : un code compté le reste tant qu'il est dans le
     * viseur — une carte posée devant l'objectif ne s'ajoute pas en boucle — et redevient
     * comptable après [ABSENCE_COOLDOWN_MS] d'absence, donc repasser la même carte ajoute bien
     * un deuxième exemplaire. Globalement : rien ne s'ajoute pendant [SCAN_COOLDOWN_MS] après
     * une carte trouvée, le temps d'en changer. Les codes continuent d'être datés pendant ce
     * délai, sinon ce qui est resté sous l'objectif redeviendrait comptable en l'attendant.
     */
    fun lookup(codes: List<String>) {
        val stamp = System.currentTimeMillis()
        if (stamp < cooldownUntil) {
            // On date, mais on ne laisse rien expirer : c'est l'ÉCART entre deux lectures qui
            // déclenche l'expiration, et la reconnaissance clignote. Laisser faire, et une
            // carte restée sous l'objectif sortirait de `counted` pendant le délai pour
            // repartir à la reprise — exactement ce que le délai est censé empêcher.
            codes.forEach { lastSeen[it] = stamp }
            return
        }
        lastSeen.entries.filter { stamp - it.value > ABSENCE_COOLDOWN_MS }.forEach {
            lastSeen.remove(it.key)
            counted.remove(it.key)
        }
        val fresh = codes.firstOrNull { it !in counted }
        codes.forEach { lastSeen[it] = stamp }
        if (lookingUp != null || fresh == null) return

        counted += fresh
        lookingUp = fresh
        notFound = null
        scope.launch {
            try {
                // Une seule requête, et c'est le serveur qui sait quoi faire d'un code
                // inconnu : il va chercher l'extension manquante avant d'abandonner.
                val result = app.endpoints.cardByPrintCode(fresh)
                val card = result.card
                if (card == null || result.status != PrintLookup.Status.FOUND) {
                    notFound = describe(result)
                    return@launch
                }
                val scanned = PrintCode.parse(fresh)
                batch.add(
                    card = card.summary,
                    prints = card.prints,
                    code = fresh,
                    printId = scanned?.let { p -> card.prints.firstOrNull { p.matches(it.printCode) }?.id },
                    // La langue vient du réglage de collection, pas du code lu : ranger selon
                    // la région imprimée est précisément ce qui mélangeait la bibliothèque.
                    language = app.collectionLanguage,
                )
                cooldownUntil = System.currentTimeMillis() + SCAN_COOLDOWN_MS
            } catch (e: Exception) {
                notFound = t("android.scan.notFound", mapOf("code" to fresh))
            } finally {
                lookingUp = null
            }
        }
    }

    if (reviewing) {
        ScanReviewScreen(
            app = app,
            batch = batch,
            onBack = {
                reviewing = false
                cooldownUntil = 0L
            },
            onDone = onClose,
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("android.scan.title")) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = t("common.actions.close"))
                    }
                },
                actions = {
                    if (!batch.isEmpty) {
                        Text(
                            t("android.scan.queued", mapOf("count" to batch.totalCopies)),
                            color = Theme.success,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    }
                },
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (granted && !cameraFailed) {
                CameraReader(
                    scanning = true,
                    onCodes = ::lookup,
                    onFailure = { cameraFailed = true },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(t("android.scan.unavailable"), style = MaterialTheme.typography.titleMedium)
                    Text(
                        t("android.scan.unavailableHint"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Panel(
                batch = batch,
                lookingUp = lookingUp,
                notFound = notFound,
                cooldownLeft = (cooldownUntil - now).coerceAtLeast(0L),
                onUndo = {
                    batch.undoLast()
                    // La carte reste dans `counted` : il faut la sortir du viseur pour la
                    // rescanner, sinon elle repartirait en boucle.
                    cooldownUntil = 0L
                },
                onReview = { reviewing = true },
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            )
        }
    }
}

/** Pourquoi ce code n'a rien donné, en toutes lettres : la cause change ce qu'on peut faire. */
private fun describe(lookup: PrintLookup): String = when (lookup.status) {
    PrintLookup.Status.UNKNOWN_SET -> t("android.scan.unknownSet", mapOf("code" to lookup.code))
    PrintLookup.Status.UNKNOWN_NUMBER -> t("android.scan.unknownNumber", mapOf("code" to lookup.code))
    else -> t("android.scan.notFound", mapOf("code" to lookup.code))
}

@Composable
private fun Panel(
    batch: ScanBatch,
    lookingUp: String?,
    notFound: String?,
    cooldownLeft: Long,
    onUndo: () -> Unit,
    onReview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 3.dp,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val last = batch.last
            when {
                last != null -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(last.card.name, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                        Text(
                            last.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (last.quantity > 1) {
                            Text(
                                t("android.scan.copies", mapOf("count" to last.quantity)),
                                style = MaterialTheme.typography.bodySmall,
                                color = Theme.success,
                            )
                        } else if (last.card.owned > 0) {
                            Text(
                                t("android.scan.alreadyOwned", mapOf("count" to last.card.owned)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = onUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = t("android.scan.undo"))
                    }
                }

                lookingUp != null -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(lookingUp, fontFamily = FontFamily.Monospace)
                }

                notFound != null -> Text(notFound, style = MaterialTheme.typography.bodyMedium)

                else -> Text(
                    t("android.scan.hint"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (cooldownLeft > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LinearProgressIndicator(
                        progress = { cooldownLeft.toFloat() / SCAN_COOLDOWN_MS },
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        t("android.scan.cooldown"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Button(onClick = onReview, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Checklist, contentDescription = null)
                Text(
                    if (batch.isEmpty) t("android.scan.review.openEmpty")
                    else t("android.scan.review.open", mapOf("count" to batch.totalCopies)),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

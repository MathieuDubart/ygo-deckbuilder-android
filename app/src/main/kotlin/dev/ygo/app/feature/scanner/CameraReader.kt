package dev.ygo.app.feature.scanner

import android.annotation.SuppressLint
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dev.ygo.domain.PrintCodeReader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * La caméra et la lecture de texte en direct.
 *
 * Équivalent Android du `DataScannerViewController` de VisionKit : CameraX pour l'aperçu et
 * le flux d'images, ML Kit pour la reconnaissance de texte. Ce qu'on en tire est passé à
 * `PrintCodeReader`, le **même** code que le web et iOS — toute la tolérance aux O lus pour
 * des 0 vit là-bas, testée, et pas ici.
 *
 * `scanning` met l'analyse en pause sans démonter la caméra : pendant la relecture du lot,
 * rien ne doit s'ajouter, mais rouvrir l'objectif ensuite coûte une seconde de noir.
 */
@Composable
fun CameraReader(
    scanning: Boolean,
    onCodes: (List<String>) -> Unit,
    onFailure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val analysis = remember {
        ImageAnalysis.Builder()
            // Seule la dernière image compte : empiler les images d'un flux vidéo pour les
            // analyser toutes ferait fondre la batterie et prendre du retard sur le réel.
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
    }

    DisposableEffect(Unit) {
        onDispose {
            analysis.clearAnalyzer()
            recognizer.close()
            executor.shutdown()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                runCatching {
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build()
                        .also { it.surfaceProvider = previewView.surfaceProvider }
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                }.onFailure { onFailure() }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        update = {
            if (scanning) {
                analysis.setAnalyzer(executor, codeAnalyzer(recognizer, onCodes))
            } else {
                analysis.clearAnalyzer()
            }
        },
    )
}

/**
 * Un analyseur qui rend les codes lus sur une image. `close()` est impératif et dans tous
 * les cas : une image non fermée bloque le flux au bout de deux ou trois trames.
 */
@SuppressLint("UnsafeOptInUsageError")
private fun codeAnalyzer(
    recognizer: com.google.mlkit.vision.text.TextRecognizer,
    onCodes: (List<String>) -> Unit,
) = ImageAnalysis.Analyzer { proxy ->
    val media = proxy.image
    if (media == null) {
        proxy.close()
        return@Analyzer
    }
    val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
    recognizer.process(image)
        .addOnSuccessListener { text ->
            // Ligne par ligne plutôt que sur le texte entier : un bloc colle parfois deux
            // colonnes de la carte, et le code y perdrait son tiret.
            val codes = text.textBlocks
                .flatMap { it.lines }
                .flatMap { PrintCodeReader.codes(it.text) }
                .distinct()
            if (codes.isNotEmpty()) onCodes(codes)
        }
        .addOnCompleteListener { proxy.close() }
}

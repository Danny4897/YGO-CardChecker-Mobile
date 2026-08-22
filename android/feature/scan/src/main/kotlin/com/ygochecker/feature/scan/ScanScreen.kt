package com.ygochecker.feature.scan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn as AndroidXOptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.ygochecker.core.designsystem.CollectionPickOption
import com.ygochecker.core.designsystem.DuelSpacing
import com.ygochecker.core.designsystem.LocalGoBack
import com.ygochecker.core.designsystem.R as DesignR
import com.ygochecker.core.designsystem.SaveToCollectionDialog
import com.ygochecker.core.designsystem.formatPriceEur
import com.ygochecker.core.domain.CardRepository
import com.ygochecker.core.domain.ProfileRepository
import com.ygochecker.core.model.Card
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlin.coroutines.resume

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val cardRepo: CardRepository,
    private val profile: ProfileRepository,
) : ViewModel() {
    var matched by mutableStateOf<Card?>(null)
        private set
    private var lastQuery = ""
    private var searchJob: Job? = null

    val collections = profile.observeCollections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Best-effort match: the topmost OCR text line against the local (offline) card catalog. */
    fun onRecognizedText(text: String) {
        val candidate = text.lines().map(String::trim).firstOrNull { it.length >= 3 } ?: return
        if (candidate.equals(lastQuery, ignoreCase = true)) return
        lastQuery = candidate
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val results = runCatching { cardRepo.search(candidate).first() }.getOrDefault(emptyList())
            val needle = candidate.lowercase()
            val best = results.minByOrNull { levenshtein(it.name.lowercase(), needle) } ?: return@launch
            val distance = levenshtein(best.name.lowercase(), needle)
            // Allow ~40% of the OCR'd length as edit distance — OCR glare/angle causes typos,
            // but this still rejects a mismatched card entirely.
            if (distance <= (needle.length * 0.4).toInt().coerceAtLeast(2)) matched = best
        }
    }

    fun saveToCollection(collectionId: Long, cardId: Int) = viewModelScope.launch {
        val id = if (collectionId <= 0) profile.createCollection("Inbox") else collectionId
        profile.addCardToCollection(id, cardId)
    }

    fun createCollectionAndSave(name: String, cardId: Int) = viewModelScope.launch {
        val id = profile.createCollection(name)
        profile.addCardToCollection(id, cardId)
    }
}

@Composable
fun ScanRoute(vm: ScanViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val goBack = LocalGoBack.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    var saveCollectionOpen by remember { mutableStateOf(false) }
    val collections by vm.collections.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        if (hasPermission) {
            CameraPreviewWithOcr(onTextRecognized = vm::onRecognizedText)
        } else {
            Column(
                Modifier.fillMaxSize().padding(DuelSpacing.space4),
                verticalArrangement = Arrangement.spacedBy(DuelSpacing.space3, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Text(
                    stringResource(DesignR.string.scan_permission_needed),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text(stringResource(DesignR.string.scan_grant_permission))
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(DuelSpacing.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (goBack != null) {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)) {
                    IconButton(onClick = goBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            }
        }

        val match = vm.matched
        if (match != null) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(DuelSpacing.space4),
            ) {
                Row(
                    Modifier.padding(DuelSpacing.space4),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DuelSpacing.space3),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            match.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            match.type,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        match.priceEur?.let {
                            Text(
                                formatPriceEur(it),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    FilledTonalIconButton(onClick = { saveCollectionOpen = true }) {
                        Icon(Icons.Default.Add, stringResource(DesignR.string.scan_add_to_collection))
                    }
                }
            }
        }
    }

    if (saveCollectionOpen && vm.matched != null) {
        val cardId = vm.matched!!.id
        SaveToCollectionDialog(
            collections = collections.map { CollectionPickOption(it.id, it.name) },
            onDismiss = { saveCollectionOpen = false },
            onPick = { id ->
                vm.saveToCollection(id, cardId)
                saveCollectionOpen = false
            },
            onCreateAndSave = { name ->
                vm.createCollectionAndSave(name, cardId)
                saveCollectionOpen = false
            },
        )
    }
}

@Composable
private fun CameraPreviewWithOcr(onTextRecognized: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
            recognizer.close()
        }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

    LaunchedEffect(previewView) {
        val cameraProvider = context.cameraProvider()
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(analysisExecutor, TextAnalyzer(recognizer, onTextRecognized)) }
        runCatching {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }
    }
}

private class TextAnalyzer(
    private val recognizer: TextRecognizer,
    private val onText: (String) -> Unit,
) : ImageAnalysis.Analyzer {
    private var lastProcessedAt = 0L

    @AndroidXOptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        val mediaImage = imageProxy.image
        if (mediaImage == null || now - lastProcessedAt < THROTTLE_MS) {
            imageProxy.close()
            return
        }
        lastProcessedAt = now
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                result.textBlocks.firstOrNull()?.text?.let(onText)
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    private companion object {
        const val THROTTLE_MS = 700L
    }
}

private suspend fun Context.cameraProvider(): ProcessCameraProvider = suspendCancellableCoroutine { cont ->
    val future = ProcessCameraProvider.getInstance(this)
    future.addListener({ cont.resume(future.get()) }, ContextCompat.getMainExecutor(this))
}

/** Iterative Levenshtein distance — short OCR'd strings only, no need for a library. */
private fun levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    val prev = IntArray(b.length + 1) { it }
    val curr = IntArray(b.length + 1)
    for (i in 1..a.length) {
        curr[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
        }
        System.arraycopy(curr, 0, prev, 0, curr.size)
    }
    return prev[b.length]
}

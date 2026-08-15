package com.ygochecker.feature.overlay

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Periodic MediaProjection frames → ML Kit OCR → [MdproOcrParse] for MDPRO card-detail auto-detect.
 */
class MdproAutoDetectEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onParsed: suspend (MdproOcrParseResult) -> Unit,
) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var loopJob: Job? = null
    private var lastIdentity: String = ""
    private var width = 720
    private var height = 1280
    private var densityDpi = 320

    val isRunning: Boolean get() = projection != null

    fun start(resultCode: Int, data: Intent) {
        stop()
        val mpm = context.getSystemService(MediaProjectionManager::class.java) ?: return
        val proj = mpm.getMediaProjection(resultCode, data) ?: return
        projection = proj
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        context.getSystemService(WindowManager::class.java)
            ?.defaultDisplay
            ?.getRealMetrics(metrics)
        width = (metrics.widthPixels.takeIf { it > 0 } ?: 1080).coerceAtMost(1080)
        height = (metrics.heightPixels.takeIf { it > 0 } ?: 1920).coerceAtMost(1920)
        densityDpi = metrics.densityDpi.takeIf { it > 0 } ?: 320

        handlerThread = HandlerThread("mdpro-capture").also { it.start() }
        val handler = Handler(handlerThread!!.looper)
        // API 34+: must register callback before createVirtualDisplay.
        proj.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    stop()
                }
            },
            handler,
        )
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = proj.createVirtualDisplay(
            "ygochecker-mdpro",
            width,
            height,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null,
            handler,
        )
        loopJob = scope.launch(Dispatchers.Default) {
            while (isActive && projection != null) {
                runCatching { captureAndOcr() }
                    .onFailure { Log.w(TAG, "auto-detect frame failed", it) }
                delay(1_400)
            }
        }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        projection?.stop()
        projection = null
        handlerThread?.quitSafely()
        handlerThread = null
        lastIdentity = ""
    }

    private suspend fun captureAndOcr() {
        val reader = imageReader ?: return
        val image = reader.acquireLatestImage() ?: return
        val bitmap = withContext(Dispatchers.Default) {
            image.use { img ->
                val plane = img.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * img.width
                val full = Bitmap.createBitmap(
                    img.width + rowPadding / pixelStride,
                    img.height,
                    Bitmap.Config.ARGB_8888,
                )
                full.copyPixelsFromBuffer(buffer)
                // Card detail title/passcode sit in the upper third on MDPRO3 phone UI.
                val cropTop = (img.height * 0.06f).toInt()
                val cropHeight = (img.height * 0.38f).toInt().coerceAtLeast(120)
                val cropWidth = img.width.coerceAtMost(full.width)
                val h = cropHeight.coerceAtMost(full.height - cropTop)
                Bitmap.createBitmap(full, 0, cropTop, cropWidth, h).also { full.recycle() }
            }
        }
        try {
            val vision = InputImage.fromBitmap(bitmap, 0)
            val text = recognizer.process(vision).awaitTask().text
            if (text.isBlank()) return
            val parsed = MdproOcrParse.parse(text)
            val key = MdproOcrParse.identityKey(parsed)
            if (key.isBlank() || key == lastIdentity) return
            lastIdentity = key
            onParsed(parsed)
        } finally {
            bitmap.recycle()
        }
    }

    companion object {
        private const val TAG = "MdproAutoDetect"
    }
}

private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result -> cont.resume(result) }
    addOnFailureListener { e -> cont.resumeWithException(e) }
    addOnCanceledListener { cont.cancel() }
}

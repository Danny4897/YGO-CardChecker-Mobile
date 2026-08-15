package com.ygochecker.feature.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ygochecker.core.designsystem.DuelSpacing
import com.ygochecker.core.designsystem.R as DesignR
import com.ygochecker.core.designsystem.StatusChip
import com.ygochecker.core.designsystem.StatusTone
import com.ygochecker.core.designsystem.YgoCheckerTheme
import com.ygochecker.core.domain.EvaluateDeckLegality
import com.ygochecker.core.domain.FormatPreference
import com.ygochecker.core.domain.ResolveCardById
import com.ygochecker.core.domain.ResolveCardByName
import com.ygochecker.core.model.Card
import com.ygochecker.core.model.GameFormat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Floating legality HUD over MDPRO3.
 * Optional MediaProjection + ML Kit OCR auto-fills a compact qty/playability check
 * when a card detail is open on screen.
 */
@AndroidEntryPoint
class LegalityOverlayService : LifecycleService() {

    @Inject lateinit var legality: EvaluateDeckLegality
    @Inject lateinit var formatPreference: FormatPreference
    @Inject lateinit var resolveById: ResolveCardById
    @Inject lateinit var resolveByName: ResolveCardByName

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var overlayLifecycle: OverlayComposeLifecycle? = null
    private var autoDetect: MdproAutoDetectEngine? = null

    private var selected by mutableStateOf<HudSelection?>(null)
    private var format by mutableStateOf(GameFormat.TCG)
    private var autoDetectActive by mutableStateOf(false)

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startAsForeground(withProjection = false)
        serviceScope.launch {
            formatPreference.values.collectLatest { format = it }
        }
        attachOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        maybeStartAutoDetect(intent)
        return START_STICKY
    }

    override fun onDestroy() {
        autoDetect?.stop()
        autoDetect = null
        detachOverlay()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun maybeStartAutoDetect(intent: Intent?) {
        val code = intent?.getIntExtra(EXTRA_PROJECTION_RESULT_CODE, 0) ?: 0
        val data = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_PROJECTION_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_PROJECTION_DATA)
        }
        if (code == 0 || data == null) return
        // API 14+: FGS must already declare MEDIA_PROJECTION before getMediaProjection.
        startAsForeground(withProjection = true)
        if (autoDetect == null) {
            autoDetect = MdproAutoDetectEngine(this, serviceScope) { parsed ->
                applyOcrHit(parsed)
            }
        }
        autoDetect?.start(code, data)
        autoDetectActive = true
    }

    private suspend fun applyOcrHit(parsed: MdproOcrParseResult) {
        val card = withContext(Dispatchers.IO) {
            when {
                parsed.passcodes.isNotEmpty() -> {
                    parsed.passcodes.firstNotNullOfOrNull { resolveById.invoke(it) }
                }
                !parsed.candidateName.isNullOrBlank() -> resolveByName.invoke(parsed.candidateName!!)
                else -> null
            }
        } ?: return
        val max = legality.maxCopies(card.id, format)
        withContext(Dispatchers.Main.immediate) {
            selected = HudSelection(card, max, autoDetected = true)
        }
    }

    private fun startAsForeground(withProjection: Boolean = false) {
        val openApp = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, LegalityOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(DesignR.string.overlay_hud_notification_title))
            .setContentText(getString(DesignR.string.overlay_hud_notification_body))
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(openApp)
            .addAction(0, getString(DesignR.string.overlay_hud_stop), stop)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            val type = if (withProjection) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            }
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(DesignR.string.overlay_hud_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun attachOverlay() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 12
            y = 180
        }
        layoutParams = params
        val lifecycle = OverlayComposeLifecycle()
        overlayLifecycle = lifecycle
        lifecycle.onCreate()
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycle)
            setViewTreeViewModelStoreOwner(lifecycle)
            setViewTreeSavedStateRegistryOwner(lifecycle)
            setContent {
                YgoCheckerTheme(darkTheme = true) {
                    LegalityHud(
                        selected = selected,
                        format = format,
                        autoDetectActive = autoDetectActive,
                        onClose = { stopSelf() },
                        onDrag = { dx, dy ->
                            params.x = (params.x - dx.toInt()).coerceAtLeast(0)
                            params.y = (params.y + dy.toInt()).coerceAtLeast(0)
                            wm.updateViewLayout(this, params)
                        },
                    )
                }
            }
        }
        overlayView = view
        wm.addView(view, params)
        lifecycle.onResume()
    }

    private fun detachOverlay() {
        overlayLifecycle?.onDestroy()
        overlayLifecycle = null
        overlayView?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        overlayView = null
        windowManager = null
        layoutParams = null
    }

    companion object {
        const val ACTION_STOP = "com.ygochecker.feature.overlay.STOP_HUD"
        const val EXTRA_PROJECTION_RESULT_CODE = "projection_result_code"
        const val EXTRA_PROJECTION_DATA = "projection_data"
        private const val CHANNEL_ID = "legality_hud"
        private const val NOTIFICATION_ID = 42

        fun start(context: Context, projectionResultCode: Int? = null, projectionData: Intent? = null) {
            val intent = Intent(context, LegalityOverlayService::class.java)
            if (projectionResultCode != null && projectionData != null) {
                intent.putExtra(EXTRA_PROJECTION_RESULT_CODE, projectionResultCode)
                intent.putExtra(EXTRA_PROJECTION_DATA, projectionData)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, LegalityOverlayService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}

data class HudSelection(val card: Card, val maxCopies: Int, val autoDetected: Boolean = false)

@Composable
private fun LegalityHud(
    selected: HudSelection?,
    format: GameFormat,
    autoDetectActive: Boolean,
    onClose: () -> Unit,
    onDrag: (Float, Float) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .widthIn(max = if (selected != null) 240.dp else 200.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    onDrag(drag.x, drag.y)
                }
            },
    ) {
        Column(Modifier.padding(DuelSpacing.space3)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(DesignR.string.overlay_hud_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(DesignR.string.overlay_hud_format, format.id.uppercase()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(DesignR.string.overlay_hud_stop))
                }
            }

            selected?.let { sel ->
                val (label, tone) = when (sel.maxCopies) {
                    0 -> stringResource(DesignR.string.legality_unavailable) to StatusTone.Error
                    1 -> stringResource(DesignR.string.legality_limited) to StatusTone.Warning
                    2 -> stringResource(DesignR.string.legality_semi_limited) to StatusTone.Warning
                    else -> stringResource(DesignR.string.legality_valid) to StatusTone.Success
                }
                Text(
                    sel.card.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(DuelSpacing.space1),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    StatusChip(label, tone)
                    StatusChip(
                        stringResource(DesignR.string.overlay_hud_max_copies, sel.maxCopies),
                        if (sel.maxCopies == 0) StatusTone.Error else StatusTone.Neutral,
                    )
                }
                if (sel.autoDetected) {
                    Text(
                        stringResource(DesignR.string.overlay_hud_auto_detected),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            } ?: Text(
                if (autoDetectActive) {
                    stringResource(DesignR.string.overlay_hud_auto_waiting)
                } else {
                    stringResource(DesignR.string.overlay_hud_idle)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Minimal lifecycle + SavedState for ComposeView hosted in a Service window. */
private class OverlayComposeLifecycle : SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    fun onCreate() {
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun onResume() {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}

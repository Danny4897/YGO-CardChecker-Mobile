package com.ygochecker.feature.overlay

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ygochecker.core.designsystem.DuelSpacing
import com.ygochecker.core.designsystem.EmptyState
import com.ygochecker.core.designsystem.R as DesignR
import com.ygochecker.core.designsystem.ThemedScreenHeader
import com.ygochecker.core.domain.ObserveOfflinePack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class OverlayViewModel @Inject constructor(
    observePack: ObserveOfflinePack,
) : ViewModel() {
    val pack = observePack.status().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        com.ygochecker.core.model.OfflinePackStatus(0, false, 0, null, null),
    )
}

@Composable
fun OverlayRoute(vm: OverlayViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val pack by vm.pack.collectAsStateWithLifecycle()
    var canDrawOverlays by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var batteryUnrestricted by remember {
        mutableStateOf(isIgnoringBatteryOptimizations(context))
    }
    var hudLikelyRunning by remember { mutableStateOf(false) }
    var pendingProjection by remember { mutableStateOf<Pair<Int, Intent>?>(null) }
    var mediaProjectionNote by remember { mutableStateOf<String?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canDrawOverlays = Settings.canDrawOverlays(context)
                batteryUnrestricted = isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        if (Settings.canDrawOverlays(context)) {
            LegalityOverlayService.start(context)
            hudLikelyRunning = true
        }
    }

    fun startHud() {
        if (!Settings.canDrawOverlays(context)) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        val pending = pendingProjection
        if (pending != null) {
            LegalityOverlayService.start(context, pending.first, pending.second)
        } else {
            LegalityOverlayService.start(context)
        }
        hudLikelyRunning = true
    }

    fun stopHud() {
        LegalityOverlayService.stop(context)
        hudLikelyRunning = false
    }

    fun requestBatteryUnrestricted() {
        if (isIgnoringBatteryOptimizations(context)) return
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        runCatching { context.startActivity(intent) }.onFailure {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            )
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ThemedScreenHeader(
            stringResource(DesignR.string.overlay_title),
            stringResource(DesignR.string.overlay_subtitle),
        )

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DuelSpacing.space4, vertical = DuelSpacing.space2),
        ) {
            Column(
                Modifier.padding(DuelSpacing.space3),
                verticalArrangement = Arrangement.spacedBy(DuelSpacing.space2),
            ) {
                Text(
                    stringResource(DesignR.string.overlay_hud_section_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(DesignR.string.overlay_hint_ingame),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(DesignR.string.overlay_hud_auto_detect_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val mediaProjectionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) { result ->
                    if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
                        pendingProjection = result.resultCode to result.data!!
                        mediaProjectionNote = context.getString(DesignR.string.overlay_media_projection_pending)
                        if (Settings.canDrawOverlays(context)) {
                            LegalityOverlayService.start(
                                context,
                                projectionResultCode = result.resultCode,
                                projectionData = result.data,
                            )
                            hudLikelyRunning = true
                        }
                    } else {
                        mediaProjectionNote = null
                        pendingProjection = null
                    }
                }
                OutlinedButton(
                    onClick = {
                        val mpm = context.getSystemService(android.media.projection.MediaProjectionManager::class.java)
                        mediaProjectionLauncher.launch(mpm.createScreenCaptureIntent())
                    },
                ) {
                    Text(stringResource(DesignR.string.overlay_media_projection_cta))
                }
                mediaProjectionNote?.let { note ->
                    Text(
                        note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                if (canDrawOverlays) {
                    Text(
                        stringResource(DesignR.string.overlay_hud_permissions_ready),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(DuelSpacing.space2)) {
                    if (hudLikelyRunning) {
                        OutlinedButton(onClick = ::stopHud) {
                            Text(stringResource(DesignR.string.overlay_hud_stop))
                        }
                    } else {
                        Button(onClick = ::startHud) {
                            Icon(Icons.Default.Visibility, contentDescription = null)
                            Text(
                                stringResource(
                                    if (canDrawOverlays) DesignR.string.overlay_hud_start
                                    else DesignR.string.overlay_hud_grant_permission,
                                ),
                                modifier = Modifier.padding(start = DuelSpacing.space2),
                            )
                        }
                    }
                }
                if (!batteryUnrestricted) {
                    Text(
                        stringResource(DesignR.string.overlay_hud_battery_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(DesignR.string.overlay_hud_battery_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = ::requestBatteryUnrestricted) {
                        Text(stringResource(DesignR.string.overlay_hud_battery_action))
                    }
                }
            }
        }

        if (pack.effectScriptCount == 0) {
            Text(
                stringResource(DesignR.string.overlay_scripts_missing),
                modifier = Modifier.padding(horizontal = DuelSpacing.space4),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        EmptyState(
            icon = Icons.Default.Visibility,
            title = stringResource(DesignR.string.overlay_title),
            body = stringResource(DesignR.string.overlay_manual_fallback),
            modifier = Modifier
                .fillMaxWidth()
                .padding(DuelSpacing.space4),
        )
    }
}

private fun isIgnoringBatteryOptimizations(context: android.content.Context): Boolean {
    val pm = context.getSystemService(PowerManager::class.java) ?: return true
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

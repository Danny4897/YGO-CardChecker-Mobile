package com.ygochecker.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ygochecker.core.common.AppResult
import com.ygochecker.core.designsystem.DuelSpacing
import com.ygochecker.core.designsystem.R as DesignR
import com.ygochecker.core.designsystem.ThemedScreenHeader
import com.ygochecker.core.designsystem.SettingsGroup
import com.ygochecker.core.domain.FormatPreference
import com.ygochecker.core.domain.LanguagePreference
import com.ygochecker.core.domain.ObserveOfflinePack
import com.ygochecker.core.domain.SyncAllKnowledge
import com.ygochecker.core.model.AppLanguage
import com.ygochecker.core.model.CatalogSyncProgress
import com.ygochecker.core.model.GameFormat
import com.ygochecker.core.model.OfflinePackStatus
import com.ygochecker.core.model.SyncPhase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val formats: FormatPreference,
    private val languages: LanguagePreference,
    private val observePack: ObserveOfflinePack,
    private val syncAll: SyncAllKnowledge,
) : ViewModel() {
    val format = formats.values.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GameFormat.TCG)
    val language = languages.values.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppLanguage.ENGLISH)
    val packStatus = observePack.status().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        OfflinePackStatus(0, false, 0, null, null),
    )
    val progress = observePack.progress().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val _notice = MutableStateFlow<String?>(null)
    val notice = _notice.asStateFlow()

    fun setFormat(value: GameFormat) = viewModelScope.launch { formats.set(value) }
    fun setLanguage(value: AppLanguage) = viewModelScope.launch { languages.set(value) }
    fun clearNotice() { _notice.value = null }
    fun clearStuckProgress() = observePack.clearProgress()

    fun downloadAll() = runSync { syncAll.invoke() }

    private fun runSync(block: suspend () -> AppResult<Int>) = viewModelScope.launch {
        _notice.value = null
        when (val result = block()) {
            is AppResult.Ok -> _notice.value = "ok:${result.value}"
            is AppResult.Err -> _notice.value = "err:${result.error.errorKey}"
        }
    }
}

@Composable
fun SettingsRoute(
    onCheckUpdates: (() -> Unit)? = null,
    installedVersionName: String = "",
    installedVersionCode: Int = 0,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val format by vm.format.collectAsStateWithLifecycle()
    val language by vm.language.collectAsStateWithLifecycle()
    val pack by vm.packStatus.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    // Complete N/N without clear used to lock every sync button forever.
    val busy = progress.let { p ->
        p != null && (p.total <= 0 || p.done < p.total)
    }

    LaunchedEffect(progress) {
        val p = progress ?: return@LaunchedEffect
        if (p.total > 0 && p.done >= p.total) {
            kotlinx.coroutines.delay(350)
            vm.clearStuckProgress()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = DuelSpacing.space8),
    ) {
        ThemedScreenHeader(
            stringResource(DesignR.string.settings_title),
            stringResource(DesignR.string.settings_subtitle),
        )

        SettingsGroup(title = stringResource(DesignR.string.settings_format)) {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(DuelSpacing.space2)) {
                GameFormat.entries.forEach { item ->
                    FormatOptionRow(
                        title = formatTitle(item),
                        subtitle = formatSubtitle(item),
                        selected = format == item,
                        enabled = true,
                        onSelect = { vm.setFormat(item) },
                    )
                }
            }
        }

        SettingsGroup(
            title = stringResource(DesignR.string.settings_language),
            modifier = Modifier.padding(top = DuelSpacing.space5),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppLanguage.entries.forEach { item ->
                    val label = when (item) {
                        AppLanguage.ITALIAN -> stringResource(DesignR.string.settings_language_italian)
                        AppLanguage.ENGLISH -> stringResource(DesignR.string.settings_language_english)
                    }
                    FilterChip(
                        selected = language == item,
                        onClick = { vm.setLanguage(item) },
                        enabled = true,
                        label = { Text(label) },
                    )
                }
            }
            Text(
                stringResource(DesignR.string.settings_language_override_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsGroup(
            title = stringResource(DesignR.string.settings_sync),
            modifier = Modifier.padding(top = DuelSpacing.space5),
        ) {
            Text(
                stringResource(DesignR.string.settings_catalog_count, pack.catalogCount),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(
                    DesignR.string.settings_scripts_count,
                    pack.effectScriptCount,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (pack.legalityReady) {
                    stringResource(DesignR.string.settings_legality_ready)
                } else {
                    stringResource(DesignR.string.settings_legality_missing)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            progress?.let { SyncProgressBar(it) }

            notice?.let { raw ->
                val message = when {
                    raw.startsWith("ok:") -> stringResource(DesignR.string.settings_sync_success, raw.removePrefix("ok:"))
                    raw.startsWith("err:network") -> stringResource(DesignR.string.error_network)
                    else -> stringResource(DesignR.string.settings_sync_failed)
                }
                Text(message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }

            Text(
                stringResource(DesignR.string.settings_sync_all_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { vm.clearNotice(); vm.downloadAll() },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(DesignR.string.settings_sync_all))
            }
            progress?.let { p ->
                Text(
                    when (p.phase) {
                        SyncPhase.CATALOG -> stringResource(DesignR.string.settings_sync_phase_catalog)
                        SyncPhase.LEGALITY -> stringResource(DesignR.string.settings_sync_phase_legality)
                        SyncPhase.EFFECT_SCRIPTS -> stringResource(DesignR.string.settings_sync_phase_scripts)
                        SyncPhase.RELATED -> stringResource(DesignR.string.settings_sync_phase_related)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (onCheckUpdates != null) {
            SettingsGroup(
                title = stringResource(DesignR.string.settings_update),
                modifier = Modifier.padding(top = DuelSpacing.space5),
            ) {
                if (installedVersionName.isNotEmpty()) {
                    Text(
                        stringResource(
                            DesignR.string.settings_app_version,
                            installedVersionName,
                            installedVersionCode,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Button(
                    onClick = onCheckUpdates,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(stringResource(DesignR.string.settings_check_update))
                }
            }
        }
    }
}

@Composable
private fun FormatOptionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onSelect,
            ),
    ) {
        Row(
            Modifier.padding(horizontal = DuelSpacing.space4, vertical = DuelSpacing.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                contentDescription = null,
            )
            Spacer(Modifier.width(DuelSpacing.space3))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun SyncProgressBar(progress: CatalogSyncProgress) {
    val label = when (progress.phase) {
        SyncPhase.CATALOG -> stringResource(DesignR.string.settings_syncing_catalog)
        SyncPhase.LEGALITY -> stringResource(DesignR.string.settings_syncing_legality)
        SyncPhase.EFFECT_SCRIPTS -> stringResource(DesignR.string.settings_syncing_scripts)
        SyncPhase.RELATED -> stringResource(DesignR.string.settings_syncing_related)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (progress.total > 0) {
            Text(
                "${progress.done} / ${progress.total}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun formatTitle(format: GameFormat): String = stringResource(
    when (format) {
        GameFormat.TCG -> DesignR.string.format_tcg
        GameFormat.GOAT -> DesignR.string.format_goat
        GameFormat.EDISON -> DesignR.string.format_edison
        GameFormat.HAT -> DesignR.string.format_hat
        GameFormat.TENGU -> DesignR.string.format_tengu
    },
)

@Composable
private fun formatSubtitle(format: GameFormat): String = stringResource(
    when (format) {
        GameFormat.TCG -> DesignR.string.format_tcg_sub
        GameFormat.GOAT -> DesignR.string.format_goat_sub
        GameFormat.EDISON -> DesignR.string.format_edison_sub
        GameFormat.HAT -> DesignR.string.format_hat_sub
        GameFormat.TENGU -> DesignR.string.format_tengu_sub
    },
)

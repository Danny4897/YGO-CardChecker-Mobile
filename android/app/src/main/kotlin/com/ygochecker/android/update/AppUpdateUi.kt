package com.ygochecker.android.update

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ygochecker.android.BuildConfig
import com.ygochecker.core.designsystem.DuelUpdateDialog
import com.ygochecker.core.designsystem.DuelWhatsNewDialog
import com.ygochecker.core.designsystem.R as DesignR
import com.ygochecker.core.domain.AppUpdateCheck
import com.ygochecker.core.domain.AppUpdateManifest
import com.ygochecker.core.domain.AppUpdateRepository
import com.ygochecker.core.domain.CheckAppUpdate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UpdateUiState(
    val prompt: AppUpdateManifest? = null,
    val downloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val awaitingInstall: Boolean = false,
    val statusMessage: String? = null,
    val needPermission: Boolean = false,
    val whatsNew: WhatsNewPayload? = null,
)

data class WhatsNewPayload(
    val versionName: String,
    val changelog: String,
)

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val checkUpdate: CheckAppUpdate,
    private val repo: AppUpdateRepository,
    private val installer: ApkUpdateInstaller,
) : ViewModel() {
    private val _state = MutableStateFlow(UpdateUiState())
    val state = _state.asStateFlow()

    fun checkOnLaunch() = viewModelScope.launch {
        maybeShowWhatsNew()
        when (val result = checkUpdate.invoke(BuildConfig.VERSION_CODE)) {
            is AppUpdateCheck.Available -> _state.update { it.copy(prompt = result.manifest) }
            is AppUpdateCheck.Failed -> Unit
            else -> Unit
        }
    }

    private suspend fun maybeShowWhatsNew() {
        val installed = BuildConfig.VERSION_CODE
        val seen = repo.lastSeenVersionCode()
        if (seen >= installed) return
        val notes = BuildConfig.WHATS_NEW.trim()
        if (notes.isEmpty()) {
            repo.setLastSeenVersionCode(installed)
            return
        }
        _state.update {
            it.copy(
                whatsNew = WhatsNewPayload(
                    versionName = BuildConfig.VERSION_NAME,
                    changelog = notes.replace("\\n", "\n"),
                ),
            )
        }
    }

    fun dismissWhatsNew() = viewModelScope.launch {
        repo.setLastSeenVersionCode(BuildConfig.VERSION_CODE)
        _state.update { it.copy(whatsNew = null) }
    }

    fun checkManual(upToDateLabel: String, disabledLabel: String, failedLabel: String) =
        viewModelScope.launch {
            when (val result = checkUpdate.invoke(BuildConfig.VERSION_CODE)) {
                is AppUpdateCheck.Available -> _state.update {
                    it.copy(prompt = result.manifest, statusMessage = null)
                }
                AppUpdateCheck.UpToDate, AppUpdateCheck.Skipped -> _state.update {
                    it.copy(statusMessage = upToDateLabel)
                }
                AppUpdateCheck.Disabled -> _state.update {
                    it.copy(statusMessage = disabledLabel)
                }
                is AppUpdateCheck.Failed -> _state.update {
                    it.copy(statusMessage = failedLabel)
                }
            }
        }

    fun dismissLater() = viewModelScope.launch {
        val code = _state.value.prompt?.versionCode
        if (code != null && !_state.value.awaitingInstall) {
            repo.setSkippedVersionCode(code)
        }
        _state.update {
            it.copy(prompt = null, awaitingInstall = false, downloading = false, downloadProgress = 0f)
        }
    }

    fun dismissStatus() {
        _state.update { it.copy(statusMessage = null) }
    }

    fun download(activity: Activity, failedLabel: String) = viewModelScope.launch {
        val url = _state.value.prompt?.apkUrl ?: return@launch
        _state.update {
            it.copy(
                downloading = true,
                downloadProgress = 0f,
                awaitingInstall = false,
                needPermission = false,
                statusMessage = null,
            )
        }
        val outcome = installer.downloadAndInstall(activity, url) { progress ->
            _state.update { s -> s.copy(downloadProgress = progress) }
        }
        when (outcome) {
            InstallOutcome.AwaitingUserConfirm -> _state.update {
                it.copy(
                    downloading = false,
                    downloadProgress = 1f,
                    awaitingInstall = true,
                )
            }
            InstallOutcome.NeedInstallPermission -> _state.update {
                it.copy(downloading = false, needPermission = true)
            }
            is InstallOutcome.Failed -> _state.update {
                it.copy(
                    downloading = false,
                    downloadProgress = 0f,
                    statusMessage = failedLabel,
                )
            }
        }
    }

    fun openInstallPermission(context: Context) {
        installer.openUnknownSourcesSettings(context)
        _state.update { it.copy(needPermission = false) }
    }
}

@Composable
fun AppUpdateHost(
    viewModel: AppUpdateViewModel = hiltViewModel(),
    checkOnLaunch: Boolean = true,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val failedLabel = stringResource(DesignR.string.update_download_failed)
    if (checkOnLaunch) {
        LaunchedEffect(Unit) { viewModel.checkOnLaunch() }
    }

    state.whatsNew?.let { payload ->
        DuelWhatsNewDialog(
            versionName = payload.versionName,
            changelog = payload.changelog,
            onDismiss = viewModel::dismissWhatsNew,
        )
    }

    state.prompt?.let { manifest ->
        if (state.whatsNew != null) return@let
        DuelUpdateDialog(
            versionName = manifest.versionName,
            currentVersionName = BuildConfig.VERSION_NAME,
            changelog = manifest.changelog,
            downloading = state.downloading,
            downloadProgress = state.downloadProgress,
            awaitingInstall = state.awaitingInstall,
            onDownload = {
                context.findActivity()?.let { viewModel.download(it, failedLabel) }
            },
            onLater = viewModel::dismissLater,
        )
    }

    if (state.needPermission) {
        AlertDialog(
            onDismissRequest = { viewModel.openInstallPermission(context) },
            title = { Text(stringResource(DesignR.string.update_permission_title)) },
            text = { Text(stringResource(DesignR.string.update_permission_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.openInstallPermission(context) }) {
                    Text(stringResource(DesignR.string.update_permission_open))
                }
            },
        )
    }

    state.statusMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = viewModel::dismissStatus,
            title = { Text(stringResource(DesignR.string.update_check_title)) },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissStatus) {
                    Text(stringResource(DesignR.string.action_close))
                }
            },
        )
    }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return ctx as? Activity
}

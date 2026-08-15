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
    val statusMessage: String? = null,
    val needPermission: Boolean = false,
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
        when (val result = checkUpdate.invoke(BuildConfig.VERSION_CODE)) {
            is AppUpdateCheck.Available -> _state.update { it.copy(prompt = result.manifest) }
            is AppUpdateCheck.Failed -> Unit // silent on launch
            else -> Unit
        }
    }

    /** Manual check from Settings — surfaces UpToDate / Failed. */
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
        val code = _state.value.prompt?.versionCode ?: return@launch
        repo.setSkippedVersionCode(code)
        _state.update { it.copy(prompt = null) }
    }

    fun dismissStatus() {
        _state.update { it.copy(statusMessage = null) }
    }

    fun download(activity: Activity) = viewModelScope.launch {
        val url = _state.value.prompt?.apkUrl ?: return@launch
        _state.update { it.copy(downloading = true, needPermission = false) }
        when (installer.downloadAndInstall(activity, url)) {
            InstallOutcome.Started -> _state.update {
                it.copy(downloading = false, prompt = null)
            }
            InstallOutcome.NeedInstallPermission -> _state.update {
                it.copy(downloading = false, needPermission = true)
            }
            InstallOutcome.Failed -> _state.update {
                it.copy(downloading = false)
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
    if (checkOnLaunch) {
        LaunchedEffect(Unit) { viewModel.checkOnLaunch() }
    }

    state.prompt?.let { manifest ->
        DuelUpdateDialog(
            versionName = manifest.versionName,
            currentVersionName = BuildConfig.VERSION_NAME,
            changelog = manifest.changelog,
            downloading = state.downloading,
            onDownload = { context.findActivity()?.let { viewModel.download(it) } },
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

package com.ygochecker.android.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class ApkUpdateInstaller @Inject constructor(
    private val http: OkHttpClient,
) {
    /**
     * Downloads [apkUrl] into cache and opens the **system package installer** UI
     * (FileProvider + ACTION_VIEW). User must confirm; the OS then replaces this app.
     */
    suspend fun downloadAndInstall(
        activity: Activity,
        apkUrl: String,
        onProgress: (Float) -> Unit = {},
    ): InstallOutcome = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            return@withContext InstallOutcome.NeedInstallPermission
        }
        try {
            val dir = File(activity.cacheDir, "updates").apply { mkdirs() }
            val apk = File(dir, "ygochecker-update.apk")
            if (apk.exists()) apk.delete()

            val request = Request.Builder()
                .url(apkUrl)
                .header("Accept", "application/vnd.android.package-archive,application/octet-stream,*/*")
                .header("User-Agent", "YGOChecker-Updater/${activity.packageName}")
                .get()
                .build()

            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext InstallOutcome.Failed("HTTP ${response.code}")
                }
                val body = response.body ?: return@withContext InstallOutcome.Failed("empty")
                val total = body.contentLength()
                var read = 0L
                apk.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            read += n
                            if (total > 0L) {
                                onProgress((read.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                            } else {
                                // Indeterminate-ish progress for unknown length
                                onProgress((read % 5_000_000L) / 5_000_000f * 0.9f)
                            }
                        }
                        out.flush()
                    }
                }
            }

            if (!looksLikeApk(apk)) {
                apk.delete()
                return@withContext InstallOutcome.Failed("not_apk")
            }
            onProgress(1f)

            withContext(Dispatchers.Main) {
                launchSystemInstaller(activity, apk)
            }
            InstallOutcome.AwaitingUserConfirm
        } catch (_: IOException) {
            InstallOutcome.Failed("network")
        } catch (e: Exception) {
            InstallOutcome.Failed(e.javaClass.simpleName)
        }
    }

    fun openUnknownSourcesSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun launchSystemInstaller(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        listOf(
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.samsung.android.packageinstaller",
        ).forEach { pkg ->
            try {
                context.grantUriPermission(
                    pkg,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: Exception) {
                // Package may be absent on this device.
            }
        }
        // Prefer the system installer directly — createChooser can look more "unknown" to Play Protect.
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            context.startActivity(Intent.createChooser(intent, null))
        }
    }

    private fun looksLikeApk(file: File): Boolean {
        if (!file.isFile || file.length() < 10_000L) return false
        file.inputStream().use { input ->
            val magic = ByteArray(4)
            if (input.read(magic) != 4) return false
            // ZIP / APK local file header
            return magic[0] == 0x50.toByte() && magic[1] == 0x4B.toByte()
        }
    }
}

sealed interface InstallOutcome {
    /** System installer UI was launched — user must confirm. */
    data object AwaitingUserConfirm : InstallOutcome
    data object NeedInstallPermission : InstallOutcome
    data class Failed(val reason: String = "") : InstallOutcome
}

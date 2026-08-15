package com.ygochecker.android.update

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApkUpdateInstaller @Inject constructor(
    private val http: OkHttpClient,
) {
    /**
     * Downloads [apkUrl] into cache and launches the system package installer.
     * Prefers [PackageInstaller] sessions (same path most FOSS updaters use).
     */
    suspend fun downloadAndInstall(activity: Activity, apkUrl: String): InstallOutcome =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !activity.packageManager.canRequestPackageInstalls()
            ) {
                return@withContext InstallOutcome.NeedInstallPermission
            }
            try {
                val dir = File(activity.cacheDir, "updates").apply { mkdirs() }
                val apk = File(dir, "ygochecker-update.apk")
                if (apk.exists()) apk.delete()
                val request = Request.Builder().url(apkUrl).get().build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext InstallOutcome.Failed
                    val body = response.body ?: return@withContext InstallOutcome.Failed
                    apk.outputStream().use { out -> body.byteStream().copyTo(out) }
                }
                withContext(Dispatchers.Main) {
                    launchInstaller(activity, apk)
                }
                InstallOutcome.Started
            } catch (_: IOException) {
                InstallOutcome.Failed
            } catch (_: Exception) {
                InstallOutcome.Failed
            }
        }

    fun openUnknownSourcesSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun launchInstaller(context: Context, apk: File) {
        try {
            commitPackageSession(context, apk)
        } catch (_: Exception) {
            // Fallback for OEM quirks — classic VIEW intent.
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apk,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private fun commitPackageSession(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        // Self-update of the same package — avoid an extra confirmation step when the OS allows it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            } catch (_: Exception) {
                // OEM stubs may reject this; default user action is fine.
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("ygochecker", 0, apk.length()).use { out ->
                apk.inputStream().use { input -> input.copyTo(out) }
                session.fsync(out)
            }
            val callback = Intent(context, context.javaClass).apply {
                action = Intent.ACTION_MAIN
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            // Prefer returning to our MainActivity after install confirmation.
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: callback
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pending = PendingIntent.getActivity(context, sessionId, launch, flags)
            session.commit(pending.intentSender)
        }
    }
}

enum class InstallOutcome {
    Started,
    NeedInstallPermission,
    Failed,
}

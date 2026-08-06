package nl.streamfix.ui.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import nl.streamfix.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request

enum class UpdateResult {
    InstallerOpened,
    DownloadFailed,
    IntegrityFailed,
    PermissionRequired,
    InstallerFailed,
}

data class UpdateOutcome(
    val result: UpdateResult,
    val diagnostic: String? = null,
)

/** Downloadt de update-APK en start de Android-installer. */
object AppUpdater {

    private const val SUBPATH = "updates/streamtotaal-update.apk"
    private const val PARTIAL_SUBPATH = "$SUBPATH.part"
    private val userAgent = "StreamTotaal/${BuildConfig.VERSION_NAME} (Android TV)"

    private enum class DownloadResult { Success, Failed, IntegrityFailed }

    private data class DownloadOutcome(
        val result: DownloadResult,
        val diagnostic: String? = null,
    )

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * Downloadt de update-APK en start bij succes de installer.
     * [expectedSha256] (hex, optioneel) wordt na de download tegen het
     * bestand geverifieerd; mismatch = mislukt, geen installatie.
     * [onResult] wordt op de main-thread aangeroepen met de uitkomst en,
     * bij een fout, een korte diagnosecode voor ondersteuning op afstand.
     */
    fun downloadAndInstall(
        context: Context,
        apkUrl: String,
        expectedSha256: String? = null,
        onProgress: (Int) -> Unit = {},
        onResult: (UpdateOutcome) -> Unit = {},
    ) {
        val appContext = context.applicationContext
        if (!canInstallPackages(appContext)) {
            val settingsOpened = openInstallPermissionSettings(appContext)
            onResult(
                UpdateOutcome(
                    result = UpdateResult.PermissionRequired,
                    diagnostic = if (settingsOpened) {
                        "INSTALL_PERMISSION"
                    } else {
                        "PERMISSION_SETTINGS_UNAVAILABLE"
                    },
                ),
            )
            return
        }

        val mainHandler = Handler(Looper.getMainLooper())
        Thread {
            val downloadResult = downloadApk(
                context = appContext,
                apkUrl = apkUrl,
                expectedSha256 = expectedSha256,
                onProgress = { percent ->
                    mainHandler.post { onProgress(percent) }
                },
            )
            mainHandler.post {
                val outcome = when (downloadResult.result) {
                    DownloadResult.Failed -> UpdateOutcome(
                        UpdateResult.DownloadFailed,
                        downloadResult.diagnostic,
                    )
                    DownloadResult.IntegrityFailed -> UpdateOutcome(
                        UpdateResult.IntegrityFailed,
                        downloadResult.diagnostic,
                    )
                    DownloadResult.Success -> {
                        val installDiagnostic = install(appContext)
                        if (installDiagnostic == null) {
                            UpdateOutcome(UpdateResult.InstallerOpened)
                        } else {
                            UpdateOutcome(UpdateResult.InstallerFailed, installDiagnostic)
                        }
                    }
                }
                onResult(outcome)
            }
        }.apply {
            name = "StreamTotaal-update"
            isDaemon = true
            start()
        }
    }

    /**
     * Downloadt rechtstreeks naar app-private externe opslag. Sommige
     * Android TV-fabrikanten laten DownloadManager-taken eindeloos op nul
     * bytes staan; een gewone OkHttp-stream heeft daar geen last van.
     */
    private fun downloadApk(
        context: Context,
        apkUrl: String,
        expectedSha256: String?,
        onProgress: (Int) -> Unit,
    ): DownloadOutcome {
        val target = updateFile(context)
        val partial = File(context.filesDir, PARTIAL_SUBPATH)
        val parent = target.parentFile ?: return DownloadOutcome(
            DownloadResult.Failed,
            "STORAGE_UNAVAILABLE",
        )
        if ((!parent.exists() && !parent.mkdirs()) || !parent.isDirectory) {
            return DownloadOutcome(DownloadResult.Failed, "STORAGE_UNAVAILABLE")
        }
        target.delete()
        partial.delete()

        val result = runCatching {
            val request = Request.Builder()
                .url(apkUrl)
                .header("User-Agent", userAgent)
                .header("Accept", "application/vnd.android.package-archive,*/*")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use DownloadOutcome(
                        DownloadResult.Failed,
                        "HTTP_${response.code}",
                    )
                }
                val body = response.body ?: return@use DownloadOutcome(
                    DownloadResult.Failed,
                    "EMPTY_RESPONSE",
                )
                val totalBytes = body.contentLength()
                var downloadedBytes = 0L
                var lastPercent = -1

                body.byteStream().use { input ->
                    partial.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            if (totalBytes > 0) {
                                val percent = (
                                    downloadedBytes * 100 / totalBytes
                                ).toInt().coerceIn(0, 100)
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    onProgress(percent)
                                }
                            }
                        }
                    }
                }

                if (totalBytes > 0 && downloadedBytes != totalBytes) {
                    return@use DownloadOutcome(
                        DownloadResult.Failed,
                        "INCOMPLETE_DOWNLOAD",
                    )
                }
                if (!partial.renameTo(target)) {
                    partial.copyTo(target, overwrite = true)
                    partial.delete()
                }
                if (checksumOk(context, expectedSha256)) {
                    DownloadOutcome(DownloadResult.Success)
                } else {
                    DownloadOutcome(DownloadResult.IntegrityFailed, "HASH_MISMATCH")
                }
            }
        }.getOrElse { error ->
            DownloadOutcome(
                DownloadResult.Failed,
                error.javaClass.simpleName.ifBlank { "DOWNLOAD_EXCEPTION" },
            )
        }

        if (result.result != DownloadResult.Success) {
            partial.delete()
            target.delete()
        }
        return result
    }

    private fun canInstallPackages(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    private fun openInstallPermissionSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
    }

    /** True als er geen hash is meegegeven of het bestand exact klopt. */
    private fun checksumOk(context: Context, expected: String?): Boolean {
        if (expected.isNullOrBlank()) return true
        return runCatching {
            val file = updateFile(context)
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest()
                .joinToString("") { "%02x".format(it) }
                .equals(expected.trim(), ignoreCase = true)
        }.getOrDefault(false)
    }

    /** Null als de Android-installer daadwerkelijk is geopend, anders een foutcode. */
    private fun install(context: Context): String? {
        val file = updateFile(context)
        if (!file.exists()) return "APK_NOT_FOUND"
        return runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            null
        }.getOrElse { error ->
            "INSTALL_${error.javaClass.simpleName.ifBlank { "EXCEPTION" }}"
        }
    }

    private fun updateFile(context: Context): File = File(context.filesDir, SUBPATH)
}

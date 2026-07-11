package nl.streamfix.ui.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest

/** Downloadt de update-APK en start de Android-installer. */
object AppUpdater {

    private const val SUBPATH = "updates/streamtotaal-update.apk"
    private const val POLL_INTERVAL_MS = 500L
    private const val STALL_TIMEOUT_MS = 90_000L

    private data class DownloadState(
        val status: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
    )

    /**
     * Downloadt de update-APK en start bij succes de installer.
     * [expectedSha256] (hex, optioneel) wordt na de download tegen het
     * bestand geverifieerd; mismatch = mislukt, geen installatie.
     * [onResult] wordt op de main-thread aangeroepen: true = download
     * geslaagd en installer gestart, false = mislukt (geen install
     * geprobeerd, zodat de UI een nette fout/retry kan tonen).
     */
    fun downloadAndInstall(
        context: Context,
        apkUrl: String,
        expectedSha256: String? = null,
        onProgress: (Int) -> Unit = {},
        onResult: (Boolean) -> Unit = {},
    ) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE)
            as? DownloadManager
        if (dm == null) {
            onResult(false)
            return
        }

        // Oude download opruimen zodat de installer de nieuwe pakt.
        File(context.getExternalFilesDir(null), SUBPATH).delete()

        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("StreamTotaal update")
            .setDestinationInExternalFilesDir(context, null, SUBPATH)
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
            )
        val id = runCatching { dm.enqueue(request) }.getOrNull()
        if (id == null) {
            onResult(false)
            return
        }

        // Niet vertrouwen op alleen ACTION_DOWNLOAD_COMPLETE: sommige
        // Android-fabrikanten leveren die dynamische broadcast niet altijd
        // af. Polling geeft bovendien voortgang en kan een vastgelopen
        // DownloadManager-taak gecontroleerd afbreken.
        val appContext = context.applicationContext
        val mainHandler = Handler(Looper.getMainLooper())
        Thread {
            var lastBytes = -1L
            var lastProgressAt = SystemClock.elapsedRealtime()
            var lastPercent = -1
            var downloadOk = false

            while (true) {
                val state = queryDownload(dm, id) ?: break
                val now = SystemClock.elapsedRealtime()
                if (state.downloadedBytes > lastBytes) {
                    lastBytes = state.downloadedBytes
                    lastProgressAt = now
                }
                if (state.totalBytes > 0) {
                    val percent = (
                        state.downloadedBytes * 100 / state.totalBytes
                    ).toInt().coerceIn(0, 100)
                    if (percent != lastPercent) {
                        lastPercent = percent
                        mainHandler.post { onProgress(percent) }
                    }
                }

                when (state.status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        downloadOk = checksumOk(appContext, expectedSha256)
                        break
                    }
                    DownloadManager.STATUS_FAILED -> break
                }

                if (now - lastProgressAt >= STALL_TIMEOUT_MS) {
                    runCatching { dm.remove(id) }
                    break
                }
                try {
                    Thread.sleep(POLL_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }

            mainHandler.post {
                val installed = downloadOk && install(appContext)
                onResult(installed)
            }
        }.apply {
            name = "StreamTotaal-update"
            isDaemon = true
            start()
        }
    }

    /** True als er geen hash is meegegeven of het bestand exact klopt. */
    private fun checksumOk(context: Context, expected: String?): Boolean {
        if (expected.isNullOrBlank()) return true
        return runCatching {
            val file = File(context.getExternalFilesDir(null), SUBPATH)
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

    private fun queryDownload(dm: DownloadManager, id: Long): DownloadState? =
        runCatching {
            dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
                if (c != null && c.moveToFirst()) {
                    DownloadState(
                        status = c.getInt(
                            c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS),
                        ),
                        downloadedBytes = c.getLong(
                            c.getColumnIndexOrThrow(
                                DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR,
                            ),
                        ),
                        totalBytes = c.getLong(
                            c.getColumnIndexOrThrow(
                                DownloadManager.COLUMN_TOTAL_SIZE_BYTES,
                            ),
                        ),
                    )
                } else {
                    null
                }
            }
        }.getOrNull()

    /** True als de Android-installer daadwerkelijk is geopend. */
    private fun install(context: Context): Boolean {
        val file = File(context.getExternalFilesDir(null), SUBPATH)
        if (!file.exists()) return false
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
        }.isSuccess
    }
}

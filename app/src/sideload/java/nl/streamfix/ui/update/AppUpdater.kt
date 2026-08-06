package nl.streamfix.ui.update

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/** Downloadt de update-APK en start de Android-installer. */
object AppUpdater {

    private const val SUBPATH = "updates/streamtotaal-update.apk"
    private const val PARTIAL_SUBPATH = "$SUBPATH.part"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(3, TimeUnit.MINUTES)
            .followRedirects(true)
            .build()
    }

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
        val appContext = context.applicationContext
        val mainHandler = Handler(Looper.getMainLooper())
        Thread {
            val downloadOk = downloadApk(
                context = appContext,
                apkUrl = apkUrl,
                expectedSha256 = expectedSha256,
                onProgress = { percent ->
                    mainHandler.post { onProgress(percent) }
                },
            )
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
    ): Boolean {
        val target = File(context.getExternalFilesDir(null), SUBPATH)
        val partial = File(context.getExternalFilesDir(null), PARTIAL_SUBPATH)
        target.parentFile?.mkdirs()
        target.delete()
        partial.delete()

        val ok = runCatching {
            val request = Request.Builder().url(apkUrl).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use false
                val body = response.body ?: return@use false
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
                    return@use false
                }
                if (!partial.renameTo(target)) {
                    partial.copyTo(target, overwrite = true)
                    partial.delete()
                }
                checksumOk(context, expectedSha256)
            }
        }.getOrDefault(false)

        if (!ok) {
            partial.delete()
            target.delete()
        }
        return ok
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

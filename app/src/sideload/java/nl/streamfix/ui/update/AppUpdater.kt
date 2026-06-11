package nl.streamfix.ui.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest

/** Downloadt de update-APK en start de Android-installer. */
object AppUpdater {

    private const val SUBPATH = "updates/streamtotaal-update.apk"

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

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val done = intent.getLongExtra(
                    DownloadManager.EXTRA_DOWNLOAD_ID, -1L,
                )
                if (done != id) return
                runCatching { ctx.unregisterReceiver(this) }
                // Hashen van ~9 MB hoort niet op de main-thread; goAsync
                // houdt de receiver levend tot het resultaat er is.
                val pending = goAsync()
                Thread {
                    val ok = downloadSucceeded(dm, id) &&
                        checksumOk(ctx, expectedSha256)
                    Handler(Looper.getMainLooper()).post {
                        if (ok) install(ctx)
                        onResult(ok)
                        pending.finish()
                    }
                }.start()
            }
        }
        ContextCompat.registerReceiver(
            context.applicationContext,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            // Systeembroadcasts komen ook bij NOT_EXPORTED gewoon aan;
            // andere apps kunnen deze receiver dan niet bereiken.
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
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

    private fun downloadSucceeded(dm: DownloadManager, id: Long): Boolean =
        runCatching {
            dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
                if (c != null && c.moveToFirst()) {
                    val status = c.getInt(
                        c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS),
                    )
                    status == DownloadManager.STATUS_SUCCESSFUL
                } else {
                    false
                }
            }
        }.getOrDefault(false)

    private fun install(context: Context) {
        val file = File(context.getExternalFilesDir(null), SUBPATH)
        if (!file.exists()) return
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
        runCatching { context.startActivity(intent) }
    }
}

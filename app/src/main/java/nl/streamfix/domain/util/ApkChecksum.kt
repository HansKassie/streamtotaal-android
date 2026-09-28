package nl.streamfix.domain.util

import java.io.File
import java.security.MessageDigest

internal object ApkChecksum {
    private val hexSha256 = Regex("[0-9a-fA-F]{64}")

    fun isValid(expected: String?): Boolean =
        expected != null && hexSha256.matches(expected.trim())

    fun matches(file: File, expected: String?): Boolean {
        if (!isValid(expected)) return false
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
                .equals(expected?.trim(), ignoreCase = true)
        }.getOrDefault(false)
    }
}

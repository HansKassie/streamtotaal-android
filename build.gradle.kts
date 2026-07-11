import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

/**
 * Bouwt alleen de sideload-release en maakt de twee uploadbestanden gereed:
 * streamtotaal.apk en version.json met versiegegevens en SHA-256 uit die APK.
 */
tasks.register("prepareSideloadRelease") {
    group = "distribution"
    description = "Builds and prepares the signed sideload release for upload."
    dependsOn(":app:assembleSideloadRelease")

    doLast {
        val releaseDir = file("app/build/outputs/apk/sideload/release")
        val metadataFile = releaseDir.resolve("output-metadata.json")
        val metadata = JsonSlurper().parse(metadataFile) as Map<*, *>
        val element = (metadata["elements"] as List<*>).single() as Map<*, *>
        val sourceApk = releaseDir.resolve(element["outputFile"] as String)
        val uploadApk = file("streamtotaal.apk")
        sourceApk.copyTo(uploadApk, overwrite = true)

        val digest = MessageDigest.getInstance("SHA-256")
        uploadApk.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        val sha256 = digest.digest().joinToString("") { "%02x".format(it) }

        val manifestFile = file("version.json")
        @Suppress("UNCHECKED_CAST")
        val manifest = LinkedHashMap(
            JsonSlurper().parse(manifestFile) as Map<String, Any?>,
        )
        manifest["versionCode"] = (element["versionCode"] as Number).toInt()
        manifest["versionName"] = element["versionName"] as String
        manifest["sha256"] = sha256
        manifestFile.writeText(
            JsonOutput.prettyPrint(JsonOutput.toJson(manifest)) + "\n",
        )

        logger.lifecycle("Sideload release ready: ${uploadApk.absolutePath}")
        logger.lifecycle("SHA-256: $sha256")
    }
}

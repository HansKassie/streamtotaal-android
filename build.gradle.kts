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

/** Waar R2 de bestanden serveert; publish-release.sh gebruikt hetzelfde adres. */
val updateHost = "https://updates.smarttv-solutions.xyz"

/**
 * Bouwt de ondertekende ZelViro-release en zet de uploadbestanden klaar:
 * - zelviro-player-<versie>.apk: voor de updater in de app. Elke versie een
 *   eigen naam, dus de edge-cache van Cloudflare kan nooit een oude APK
 *   serveren bij een nieuw manifest, en een gepubliceerde versie wordt nooit
 *   overschreven;
 * - zelviro-player.apk: dezelfde bytes onder een vaste naam, voor de
 *   downloadknop op de website en de Downloader-code;
 * - zelviro.json: versie, SHA-256 en apkUrl uit diezelfde APK. Alleen de
 *   releaseNotes vul je zelf in.
 * Daarna publiceert ./publish-release.sh alles en controleert het.
 */
tasks.register("prepareZelviroRelease") {
    group = "distribution"
    description = "Builds and prepares the signed ZelViro release for upload."
    dependsOn(":app:assembleZelviroRelease")

    doLast {
        // Zonder keystore.properties slaat het app-project de release-signing
        // stil over en rolt er een niet-ondertekende APK uit. Die kopieren en
        // hashen zou een uploadbestand opleveren dat klanten niet kunnen
        // installeren. Daarom hier hard stoppen; zie SETUP.md.
        if (!rootProject.file("keystore.properties").exists()) {
            throw GradleException(
                "keystore.properties ontbreekt, dus de release-APK is niet " +
                    "ondertekend. Zet de keystore klaar volgens SETUP.md " +
                    "voordat je een uitrol voorbereidt.",
            )
        }

        val releaseDir = file("app/build/outputs/apk/zelviro/release")
        val metadataFile = releaseDir.resolve("output-metadata.json")
        val metadata = JsonSlurper().parse(metadataFile) as Map<*, *>
        val element = (metadata["elements"] as List<*>).single() as Map<*, *>
        val versionCode = (element["versionCode"] as Number).toInt()
        val versionName = element["versionName"] as String
        if (!Regex("""\d+\.\d+\.\d+""").matches(versionName)) {
            throw GradleException("Onverwachte versienaam '$versionName': verwacht bijvoorbeeld 1.0.5.")
        }
        val sourceApk = releaseDir.resolve(element["outputFile"] as String)
        val versieApk = file("zelviro-player-$versionName.apk")
        val vasteApk = file("zelviro-player.apk")
        sourceApk.copyTo(versieApk, overwrite = true)
        sourceApk.copyTo(vasteApk, overwrite = true)

        val digest = MessageDigest.getInstance("SHA-256")
        versieApk.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        val sha256 = digest.digest().joinToString("") { "%02x".format(it) }

        val manifestFile = file("zelviro.json")
        @Suppress("UNCHECKED_CAST")
        val manifest = LinkedHashMap(
            JsonSlurper().parse(manifestFile) as Map<String, Any?>,
        )
        manifest["versionCode"] = versionCode
        manifest["versionName"] = versionName
        manifest["apkUrl"] = "$updateHost/${versieApk.name}"
        manifest["sha256"] = sha256
        manifestFile.writeText(
            JsonOutput.prettyPrint(JsonOutput.toJson(manifest)) + "\n",
        )

        logger.lifecycle("ZelViro $versionName (code $versionCode) klaar:")
        logger.lifecycle("  ${versieApk.name} en ${vasteApk.name} (zelfde bytes)")
        logger.lifecycle("  zelviro.json -> ${manifest["apkUrl"]}")
        logger.lifecycle("  SHA-256: $sha256")
        logger.lifecycle("Vergeet de releaseNotes in zelviro.json niet; publiceer met ./publish-release.sh")
    }
}

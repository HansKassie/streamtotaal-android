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
 * Bouwt een sideloadbare release en maakt de twee uploadbestanden gereed: de
 * APK en het manifest met versiegegevens en de SHA-256 uit diezelfde APK.
 *
 * Twee uitvoeringen, want er zijn twee updatekanalen. Beide doorlopen
 * dezelfde controles; het verschil zit alleen in de flavor en de
 * bestandsnamen. Een kopie van deze taak zou betekenen dat een controle
 * hierboven wel wordt aangescherpt en die hieronder niet.
 */
fun registreerReleaseTaak(
    taakNaam: String,
    flavor: String,
    apkNaam: String,
    manifestNaam: String,
) = tasks.register(taakNaam) {
    group = "distribution"
    description = "Builds and prepares the signed $flavor release for upload."
    dependsOn(":app:assemble${flavor.replaceFirstChar { it.uppercase() }}Release")

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

        val releaseDir = file("app/build/outputs/apk/$flavor/release")
        val metadataFile = releaseDir.resolve("output-metadata.json")
        val metadata = JsonSlurper().parse(metadataFile) as Map<*, *>
        val element = (metadata["elements"] as List<*>).single() as Map<*, *>
        val sourceApk = releaseDir.resolve(element["outputFile"] as String)
        val uploadApk = file(apkNaam)
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

        val manifestFile = file(manifestNaam)
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

        logger.lifecycle("Release ($flavor) ready: ${uploadApk.absolutePath}")
        logger.lifecycle("Manifest: ${manifestFile.name}")
        logger.lifecycle("SHA-256: $sha256")
    }
}

// Onze eigen uitvoering. De naam blijft ongewijzigd, want SETUP.md en
// publish-release.sh verwijzen ernaar.
registreerReleaseTaak(
    taakNaam = "prepareSideloadRelease",
    flavor = "sideload",
    apkNaam = "streamtotaal.apk",
    manifestNaam = "version.json",
)

// De uitvoering voor de collega-reseller. Eigen APK en eigen manifest, want
// een gedeeld manifest zou bij de volgende update onze volledige app op zijn
// toestellen installeren.
registreerReleaseTaak(
    taakNaam = "prepareResellerRelease",
    flavor = "reseller",
    apkNaam = "streamtotaal-e.apk",
    manifestNaam = "version-e.json",
)

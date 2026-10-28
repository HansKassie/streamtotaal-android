import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Release-signing wordt geladen uit keystore.properties (niet in git).
// Zie SETUP.md voor het aanmaken van de keystore.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(keystorePropertiesFile.inputStream())
    }
}
val hasReleaseSigning = keystorePropertiesFile.exists()

android {
    namespace = "nl.streamfix"
    compileSdk = 35

    defaultConfig {
        applicationId = "nl.streamfix.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 37
        versionName = "1.0.36"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // Beperk de meegeleverde locales: Nederlands (default in values/)
        // en Engels (values-en/). Voorkomt half-vertaalde Material/Compose-
        // strings in andere talen en houdt de APK klein.
        resourceConfigurations += listOf("nl", "en")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // Drie distributiekanalen vanuit een gedeelde codebase.
    // - sideload  = bestaande klanten via R2 + in-app updater + preset-providers.
    // - playstore = Google Play; geen self-updater (verboden door beleid),
    //               geen preset-providers, gebruiker tikt zelf een URL in.
    // - reseller  = collega-reseller; zelfde app, alleen zijn eigen netwerk en
    //               een eigen updatekanaal. Eigen applicationId, anders zou
    //               zijn uitvoering die van ons overschrijven op hetzelfde
    //               toestel.
    //
    // sideload en reseller delen src/distributable: de updater en de
    // netwerkhernoeming staan daar EEN keer. Zonder die gedeelde map zou een
    // derde flavor een kopie van echte logica betekenen, en die lopen uiteen.
    // Alleen ProviderCatalog verschilt nog per flavor, plus de twee
    // buildConfigFields hieronder.
    flavorDimensions += "distribution"
    productFlavors {
        create("sideload") {
            dimension = "distribution"
            buildConfigField("boolean", "HAS_UPDATER", "true")
            buildConfigField("boolean", "HAS_BUNDLED_PROVIDERS", "true")
            buildConfigField(
                "String",
                "UPDATE_HOST",
                "\"updates.smarttv-solutions.xyz\"",
            )
            buildConfigField(
                "String",
                "UPDATE_MANIFEST_URL",
                "\"https://updates.smarttv-solutions.xyz/version.json\"",
            )
        }
        create("playstore") {
            dimension = "distribution"
            applicationIdSuffix = ".playstore"
            buildConfigField("boolean", "HAS_UPDATER", "false")
            buildConfigField("boolean", "HAS_BUNDLED_PROVIDERS", "false")
        }
        create("reseller") {
            dimension = "distribution"
            applicationIdSuffix = ".e"
            buildConfigField("boolean", "HAS_UPDATER", "true")
            buildConfigField("boolean", "HAS_BUNDLED_PROVIDERS", "true")
            buildConfigField(
                "String",
                "UPDATE_HOST",
                "\"updates.smarttv-solutions.xyz\"",
            )
            // Eigen manifest. Zou dit naar version.json wijzen, dan
            // installeert zijn app bij de volgende update onze volledige
            // uitvoering, inclusief alle netwerken.
            buildConfigField(
                "String",
                "UPDATE_MANIFEST_URL",
                "\"https://updates.smarttv-solutions.xyz/version-e.json\"",
            )
        }
    }

    // De updater en de netwerkhernoeming horen bij allebei de sideloadbare
    // varianten, maar NIET bij de Play Store-build: zelfupdate-code hoort
    // daar helemaal niet in te zitten.
    sourceSets {
        getByName("sideload") { java.srcDir("src/distributable/java") }
        getByName("reseller") { java.srcDir("src/distributable/java") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core en lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    // Dependency injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Lokale opslag
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.androidx.security.crypto)

    // Video player
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.media3.common)

    // Casten naar Chromecast / Google TV
    implementation(libs.media3.cast)
    implementation(libs.play.services.cast.framework)
    implementation(libs.androidx.mediarouter)

    // Netwerk
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.kotlinx.coroutines.android)

    // Image loading
    implementation(libs.coil.compose)

    // Crash reporting
    implementation(libs.sentry.android)

    // Tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}

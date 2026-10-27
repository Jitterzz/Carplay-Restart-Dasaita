import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Signing. When the repo has the release-key secrets, the workflow writes keystore/keystore.properties and
// that key signs the APK. Without them, the app's own key in signing/ does, so every build still installs
// over the one before it (keeping your settings).
val releaseProps = Properties().apply {
    val f = rootProject.file("keystore/keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val ownProps = Properties().apply {
    val f = rootProject.file("signing/signing.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val useRelease = releaseProps.isNotEmpty()
val keyProps = if (useRelease) releaseProps else ownProps
val keyDir = if (useRelease) "keystore/" else "signing/"

android {
    namespace = "app.carplayrestart"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.carplayrestart"
        // Android 8.0 and up: every Dasaita radio with ZLink.
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "1.3"
    }

    signingConfigs {
        create("release") {
            if (keyProps.isNotEmpty()) {
                storeFile = rootProject.file(keyDir + keyProps.getProperty("storeFile"))
                storePassword = keyProps.getProperty("storePassword")
                keyAlias = keyProps.getProperty("keyAlias")
                keyPassword = keyProps.getProperty("keyPassword")
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }

    lint {
        // Lint findings shouldn't stop an APK from building.
        checkReleaseBuilds = false
        abortOnError = false
    }
}

// No libraries: everything here is plain Android, so the app stays tiny and quick on the radio.

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Signing secrets come from the environment (CI secrets) or a git-ignored keystore.properties
// (local). They are NOT committed, so the repo can be public without exposing the signing key.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingSecret(name: String): String? = System.getenv(name) ?: keystoreProps.getProperty(name)
val keystoreFile = file(signingSecret("KEYSTORE_FILE") ?: "cripta-release.keystore")
val hasSigning = keystoreFile.exists() && signingSecret("KEYSTORE_PASSWORD") != null

android {
    namespace = "com.cripta.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cripta.app"
        minSdk = 31
        targetSdk = 34
        versionCode = ((project.findProperty("buildNumber") as String?)?.toIntOrNull()) ?: 1
        versionName = "0.1.0" + ((project.findProperty("buildLabel") as String?)?.let { "-$it" } ?: "")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // yt-dlp ships native Python payloads per ABI; limit to the two real-device architectures
        // to keep the APK from ballooning with x86 emulator binaries.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    signingConfigs {
        if (hasSigning) create("shared") {
            storeFile = keystoreFile
            storePassword = signingSecret("KEYSTORE_PASSWORD")
            keyAlias = signingSecret("KEY_ALIAS") ?: "cripta"
            keyPassword = signingSecret("KEY_PASSWORD")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            // Sign with the shared key when available (CI / local), so updates install in place.
            // Without it (e.g. a public clone with no keystore) fall back to the default debug key.
            if (hasSigning) signingConfig = signingConfigs.getByName("shared")
            // Ship the CI "debug" APK as a non-debuggable build: keeps the same
            // applicationId (updates in place) and debug cert, but lets ART fully
            // optimize and drops Compose debug overhead -> much smoother scrolling.
            isDebuggable = false
        }
        release {
            isMinifyEnabled = false
            if (hasSigning) signingConfig = signingConfigs.getByName("shared")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // youtubedl-android must be able to extract its Python .so payloads at runtime.
        jniLibs.useLegacyPackaging = true
    }
}

dependencies {
    implementation(project(":crypto"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.sqlcipher)
    implementation(libs.androidx.sqlite)
    implementation(libs.androidx.sqlite.ktx)

    implementation(libs.tink.android)
    implementation(libs.androidx.biometric)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.datasource)
    implementation(libs.media3.extractor)
    implementation(libs.media3.transformer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.effect)
    implementation(libs.media3.common)

    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.documentfile)
    implementation(libs.pdfbox.android)

    implementation(libs.youtubedl.android)
    implementation(libs.youtubedl.ffmpeg)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
}

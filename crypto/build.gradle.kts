plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.cripta.crypto"
    compileSdk = 37

    defaultConfig {
        minSdk = 31
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(libs.tink.android)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

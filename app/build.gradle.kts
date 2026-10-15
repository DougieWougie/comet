plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Default LibreSpeed backend; override with -PcometServerUrl=https://speed.example.com/
val defaultServerUrl = providers.gradleProperty("cometServerUrl").orElse("https://speedtest.example.com/")

android {
    namespace = "dev.dougie.comet"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.dougie.comet"
        // 31 = Android 12: NetworkCallback.FLAG_INCLUDE_LOCATION_INFO is available (needed for the SSID).
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "DEFAULT_SERVER_URL", "\"${defaultServerUrl.get()}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions.unitTests.all { it.maxHeapSize = "1g" }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    // org.json is a stub in unit tests; use the real implementation.
    testImplementation("org.json:json:20250517")
}

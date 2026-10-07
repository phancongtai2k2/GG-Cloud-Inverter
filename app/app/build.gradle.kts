import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Địa chỉ backend chỉ đặt trong local.properties (không commit, AGENTS.md §10), ví dụ:
//   ggp.api.baseUrl.live=https://<máy chủ dev>/
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun localProperty(key: String): String = localProperties.getProperty(key, "")

android {
    namespace = "vn.ggpower.inverter"
    compileSdk = 37

    defaultConfig {
        applicationId = "vn.ggpower.inverter"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        // ADR-001: bản demo cho ghi cấu hình thật qua backend (trong danh sách cho phép).
        // Bật cờ này (true) để chuyển toàn bộ control cấu hình sang chỉ xem.
        buildConfigField("boolean", "CONFIG_READ_ONLY", "false")
    }

    // Quy trình B1: mock chạy toàn bộ Fake…Repository, không cần mạng (bản dự phòng khi demo); live gọi backend .NET.
    // Bản mock có hậu tố applicationId để cài song song với bản live trên cùng một máy.
    flavorDimensions += "backend"
    productFlavors {
        create("mock") {
            dimension = "backend"
            isDefault = true
            applicationIdSuffix = ".mock"
            versionNameSuffix = "-mock"
            buildConfigField("String", "API_BASE_URL", "\"\"")
        }
        create("live") {
            dimension = "backend"
            buildConfigField("String", "API_BASE_URL", "\"${localProperty("ggp.api.baseUrl.live")}\"")
        }
    }

    // Ký bản phát hành: thông tin khoá nằm trong keystore.properties (không commit, đã chặn ở .gitignore):
    //   storeFile=keystore/release.jks   storePassword=...   keyAlias=...   keyPassword=...
    // Chưa có file này thì bản release được build nhưng chưa ký.
    val keystoreProperties = Properties().apply {
        rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    signingConfigs {
        if (keystoreProperties.containsKey("storeFile")) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
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
}

dependencies {
    implementation(project(":chart"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.compose)
    implementation(libs.androidx.camera.mlkit.vision)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.androidx.appcompat)
    implementation(libs.okhttp)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

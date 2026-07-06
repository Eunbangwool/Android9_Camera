plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.farmmachine.cctv"
    compileSdk = 34             // AGP 8.2 상한 — 자매 레포와 동일

    defaultConfig {
        applicationId = "com.farmmachine.cctv"
        minSdk = 23                 // 실기기 Apollo 10 Pro = Android 9(API 28) 확인됨. CameraX 는 21+
        targetSdk = 34
        versionCode = (project.findProperty("versionCodeOverride") as? String)?.toIntOrNull() ?: 1
        versionName = "0.1"

        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    // 고정 debug 키스토어로 서명 → CI 빌드마다 서명 동일 → 덮어쓰기 설치 가능
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.2")   // ComponentActivity = LifecycleOwner (CameraX bind)
    // CameraX — 실기기 카메라 HAL 이 LEGACY(v1.0)라도 Preview 유스케이스는 전 하드웨어레벨 지원
    val camerax = "1.3.4"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
}

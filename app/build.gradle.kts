plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.farmmachine.cctv"
    compileSdk = 34             // AGP 8.2 상한(35 는 AGP 8.6+ 필요) — 자매 레포와 동일

    defaultConfig {
        applicationId = "com.farmmachine.cctv"
        minSdk = 23                 // Apollo 10 Pro 변종 = Android 6.0.1(API 23) 가능성 커버. 실기기 getprop 확인
        targetSdk = 34
        versionCode = (project.findProperty("versionCodeOverride") as? String)?.toIntOrNull() ?: 1
        versionName = "0.1"

        // Apollo 10 Pro ABI. libvlc-all 의 x86 네이티브 라이브러리 제외로 APK 크기 절감.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    // 고정 debug 키스토어로 서명 → CI 빌드마다 서명이 동일 → 덮어쓰기 설치 가능.
    // (기본값은 러너마다 새로 생성되는 ~/.android/debug.keystore 라 서명 충돌 발생)
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

    buildFeatures {
        buildConfig = true      // VlcEngine 이 BuildConfig.DEBUG 로 verbose 로그 제어
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    // RTSP/H.265 재생 — Media3 RTSP 는 H.265 depacketize·TCP interleaved 미성숙이라 libVLC 채택
    implementation("org.videolan.android:libvlc-all:3.6.3")
    // ONVIF SOAP(HTTP POST) 용. minSdk 21 요구 → 23 OK
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}

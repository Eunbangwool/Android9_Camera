plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// IP/RTSP 뷰어 앱 (하우스용). AHD→IP 인코더/XVR 의 RTSP 를 최대 12칸 그리드로 표시.
// 아날로그 qcarcam 앱(:app)과 별개 — 패키지 com.farmmachine.cctv.rtsp 로 동시 설치 가능.
android {
    namespace = "com.farmmachine.cctv.rtsp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.farmmachine.cctv.rtsp"
        minSdk = 23
        targetSdk = 34
        versionCode = (project.findProperty("versionCodeOverride") as? String)?.toIntOrNull() ?: 1
        versionName = "0.1"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

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
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { buildConfig = true }   // VlcEngine 이 BuildConfig.DEBUG 사용
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    // RTSP/H.264·H.265 재생 (IP 카메라/인코더/XVR)
    implementation("org.videolan.android:libvlc-all:3.6.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")   // ONVIF SOAP
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.farmmachine.cctv"
    compileSdk = 34             // AGP 8.2 상한 — 자매 레포와 동일

    defaultConfig {
        // applicationId 는 플레이버에서 지정 (local / cast)
        minSdk = 23                 // 실기기 Apollo 10 Pro = Android 9(API 28) 확인됨
        targetSdk = 34
        versionCode = (project.findProperty("versionCodeOverride") as? String)?.toIntOrNull() ?: 1
        versionName = "0.1"

        // 실기기 = arm64-v8a. Quectel .so 가 arm64 만 있으므로 이 ABI 로 고정.
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    // 한 레포에서 두 앱 빌드:
    //  local = 기존 완성본(화면 전용) / cast = 화면 + MJPEG 서버(폰에서 브라우저로 시청)
    flavorDimensions += "mode"
    productFlavors {
        create("local") {
            dimension = "mode"
            applicationId = "com.farmmachine.cctv"
            resValue("string", "app_name", "FarmMachine CCTV")
            buildConfigField("boolean", "CAST_MODE", "false")
        }
        create("cast") {
            dimension = "mode"
            applicationId = "com.farmmachine.cctv.cast"
            versionNameSuffix = "-cast"
            resValue("string", "app_name", "FarmMachine CCTV Cast")
            buildConfigField("boolean", "CAST_MODE", "true")
        }
    }

    buildFeatures { buildConfig = true }

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

    // 번들 .so 를 디스크로 추출(useLegacyPackaging=true) → 동적 링커가 DT_NEEDED/dlopen 을
    // nativeLibraryDir 에서 해결 가능. Quectel 라이브러리 상호 의존 해결에 필요.
    packaging {
        jniLibs {
            useLegacyPackaging = true
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
    implementation("androidx.activity:activity-ktx:1.9.2")
    // 카메라는 프레임워크 Camera1 API(android.hardware.Camera) 직접 사용 —
    // 실기기 HAL 이 "Camera1 API shim / legacy device@1.0" 라 CameraX(Camera2 상위)보다 정합.
}

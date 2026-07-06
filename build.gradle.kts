// 툴체인 = farmmachine-auto-steering 과 동일 스택 (Gradle 8.2.1 / AGP 8.2.2 / Kotlin 2.0.21)
// 실기기(Apollo 10 Pro)가 Android 9 로 안내되었으나 자매 기기 변종이 6.0.1(API 23)이라 minSdk 23 유지.
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}

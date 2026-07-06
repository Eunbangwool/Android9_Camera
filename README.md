# FarmMachine CCTV

Apollo 10 Pro 산업용 태블릿(Android 9, Qualcomm MSM8953)용 **아날로그 카메라 CCTV 뷰어**.
차량 방수 아날로그 카메라 2대를 태블릿에서 실시간으로 본다.

## 카메라 경로 (현장 확인 결과)

- 카메라는 **아날로그(AHD)** — `/dev/rn6864m`(RN6864 AHD 디코더)로 디코딩되어 CSI 채널 0·1 로 입력.
- 표준 안드로이드 카메라 API 는 이 입력을 못 받음(파란 stub 확인). 실제 영상은 **Qualcomm AIS/qcarcam** 경로로만 나옴.
- 그래서 벤더 네이티브 라이브러리(`libmmqcar_qcar_jni.so` 외, `app/src/main/jniLibs/arm64-v8a/`)를 통해
  `com.quectel.qcarapi` 인터페이스로 채널 0·1 프레임(NV21)을 받아 렌더링한다.
  (오너가 AGMO 사용 허락 확보, 비상업 목적.)
- 카메라 전원은 GPIO 로 잠겨 있어 시스템 서비스 `com.van.service` 에 `com.cpdevice.action.CAMERGPIOON`
  브로드캐스트를 보내 켠다(`VanCamera`, root 불필요).

## 기능

- **2채널 분할**(좌 ch0 / 우 ch1), 카메라 화면 **탭 → 전체화면 / 다시 탭 → 분할**
- **720p**, 화면비 유지(레터박스) 렌더
- 카메라 전원 자동 인가
- **부팅 시 자동 실행**(AGMO Solution 이 먼저 뜨도록 30초 지연 — `BootReceiver`)

## 두 가지 앱 (Gradle 플레이버)

| 플레이버 | 패키지 | 설명 |
|---|---|---|
| **local** | `com.farmmachine.cctv` | 태블릿 화면 전용 뷰어 (완성본) |
| **cast**  | `com.farmmachine.cctv.cast` | 화면 + **폰 시청용 MJPEG 서버** |

패키지가 달라 **두 앱 동시 설치 가능**.

### 폰에서 실시간 시청 (cast 앱)

cast 앱 실행 중, 태블릿과 **같은 Wi-Fi**에 연결된 폰의 브라우저에서:

```
http://<태블릿 Wi-Fi IP>:8080
```

주소는 cast 앱 화면 좌상단(`📱 폰 접속: http://…:8080`)에 표시된다. `/ch0`, `/ch1` 로 개별 채널도 접근 가능.
(MJPEG over HTTP — 폰에 앱 설치 불필요, 브라우저만 있으면 됨.)

## 설치

CI 가 푸시마다 두 APK 를 빌드해 **Releases 의 `cctv-debug` 롤링 프리릴리스**에 올린다:
`app-local-debug.apk`, `app-cast-debug.apk`. 태블릿 브라우저로 받아 설치(출처 불명 앱 허용).
최초 1회 수동 실행해야 부팅 자동실행이 활성화된다.

## 빌드

```bash
./gradlew :app:assembleDebug     # local + cast 두 플레이버 모두 빌드 (JDK 17 + SDK 34)
```

- 스택: Gradle 8.2.1 / AGP 8.2.2 / Kotlin 2.0.21 / minSdk 23 / targetSdk 34 / abiFilters arm64-v8a
- 카메라: 프레임워크 API 아님 → Quectel qcarcam(`jniLibs`), 카메라 프레임 폴링(getPreviewFrameInfo) → NV21

## 구조

```
app/src/main/java/com/farmmachine/cctv/
├── MainActivity.kt        # 2채널 분할↔전체화면, cast 모드면 MJPEG 서버 기동
├── CameraController.kt    # qcarcam open + 채널별 프레임 폴링/렌더(ChannelReader), NV21→ARGB
├── VanCamera.kt           # com.van.service 로 카메라 전원 ON/OFF
├── BootReceiver.kt        # 부팅 지연 자동실행(AlarmManager)
├── MjpegServer.kt         # (cast) 초경량 MJPEG-over-HTTP 서버
└── FrameHub.kt            # 캡처→서버 최신 NV21 공유
com/quectel/qcarapi/…      # qcarcam JNI 인터페이스(재구현/원본, JNI_OnLoad 바인딩용)
app/src/main/jniLibs/arm64-v8a/  # Quectel 벤더 .so (비상업·오너 허가)
```

## 관련 레포

- `Eunbangwool/farmmachine-auto-steering` — 자율조향 앱 (동일 태블릿, Gradle/CI 스캐폴딩 공유)

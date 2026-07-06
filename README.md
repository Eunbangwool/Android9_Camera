# FarmMachine CCTV

Apollo 10 Pro 산업용 태블릿(Android 9, Qualcomm MSM8953)용 **아날로그 카메라 라이브뷰 앱**.
차량 방수 아날로그 카메라(4핀 항공 커넥터 → Deutsch 하네스)를 태블릿 카메라 입력에 연결해 전체화면으로 본다.

## 이 기기의 카메라 구조 (현장 확인 결과)

- 카메라는 **아날로그(AHD/CVBS)** — 케이블에 RJ45 없음, IP/POE 아님. `eth0`는 저속 SPI 이더넷으로 영상과 무관.
- 태블릿이 아날로그 신호를 CSI 로 브릿지해 **표준 안드로이드 카메라 장치 1개**(Back, 최대 1280×720)로 노출.
  → CameraX 로 표시. `/dev/video0`은 cameraserver 가 열어주므로 앱은 `CAMERA` 권한만 있으면 됨(root 불필요).
- **카메라 전원**은 GPIO 로 잠겨 있어, 시스템 서비스 `com.van.service` 에 브로드캐스트를 보내 켠다:
  - ON  : `com.cpdevice.action.CAMERGPIOON`  → `com.van.service/.CamerGpioOnBoardcastReceiver`
  - OFF : `com.cpdevice.action.CAMERGPIOOFF` → `.CamerGpioOffBoardcastReceiver`
  - 앱이 시작 시 자동으로 ON 브로드캐스트를 보냄 (`VanCamera.powerOn`).

## 동작

1. 앱 실행 → 카메라 전원 ON 브로드캐스트
2. `CAMERA` 권한 요청 (최초 1회)
3. CameraX 로 후면 카메라(외부 아날로그 입력) 전체화면 프리뷰
4. 신호 없거나 바인드 실패 시 전원 재인가 + 재시도 (2초 간격)

⚠️ **여러 대 분할화면은 이 하드웨어에서 불가.** 아날로그 입력이 1채널(카메라 장치 1개)뿐이다.
다중 카메라는 아날로그 카메라 추가 + 쿼드 멀티플렉서 + 벤더(Quectel) 멀티채널 SDK 가 필요한 별도 작업.

## 설치

푸시마다 CI 가 디버그 APK 를 빌드해 **Releases 의 `cctv-debug` 롤링 프리릴리스**로 올린다.
태블릿 브라우저로 다운로드 → "출처를 알 수 없는 앱" 허용 → 설치. (서명 고정이라 덮어쓰기 설치 가능)

## 현장 점검

1. 카메라 하네스 연결 후 앱 실행 → 화면에 영상이 뜨는지 확인
2. 안 뜨면: 앱이 카메라 전원 브로드캐스트를 보냈는지 logcat 확인
   `adb logcat -s VanCamera CctvMain`
3. 어두운 데서 렌즈 앞을 보면 적외선 LED(붉은 점)로 카메라 생존 확인
4. 카메라 장치 재확인: `adb shell dumpsys media.camera | findstr "Number of camera"`

## 빌드

```bash
./gradlew :app:assembleDebug     # JDK 17 + Android SDK 34
```

- 스택: Gradle 8.2.1 / AGP 8.2.2 / Kotlin 2.0.21 / minSdk 23 / targetSdk 34
- 의존성: CameraX 1.3.4 (core/camera2/lifecycle/view)
- CI: `.github/workflows/build-cctv-apk.yml` (롤링 디버그) / `release-apk.yml` (v* 태그 정식)

## 구조

```
app/src/main/java/com/farmmachine/cctv/
├── MainActivity.kt   # CameraX 전체화면 프리뷰, 권한, 재시도, 몰입형
└── VanCamera.kt      # com.van.service 브로드캐스트로 카메라 전원 ON/OFF
```

## 관련 레포

- `Eunbangwool/farmmachine-auto-steering` — 자율조향 앱 (동일 태블릿, Gradle/CI 스캐폴딩 공유)

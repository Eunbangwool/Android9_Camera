# FarmMachine CCTV

Apollo 10 Pro 태블릿(Android)용 **POE IP 카메라 라이브뷰 앱**.
AliExpress 8MP POE 카메라(H.265 · ONVIF · IR 야간)를 태블릿에서 CCTV 모니터로 보기 위한 전용 앱이다.

- 최대 **4대 분할화면**(1대=전면 / 2대=좌우 / 3–4대=2×2), 타일 탭 → 전체화면, 뒤로가기 → 분할 복귀
- **분할화면 = 서브 스트림 / 전체화면 = 메인 스트림** 자동 전환 — 8MP H.265 를 여러 개 동시 디코드하면
  SoC 가 못 버티므로 타일은 저해상도 스트림을 쓴다 (카메라 1대뿐이면 타일도 메인 스트림)
- **라이브뷰 전용** (녹화/스냅샷 없음)
- 재생 엔진 = **libVLC** (`--rtsp-tcp`, HW 디코드 + SW 폴백), 자동 재연결(1→2→4→8s 백오프, 무한),
  프레임 프리즈 워치독(8초 무프레임 시 세션 재시작)
- 카메라 등록 = 수동 RTSP URL 입력(항상 가능) + **ONVIF 보조**(검색·스트림 URL 자동 조회)
- 네트워크 진단 패널: 인터페이스별 IP, 카메라 RTSP(554) 도달성 프로브, (루트 한정) 이더넷 고정 IP 헬퍼

## 설치

푸시마다 CI 가 디버그 APK 를 빌드해 **Releases 의 `cctv-debug` 롤링 프리릴리스**로 올린다.
태블릿 브라우저로 다운로드 → "출처를 알 수 없는 앱" 허용 → 설치. (서명이 고정이라 덮어쓰기 설치 가능)

## 카메라 연결 (POE 직결)

POE 카메라는 **PoE 전원 공급 장치가 반드시 필요**하다. 태블릿 이더넷 포트는 PoE 전원을 못 준다.

```
[카메라] ──LAN──> [PoE 인젝터/스위치] ──LAN──> [태블릿 이더넷(어댑터)]
                        │
                      DC 전원
```

직결 링크에는 DHCP 서버가 없으므로 **양쪽 다 고정 IP** 로 맞춘다:

| 장치 | 예시 IP | 비고 |
|---|---|---|
| 카메라 | 192.168.1.168 | 보급형 공장 기본값이 192.168.1.x 인 경우가 많음 (매뉴얼 확인) |
| 태블릿 | 192.168.1.100 / 255.255.255.0 | 설정 화면의 "이더넷 고정 IP 설정(루트)" 버튼 또는 시스템 설정 |

## 현장 점검 순서

1. 실기기 OS 확인: `adb shell getprop ro.build.version.sdk` (minSdk 23 = Android 6.0.1 이상 커버)
2. 카메라 배선 후 앱 설정 → **네트워크 상태**에서 eth0/usb0 에 IP 가 잡혔는지 확인.
   없으면 고정 IP 헬퍼(루트) 또는 시스템 설정에서 수동 지정
3. 카메라 행의 **RTSP(554) 연결 가능** 표시 확인 (불가면 IP 대역/케이블/PoE 전원 문제)
4. 카메라 편집 → **[ONVIF 에서 URL 가져오기]** (실패 시 카메라 매뉴얼의 RTSP 경로를 직접 입력.
   흔한 형식: `rtsp://IP:554/stream0` · `rtsp://IP:554/live/ch0` · `rtsp://IP:554/user=admin&...`)
5. 분할화면 부드러움 확인 — 끊기면 카메라 웹설정에서 **서브 스트림을 ≤720p/15fps** 로
6. 전체화면(메인 스트림 8MP H.265) 확인 — SoC 가 4K H.265 디코드를 못 하면
   카메라 메인 스트림을 1440p 이하로 낮출 것
7. IR 야간 전환(비트레이트 스파이크) 시 재연결 동작 확인

## 빌드

```bash
./gradlew :app:assembleDebug     # JDK 17 + Android SDK 34 필요
```

- 스택: Gradle 8.2.1 / AGP 8.2.2 / Kotlin 2.0.21 / minSdk 23 / targetSdk 34 (farmmachine-auto-steering 과 동일)
- 의존성: libvlc-all 3.6.3 (RTSP/H.265), okhttp 4.12.0 (ONVIF SOAP), recyclerview, coroutines
- CI: `.github/workflows/build-cctv-apk.yml` (롤링 디버그) / `release-apk.yml` (v* 태그 정식 릴리스)

## 구조

```
app/src/main/java/com/farmmachine/cctv/
├── MainActivity.kt                 # 분할↔전체화면 상태머신, 몰입형 풀스크린
├── model/  CameraConfig, CameraRepository      # 설정 (SharedPreferences+JSON)
├── player/ VlcEngine, CameraTileController     # libVLC 재생·재연결·워치독
├── onvif/  WsDiscovery, WsSecurity, OnvifClient, OnvifXml   # ONVIF 미니 클라이언트 (자체 구현)
├── net/    NetworkStatusHelper, RootShell      # 진단·고정IP 헬퍼
└── settings/ SettingsActivity, CameraListAdapter
```

## 관련 레포

- `Eunbangwool/farmmachine-auto-steering` — 자율조향 앱 (동일 태블릿, 스캐폴딩 공유)

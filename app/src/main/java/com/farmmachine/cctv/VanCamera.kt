package com.farmmachine.cctv

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Apollo 10 Pro(ApolloPro/MSM8953) 아날로그 카메라 전원 제어.
 *
 * ★ 기능적 사실(디컴파일에서 추출한 인터페이스 — 소스 복제 아님):
 *   이 태블릿은 카메라 전원 GPIO 를 일반 앱이 직접 못 켠다(/dev/gpio_dev = root 필요).
 *   대신 시스템 서비스 `com.van.service` 의 브로드캐스트 리시버가 시스템 권한으로 켜준다.
 *   - 액션  : com.cpdevice.action.CAMERGPIOON / ...OFF
 *   - 리시버: com.van.service / com.van.service.Camer{Gpio}On/OffBoardcastReceiver
 *   실기기 확인: `am broadcast` 로 쐈을 때 result=0 수신 확인됨.
 */
object VanCamera {
    private const val TAG = "VanCamera"
    private const val SERVICE_PKG = "com.van.service"
    private const val ACTION_ON = "com.cpdevice.action.CAMERGPIOON"
    private const val ACTION_OFF = "com.cpdevice.action.CAMERGPIOOFF"
    private const val RECEIVER_ON = "com.van.service.CamerGpioOnBoardcastReceiver"
    private const val RECEIVER_OFF = "com.van.service.CamerGpioOffBoardcastReceiver"

    @Volatile
    var lastResult: String = "미실행"
        private set

    fun powerOn(ctx: Context) = send(ctx, ACTION_ON, RECEIVER_ON, "on")
    fun powerOff(ctx: Context) = send(ctx, ACTION_OFF, RECEIVER_OFF, "off")

    private fun send(ctx: Context, action: String, receiver: String, label: String) {
        // best-effort. 서비스가 없거나 리시버 이름이 다른 변종이면 액션-only 로도 시도.
        val results = StringBuilder()
        runCatching {
            ctx.sendBroadcast(Intent(action).apply {
                component = ComponentName(SERVICE_PKG, receiver)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)   // 서비스가 정지 상태여도 깨워 전달
            })
            results.append("cmp ")
        }.onFailure { results.append("cmp-fail(${it.message}) ") }
        runCatching {
            ctx.sendBroadcast(Intent(action).apply {
                setPackage(SERVICE_PKG)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            })
            results.append("pkg ")
        }.onFailure { results.append("pkg-fail(${it.message}) ") }
        lastResult = "$label: $results".trim()
        Log.i(TAG, "카메라 전원 $lastResult")
    }
}

package com.farmmachine.cctv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 부팅 완료 시 CCTV 화면 자동 실행.
 * (Android 9(API28)은 부팅 브로드캐스트에서 액티비티 시작 허용. 단, 사용자가 앱을 최초 1회
 *  실행해 'stopped' 상태를 해제한 뒤부터 BOOT_COMPLETED 가 전달됨 — 최초 설치 후 한 번은 수동 실행.)
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("CctvBoot", "boot event: ${intent.action} → launch MainActivity")
        val launch = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(launch) }
            .onFailure { Log.e("CctvBoot", "자동 실행 실패: ${it.message}") }
    }
}

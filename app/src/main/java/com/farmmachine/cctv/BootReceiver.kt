package com.farmmachine.cctv

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 부팅 완료 시 CCTV 화면을 '지연' 실행한다.
 * AGMO Solution(관리자앱)이 부팅 시 먼저 뜨므로, 그 뒤에 CCTV 가 올라오도록
 * AlarmManager 로 [BOOT_LAUNCH_DELAY_MS] 만큼 늦춰 MainActivity 를 띄운다.
 * (리시버 onReceive 는 짧게 끝나야 하므로 sleep 대신 알람 예약 사용)
 *
 * 최초 설치 후 사용자가 앱을 1회 수동 실행해야 부팅 브로드캐스트가 전달된다(stopped 상태 해제).
 */
class BootReceiver : BroadcastReceiver() {
    companion object {
        // AGMO Solution 이 먼저 뜰 시간을 확보. 현장에서 AGMO 부팅이 더 느리면 늘릴 것.
        private const val BOOT_LAUNCH_DELAY_MS = 30_000L
        private const val TAG = "CctvBoot"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "boot event: ${intent.action} → ${BOOT_LAUNCH_DELAY_MS / 1000}s 뒤 실행 예약")
        val launch = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getActivity(context, 0, launch, flags)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val at = System.currentTimeMillis() + BOOT_LAUNCH_DELAY_MS
        runCatching { am.set(AlarmManager.RTC_WAKEUP, at, pi) }
            .onFailure { Log.e(TAG, "알람 예약 실패: ${it.message}") }
    }
}

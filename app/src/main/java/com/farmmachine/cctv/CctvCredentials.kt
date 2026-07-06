package com.farmmachine.cctv

import android.content.Context
import java.security.SecureRandom

/**
 * cast 서버 접속 자격증명. 비밀번호는 기기에서 무작위 생성해 SharedPreferences 에 저장한다.
 * → 공개 repo 소스에 비밀번호를 넣지 않으므로 소스 유출로도 노출되지 않음.
 *   태블릿 화면에 ID/PW 를 표시해 오너가 확인 후 폰에 입력.
 */
object CctvCredentials {
    const val USER = "admin"
    private const val PREFS = "cctv"
    private const val KEY_PW = "cast_pw"

    fun password(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_PW, null)?.let { return it }
        val pw = generate()
        prefs.edit().putString(KEY_PW, pw).apply()
        return pw
    }

    // 혼동되는 문자(0,o,1,l,i) 제외한 8자리
    private fun generate(): String {
        val chars = "abcdefghijkmnpqrstuvwxyz23456789"
        val sr = SecureRandom()
        return buildString { repeat(8) { append(chars[sr.nextInt(chars.length)]) } }
    }
}

package com.farmmachine.cctv.onvif

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * WS-UsernameToken PasswordDigest (ONVIF 인증 헤더).
 * digest = Base64( SHA1( nonceBytes + created(UTF8) + password(UTF8) ) )
 * 보급형 카메라는 시계가 틀린 경우가 많아 [clockOffsetMs](카메라시각-단말시각) 보정이 필수 —
 * skew 가 수 초만 나도 digest 를 거부한다.
 */
object WsSecurity {
    private const val NONCE_BYTES = 16

    fun securityHeader(user: String, pass: String, clockOffsetMs: Long): String {
        val nonce = ByteArray(NONCE_BYTES).also { SecureRandom().nextBytes(it) }
        val created = utcTimestamp(System.currentTimeMillis() + clockOffsetMs)
        val sha1 = MessageDigest.getInstance("SHA-1").apply {
            update(nonce)
            update(created.toByteArray(Charsets.UTF_8))
            update(pass.toByteArray(Charsets.UTF_8))
        }.digest()
        val nonceB64 = Base64.encodeToString(nonce, Base64.NO_WRAP)
        val digestB64 = Base64.encodeToString(sha1, Base64.NO_WRAP)
        return """
            <s:Header>
              <wsse:Security xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd"
                             xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd">
                <wsse:UsernameToken>
                  <wsse:Username>${xmlEscape(user)}</wsse:Username>
                  <wsse:Password Type="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest">$digestB64</wsse:Password>
                  <wsse:Nonce EncodingType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary">$nonceB64</wsse:Nonce>
                  <wsu:Created>$created</wsu:Created>
                </wsse:UsernameToken>
              </wsse:Security>
            </s:Header>
        """.trimIndent()
    }

    /** ISO8601 UTC (java.time 은 API 26+ 라 SimpleDateFormat 사용 — minSdk 23) */
    fun utcTimestamp(epochMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(epochMs))

    fun xmlEscape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}

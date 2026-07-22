package com.farmmachine.cctv.rtsp.onvif

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * ONVIF Profile S 최소 클라이언트 (SOAP 라이브러리 없이 hand-rolled XML).
 * 시퀀스: GetSystemDateAndTime(무인증, 시계offset) → GetCapabilities(Media XAddr)
 *        → GetProfiles → 프로파일별 GetStreamUri.
 * 최고해상도 → mainUrl, 최저해상도 → subUrl.
 *
 * 보급형 카메라 방어: XAddr/스트림 URI 의 호스트가 엉뚱하면(공장 고정값 반환 흔함)
 * 사용자가 입력한 카메라 IP 로 재작성한다.
 */
class OnvifClient(
    private val host: String,
    private val port: Int,
    private val user: String,
    private val pass: String
) {
    data class StreamUris(
        val mainUrl: String,
        val subUrl: String,
        /** "2560x1440, 640x360" 형태 — UI 표시용 */
        val resolutions: String
    )

    class OnvifAuthException(message: String) : Exception(message)

    private val http = OkHttpClient.Builder()
        .connectTimeout(HTTP_TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(HTTP_TIMEOUT_S, TimeUnit.SECONDS)
        .build()

    private var clockOffsetMs = 0L

    /** 블로킹 — Dispatchers.IO 에서 호출할 것 */
    fun fetchStreamUris(): StreamUris {
        val deviceService = "http://$host:$port/onvif/device_service"

        // 1) 시계 offset (무인증 — 인증 실패와 무관하게 먼저)
        val dtResp = post(deviceService, envelope(header = "", body = BODY_GET_DATETIME))
        OnvifXml.parseUtcDateTime(dtResp)?.let {
            clockOffsetMs = it - System.currentTimeMillis()
        } // 파싱 실패 시 offset 0 으로 진행 (시계 맞는 카메라면 문제 없음)

        // 2) Media 서비스 XAddr
        val capResp = postAuth(deviceService, BODY_GET_CAPABILITIES)
        val mediaXAddr = OnvifXml.textInside(capResp, "Media", "XAddr")
            ?.let { rewriteHost(it) }
            ?: "http://$host:$port/onvif/media_service"   // 일부 기종 GetCapabilities 부실 → 관례 경로

        // 3) 프로파일 목록
        val profResp = postAuth(mediaXAddr, BODY_GET_PROFILES)
        val profiles = OnvifXml.parseProfiles(profResp)
        if (profiles.isEmpty()) throw IllegalStateException("no media profiles")

        // 4) 프로파일별 스트림 URI — 해상도 내림차순으로 main/sub 선정
        val sorted = profiles.sortedByDescending { it.width.toLong() * it.height }
        val mainProfile = sorted.first()
        val subProfile = sorted.last()

        val mainUri = fetchUri(mediaXAddr, mainProfile.token)
        val subUri =
            if (subProfile.token == mainProfile.token) "" else fetchUri(mediaXAddr, subProfile.token)

        val resolutions = sorted.joinToString(", ") { "${it.width}x${it.height}" }
        return StreamUris(mainUri, subUri, resolutions)
    }

    private fun fetchUri(mediaXAddr: String, profileToken: String): String {
        val resp = postAuth(mediaXAddr, bodyGetStreamUri(profileToken))
        val uri = OnvifXml.firstText(resp, "Uri")
            ?: throw IllegalStateException("GetStreamUri: no Uri for $profileToken")
        return rewriteHost(uri)
    }

    /** URL 의 호스트만 카메라 IP 로 교체 (포트·경로 유지) */
    internal fun rewriteHost(url: String): String = runCatching {
        val u = URI(url)
        if (u.host == host) return url
        URI(u.scheme, u.userInfo, host, u.port, u.path, u.query, u.fragment).toString()
    }.getOrDefault(url)

    private fun postAuth(url: String, body: String): String {
        val header = WsSecurity.securityHeader(user, pass, clockOffsetMs)
        val resp = post(url, envelope(header, body))
        // SOAP Fault 로 오는 인증 실패 감지 (HTTP 200 으로 Fault 를 주는 기종도 있음)
        if (resp.contains("NotAuthorized", ignoreCase = true) ||
            resp.contains("Sender not Authorized", ignoreCase = true)
        ) throw OnvifAuthException("not authorized")
        return resp
    }

    private fun post(url: String, xml: String): String {
        val request = Request.Builder()
            .url(url)
            .post(xml.toRequestBody(SOAP_MEDIA_TYPE))
            .build()
        http.newCall(request).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (resp.code == 401) throw OnvifAuthException("HTTP 401")
            if (!resp.isSuccessful && !text.contains("Envelope")) {
                throw IllegalStateException("HTTP ${resp.code} from $url")
            }
            return text
        }
    }

    companion object {
        private const val HTTP_TIMEOUT_S = 10L
        private val SOAP_MEDIA_TYPE = "application/soap+xml; charset=utf-8".toMediaType()

        // 주의: trimIndent 는 다중행 $header 삽입 시 공통 들여쓰기 계산이 깨져
        // XML 선언 앞에 공백이 남을 수 있음 → 한 줄 조립으로 고정
        private fun envelope(header: String, body: String) =
            """<?xml version="1.0" encoding="UTF-8"?><s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope">$header<s:Body>$body</s:Body></s:Envelope>"""

        private const val BODY_GET_DATETIME =
            """<tds:GetSystemDateAndTime xmlns:tds="http://www.onvif.org/ver10/device/wsdl"/>"""

        private const val BODY_GET_CAPABILITIES =
            """<tds:GetCapabilities xmlns:tds="http://www.onvif.org/ver10/device/wsdl"><tds:Category>Media</tds:Category></tds:GetCapabilities>"""

        private const val BODY_GET_PROFILES =
            """<trt:GetProfiles xmlns:trt="http://www.onvif.org/ver10/media/wsdl"/>"""

        private fun bodyGetStreamUri(token: String) = """
            <trt:GetStreamUri xmlns:trt="http://www.onvif.org/ver10/media/wsdl"
                              xmlns:tt="http://www.onvif.org/ver10/schema">
              <trt:StreamSetup>
                <tt:Stream>RTP-Unicast</tt:Stream>
                <tt:Transport><tt:Protocol>RTSP</tt:Protocol></tt:Transport>
              </trt:StreamSetup>
              <trt:ProfileToken>${WsSecurity.xmlEscape(token)}</trt:ProfileToken>
            </trt:GetStreamUri>
        """.trimIndent()
    }
}

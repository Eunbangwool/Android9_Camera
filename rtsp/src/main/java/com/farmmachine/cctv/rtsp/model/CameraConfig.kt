package com.farmmachine.cctv.rtsp.model

import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID

/**
 * 카메라 1대 설정. URL 은 자격증명 없이 저장하고, 재생 직전에 [rtspWithAuth] 로 주입한다.
 * (ONVIF GetStreamUri 는 자격증명 없는 URI 를 돌려주며, 보급형 카메라는 URL 인라인 인증이 가장 확실)
 */
data class CameraConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val onvifPort: Int = DEFAULT_ONVIF_PORT,
    val user: String = DEFAULT_USER,
    val pass: String = "",
    /** 메인 스트림(고해상도) — 전체화면 재생용 */
    val mainUrl: String,
    /** 서브 스트림(저해상도) — 분할화면 재생용. 비어 있으면 mainUrl 로 폴백 */
    val subUrl: String = ""
) {
    /** 분할화면 타일이 실제로 재생할 URL */
    fun effectiveSubUrl(): String = subUrl.ifBlank { mainUrl }

    /** rtsp://user:pass@host... 형태로 자격증명 주입. 이미 user@ 가 있거나 계정이 비면 원본 유지 */
    fun rtspWithAuth(url: String): String {
        if (user.isBlank()) return url
        val schemeSep = url.indexOf("://")
        if (schemeSep < 0) return url
        val afterScheme = url.substring(schemeSep + 3)
        if (afterScheme.contains('@')) return url
        val u = URLEncoder.encode(user, "UTF-8")
        val p = URLEncoder.encode(pass, "UTF-8")
        return url.substring(0, schemeSep + 3) + "$u:$p@" + afterScheme
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("host", host)
        put("onvifPort", onvifPort)
        put("user", user)
        put("pass", pass)
        put("mainUrl", mainUrl)
        put("subUrl", subUrl)
    }

    companion object {
        const val DEFAULT_ONVIF_PORT = 80     // ONVIF 디바이스 서비스 관례 포트 (일부 기종 8000/8899)
        const val DEFAULT_USER = "admin"      // 보급형 IP 카메라 공장 기본 계정
        const val RTSP_PORT = 554             // RTSP 표준 포트 (도달성 프로브용)

        fun fromJson(o: JSONObject): CameraConfig = CameraConfig(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name", ""),
            host = o.optString("host", ""),
            onvifPort = o.optInt("onvifPort", DEFAULT_ONVIF_PORT),
            user = o.optString("user", DEFAULT_USER),
            pass = o.optString("pass", ""),
            mainUrl = o.optString("mainUrl", ""),
            subUrl = o.optString("subUrl", "")
        )
    }
}

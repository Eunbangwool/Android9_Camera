package com.farmmachine.cctv.onvif

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/**
 * ONVIF 응답 파싱 헬퍼. 카메라마다 네임스페이스 프리픽스가 제각각이라
 * 전부 local name 기준으로만 매칭한다.
 */
object OnvifXml {

    data class ProfileInfo(val token: String, val width: Int, val height: Int)

    private fun newParser(xml: String): XmlPullParser =
        XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
            .newPullParser().apply { setInput(StringReader(xml)) }

    /** 문서 전체에서 첫 <localName> 텍스트 */
    fun firstText(xml: String, localName: String): String? {
        val p = newParser(xml)
        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && p.name == localName) {
                return p.nextText().trim()
            }
            event = p.next()
        }
        return null
    }

    /** <outer> ... <inner>text</inner> ... </outer> 범위 안의 첫 inner 텍스트 (예: Media/XAddr) */
    fun textInside(xml: String, outerLocal: String, innerLocal: String): String? {
        val p = newParser(xml)
        var event = p.eventType
        var depth = 0
        var insideOuter = false
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (!insideOuter && p.name == outerLocal) {
                        insideOuter = true
                        depth = p.depth
                    } else if (insideOuter && p.name == innerLocal) {
                        return p.nextText().trim()
                    }
                }
                XmlPullParser.END_TAG ->
                    if (insideOuter && p.name == outerLocal && p.depth == depth) insideOuter = false
            }
            event = p.next()
        }
        return null
    }

    /**
     * GetProfiles 응답 → (token, 해상도) 목록.
     * 프로파일 요소는 local name "Profiles"(trt:Profiles) 반복, token 속성 필수,
     * 해상도는 VideoEncoderConfiguration/Resolution/Width·Height.
     */
    fun parseProfiles(xml: String): List<ProfileInfo> {
        val result = mutableListOf<ProfileInfo>()
        val p = newParser(xml)
        var event = p.eventType
        var token: String? = null
        var profileDepth = 0
        var width = 0
        var height = 0
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when {
                    p.name == "Profiles" && token == null -> {
                        token = p.getAttributeValue(null, "token") ?: ""
                        profileDepth = p.depth
                        width = 0; height = 0
                    }
                    token != null && p.name == "Width" ->
                        width = p.nextText().trim().toIntOrNull() ?: width
                    token != null && p.name == "Height" ->
                        height = p.nextText().trim().toIntOrNull() ?: height
                }
                XmlPullParser.END_TAG ->
                    if (token != null && p.name == "Profiles" && p.depth == profileDepth) {
                        if (token.isNotBlank()) result.add(ProfileInfo(token, width, height))
                        token = null
                    }
            }
            event = p.next()
        }
        return result
    }

    /**
     * GetSystemDateAndTime 응답의 UTCDateTime → epoch ms.
     * 요소: UTCDateTime > Time(Hour/Minute/Second) + Date(Year/Month/Day)
     */
    fun parseUtcDateTime(xml: String): Long? {
        val fields = mutableMapOf<String, Int>()
        val wanted = setOf("Hour", "Minute", "Second", "Year", "Month", "Day")
        val p = newParser(xml)
        var event = p.eventType
        var insideUtc = false
        var utcDepth = 0
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (p.name == "UTCDateTime") { insideUtc = true; utcDepth = p.depth }
                    else if (insideUtc && p.name in wanted && p.name !in fields) {
                        fields[p.name] = p.nextText().trim().toIntOrNull() ?: return null
                    }
                }
                XmlPullParser.END_TAG ->
                    if (insideUtc && p.name == "UTCDateTime" && p.depth == utcDepth) insideUtc = false
            }
            event = p.next()
        }
        if (!fields.keys.containsAll(wanted)) return null
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(
                fields.getValue("Year"), fields.getValue("Month") - 1, fields.getValue("Day"),
                fields.getValue("Hour"), fields.getValue("Minute"), fields.getValue("Second")
            )
        }
        return cal.timeInMillis
    }

    /** WS-Discovery ProbeMatch 응답에서 XAddrs(공백 구분 URL 목록) 전부 수집 */
    fun parseXAddrs(xml: String): List<String> {
        val urls = mutableListOf<String>()
        val p = newParser(xml)
        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && p.name == "XAddrs") {
                urls += p.nextText().trim().split(Regex("\\s+")).filter { it.startsWith("http") }
            }
            event = p.next()
        }
        return urls
    }
}

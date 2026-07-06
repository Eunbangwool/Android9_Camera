package com.farmmachine.cctv

/**
 * 채널별 최신 NV21 프레임 공유 버퍼 (캡처 스레드 → MJPEG 서버 스레드).
 * cast 모드에서만 publish 된다.
 */
object FrameHub {
    class Frame(val nv21: ByteArray, val w: Int, val h: Int, val id: Long)

    private val latest = arrayOfNulls<Frame>(4)

    fun publish(channel: Int, nv21: ByteArray, w: Int, h: Int, id: Long) {
        if (channel in latest.indices) latest[channel] = Frame(nv21, w, h, id)
    }

    fun get(channel: Int): Frame? = if (channel in latest.indices) latest[channel] else null
}

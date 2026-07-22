package com.farmmachine.cctv.rtsp.player

import android.content.Context
import android.net.Uri
import com.farmmachine.cctv.rtsp.BuildConfig
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

/**
 * 프로세스 전역 LibVLC 싱글턴 + MediaPlayer 팩토리.
 * LibVLC 인스턴스 하나를 모든 플레이어가 공유하는 것이 공식 권장 패턴(메모리 절약).
 */
object VlcEngine {
    /** RTSP 지연 vs 끊김 트레이드오프. CCTV 는 0.3s 버퍼가 무난 (필요시 현장 튜닝) */
    private const val NETWORK_CACHING_MS = 300

    @Volatile
    private var libVlc: LibVLC? = null

    fun get(context: Context): LibVLC =
        libVlc ?: synchronized(this) {
            libVlc ?: LibVLC(context.applicationContext, buildOptions()).also { libVlc = it }
        }

    private fun buildOptions() = arrayListOf(
        "--rtsp-tcp",            // UDP 는 직결/보급형 카메라에서 패킷 드랍 잦음 → TCP interleaved 고정
        "--drop-late-frames",    // 실시간 우선: 늦은 프레임 버림
        "--skip-frames",
        "--no-stats"
    ).apply {
        if (BuildConfig.DEBUG) add("-vv")   // 디버그 빌드만 verbose 로그
    }

    /**
     * RTSP 플레이어 생성. 호출측이 stop()/release() 책임.
     * @param audio 전체화면만 true — 분할 타일 4개가 동시에 소리내면 안 됨
     */
    fun createPlayer(context: Context, url: String, audio: Boolean): MediaPlayer {
        val lib = get(context)
        val media = Media(lib, Uri.parse(url)).apply {
            // (enable=true, force=false): MediaCodec HW 디코드 우선, 프로파일 미지원 시 SW 폴백 허용.
            // force=true 는 SoC 가 4K H.265 를 거부하면 재생 자체가 죽으므로 금지.
            setHWDecoderEnabled(true, false)
            addOption(":network-caching=$NETWORK_CACHING_MS")
            addOption(":clock-jitter=0")
            addOption(":clock-synchro=0")
            if (!audio) addOption(":no-audio")
        }
        return MediaPlayer(lib).apply {
            this.media = media
            media.release()   // MediaPlayer 가 참조를 쥐므로 로컬 참조 해제
        }
    }
}

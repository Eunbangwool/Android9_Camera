package com.farmmachine.cctv.player

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.farmmachine.cctv.R
import com.farmmachine.cctv.model.CameraConfig
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * 타일 1개 = 카메라 1대 재생 담당.
 * 생명주기: [start] → (에러/정지 시 백오프 재연결, 프리즈 워치독) → [stop]
 * RTSP 는 pause/resume 이 깨끗하지 않아 재시작은 항상 완전 release 후 재생성.
 */
class CameraTileController(
    private val parent: ViewGroup,
    private val camera: CameraConfig,
    /** true=메인 스트림(전체화면, 오디오 on) / false=서브 스트림(그리드, 오디오 off) */
    private val useMainStream: Boolean,
    private val onTap: (() -> Unit)? = null
) {
    companion object {
        private const val TAG = "CameraTile"
        // 재연결 백오프: 1→2→4→8s, 상한 10s, 무한 재시도 (고정 설치 모니터라 포기하지 않음)
        private const val BACKOFF_BASE_MS = 1_000L
        private const val BACKOFF_CAP_MS = 10_000L
        // 재생 중 TimeChanged 가 이 시간 이상 멈추면 세션 프리즈로 판단(에러 이벤트 없이 얼어붙는 RTSP 대응)
        private const val FREEZE_TIMEOUT_MS = 8_000L
        private const val WATCHDOG_PERIOD_MS = 5_000L
        // 이 시간 이상 안정 재생되면 백오프 카운터 리셋
        private const val STABLE_RESET_MS = 10_000L
    }

    val view: View = LayoutInflater.from(parent.context)
        .inflate(R.layout.view_camera_tile, parent, false)

    private val videoLayout: VLCVideoLayout = view.findViewById(R.id.videoLayout)
    private val nameLabel: TextView = view.findViewById(R.id.nameLabel)
    private val statusOverlay: TextView = view.findViewById(R.id.statusOverlay)

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var retryCount = 0
    private var lastTimeChangedAt = 0L
    private var playingSince = 0L
    private var stopped = true

    /** 마지막 실패 사유 — 조용히 먹지 않고 로그+오버레이에 남김 */
    var lastError: String? = null
        private set

    init {
        nameLabel.text = camera.name
        onTap?.let { tap -> view.setOnClickListener { tap() } }
    }

    fun start() {
        stopped = false
        parent.post { startPlayerInternal() }
        handler.postDelayed(watchdog, WATCHDOG_PERIOD_MS)
    }

    fun stop() {
        stopped = true
        handler.removeCallbacksAndMessages(null)
        releasePlayer()
    }

    private fun streamUrl(): String {
        val raw = if (useMainStream) camera.mainUrl else camera.effectiveSubUrl()
        return camera.rtspWithAuth(raw)
    }

    private fun startPlayerInternal() {
        if (stopped) return
        releasePlayer()
        showOverlay(parent.context.getString(R.string.tile_connecting))
        val p = VlcEngine.createPlayer(parent.context, streamUrl(), audio = useMainStream)
        player = p
        p.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing, MediaPlayer.Event.Vout -> {
                    if (playingSince == 0L) playingSince = SystemClock.elapsedRealtime()
                    lastTimeChangedAt = SystemClock.elapsedRealtime()
                    hideOverlay()
                }
                MediaPlayer.Event.TimeChanged -> {
                    lastTimeChangedAt = SystemClock.elapsedRealtime()
                    // 일정 시간 안정 재생 → 백오프 리셋 (다음 장애는 1초부터 다시)
                    if (retryCount > 0 && playingSince > 0 &&
                        SystemClock.elapsedRealtime() - playingSince > STABLE_RESET_MS
                    ) retryCount = 0
                }
                MediaPlayer.Event.EncounteredError -> scheduleRestart("player error")
                MediaPlayer.Event.EndReached -> scheduleRestart("stream ended")
                MediaPlayer.Event.Stopped -> if (!stopped) scheduleRestart("unexpected stop")
            }
        }
        // (subtitle view 없음, ANDROID_SURFACE=SurfaceView 경로 — 텍스처 대비 가장 저렴)
        p.attachViews(videoLayout, null, false, false)
        p.play()
    }

    private fun scheduleRestart(reason: String) {
        if (stopped) return
        lastError = reason
        Log.w(TAG, "[${camera.name}] $reason → retry ${retryCount + 1}")
        playingSince = 0L
        retryCount++
        showOverlay(parent.context.getString(R.string.tile_retry_fmt, retryCount))
        val delay = (BACKOFF_BASE_MS shl (retryCount - 1).coerceAtMost(3))
            .coerceAtMost(BACKOFF_CAP_MS)
        handler.removeCallbacks(restartRunnable)
        handler.postDelayed(restartRunnable, delay)
    }

    private val restartRunnable = Runnable { startPlayerInternal() }

    private val watchdog = object : Runnable {
        override fun run() {
            if (stopped) return
            val p = player
            if (p != null && p.isPlaying && lastTimeChangedAt > 0 &&
                SystemClock.elapsedRealtime() - lastTimeChangedAt > FREEZE_TIMEOUT_MS
            ) {
                scheduleRestart("frozen (no frames ${FREEZE_TIMEOUT_MS / 1000}s)")
            }
            handler.postDelayed(this, WATCHDOG_PERIOD_MS)
        }
    }

    private fun releasePlayer() {
        player?.let { p ->
            runCatching {
                p.setEventListener(null)
                p.stop()
                p.detachViews()
                p.release()
            }.onFailure { Log.w(TAG, "release failed: ${it.message}") }
        }
        player = null
        playingSince = 0L
        lastTimeChangedAt = 0L
    }

    private fun showOverlay(text: String) {
        statusOverlay.text = text
        statusOverlay.visibility = View.VISIBLE
    }

    private fun hideOverlay() {
        statusOverlay.visibility = View.GONE
    }
}

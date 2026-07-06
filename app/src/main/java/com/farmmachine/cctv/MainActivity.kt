package com.farmmachine.cctv

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

/**
 * Apollo 10 Pro 아날로그 카메라 CCTV 뷰어 — qcarcam(Qualcomm AIS).
 *
 * 최대 4채널 2×2 그리드(RN6864 디코더 상한). 타일 탭 → 전체화면 / 다시 탭 → 그리드.
 * 신호 없는 채널은 "신호 없음" 표시. cast 플레이버면 MJPEG 서버로 폰 시청.
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val COLS = 2       // 2x2 그리드
        private const val CHANNELS = 4   // RN6864 최대 4채널 (실측용)
    }

    private val castMode = BuildConfig.CAST_MODE
    private val controller = CameraController(inputNum = CHANNELS, publishFrames = castMode)
    private var server: MjpegServer? = null

    private lateinit var grid: LinearLayout
    private lateinit var status: TextView
    private val rows = ArrayList<LinearLayout>()
    private val tileBox = arrayOfNulls<View>(CHANNELS)
    private val surfaces = arrayOfNulls<SurfaceView>(CHANNELS)
    private val overlays = arrayOfNulls<TextView>(CHANNELS)
    private val rowOfChannel = IntArray(CHANNELS)
    private val surfaceReady = BooleanArray(CHANNELS)

    @Volatile private var cameraReady = false
    private var fullscreen: Int? = null
    private var statusPinned = true

    private val ui = Handler(Looper.getMainLooper())
    private val worker = Handler(
        android.os.HandlerThread("qcar-init").apply { start() }.looper
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        grid = findViewById(R.id.grid)
        status = findViewById(R.id.status)
        buildGrid()

        showStatus("카메라 전원 인가 중…")
        VanCamera.powerOn(this)

        if (castMode) {
            val pw = CctvCredentials.password(this)
            server = MjpegServer(
                channels = CHANNELS,
                authUser = CctvCredentials.USER,
                authPass = pw
            ).also { it.start() }
        }

        worker.post {
            val loaded = controller.loadLibraries()
            ui.post { if (statusPinned) showStatus(controller.status) }
            if (!loaded) return@post
            val opened = controller.open()
            ui.post {
                if (statusPinned) showStatus(controller.status)
                if (opened) {
                    cameraReady = true
                    for (ch in 0 until CHANNELS) if (surfaceReady[ch]) startChannel(ch)
                }
            }
        }
        ui.post(statusTick)
    }

    /** 채널 수에 맞춰 2×2 그리드 타일 생성 */
    private fun buildGrid() {
        val rowsCount = (CHANNELS + COLS - 1) / COLS
        for (r in 0 until rowsCount) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            grid.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            rows.add(row)
            for (c in 0 until COLS) {
                val ch = r * COLS + c
                if (ch >= CHANNELS) {
                    row.addView(View(this), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
                    continue
                }
                val box = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
                val sv = SurfaceView(this)
                box.addView(sv, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                val ov = TextView(this).apply {
                    text = "CH${ch + 1} 신호 없음"
                    setTextColor(Color.WHITE)
                    textSize = 14f
                }
                box.addView(ov, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
                row.addView(box, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))

                tileBox[ch] = box; surfaces[ch] = sv; overlays[ch] = ov; rowOfChannel[ch] = r
                bindSurface(sv, ch)
                sv.setOnClickListener { toggleFullscreen(ch) }
            }
        }
    }

    private fun bindSurface(view: SurfaceView, channel: Int) {
        view.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                surfaceReady[channel] = true
                if (cameraReady) startChannel(channel)
            }
            override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, ht: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) { surfaceReady[channel] = false }
        })
    }

    private fun startChannel(ch: Int) {
        surfaces[ch]?.let { controller.startChannel(ch, it.holder) }
    }

    private fun toggleFullscreen(ch: Int) {
        fullscreen = if (fullscreen == ch) null else ch
        applyMode()
    }

    private fun applyMode() {
        val f = fullscreen
        if (f == null) {
            rows.forEach { row ->
                row.visibility = View.VISIBLE; setWeight(row, 1f)
                for (k in 0 until row.childCount) row.getChildAt(k).let { it.visibility = View.VISIBLE; setWeight(it, 1f) }
            }
        } else {
            val fr = rowOfChannel[f]
            rows.forEachIndexed { i, row ->
                if (i != fr) { row.visibility = View.GONE; return@forEachIndexed }
                row.visibility = View.VISIBLE; setWeight(row, 1f)
                for (k in 0 until row.childCount) {
                    val child = row.getChildAt(k)
                    if (child === tileBox[f]) { child.visibility = View.VISIBLE; setWeight(child, 1f) }
                    else child.visibility = View.GONE
                }
            }
        }
    }

    private fun setWeight(v: View, weight: Float) {
        (v.layoutParams as? LinearLayout.LayoutParams)?.let { it.weight = weight; v.layoutParams = it }
    }

    private val statusTick = object : Runnable {
        override fun run() {
            // 채널별 신호 없음 오버레이 갱신
            for (ch in 0 until CHANNELS) {
                overlays[ch]?.visibility = if (controller.frames(ch) > 0) View.GONE else View.VISIBLE
            }
            if (castMode) {
                val ip = server?.wifiIpAddress()
                val port = server?.boundPort ?: -1
                val url = if (ip != null && port > 0) "http://$ip:$port" else "Wi-Fi/서버 확인 필요"
                val cred = "🔒 ID: ${CctvCredentials.USER}  PW: ${CctvCredentials.password(this@MainActivity)}"
                if (controller.framesFlowing()) showStatus("📱 폰 접속: $url\n$cred")
                else if (statusPinned) showStatus("${controller.status}\n프레임: ${controller.frameSummary()}\n폰 접속: $url\n$cred")
            } else if (statusPinned) {
                showStatus("${controller.status}\n프레임: ${controller.frameSummary()}\n(카메라 탭 = 전체화면/복귀)")
                if (controller.framesFlowing()) { statusPinned = false; status.visibility = View.GONE }
            }
            ui.postDelayed(this, 1000)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersive()
    }

    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        server?.stop()
        worker.post { controller.stop() }
        super.onDestroy()
    }

    private fun showStatus(text: String) {
        status.text = text
        status.visibility = View.VISIBLE
    }

    @Suppress("DEPRECATION")
    private fun enterImmersive() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }
}

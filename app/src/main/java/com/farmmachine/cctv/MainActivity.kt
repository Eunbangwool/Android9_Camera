package com.farmmachine.cctv

import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
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
 * 최대 4채널. 분할화면(멀티윈도우) 대응:
 *  - resizeableActivity, 방향고정 없음. 리사이즈/멀티윈도우 시 재생성 없이 onConfigurationChanged 로
 *    타일만 재배치(카메라는 계속 열린 상태 유지 — 벤더 디코드 원본 해상도 그대로, 표시 Surface만 스케일).
 *  - 창 가로세로 비율에 따라 반응형 배치(좁은 반쪽 폭이면 세로 스택).
 *  - 렌더는 ChannelReader 가 캔버스 크기 기준 레터박스(하드코딩 없음).
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val CHANNELS = 4   // RN6864 최대 4채널
    }

    private val castMode = BuildConfig.CAST_MODE
    private val controller = CameraController(inputNum = CHANNELS, publishFrames = castMode)
    private var server: MjpegServer? = null

    private lateinit var root: FrameLayout
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
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(grid, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        status = TextView(this).apply {
            setBackgroundColor(0x88000000.toInt())
            setTextColor(Color.WHITE)
            textSize = 12f
            val p = (8 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
        }
        root.addView(status, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START))
        setContentView(root)

        buildTiles()

        showStatus("카메라 전원 인가 중…")
        VanCamera.powerOn(this)

        if (castMode) {
            val pw = CctvCredentials.password(this)
            server = MjpegServer(channels = CHANNELS, authUser = CctvCredentials.USER, authPass = pw).also { it.start() }
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

    /** 창 크기(비율)에 맞춰 타일 그리드를 (재)구성. 카메라는 닫지 않고 리더만 재시작. */
    private fun buildTiles() {
        controller.stopReaders()
        grid.removeAllViews()
        rows.clear()
        for (i in 0 until CHANNELS) { tileBox[i] = null; surfaces[i] = null; overlays[i] = null; surfaceReady[i] = false }
        fullscreen = null

        val cols = computeCols(CHANNELS)
        val rowsCount = (CHANNELS + cols - 1) / cols
        var ch = 0
        for (r in 0 until rowsCount) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            grid.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            rows.add(row)
            for (c in 0 until cols) {
                if (ch >= CHANNELS) {
                    row.addView(View(this), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
                    continue
                }
                val box = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
                val sv = SurfaceView(this)
                box.addView(sv, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                val ov = TextView(this).apply { text = "CH${ch + 1} 신호 없음"; setTextColor(Color.WHITE); textSize = 14f }
                box.addView(ov, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
                row.addView(box, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))

                val channel = ch
                tileBox[channel] = box; surfaces[channel] = sv; overlays[channel] = ov; rowOfChannel[channel] = r
                bindSurface(sv, channel)
                sv.setOnClickListener { toggleFullscreen(channel) }
                ch++
            }
        }
    }

    /** 창 가로세로 비율 기반 열 수. 좁은(세로로 긴) 반쪽 폭이면 세로로 쌓음. */
    private fun computeCols(n: Int): Int {
        if (n <= 1) return 1
        val cfg = resources.configuration
        val w = cfg.screenWidthDp
        val h = cfg.screenHeightDp
        val aspect = if (h > 0) w.toFloat() / h else 1.7f
        return when {
            aspect < 1.0f -> if (n <= 3) 1 else 2   // 좁은 반쪽 → 세로 스택(2채널이면 위/아래)
            else -> Math.ceil(Math.sqrt(n.toDouble())).toInt()
        }.coerceIn(1, n)
    }

    private fun bindSurface(view: SurfaceView, channel: Int) {
        view.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                surfaceReady[channel] = true
                if (cameraReady) startChannel(channel)
            }
            // surfaceChanged(w,h): 표시 Surface 크기만 바뀜. ChannelReader 가 매 프레임 캔버스
            // 크기 기준 레터박스로 그리므로 별도 처리 불필요(벤더 디코드 해상도는 불변).
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

    // ── 멀티윈도우/리사이즈 ─────────────────────────────────────
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 창 크기 변경(분할화면 진입/리사이즈) → 카메라 유지한 채 타일만 재배치
        buildTiles()
        applyImmersive()
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        buildTiles()
        applyImmersive()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersive()
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

    /** 단독 실행 시에만 몰입형 전체화면. 분할화면(멀티윈도우)에선 강제하지 않음. */
    @Suppress("DEPRECATION")
    private fun applyImmersive() {
        val multi = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInMultiWindowMode
        window.decorView.systemUiVisibility = if (multi) {
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        } else {
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
    }
}

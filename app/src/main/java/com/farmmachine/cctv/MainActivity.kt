package com.farmmachine.cctv

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

/**
 * Apollo 10 Pro 아날로그 카메라 CCTV 뷰어 — qcarcam(Qualcomm AIS) 2채널.
 *
 * 분할(좌 ch0 / 우 ch1) ↔ 전체화면 토글: 카메라 화면을 탭하면 그 채널만 전체화면,
 * 전체화면에서 다시 탭하면 분할로 복귀. 렌더는 비율 유지(레터박스).
 */
class MainActivity : ComponentActivity() {

    private val controller = CameraController()
    private lateinit var status: TextView
    private lateinit var surface0: SurfaceView
    private lateinit var surface1: SurfaceView
    private lateinit var divider: View

    private val surfaceReady = BooleanArray(2)
    @Volatile private var cameraReady = false
    private var fullscreen: Int? = null          // null=분할, 0/1=해당 채널 전체화면
    private var statusPinned = true               // 프레임 들어오면 자동 숨김

    private val ui = Handler(Looper.getMainLooper())
    private val worker = Handler(
        android.os.HandlerThread("qcar-init").apply { start() }.looper
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        surface0 = findViewById(R.id.surface0)
        surface1 = findViewById(R.id.surface1)
        divider = findViewById(R.id.divider)
        bindSurface(surface0, 0)
        bindSurface(surface1, 1)
        surface0.setOnClickListener { toggleFullscreen(0) }
        surface1.setOnClickListener { toggleFullscreen(1) }

        showStatus("카메라 전원 인가 중…")
        VanCamera.powerOn(this)

        worker.post {
            val loaded = controller.loadLibraries()
            ui.post { if (statusPinned) showStatus(controller.status) }
            if (!loaded) return@post
            val opened = controller.open()
            ui.post {
                if (statusPinned) showStatus(controller.status)
                if (opened) {
                    cameraReady = true
                    for (ch in 0..1) if (surfaceReady[ch]) startChannel(ch)
                }
            }
        }
        ui.post(statusTick)
    }

    private fun bindSurface(view: SurfaceView, channel: Int) {
        view.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                surfaceReady[channel] = true
                if (cameraReady) startChannel(channel)
            }
            override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, ht: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                surfaceReady[channel] = false
            }
        })
    }

    private fun startChannel(ch: Int) {
        val holder = if (ch == 0) surface0.holder else surface1.holder
        controller.startChannel(ch, holder)
    }

    /** 채널 전체화면 ↔ 분할 토글 */
    private fun toggleFullscreen(ch: Int) {
        fullscreen = if (fullscreen == ch) null else ch
        applyMode()
    }

    private fun applyMode() {
        val f = fullscreen
        if (f == null) {
            setWeight(surface0, 1f); surface0.visibility = View.VISIBLE
            setWeight(surface1, 1f); surface1.visibility = View.VISIBLE
            divider.visibility = View.VISIBLE
        } else {
            divider.visibility = View.GONE
            surface0.visibility = if (f == 0) View.VISIBLE else View.GONE
            surface1.visibility = if (f == 1) View.VISIBLE else View.GONE
            setWeight(if (f == 0) surface0 else surface1, 1f)
        }
    }

    private fun setWeight(v: View, weight: Float) {
        (v.layoutParams as LinearLayout.LayoutParams).let {
            it.weight = weight; v.layoutParams = it
        }
    }

    private val statusTick = object : Runnable {
        override fun run() {
            if (statusPinned) {
                showStatus("${controller.status}\n프레임: ${controller.frameSummary()}\n(카메라 탭 = 전체화면/복귀)")
                // 양 채널 프레임이 흐르기 시작하면 상태창 자동 숨김
                if (controller.framesFlowing()) {
                    statusPinned = false
                    status.visibility = View.GONE
                }
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

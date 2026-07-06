package com.farmmachine.cctv

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity

/**
 * Apollo 10 Pro 아날로그 카메라 CCTV 뷰어 — qcarcam(Qualcomm AIS) 2채널 분할.
 *
 * 흐름: CAMERGPIOON 전원 → Quectel .so 로드 → cameraOpen(csi0, 2채널) →
 *       채널0·1 프레임 폴링해 좌/우 SurfaceView 렌더.
 * 표준 Camera API 는 파란 stub 이라 불가 → 벤더 .so 경로로 구현(현장 로그로 확정).
 */
class MainActivity : ComponentActivity() {

    private val controller = CameraController()
    private lateinit var status: TextView
    private val surfaceReady = BooleanArray(2)
    @Volatile private var cameraReady = false

    private val ui = Handler(Looper.getMainLooper())
    private val worker = Handler(
        android.os.HandlerThread("qcar-init").apply { start() }.looper
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        bindSurface(findViewById(R.id.surface0), 0)
        bindSurface(findViewById(R.id.surface1), 1)

        showStatus("카메라 전원 인가 중…")
        VanCamera.powerOn(this)

        // 라이브러리 로드 + 카메라 오픈은 블로킹 가능 → 워커 스레드
        worker.post {
            val loaded = controller.loadLibraries()
            ui.post { showStatus(controller.status) }
            if (!loaded) return@post
            val opened = controller.open()
            ui.post {
                showStatus(controller.status)
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
        val holder = when (ch) {
            0 -> findViewById<SurfaceView>(R.id.surface0).holder
            else -> findViewById<SurfaceView>(R.id.surface1).holder
        }
        controller.startChannel(ch, holder)
    }

    private val statusTick = object : Runnable {
        override fun run() {
            showStatus("${controller.status}\n프레임: ${controller.frameSummary()}")
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

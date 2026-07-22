package com.farmmachine.cctv.rtsp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import com.farmmachine.cctv.rtsp.model.CameraConfig
import com.farmmachine.cctv.rtsp.model.CameraRepository
import com.farmmachine.cctv.rtsp.player.CameraTileController
import com.farmmachine.cctv.rtsp.settings.SettingsActivity

/**
 * 그리드(분할화면) ↔ 전체화면 상태머신.
 * - 그리드: 카메라별 서브 스트림 (1대뿐이면 그 타일은 메인 스트림)
 * - 타일 탭 → 그리드 전부 정지 후 해당 카메라 메인 스트림 전체화면 (8MP 디코더 단독 점유)
 * - 뒤로가기 → 전체화면 정지 후 그리드 복귀
 */
class MainActivity : ComponentActivity() {

    private lateinit var tileHost: FrameLayout
    private lateinit var emptyView: View
    private val activeControllers = mutableListOf<CameraTileController>()
    private var fullscreenCamera: CameraConfig? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tileHost = findViewById(R.id.tileHost)
        emptyView = findViewById(R.id.emptyView)
        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener { openSettings() }
        findViewById<View>(R.id.btnEmptyAdd).setOnClickListener { openSettings() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (fullscreenCamera != null) {
                    fullscreenCamera = null
                    rebuild()
                } else {
                    finish()
                }
            }
        })
    }

    override fun onStart() {
        super.onStart()
        rebuild()
    }

    override fun onStop() {
        stopAll()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersive()
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    /** 현재 상태(fullscreenCamera 유무)에 맞춰 타일 전체 재구성 */
    private fun rebuild() {
        stopAll()
        tileHost.removeAllViews()
        val cameras = CameraRepository.getAll(this)

        // 전체화면 대상 카메라가 삭제됐으면 그리드로 복귀
        val full = fullscreenCamera?.let { fc -> cameras.find { it.id == fc.id } }
        if (fullscreenCamera != null && full == null) fullscreenCamera = null

        if (cameras.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            return
        }
        emptyView.visibility = View.GONE

        if (full != null) {
            addTile(tileHost, full, useMainStream = true) {
                fullscreenCamera = null
                rebuild()
            }
        } else {
            buildGrid(cameras)
        }
        activeControllers.forEach { it.start() }
    }

    /** 대수에 맞춰 동적 그리드 (최대 12): cols=ceil(√n), rows=ceil(n/cols). 빈 칸은 검정.
     *  그리드 타일은 서브 스트림(저해상도), 1대뿐이면 메인 스트림. */
    private fun buildGrid(cameras: List<CameraConfig>) {
        val n = cameras.size
        if (n == 1) {
            addTile(tileHost, cameras[0], useMainStream = true) { /* 이미 전면 */ }
            return
        }
        val cols = Math.ceil(Math.sqrt(n.toDouble())).toInt().coerceAtLeast(1)
        val rowsCount = Math.ceil(n / cols.toDouble()).toInt()
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        tileHost.addView(column)
        var idx = 0
        for (r in 0 until rowsCount) {
            val row = newRow().also { column.addView(it, rowParams()) }
            for (c in 0 until cols) {
                if (idx < n) {
                    val cam = cameras[idx]
                    addTile(row, cam, useMainStream = false) { enterFullscreen(cam) }
                } else {
                    row.addView(View(this), tileParams())
                }
                idx++
            }
        }
    }

    private fun enterFullscreen(cam: CameraConfig) {
        fullscreenCamera = cam
        rebuild()
    }

    private fun addTile(
        container: android.view.ViewGroup,
        cam: CameraConfig,
        useMainStream: Boolean,
        onTap: () -> Unit
    ) {
        val controller = CameraTileController(container, cam, useMainStream, onTap)
        activeControllers.add(controller)
        val params = if (container is LinearLayout) tileParams()
        else FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        )
        container.addView(controller.view, params)
    }

    private fun stopAll() {
        activeControllers.forEach { it.stop() }
        activeControllers.clear()
    }

    private fun newRow() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

    private fun rowParams() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
    )

    private fun tileParams() = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)

    @Suppress("DEPRECATION") // API 23 호환 — WindowInsetsController 는 API 30+
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

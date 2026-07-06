package com.farmmachine.cctv

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Camera
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * 표준 Camera1(android.hardware.Camera) 기반 프리뷰 — 실기기 HAL 이 legacy(Camera1 shim)라 이 경로가 정합.
 *
 * 목적: 아날로그 카메라 프레임이 표준 API 로 실제로 들어오는지 화면 진단으로 확정.
 *  - 화면 상단에 카메라 수 / 선택 해상도 / 실제 도착 프레임 수 / 오류를 표시
 *  - 화면 탭 → 지원 프리뷰 해상도를 순회하며 재시도 (특정 해상도라야 프레임이 오는 경우 대비)
 *  프레임 카운터가 올라가면 = 표준 API 로 영상 수신 성공(파란화면은 그리기 문제였던 것).
 *  계속 0 이면 = 표준 HAL 이 이 입력을 안 내보냄(AIS/qcarcam 전용) 확정.
 */
@Suppress("DEPRECATION")
class MainActivity : ComponentActivity(), SurfaceHolder.Callback {

    companion object {
        private const val TAG = "CctvMain"
    }

    private lateinit var surfaceView: SurfaceView
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())

    private var camera: Camera? = null
    private var supportedSizes: List<Camera.Size> = emptyList()
    private var sizeIndex = 0
    private var frameCount = 0
    private var surfaceReady = false
    private var lastError: String? = null

    // 프레임 내용 분석 결과 (단색 파랑 stub vs 실제 영상 판별)
    private var chosenW = 0
    private var chosenH = 0
    private var lumaMin = 0
    private var lumaMax = 0
    private var lumaMean = 0
    private var chromaU = 0
    private var chromaV = 0

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) tryStart() else render("카메라 권한 거부됨")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        surfaceView = findViewById(R.id.previewSurface)
        status = findViewById(R.id.status)
        surfaceView.holder.addCallback(this)

        // 화면 탭 → 다음 지원 해상도로 재시도
        surfaceView.setOnClickListener {
            if (supportedSizes.isNotEmpty()) {
                sizeIndex = (sizeIndex + 1) % supportedSizes.size
                restartPreview()
            }
        }

        VanCamera.powerOn(this)   // 아날로그 카메라 전원 인가 (com.van.service 브로드캐스트)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersive()
    }

    // ── SurfaceHolder ───────────────────────────────────────────
    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        if (hasCameraPermission()) tryStart()
        else requestCameraPermission.launch(Manifest.permission.CAMERA)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (camera != null) restartPreview()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        releaseCamera()
    }

    // ── 카메라 ──────────────────────────────────────────────────
    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun tryStart() {
        if (!surfaceReady) return
        releaseCamera()
        frameCount = 0
        lastError = null
        val numCameras = Camera.getNumberOfCameras()
        try {
            val cam = Camera.open(0)   // 장치 0 (실측상 유일)
            camera = cam
            val params = cam.parameters
            supportedSizes = params.supportedPreviewSizes ?: emptyList()
            if (supportedSizes.isNotEmpty()) {
                val size = supportedSizes[sizeIndex.coerceIn(0, supportedSizes.size - 1)]
                params.setPreviewSize(size.width, size.height)
                chosenW = size.width; chosenH = size.height
            }
            cam.parameters = params
            cam.setDisplayOrientation(0)   // 가로 고정
            cam.setPreviewDisplay(surfaceView.holder)
            cam.setPreviewCallback { data, _ ->
                frameCount++
                if (data != null && frameCount % 15 == 0) analyzeFrame(data)   // 픽셀 내용 실측
            }
            cam.startPreview()
        } catch (e: Throwable) {
            lastError = e.message ?: e.javaClass.simpleName
            Log.e(TAG, "camera open/start 실패", e)
        }
        render(diag(numCameras))
        scheduleFrameTick()
    }

    private fun restartPreview() {
        releaseCamera()
        tryStart()
    }

    private fun releaseCamera() {
        camera?.let {
            runCatching {
                it.setPreviewCallback(null)
                it.stopPreview()
                it.release()
            }
        }
        camera = null
    }

    private fun scheduleFrameTick() {
        handler.removeCallbacks(frameTick)
        handler.postDelayed(frameTick, 500)
    }

    private val frameTick = object : Runnable {
        override fun run() {
            render(diag(Camera.getNumberOfCameras()))
            handler.postDelayed(this, 500)
        }
    }

    /** NV21 프레임 내용 분석: 루마 min/max/mean + 대표 색차. 단색이면 min≈max, 변화 없음. */
    private fun analyzeFrame(data: ByteArray) {
        val w = chosenW; val h = chosenH
        val ySize = w * h
        if (w == 0 || h == 0 || data.size < ySize) return
        var mn = 255; var mx = 0; var sum = 0L; var n = 0
        var i = 0
        val step = (ySize / 4000).coerceAtLeast(1)   // 약 4000점 샘플
        while (i < ySize) {
            val y = data[i].toInt() and 0xFF
            if (y < mn) mn = y
            if (y > mx) mx = y
            sum += y; n++
            i += step
        }
        // NV21: Y 다음에 V,U 인터리브 (VU 순). 중앙 부근 한 쌍 샘플
        var u = 0; var v = 0
        val cBase = ySize + (ySize / 4 / 2) * 2
        if (cBase + 1 < data.size) {
            v = data[cBase].toInt() and 0xFF
            u = data[cBase + 1].toInt() and 0xFF
        }
        lumaMin = mn; lumaMax = mx; lumaMean = if (n > 0) (sum / n).toInt() else 0
        chromaU = u; chromaV = v
    }

    private fun diag(numCameras: Int): String {
        val cur = supportedSizes.getOrNull(sizeIndex)?.let { "${it.width}x${it.height}" } ?: "-"
        val sizes = supportedSizes.joinToString(", ") { "${it.width}x${it.height}" }
        val uniform = if (lumaMax - lumaMin < 12) "단색(변화없음)" else "영상있음(변화$lumaMin~$lumaMax)"
        return buildString {
            append("카메라 수: $numCameras\n")
            append("선택 해상도: $cur  (화면 탭=다음)\n")
            append("도착 프레임: $frameCount\n")
            append("루마 min/max/mean: $lumaMin/$lumaMax/$lumaMean → $uniform\n")
            append("색차 U/V: $chromaU/$chromaV  (파랑≈U높음)\n")
            lastError?.let { append("오류: $it\n") }
            append("\n지원 해상도:\n$sizes")
        }
    }

    private fun render(text: String) {
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

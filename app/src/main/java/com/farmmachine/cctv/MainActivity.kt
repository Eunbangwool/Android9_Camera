package com.farmmachine.cctv

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat

/**
 * Apollo 10 Pro 아날로그 카메라 CCTV 뷰어 (전체화면 라이브 프리뷰).
 *
 * 흐름: 카메라 전원 ON(VanCamera 브로드캐스트) → CAMERA 권한 → CameraX Preview 바인드.
 * 이 태블릿은 아날로그 입력이 1채널(카메라 장치 1개)이라 단일 전체화면이 하드웨어 한계.
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "CctvMain"
        private const val REQ_CAMERA = 1001
        // 카메라 전원 인가 후 아날로그 디코더가 링크를 잡을 시간(경험적). 바인드 실패 시 재시도 간격도 겸함
        private const val CAMERA_SETTLE_MS = 1200L
        private const val REBIND_RETRY_MS = 2000L
    }

    private lateinit var previewView: PreviewView
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var cameraProvider: ProcessCameraProvider? = null
    private var bound = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        previewView = findViewById(R.id.previewView)
        status = findViewById(R.id.status)

        // 아날로그 카메라 전원 ON — 프리뷰보다 먼저 (디코더가 신호를 물어야 함)
        showStatus(getString(R.string.cam_powering))
        VanCamera.powerOn(this)
    }

    override fun onStart() {
        super.onStart()
        if (hasCameraPermission()) {
            // 전원 인가 직후 곧바로 열면 신호 미검출로 실패할 수 있어 잠깐 대기
            handler.postDelayed({ startCamera() }, CAMERA_SETTLE_MS)
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
        }
    }

    override fun onStop() {
        handler.removeCallbacksAndMessages(null)
        unbind()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersive()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CAMERA) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startCamera()
            else showStatus(getString(R.string.cam_permission_needed))
        }
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        showStatus(getString(R.string.cam_opening))
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = try {
                future.get()
            } catch (e: Exception) {
                Log.e(TAG, "provider 실패: ${e.message}")
                scheduleRebind(getString(R.string.cam_error))
                return@addListener
            }
            cameraProvider = provider
            bindPreview(provider)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindPreview(provider: ProcessCameraProvider) {
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        // 카메라 장치 1개뿐 — 우선 후면(외부 입력), 없으면 아무거나
        val selector = when {
            provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
            provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
            else -> CameraSelector.Builder().build()
        }
        try {
            provider.unbindAll()
            provider.bindToLifecycle(this, selector, preview)
            bound = true
            // 첫 프레임이 뜨면 PreviewView 가 상태를 STREAMING 으로 알림 → 오버레이 숨김
            previewView.previewStreamState.observe(this) { s ->
                if (s == PreviewView.StreamState.STREAMING) hideStatus()
            }
        } catch (e: Exception) {
            Log.e(TAG, "bind 실패: ${e.message}")
            scheduleRebind(getString(R.string.cam_error))
        }
    }

    private fun scheduleRebind(msg: String) {
        bound = false
        showStatus(msg)
        handler.removeCallbacks(rebindRunnable)
        handler.postDelayed(rebindRunnable, REBIND_RETRY_MS)
    }

    private val rebindRunnable = Runnable {
        // 전원 재인가 후 재시도 (아날로그 신호가 늦게 잡히는 경우 대응)
        VanCamera.powerOn(this)
        cameraProvider?.let { bindPreview(it) } ?: startCamera()
    }

    private fun unbind() {
        runCatching { cameraProvider?.unbindAll() }
        bound = false
    }

    private fun showStatus(text: String) {
        status.text = text
        status.visibility = View.VISIBLE
    }

    private fun hideStatus() {
        status.visibility = View.GONE
    }

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

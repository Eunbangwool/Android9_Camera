package com.farmmachine.cctv

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import android.view.SurfaceHolder
import com.quectel.qcarapi.stream.QCarCamera
import java.nio.ByteBuffer

/**
 * qcarcam(Qualcomm AIS) 2채널 프리뷰 컨트롤러.
 * 검증된 AGMO 경로 재현: QCarCamera(csi0).cameraOpen(inputNum, fmt0) →
 *   채널별 setFps/setPreviewStreamSize/setPreviewStreamColorFormat(NV21)/startPreviewStream →
 *   getPreviewFrameInfo(ch, buffer) 폴링 → NV21→ARGB → SurfaceView 렌더.
 */
class CameraController(
    private val width: Int = 1280,    // 720p (1280 은 16정렬이라 스트라이드 안전). 문제 시 480x270 로 복귀
    private val height: Int = 720,
    private val fps: Int = 25,
    val inputNum: Int = 2,            // 채널(카메라) 수
    private val publishFrames: Boolean = false,   // cast 모드: NV21 을 FrameHub 로 발행(MJPEG 서버용)
) {
    companion object { private const val TAG = "Qcar" }

    private var camera: QCarCamera? = null
    private val readers = arrayOfNulls<ChannelReader>(inputNum)

    @Volatile var status: String = "초기화"
        private set

    /** 의존 순서대로 로드해 실패 지점을 특정. 실패 사유는 status 에 기록. */
    fun loadLibraries(): Boolean {
        val libs = listOf(
            "qcarprotobbtrp", "qcarmdvxread", "qcarmdvxwrite", "qcaraudiorecorder",
            "qcarimageprocess", "qcarosd", "qcarlibyuv", "mmqcar_ais_client", "mmqcar_qcar_jni"
        )
        for (l in libs) {
            try {
                System.loadLibrary(l)
            } catch (t: Throwable) {
                Log.e(TAG, "loadLibrary $l 실패", t)
                if (l == "mmqcar_qcar_jni") {   // 필수 라이브러리 실패 = 중단
                    status = "라이브러리 로드 실패: lib$l.so\n${t.message}"
                    return false
                }
            }
        }
        status = "라이브러리 로드 완료"
        return true
    }

    fun open(): Boolean = try {
        val cam = QCarCamera(0)
        val rc = cam.cameraOpen(inputNum, 0)   // csi0, inputNum 채널, fmt=0
        for (ch in 0 until inputNum) {
            cam.setFps(ch, fps)
            cam.setPreviewStreamSize(ch, width, height)
            cam.setPreviewStreamColorFormat(ch, QCarCamera.YUV420_NV21)
            cam.startPreviewStream(ch)
        }
        camera = cam
        status = "cameraOpen=$rc, ${inputNum}채널 @${width}x$height"
        Log.i(TAG, status)
        true
    } catch (t: Throwable) {
        status = "카메라 open 실패: ${t.message}"
        Log.e(TAG, status, t)
        false
    }

    fun startChannel(ch: Int, holder: SurfaceHolder) {
        val cam = camera ?: return
        if (ch !in 0 until inputNum || readers[ch] != null) return
        ChannelReader(cam, ch, width, height, holder, publishFrames).also { readers[ch] = it; it.start() }
    }

    fun frameSummary(): String =
        (0 until inputNum).joinToString("  ") { "ch$it=${readers[it]?.frames ?: 0}" }

    /** 활성 리더가 모두 프레임을 받기 시작했는지 (상태창 자동 숨김 판단) */
    fun framesFlowing(): Boolean {
        val active = readers.filterNotNull()
        return active.isNotEmpty() && active.all { it.frames > 15 }
    }

    fun stop() {
        readers.forEach { it?.stopReader() }
        readers.fill(null)
        camera?.let { c ->
            runCatching {
                for (ch in 0 until inputNum) c.stopPreviewStream(ch)
                c.cameraClose()
                c.release()
            }
        }
        camera = null
    }
}

/** 채널 1개 폴링·렌더 스레드. getPreviewFrameInfo 로 NV21 프레임을 받아 SurfaceView 에 그린다. */
class ChannelReader(
    private val cam: QCarCamera,
    val channel: Int,
    private val w: Int,
    private val h: Int,
    private val holder: SurfaceHolder,
    private val publishFrames: Boolean = false,
) : Thread("qcar-ch$channel") {

    @Volatile private var running = true
    @Volatile var frames: Long = 0
        private set

    private val buffer = ByteBuffer.allocate(w * h * 3 / 2)   // AGMO 와 동일하게 heap 버퍼
    private val yuv = ByteArray(w * h * 3 / 2)
    private val argb = IntArray(w * h)
    private val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    private val dst = Rect()
    private var lastId = -1L

    override fun run() {
        while (running) {
            buffer.clear()
            val info = try {
                cam.getPreviewFrameInfo(channel, buffer)
            } catch (t: Throwable) {
                Log.e("Qcar", "ch$channel getPreviewFrameInfo 실패", t); null
            }
            if (info != null && info.frameID > lastId) {
                lastId = info.frameID
                buffer.get(yuv)
                // cast 모드: NV21 사본을 서버로 발행 (ARGB 변환 전에)
                if (publishFrames) FrameHub.publish(channel, yuv.copyOf(), w, h, info.frameID)
                nv21ToArgb(yuv, w, h, argb)
                bmp.setPixels(argb, 0, w, 0, 0, w, h)
                draw()
                frames++
            } else {
                try { sleep(5) } catch (_: InterruptedException) { }
            }
        }
        runCatching { bmp.recycle() }
    }

    private fun draw() {
        val canvas = holder.lockCanvas() ?: return
        try {
            // 비율 유지(레터박스): 화면에 꽉 채우되 찌그러지지 않게 중앙 정렬 + 검은 여백
            val cw = canvas.width
            val ch = canvas.height
            val scale = minOf(cw.toFloat() / w, ch.toFloat() / h)
            val dw = (w * scale).toInt()
            val dh = (h * scale).toInt()
            val left = (cw - dw) / 2
            val top = (ch - dh) / 2
            dst.set(left, top, left + dw, top + dh)
            canvas.drawColor(android.graphics.Color.BLACK)
            canvas.drawBitmap(bmp, null, dst, null)
        } finally {
            runCatching { holder.unlockCanvasAndPost(canvas) }
        }
    }

    fun stopReader() { running = false }

    /** NV21(YVU) → ARGB, BT.601. 자체 구현. */
    private fun nv21ToArgb(nv: ByteArray, width: Int, height: Int, out: IntArray) {
        val frameSize = width * height
        for (j in 0 until height) {
            var uvp = frameSize + (j shr 1) * width
            var u = 0; var v = 0
            val rowY = j * width
            for (i in 0 until width) {
                var y = (nv[rowY + i].toInt() and 0xff) - 16
                if (y < 0) y = 0
                if (i and 1 == 0) {
                    v = (nv[uvp++].toInt() and 0xff) - 128   // NV21: V 먼저
                    u = (nv[uvp++].toInt() and 0xff) - 128
                }
                val y1192 = 1192 * y
                var r = y1192 + 1634 * v
                var g = y1192 - 833 * v - 400 * u
                var b = y1192 + 2066 * u
                if (r < 0) r = 0 else if (r > 262143) r = 262143
                if (g < 0) g = 0 else if (g > 262143) g = 262143
                if (b < 0) b = 0 else if (b > 262143) b = 262143
                out[rowY + i] = -0x1000000 or
                    ((r shl 6) and 0xff0000) or
                    ((g shr 2) and 0xff00) or
                    ((b shr 10) and 0xff)
            }
        }
    }
}

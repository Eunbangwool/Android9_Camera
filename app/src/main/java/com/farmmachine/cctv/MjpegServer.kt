package com.farmmachine.cctv

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

/**
 * 초경량 MJPEG-over-HTTP 서버. 폰 브라우저로 http://<태블릿IP>:8080 접속 시
 * 두 카메라 채널을 실시간 MJPEG 로 시청. (외부 라이브러리 없이 ServerSocket 로 구현)
 *
 *  GET /        → ch0·ch1 나란히 보는 HTML
 *  GET /ch0,/ch1 → multipart/x-mixed-replace MJPEG 스트림 (채널별 최신 NV21 → JPEG)
 */
class MjpegServer(
    private val channels: Int = 2,
    private val jpegQuality: Int = 70,
    private val streamFps: Int = 15,
) {
    companion object {
        private const val TAG = "MjpegServer"
        // 8080 은 기기의 다른 서비스가 점유(400 응답)하고 있어 사용 안 함. 비어있는 포트를 순차 시도.
        private val CANDIDATE_PORTS = intArrayOf(8090, 8088, 8181, 9000, 8080)
    }

    private var serverSocket: ServerSocket? = null
    @Volatile private var running = false

    /** 실제 바인딩된 포트 (실패 시 -1). URL 표시에 사용 */
    @Volatile var boundPort: Int = -1
        private set

    /** 동기 바인딩 후 accept 스레드 시작. 바인딩된 포트 반환(-1=실패). */
    fun start(): Int {
        if (running) return boundPort
        for (p in CANDIDATE_PORTS) {
            try {
                serverSocket = ServerSocket(p)
                boundPort = p
                break
            } catch (e: Exception) {
                Log.w(TAG, "포트 $p 바인딩 실패: ${e.message}")
            }
        }
        val ss = serverSocket ?: run {
            Log.e(TAG, "모든 후보 포트 바인딩 실패")
            return -1
        }
        running = true
        Log.i(TAG, "MJPEG 서버 시작: ${wifiIpAddress()}:$boundPort")
        Thread({ acceptLoop(ss) }, "mjpeg-accept").start()
        return boundPort
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
    }

    private fun acceptLoop(ss: ServerSocket) {
        while (running) {
            val socket = try { ss.accept() } catch (e: Exception) { break }
            Thread({ handle(socket) }, "mjpeg-client").start()
        }
    }

    private fun handle(socket: Socket) {
        try {
            socket.use { s ->
                val reader = s.getInputStream().bufferedReader()
                val requestLine = reader.readLine() ?: return
                val path = requestLine.split(" ").getOrNull(1) ?: "/"
                val out = s.getOutputStream()
                when {
                    path == "/" || path.startsWith("/index") -> serveIndex(out)
                    path.startsWith("/ch") -> {
                        val ch = path.removePrefix("/ch").substringBefore('?').toIntOrNull()
                        if (ch != null && ch in 0 until channels) streamChannel(out, ch)
                        else notFound(out)
                    }
                    else -> notFound(out)
                }
            }
        } catch (e: Exception) {
            // 클라이언트 연결 종료 등 — 조용히 정리
        }
    }

    private fun serveIndex(out: OutputStream) {
        val imgs = (0 until channels).joinToString("") {
            "<img src=\"/ch$it\" style=\"width:${100 / channels}%;vertical-align:top\">"
        }
        val html = """
            <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>FarmMachine CCTV</title></head>
            <body style="margin:0;background:#000">$imgs</body></html>
        """.trimIndent()
        val body = html.toByteArray()
        out.write(
            ("HTTP/1.0 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray()
        )
        out.write(body)
        out.flush()
    }

    private fun notFound(out: OutputStream) {
        out.write("HTTP/1.0 404 Not Found\r\nConnection: close\r\n\r\n".toByteArray())
        out.flush()
    }

    private fun streamChannel(out: OutputStream, ch: Int) {
        val boundary = "frame"
        out.write(
            ("HTTP/1.0 200 OK\r\n" +
                "Cache-Control: no-cache\r\nPragma: no-cache\r\nConnection: close\r\n" +
                "Content-Type: multipart/x-mixed-replace; boundary=$boundary\r\n\r\n").toByteArray()
        )
        out.flush()
        val baos = ByteArrayOutputStream()
        val periodMs = (1000 / streamFps).toLong()
        var lastId = -1L
        while (running) {
            val f = FrameHub.get(ch)
            if (f != null && f.id != lastId) {
                lastId = f.id
                baos.reset()
                // NV21 → JPEG (YuvImage 가 NV21 직접 지원)
                YuvImage(f.nv21, ImageFormat.NV21, f.w, f.h, null)
                    .compressToJpeg(Rect(0, 0, f.w, f.h), jpegQuality, baos)
                val jpg = baos.toByteArray()
                out.write(
                    ("--$boundary\r\nContent-Type: image/jpeg\r\n" +
                        "Content-Length: ${jpg.size}\r\n\r\n").toByteArray()
                )
                out.write(jpg)
                out.write("\r\n".toByteArray())
                out.flush()
            }
            try { Thread.sleep(periodMs) } catch (_: InterruptedException) { break }
        }
    }

    /** wlan0 등 비루프백 IPv4 (폰에서 접속할 주소) */
    fun wifiIpAddress(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }?.hostAddress
    }.getOrNull()
}

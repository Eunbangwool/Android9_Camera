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
    private val port: Int = 8080,
    private val jpegQuality: Int = 70,
    private val streamFps: Int = 15,
) {
    companion object { private const val TAG = "MjpegServer" }

    private var serverSocket: ServerSocket? = null
    @Volatile private var running = false

    fun start() {
        if (running) return
        running = true
        Thread({ acceptLoop() }, "mjpeg-accept").start()
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
    }

    private fun acceptLoop() {
        try {
            val ss = ServerSocket(port)
            serverSocket = ss
            Log.i(TAG, "MJPEG 서버 시작: ${wifiIpAddress()}:$port")
            while (running) {
                val socket = try { ss.accept() } catch (e: Exception) { break }
                Thread({ handle(socket) }, "mjpeg-client").start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "서버 시작 실패: ${e.message}", e)
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

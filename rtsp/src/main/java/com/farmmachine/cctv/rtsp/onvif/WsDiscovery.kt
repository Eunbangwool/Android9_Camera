package com.farmmachine.cctv.rtsp.onvif

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.net.URI
import java.util.UUID

/**
 * WS-Discovery (ONVIF 카메라 자동 검색).
 * UDP 멀티캐스트 239.255.255.250:3702 로 Probe 전송 후 ProbeMatch 수집.
 * 카메라가 eth0/usb0 쪽에 있을 수 있으므로 활성 IPv4 인터페이스 **전부**에서 송신한다
 * (기본 라우팅은 wlan0 으로 빠져 직결 링크에 안 나감).
 */
object WsDiscovery {
    private const val TAG = "WsDiscovery"
    private const val MULTICAST_ADDR = "239.255.255.250"
    private const val WS_DISCOVERY_PORT = 3702
    private const val RECV_BUF_BYTES = 8 * 1024

    /** @return 발견된 카메라 IP 목록 (중복 제거) */
    suspend fun probe(context: Context, timeoutMs: Int = 3_000): List<String> =
        withContext(Dispatchers.IO) {
            // wlan 경유 멀티캐스트 수신은 MulticastLock 없으면 드랍됨 (eth 에는 무해)
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val lock = wifi?.createMulticastLock("onvif-discovery")?.apply { acquire() }
            try {
                val found = linkedSetOf<String>()
                eligibleInterfaces().forEach { (ni, addr) ->
                    runCatching { probeOnInterface(addr, timeoutMs, found) }
                        .onFailure { Log.w(TAG, "probe via ${ni.name} failed: ${it.message}") }
                }
                found.toList()
            } finally {
                lock?.release()
            }
        }

    private fun eligibleInterfaces(): List<Pair<NetworkInterface, Inet4Address>> =
        runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .mapNotNull { ni ->
                    ni.inetAddresses.toList().filterIsInstance<Inet4Address>().firstOrNull()
                        ?.let { ni to it }
                }
        }.getOrDefault(emptyList())

    private fun probeOnInterface(local: Inet4Address, timeoutMs: Int, found: MutableSet<String>) {
        DatagramSocket(InetSocketAddress(local, 0)).use { socket ->
            socket.soTimeout = timeoutMs
            val probe = probeMessage().toByteArray(Charsets.UTF_8)
            socket.send(
                DatagramPacket(probe, probe.size, InetAddress.getByName(MULTICAST_ADDR), WS_DISCOVERY_PORT)
            )
            val buf = ByteArray(RECV_BUF_BYTES)
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                val packet = DatagramPacket(buf, buf.size)
                try {
                    socket.receive(packet)
                } catch (e: SocketTimeoutException) {
                    break
                }
                val xml = String(packet.data, 0, packet.length, Charsets.UTF_8)
                OnvifXml.parseXAddrs(xml).forEach { url ->
                    runCatching { URI(url).host }.getOrNull()?.let { found.add(it) }
                }
            }
        }
    }

    private fun probeMessage() = """
        <?xml version="1.0" encoding="UTF-8"?>
        <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope"
                    xmlns:a="http://schemas.xmlsoap.org/ws/2004/08/addressing"
                    xmlns:d="http://schemas.xmlsoap.org/ws/2005/04/discovery">
          <s:Header>
            <a:Action>http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</a:Action>
            <a:MessageID>urn:uuid:${UUID.randomUUID()}</a:MessageID>
            <a:To>urn:schemas-xmlsoap-org:ws:2005:04:discovery</a:To>
          </s:Header>
          <s:Body>
            <d:Probe>
              <d:Types xmlns:dn="http://www.onvif.org/ver10/network/wsdl">dn:NetworkVideoTransmitter</d:Types>
            </d:Probe>
          </s:Body>
        </s:Envelope>
    """.trimIndent()
}

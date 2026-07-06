package com.farmmachine.cctv.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

/** 네트워크 진단: 인터페이스별 IP 표시 + 카메라 RTSP 포트 도달성 프로브 */
object NetworkStatusHelper {
    private const val PROBE_TIMEOUT_MS = 2_000

    /** (인터페이스명, IPv4) 목록 — 설정화면 네트워크 패널에 그대로 표시 */
    fun interfaceSummary(): List<Pair<String, String>> =
        runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { ni ->
                    ni.inetAddresses.toList().filterIsInstance<Inet4Address>()
                        .map { ni.name to it.hostAddress.orEmpty() }
                }
        }.getOrDefault(emptyList())

    /** TCP 연결만 확인 (RTSP 핸드셰이크 아님) — 케이블/IP 대역 문제를 빠르게 걸러내는 용도 */
    suspend fun probeTcp(host: String, port: Int): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                Socket().use { it.connect(InetSocketAddress(host, port), PROBE_TIMEOUT_MS) }
                true
            }.getOrDefault(false)
        }
}

package com.farmmachine.cctv.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 직결 링크(DHCP 서버 없음)에서 이더넷 고정 IP 를 잡아주는 베스트에포트 헬퍼.
 * Apollo 계열은 루팅 기기라 su 가 있을 가능성이 높지만 보장은 없음 —
 * 실패 시 사유를 담아 Result.failure 로 반환하고 UI 가 수동 설정을 안내한다. ★현장검증 항목
 */
object RootShell {
    private const val SU_TIMEOUT_S = 5L
    private const val POLL_MS = 100L

    suspend fun trySetStaticIp(iface: String, ip: String, netmask: String): Result<String> =
        withContext(Dispatchers.IO) {
            // 인자를 셸 문자열에 조립하므로 형식 검증으로 인젝션 차단
            if (!iface.matches(Regex("[A-Za-z0-9_.-]+")))
                return@withContext Result.failure(IllegalArgumentException("bad iface: $iface"))
            if (!ip.matches(IPV4) || !netmask.matches(IPV4))
                return@withContext Result.failure(IllegalArgumentException("bad ip/netmask"))

            runCatching {
                val proc = Runtime.getRuntime().exec(
                    arrayOf("su", "-c", "ifconfig $iface $ip netmask $netmask up")
                )
                // Process.waitFor(timeout) 은 API 26+ → minSdk 23 호환 폴링으로 대체
                val deadline = System.currentTimeMillis() + SU_TIMEOUT_S * 1000
                var exit: Int? = null
                while (System.currentTimeMillis() < deadline) {
                    try {
                        exit = proc.exitValue(); break
                    } catch (e: IllegalThreadStateException) {
                        Thread.sleep(POLL_MS)
                    }
                }
                if (exit == null) {
                    proc.destroy()
                    error("su timeout (${SU_TIMEOUT_S}s) — 루트 권한 프롬프트 확인")
                }
                val err = proc.errorStream.bufferedReader().readText().trim()
                if (exit != 0) error(err.ifBlank { "exit $exit" })
                "$iface = $ip/$netmask"
            }
        }

    private val IPV4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")
}

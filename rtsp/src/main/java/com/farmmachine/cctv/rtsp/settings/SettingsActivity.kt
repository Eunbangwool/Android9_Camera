package com.farmmachine.cctv.rtsp.settings

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.farmmachine.cctv.rtsp.R
import com.farmmachine.cctv.rtsp.model.CameraConfig
import com.farmmachine.cctv.rtsp.model.CameraRepository
import com.farmmachine.cctv.rtsp.net.NetworkStatusHelper
import com.farmmachine.cctv.rtsp.net.RootShell
import com.farmmachine.cctv.rtsp.onvif.OnvifClient
import com.farmmachine.cctv.rtsp.onvif.WsDiscovery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 카메라 CRUD + ONVIF 검색/URL 가져오기 + 네트워크 진단 패널 */
class SettingsActivity : ComponentActivity() {

    private lateinit var adapter: CameraListAdapter
    private lateinit var discoverStatus: TextView
    private lateinit var networkInfo: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        adapter = CameraListAdapter(onEdit = { showEditDialog(it) }, onDelete = { confirmDelete(it) })
        findViewById<RecyclerView>(R.id.cameraList).apply {
            layoutManager = LinearLayoutManager(this@SettingsActivity)
            adapter = this@SettingsActivity.adapter
        }
        discoverStatus = findViewById(R.id.discoverStatus)
        networkInfo = findViewById(R.id.networkInfo)

        findViewById<Button>(R.id.btnAddCamera).setOnClickListener { showEditDialog(null) }
        findViewById<Button>(R.id.btnDiscover).setOnClickListener { runDiscovery() }
        findViewById<Button>(R.id.btnRefreshNetwork).setOnClickListener { refreshAll() }
        findViewById<Button>(R.id.btnStaticIp).setOnClickListener { showStaticIpDialog() }
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
    }

    /** 목록 + 네트워크 패널 + 카메라별 554 프로브 갱신 */
    private fun refreshAll() {
        val cameras = CameraRepository.getAll(this)
        adapter.submit(cameras, emptyMap())

        val ifaces = NetworkStatusHelper.interfaceSummary()
        networkInfo.text =
            if (ifaces.isEmpty()) getString(R.string.network_no_iface)
            else ifaces.joinToString("\n") { (name, ip) -> "$name : $ip" }

        lifecycleScope.launch {
            val probes = cameras.associate { cam ->
                cam.id to NetworkStatusHelper.probeTcp(cam.host, CameraConfig.RTSP_PORT)
            }
            adapter.submit(cameras, probes)
        }
    }

    // ── ONVIF 검색 ──────────────────────────────────────────────

    private fun runDiscovery() {
        discoverStatus.visibility = View.VISIBLE
        discoverStatus.text = getString(R.string.discover_running)
        lifecycleScope.launch {
            val hosts = WsDiscovery.probe(this@SettingsActivity)
            if (hosts.isEmpty()) {
                discoverStatus.text = getString(R.string.discover_none)
            } else {
                discoverStatus.text = getString(R.string.discover_found_fmt, hosts.joinToString(", "))
                AlertDialog.Builder(this@SettingsActivity)
                    .setTitle(R.string.btn_discover)
                    .setItems(hosts.toTypedArray()) { _, which ->
                        showEditDialog(null, prefillHost = hosts[which])
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
    }

    // ── 카메라 추가/수정 다이얼로그 ─────────────────────────────

    private fun showEditDialog(existing: CameraConfig?, prefillHost: String? = null) {
        val view = layoutInflater.inflate(R.layout.dialog_camera_edit, null)
        val editName = view.findViewById<EditText>(R.id.editName)
        val editHost = view.findViewById<EditText>(R.id.editHost)
        val editPort = view.findViewById<EditText>(R.id.editOnvifPort)
        val editUser = view.findViewById<EditText>(R.id.editUser)
        val editPass = view.findViewById<EditText>(R.id.editPass)
        val editMain = view.findViewById<EditText>(R.id.editMainUrl)
        val editSub = view.findViewById<EditText>(R.id.editSubUrl)
        val onvifStatus = view.findViewById<TextView>(R.id.onvifStatus)

        existing?.let {
            editName.setText(it.name)
            editHost.setText(it.host)
            editPort.setText(it.onvifPort.toString())
            editUser.setText(it.user)
            editPass.setText(it.pass)
            editMain.setText(it.mainUrl)
            editSub.setText(it.subUrl)
        } ?: run {
            editUser.setText(CameraConfig.DEFAULT_USER)
            prefillHost?.let { editHost.setText(it) }
        }

        view.findViewById<Button>(R.id.btnOnvifFetch).setOnClickListener {
            val host = editHost.text.toString().trim()
            if (host.isEmpty()) {
                onvifStatus.visibility = View.VISIBLE
                onvifStatus.text = getString(R.string.err_need_host)
                return@setOnClickListener
            }
            val port = editPort.text.toString().trim().toIntOrNull() ?: CameraConfig.DEFAULT_ONVIF_PORT
            val user = editUser.text.toString().trim()
            val pass = editPass.text.toString()
            onvifStatus.visibility = View.VISIBLE
            onvifStatus.text = getString(R.string.onvif_fetching)
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { OnvifClient(host, port, user, pass).fetchStreamUris() }
                }
                result.fold(
                    onSuccess = { uris ->
                        editMain.setText(uris.mainUrl)
                        editSub.setText(uris.subUrl)
                        onvifStatus.text = getString(R.string.onvif_ok_fmt, uris.resolutions)
                    },
                    onFailure = { e ->
                        onvifStatus.text =
                            if (e is OnvifClient.OnvifAuthException) getString(R.string.onvif_auth_fail)
                            else getString(R.string.onvif_fail_fmt, e.message ?: e.javaClass.simpleName)
                    }
                )
            }
        }

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) R.string.dlg_camera_title_add else R.string.dlg_camera_title_edit)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                val host = editHost.text.toString().trim()
                val mainUrl = editMain.text.toString().trim()
                when {
                    host.isEmpty() -> toast(getString(R.string.err_need_host))
                    mainUrl.isEmpty() -> toast(getString(R.string.err_need_main_url))
                    else -> {
                        val cam = CameraConfig(
                            id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                            name = editName.text.toString().trim().ifBlank { host },
                            host = host,
                            onvifPort = editPort.text.toString().trim().toIntOrNull()
                                ?: CameraConfig.DEFAULT_ONVIF_PORT,
                            user = editUser.text.toString().trim(),
                            pass = editPass.text.toString(),
                            mainUrl = mainUrl,
                            subUrl = editSub.text.toString().trim()
                        )
                        if (!CameraRepository.save(this, cam)) {
                            toast(getString(R.string.err_max_cameras_fmt, CameraRepository.MAX_CAMERAS))
                        }
                        refreshAll()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(cam: CameraConfig) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.delete_confirm_fmt, cam.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                CameraRepository.delete(this, cam.id)
                refreshAll()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ── 이더넷 고정 IP (루트) ───────────────────────────────────

    private fun showStaticIpDialog() {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        val editIface = EditText(this).apply {
            hint = getString(R.string.hint_iface); setText(DEFAULT_ETH_IFACE)
        }
        val editIp = EditText(this).apply {
            hint = getString(R.string.hint_static_ip); setText(DEFAULT_STATIC_IP)
        }
        val editMask = EditText(this).apply {
            hint = getString(R.string.hint_netmask); setText(DEFAULT_NETMASK)
        }
        layout.addView(editIface); layout.addView(editIp); layout.addView(editMask)

        AlertDialog.Builder(this)
            .setTitle(R.string.dlg_static_ip_title)
            .setView(layout)
            .setPositiveButton(R.string.apply) { _, _ ->
                lifecycleScope.launch {
                    val result = RootShell.trySetStaticIp(
                        editIface.text.toString().trim(),
                        editIp.text.toString().trim(),
                        editMask.text.toString().trim()
                    )
                    result.fold(
                        onSuccess = { toast(getString(R.string.static_ip_ok_fmt, it)); refreshAll() },
                        onFailure = { toast(getString(R.string.static_ip_fail_fmt, it.message ?: "?")) }
                    )
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    companion object {
        // 카메라 공장 기본이 192.168.1.x 대역인 경우가 많음 → 태블릿을 같은 대역 .100 으로
        private const val DEFAULT_ETH_IFACE = "eth0"
        private const val DEFAULT_STATIC_IP = "192.168.1.100"
        private const val DEFAULT_NETMASK = "255.255.255.0"
    }
}

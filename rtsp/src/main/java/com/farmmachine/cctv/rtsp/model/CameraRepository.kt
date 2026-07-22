package com.farmmachine.cctv.rtsp.model

import android.content.Context
import org.json.JSONArray

/** SharedPreferences 기반 카메라 목록 저장소. 그리드가 2x2 라 최대 [MAX_CAMERAS]대 */
object CameraRepository {
    const val MAX_CAMERAS = 12

    private const val PREFS = "cameras"
    private const val KEY_LIST = "list"

    fun getAll(context: Context): List<CameraConfig> {
        val raw = prefs(context).getString(KEY_LIST, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { CameraConfig.fromJson(arr.getJSONObject(it)) }
        }.getOrElse { emptyList() }   // 손상된 JSON 은 빈 목록으로 (사용자가 재등록)
    }

    /** 같은 id 가 있으면 교체, 없으면 추가. 정원 초과 시 false */
    fun save(context: Context, cam: CameraConfig): Boolean {
        val list = getAll(context).toMutableList()
        val idx = list.indexOfFirst { it.id == cam.id }
        if (idx >= 0) list[idx] = cam
        else {
            if (list.size >= MAX_CAMERAS) return false
            list.add(cam)
        }
        persist(context, list)
        return true
    }

    fun delete(context: Context, id: String) {
        persist(context, getAll(context).filterNot { it.id == id })
    }

    private fun persist(context: Context, list: List<CameraConfig>) {
        val arr = JSONArray().apply { list.forEach { put(it.toJson()) } }
        prefs(context).edit().putString(KEY_LIST, arr.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

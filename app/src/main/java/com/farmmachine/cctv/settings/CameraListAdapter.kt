package com.farmmachine.cctv.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.farmmachine.cctv.R
import com.farmmachine.cctv.model.CameraConfig

/** 설정 화면 카메라 목록. probeResults: id → RTSP 554 도달 여부 (null=미확인) */
class CameraListAdapter(
    private val onEdit: (CameraConfig) -> Unit,
    private val onDelete: (CameraConfig) -> Unit
) : RecyclerView.Adapter<CameraListAdapter.Holder>() {

    private var items: List<CameraConfig> = emptyList()
    private var probeResults: Map<String, Boolean> = emptyMap()

    fun submit(list: List<CameraConfig>, probes: Map<String, Boolean>) {
        items = list
        probeResults = probes
        notifyDataSetChanged()
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.camName)
        val detail: TextView = view.findViewById(R.id.camDetail)
        val probe: TextView = view.findViewById(R.id.camProbe)
        val edit: Button = view.findViewById(R.id.btnEdit)
        val delete: Button = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_camera, parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val cam = items[position]
        val ctx = holder.itemView.context
        holder.name.text = cam.name
        holder.detail.text = cam.host
        when (probeResults[cam.id]) {
            true -> {
                holder.probe.text = ctx.getString(R.string.probe_ok)
                holder.probe.setTextColor(ContextCompat.getColor(ctx, R.color.accent_green))
            }
            false -> {
                holder.probe.text = ctx.getString(R.string.probe_fail)
                holder.probe.setTextColor(ContextCompat.getColor(ctx, R.color.status_error))
            }
            null -> holder.probe.text = ""
        }
        holder.edit.setOnClickListener { onEdit(cam) }
        holder.delete.setOnClickListener { onDelete(cam) }
    }
}

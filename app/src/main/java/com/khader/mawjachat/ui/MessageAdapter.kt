package com.khader.mawjachat.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.khader.mawjachat.R
import com.khader.mawjachat.model.WaveMessagePacket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageAdapter(
    private val currentUser: String,
    private val onPlayReconstructedAudio: (WaveMessagePacket) -> Unit,
    private val onSaveWavFile: (WaveMessagePacket) -> Unit
) : RecyclerView.Adapter<MessageAdapter.MsgViewHolder>() {

    private val items = mutableListOf<WaveMessagePacket>()
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    fun addMessage(packet: WaveMessagePacket) {
        items.add(packet)
        notifyItemInserted(items.size - 1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MsgViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_wave_message, parent, false)
        return MsgViewHolder(view)
    }

    override fun onBindViewHolder(holder: MsgViewHolder, position: Int) {
        val item = items[position]
        val isMine = item.senderName.equals(currentUser, ignoreCase = true)

        holder.tvSender.text = if (isMine) "أنت (${item.senderName})" else "من: ${item.senderName}"
        holder.tvTime.text = "${timeFormat.format(Date(item.timestamp))} • ${item.totalDurationMs / 1000.0}ث"

        val charCount = item.segments.count { it.type == "CHAR_WAVE" }
        val silenceCount = item.segments.count { it.type == "SILENCE" }
        val spaceCount = item.segments.count { it.type == "SPACE" }

        holder.tvStats.text = "مقاطع الحروف: $charCount | سكون: $silenceCount | فراغ: $spaceCount"
        holder.tvPhonetic.text = item.phoneticTranscript.ifEmpty { "رسالة موجية" }
        holder.waveformView.setSegments(item.segments)

        holder.btnPlay.setOnClickListener { onPlayReconstructedAudio(item) }
        holder.btnSave.setOnClickListener { onSaveWavFile(item) }
    }

    override fun getItemCount(): Int = items.size

    class MsgViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSender: TextView = view.findViewById(R.id.tvMsgSender)
        val tvTime: TextView = view.findViewById(R.id.tvMsgTime)
        val tvStats: TextView = view.findViewById(R.id.tvMsgStats)
        val tvPhonetic: TextView = view.findViewById(R.id.tvMsgPhonetic)
        val waveformView: WaveformView = view.findViewById(R.id.itemWaveformView)
        val btnPlay: ImageButton = view.findViewById(R.id.btnPlayWave)
        val btnSave: ImageButton = view.findViewById(R.id.btnSaveWav)
    }
}
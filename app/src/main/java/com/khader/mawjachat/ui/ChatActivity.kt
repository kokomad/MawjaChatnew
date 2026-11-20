package com.khader.mawjachat.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Environment
import android.view.MotionEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.khader.mawjachat.audio.AcousticWaveTranslator
import com.khader.mawjachat.audio.AudioRecorderManager
import com.khader.mawjachat.databinding.ActivityChatBinding
import com.khader.mawjachat.model.WaveMessagePacket
import com.khader.mawjachat.network.PrivateChannelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var adapter: MessageAdapter
    private lateinit var channelManager: PrivateChannelManager
    private lateinit var recorderManager: AudioRecorderManager

    private var senderName = ""
    private var receiverName = ""
    private var sharedCode = ""
    private var mediaPlayer: MediaPlayer? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        senderName = intent.getStringExtra("SENDER_NAME") ?: "المرسل"
        receiverName = intent.getStringExtra("RECEIVER_NAME") ?: "المستقبل"
        sharedCode = intent.getStringExtra("SHARED_CODE") ?: "0000"

        binding.tvRoomTitle.text = "$senderName ⇄ $receiverName"

        val prefs = getSharedPreferences("MawjaPrefs", MODE_PRIVATE)
        val relayUrl = prefs.getString("relay_url", "wss://echo.websocket.events") ?: "wss://echo.websocket.events"

        adapter = MessageAdapter(
            currentUser = senderName,
            onPlayReconstructedAudio = { packet -> playReconstructedWave(packet) },
            onSaveWavFile = { packet -> exportWaveToWav(packet) }
        )

        binding.rvMessages.layoutManager = LinearLayoutManager(this)
        binding.rvMessages.adapter = adapter

        channelManager = PrivateChannelManager(
            senderName = senderName,
            receiverName = receiverName,
            sharedCode = sharedCode,
            relayUrl = relayUrl,
            onStatusChanged = { status, _ ->
                runOnUiThread { binding.tvConnectionStatus.text = status }
            },
            onPacketReceived = { packet ->
                runOnUiThread {
                    adapter.addMessage(packet)
                    binding.rvMessages.scrollToPosition(adapter.itemCount - 1)
                    Toast.makeText(this, "تم استلام رسالة موجية وتحليلها", Toast.LENGTH_SHORT).show()
                }
            }
        )
        channelManager.connect()

        recorderManager = AudioRecorderManager { amp ->
            runOnUiThread { binding.liveWaveformView.addLiveAmplitude(amp) }
        }

        binding.btnChatSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnRecordHold.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    binding.tvRecordingHint.text = "جاري التقاط الصوت وتحليل الموجات..."
                    recorderManager.startRecording()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    binding.tvRecordingHint.text = "اضغط مطولاً للتحدث وتحويل الصوت لموجات"
                    val pcmBytes = recorderManager.stopRecording()
                    binding.liveWaveformView.clearLive()
                    if (pcmBytes.size > 1600) {
                        processAndSendVoice(pcmBytes)
                    } else {
                        Toast.makeText(this, "الرسالة قصيرة جداً", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun processAndSendVoice(pcmBytes: ByteArray) {
        binding.tvRecordingHint.text = "جاري ترجمة الموجات (حروف، كلمات، سكون، فراغ)..."
        lifecycleScope.launch(Dispatchers.Default) {
            val packet = AcousticWaveTranslator.translatePcmToWavePacket(
                pcmBytes = pcmBytes,
                roomId = channelManager.roomId,
                senderName = senderName,
                receiverName = receiverName
            )
            withContext(Dispatchers.Main) {
                channelManager.sendWavePacket(packet)
                adapter.addMessage(packet)
                binding.rvMessages.scrollToPosition(adapter.itemCount - 1)
                binding.tvRecordingHint.text = "اضغط مطولاً للتحدث وتحويل الصوت لموجات"
            }
        }
    }

    private fun playReconstructedWave(packet: WaveMessagePacket) {
        lifecycleScope.launch(Dispatchers.IO) {
            val wavFile = AcousticWaveTranslator.reconstructAudioFile(packet, cacheDir)
            withContext(Dispatchers.Main) {
                try {
                    mediaPlayer?.release()
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(wavFile.absolutePath)
                        prepare()
                        start()
                    }
                    Toast.makeText(this@ChatActivity, "جاري تشغيل الصوت المُعاد تركيبه", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@ChatActivity, "خطأ في تشغيل الصوت: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun exportWaveToWav(packet: WaveMessagePacket) {
        lifecycleScope.launch(Dispatchers.IO) {
            val outDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: filesDir
            val wavFile = AcousticWaveTranslator.reconstructAudioFile(packet, outDir)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@ChatActivity, "تم حفظ الملف الصوتي: ${wavFile.name}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        channelManager.disconnect()
    }
}
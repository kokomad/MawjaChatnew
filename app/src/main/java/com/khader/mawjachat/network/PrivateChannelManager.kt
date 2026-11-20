package com.khader.mawjachat.network

import com.google.gson.Gson
import com.khader.mawjachat.model.WaveMessagePacket
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class PrivateChannelManager(
    private val senderName: String,
    private val receiverName: String,
    private val sharedCode: String,
    private val relayUrl: String,
    private val onStatusChanged: (String, Boolean) -> Unit,
    private val onPacketReceived: (WaveMessagePacket) -> Unit
) {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    val roomId: String = generatePrivateRoomId(senderName, receiverName, sharedCode)

    companion object {
        fun generatePrivateRoomId(userA: String, userB: String, code: String): String {
            val cleanA = userA.trim().lowercase()
            val cleanB = userB.trim().lowercase()
            val cleanCode = code.trim()
            val sortedPair = listOf(cleanA, cleanB).sorted().joinToString(":")
            val rawKey = "MAWJA_PRIVATE|$sortedPair|$cleanCode"
            val digest = MessageDigest.getInstance("SHA-256").digest(rawKey.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }.take(24)
        }
    }

    fun connect() {
        onStatusChanged("جاري تأمين القناة الخاصة...", false)
        val fullUrl = "$relayUrl?room=$roomId&user=${senderName.trim()}"
        val request = Request.Builder().url(fullUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onStatusChanged("متصل بقناة خاصة مشفّرة (#${roomId.take(6)})", true)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val packet = gson.fromJson(text, WaveMessagePacket::class.java)
                    if (packet != null && packet.roomId == roomId && packet.senderName != senderName) {
                        onPacketReceived(packet)
                    }
                } catch (_: Exception) {
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onStatusChanged("وضع المعاينة المحلية (تحقق من خادم البث في الإعدادات)", false)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onStatusChanged("تم إغلاق القناة الخاصة", false)
            }
        })
    }

    fun sendWavePacket(packet: WaveMessagePacket): Boolean {
        val json = gson.toJson(packet)
        return webSocket?.send(json) ?: false
    }

    fun disconnect() {
        webSocket?.close(1000, "User left private room")
        webSocket = null
    }
}
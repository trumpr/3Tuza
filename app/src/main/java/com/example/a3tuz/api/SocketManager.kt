package com.example.a3tuz.api

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket

object SocketManager {
    private var socket: Socket? = null

    fun connect() {
        if (AppConfig.baseUrl.isEmpty()) return
        
        // Əgər socket artıq varsa və qoşuludursa, sadəcə identify et və məlumatları yenilə
        if (socket?.connected() == true) {
            Log.d("SocketManager", "Artıq qoşulub. Yenidən məlumatlar göndərilir...")
            socket?.emit("identify", AppConfig.currentUsername)
            socket?.emit("getRoomCounts")
            return
        }

        try {
            Log.d("SocketManager", "Qoşulur: ${AppConfig.baseUrl}")
            val opts = IO.Options()
            opts.forceNew = true
            opts.reconnection = true
            
            // Ngrok-un xəbərdarlıq səhifəsini keçmək üçün header əlavə edirik
            val headers = mutableMapOf<String, List<String>>()
            headers["ngrok-skip-browser-warning"] = listOf("true")
            opts.extraHeaders = headers
            
            socket = IO.socket(AppConfig.baseUrl, opts)
            
            socket?.on(Socket.EVENT_CONNECT) {
                Log.d("SocketManager", "Serverə bağlandı!")
                socket?.emit("identify", AppConfig.currentUsername)
                socket?.emit("getRoomCounts")
            }
            
            socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                Log.e("SocketManager", "Bağlantı xətası: ${args[0]}")
            }
            
            socket?.connect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getSocket(): Socket? {
        if (socket == null) {
            connect()
        } else if (!socket!!.connected()) {
            socket!!.connect()
        }
        return socket
    }

    fun disconnect() {
        socket?.disconnect()
        socket = null
    }
}

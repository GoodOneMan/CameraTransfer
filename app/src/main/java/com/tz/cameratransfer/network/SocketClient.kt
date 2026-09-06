package com.tz.cameratransfer.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer

/**
 * TCP-клиент для отправки фото и комментария на Windows-сервер.
 * Протокол: [Int32 BE: lenComment][comment UTF-8][Int32 BE: lenImage][image bytes]
 */
class SocketClient {

    companion object {
        const val CONNECT_TIMEOUT_MS = 8000
        const val WRITE_TIMEOUT_MS = 15000
        const val CHUNK_SIZE = 8192
    }

    /**
     * Отправляет сжатое изображение и комментарий.
     * Выполняется строго в IO-диспетчере.
     *
     * @throws Exception при любой сетевой ошибке (таймаут, refused, broken pipe)
     */
    suspend fun sendPhoto(
        host: String,
        port: Int,
        comment: String,
        imageBytes: ByteArray
    ): Boolean = withContext(Dispatchers.IO) {
        var socket: Socket? = null
        try {
            socket = Socket().apply {
                connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                soTimeout = WRITE_TIMEOUT_MS
            }

            DataOutputStream(socket.getOutputStream()).use { output ->
                // 1. Длина комментария (Big-Endian)
                val commentBytes = comment.toByteArray(Charsets.UTF_8)
                output.writeInt(commentBytes.size)

                // 2. Комментарий
                output.write(commentBytes)

                // 3. Длина изображения
                output.writeInt(imageBytes.size)

                // 4. Изображение по чанкам
                var offset = 0
                while (offset < imageBytes.size) {
                    val chunk = minOf(CHUNK_SIZE, imageBytes.size - offset)
                    output.write(imageBytes, offset, chunk)
                    offset += chunk
                }

                output.flush()
            }
            true
        } finally {
            try { socket?.close() } catch (_: Exception) { }
        }
    }
}
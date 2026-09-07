package com.tz.cameratransfer.viewmodel

import android.app.Application
import androidx.camera.core.ImageProxy
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tz.cameratransfer.data.SettingsDataStore
import com.tz.cameratransfer.network.SocketClient
import com.tz.cameratransfer.utils.ImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = SettingsDataStore(application)
    private val socketClient = SocketClient()

    private val _capturedImage = MutableStateFlow<ByteArray?>(null)
    val capturedImage: StateFlow<ByteArray?> = _capturedImage

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events: SharedFlow<String> = _events

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending

    // Событие для перехода на экран превью ТОЛЬКО когда фото готово
    private val _navigateToPreview = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateToPreview: SharedFlow<Unit> = _navigateToPreview

    // Событие для возврата назад после успешной отправки
    private val _navigateBack = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateBack: SharedFlow<Unit> = _navigateBack

    val serverIp = settings.serverIp
    val serverPort = settings.serverPort

    fun onImageCaptured(imageProxy: ImageProxy) {
        viewModelScope.launch {
            try {
                // 1. Мгновенно извлекаем сырые данные (это очень быстро)
                val buffer = imageProxy.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)

                val width = imageProxy.width
                val height = imageProxy.height
                val rotation = imageProxy.imageInfo.rotationDegrees

                // 2. НЕМЕДЛЕННО освобождаем ресурсы CameraX!
                // Камера может продолжать работать, пока идет сжатие.
                imageProxy.close()

                // 3. Тяжелое сжатие выполняем строго в фоновом IO-потоке
                val compressed = withContext(Dispatchers.IO) {
                    ImageCompressor.compressFromBytes(bytes, width, height, rotation)
                }

                _capturedImage.value = compressed
                _navigateToPreview.emit(Unit) // Триггерим переход на превью

            } catch (e: Exception) {
                // Гарантируем закрытие даже при ошибке
                runCatching { imageProxy.close() }
                _events.tryEmit("Ошибка обработки: ${e.localizedMessage}")
            }
        }
    }

    fun sendPhotoWithComment(comment: String) {
        val image = _capturedImage.value
        if (image == null) {
            _events.tryEmit("Нет фото для отправки")
            return
        }

        viewModelScope.launch {
            _isSending.value = true
            try {
                val ip = serverIp.first()
                val port = serverPort.first()

                val success = socketClient.sendPhoto(ip, port, comment, image)
                if (success) {
                    _events.tryEmit("✅ Отправлено на $ip:$port")
                    _capturedImage.value = null
                    _navigateBack.emit(Unit) // Триггерим возврат на камеру
                }
            } catch (e: Exception) {
                _events.tryEmit("❌ Сеть: ${e.localizedMessage}")
            } finally {
                _isSending.value = false
            }
        }
    }

    fun saveSettings(ip: String, port: Int) {
        viewModelScope.launch {
            try {
                settings.saveSettings(ip, port)
                _events.tryEmit("Настройки сохранены")
            } catch (e: Exception) {
                _events.tryEmit("Ошибка сохранения: ${e.message}")
            }
        }
    }

    fun clearCapturedImage() {
        _capturedImage.value = null
    }
}
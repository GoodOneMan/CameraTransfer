package com.tz.cameratransfer.viewmodel

import android.app.Application
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
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

/**
 * ViewModel для управления камерой, настроек и сетевой отправки.
 * Наследует AndroidViewModel для доступа к Application Context (DataStore).
 */
class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = SettingsDataStore(application)
    private val socketClient = SocketClient()

    // --- Состояния UI ---

    /** Захваченное и сжатое фото (JPEG ByteArray) */
    private val _capturedImage = MutableStateFlow<ByteArray?>(null)
    val capturedImage: StateFlow<ByteArray?> = _capturedImage

    /** Одноразовые события для Snackbar/Toast */
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events: SharedFlow<String> = _events

    /** Индикатор отправки */
    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending

    // одноразовое событие для навигации назад
    private val _navigateBack = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateBack: SharedFlow<Unit> = _navigateBack

    /** Настройки сервера (потоки) */
    val serverIp = settings.serverIp
    val serverPort = settings.serverPort

    /**
     * Обрабатывает захваченный кадр: сжимает и сохраняет.
     * ImageProxy обязательно закрывается в finally.
     */
    fun onImageCaptured(imageProxy: ImageProxy) {
        viewModelScope.launch {
            try {
                //val compressed = ImageCompressor.compress(imageProxy)
                // ИСПРАВЛЕНИЕ: тяжёлая операция выполняется в IO-потоке
                val compressed = withContext(Dispatchers.IO) {
                    ImageCompressor.compress(imageProxy)
                }
                _capturedImage.value = compressed
                _events.tryEmit("Фото готово к отправке")
            } catch (e: Exception) {
                _events.tryEmit("Ошибка обработки: ${e.localizedMessage}")
            } finally {
                imageProxy.close()
            }
        }
    }

    /**
     * Отправляет фото с комментарием на Windows-сервер.
     * Читает настройки из DataStore, выполняет в IO-потоке.
     */
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
                    // ИСПРАВЛЕНИЕ: отправляем сигнал навигации назад
                    _navigateBack.emit(Unit)
                }
            } catch (e: Exception) {
                _events.tryEmit("❌ Сеть: ${e.localizedMessage}")
            } finally {
                _isSending.value = false
            }
        }
    }

    /** Сохраняет IP и порт в DataStore */
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

    /** Сбрасывает захваченное фото (пользователь нажал "Назад") */
    fun clearCapturedImage() {
        _capturedImage.value = null
    }
}
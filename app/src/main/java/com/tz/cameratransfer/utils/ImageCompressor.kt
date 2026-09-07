package com.tz.cameratransfer.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import kotlin.math.max

object ImageCompressor {

    private const val MAX_DIMENSION = 2160
    private const val JPEG_QUALITY = 80

    /**
     * Оптимизированное сжатие. Принимает уже извлеченные данные,
     * чтобы ImageProxy можно было закрыть мгновенно.
     */
    fun compressFromBytes(
        bytes: ByteArray,
        width: Int,
        height: Int,
        rotationDegrees: Int
    ): ByteArray {
        // 1. Вычисляем масштаб сразу, без первого "холостого" декодирования!
        val scale = calculateInSampleSize(width, height)

        // 2. RGB_565 использует 2 байта на пиксель вместо 4 (ARGB_8888).
        // Это критически важно для предотвращения OutOfMemory на задних камерах высокого разрешения.
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = scale
            inPreferredConfig = Bitmap.Config.RGB_565
        }

        var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
            ?: throw IllegalStateException("Не удалось декодировать изображение")

        // 3. Поворот только если необходим
        if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (bitmap != rotatedBitmap) {
                bitmap.recycle() // Освобождаем память от исходного битмапа
            }
            bitmap = rotatedBitmap
        }

        // 4. Финальное сжатие в JPEG
        return ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
            bitmap.recycle() // Обязательно очищаем память
            stream.toByteArray()
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int): Int {
        val maxDim = max(width, height)
        if (maxDim <= MAX_DIMENSION) return 1
        var inSampleSize = 1
        while (maxDim / (inSampleSize * 2) >= MAX_DIMENSION) {
            inSampleSize *= 2
        }
        return inSampleSize
    }
}
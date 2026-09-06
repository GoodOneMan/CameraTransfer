package com.tz.cameratransfer.utils
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

/**
 * Утилита сжатия фото перед сетевой отправкой.
 * Ограничивает длинную сторону до 2160px, качество JPEG 80%.
 * Автоматически поворачивает согласно EXIF-ориентации камеры.
 */
object ImageCompressor {

    private const val MAX_DIMENSION = 2160
    private const val JPEG_QUALITY = 80

    fun compress(imageProxy: ImageProxy): ByteArray {
        // ImageProxy в CameraX использует YUV_420_888, берём первую плоскость (JPEG-encoded)
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        // Определяем исходные размеры без полной декодировки
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

        // Вычисляем inSampleSize (степень 2) для уменьшения разрешения
        val scale = calculateInSampleSize(options.outWidth, options.outHeight)

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = scale
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
            ?: throw IllegalStateException("Не удалось декодировать изображение")

        // Поворот согласно ориентации сенсора
        val rotation = imageProxy.imageInfo.rotationDegrees
        if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

        return ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
            bitmap.recycle()
            stream.toByteArray()
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int): Int {
        val maxDim = maxOf(width, height)
        if (maxDim <= MAX_DIMENSION) return 1
        var inSampleSize = 1
        while (maxDim / (inSampleSize * 2) >= MAX_DIMENSION) {
            inSampleSize *= 2
        }
        return inSampleSize
    }
}
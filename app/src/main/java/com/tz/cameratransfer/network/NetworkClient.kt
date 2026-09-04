package com.tz.cameratransfer.network

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.io.File
import java.io.FileOutputStream

interface ApiService {
    @Multipart
    @POST("api/receive")
    suspend fun sendPhoto(
        @Part image: MultipartBody.Part,
        @Part("comment") comment: RequestBody
    ): Response<ResponseBody>
}

object NetworkClient {
    private val client by lazy {
        OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()
    }

    fun getApiService(baseUrl: String): ApiService = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .build()
        .create(ApiService::class.java)

    // Сжатие изображения до 1080p и качества 80%
    fun compressImage(context: Context, uri: Uri): File {
        val inputStream = context.contentResolver.openInputStream(uri)
        var bitmap = BitmapFactory.decodeStream(inputStream) ?: throw IllegalArgumentException("Не удалось декодировать изображение")

        // Масштабирование, если разрешение превышает 1080p по любой оси
        if (bitmap.width > 1080 || bitmap.height > 1080) {
            val ratio = if (bitmap.width > bitmap.height) 1080f / bitmap.width else 1080f / bitmap.height
            val newWidth = (bitmap.width * ratio).toInt()
            val newHeight = (bitmap.height * ratio).toInt()
            bitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        }

        val file = File(context.cacheDir, "compressed_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it)
        }
        return file
    }
}
package com.videoclipper.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

object FacebookHelper {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun uploadVideo(pageId: String, token: String, file: File, hashtags: String, delayMins: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("access_token", token)
                    .addFormDataPart("description", hashtags)
                    .addFormDataPart("source", file.name, file.asRequestBody("video/mp4".toMediaTypeOrNull()))

                if (delayMins > 0) {
                    val timestamp = (System.currentTimeMillis() / 1000) + (delayMins * 60)
                    builder.addFormDataPart("published", "false")
                    builder.addFormDataPart("scheduled_publish_time", timestamp.toString())
                } else {
                    builder.addFormDataPart("published", "true")
                }

                val request = Request.Builder()
                    .url("https://graph.facebook.com/v19.0/$pageId/videos")
                    .post(builder.build())
                    .build()

                client.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
}

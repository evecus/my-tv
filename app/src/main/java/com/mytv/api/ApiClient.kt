package com.github.mytv.api

import com.github.mytv.requests.ReleaseService
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class ApiClient {

    companion object {
        private const val RELEASE_HOST = "https://github.com/evecus/my-tv/releases/latest/download/"
        const val DOWNLOAD_HOST = "https://github.com/evecus/my-tv/releases/download/v"
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    val releaseService: ReleaseService by lazy {
        Retrofit.Builder()
            .baseUrl(RELEASE_HOST)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ReleaseService::class.java)
    }
}

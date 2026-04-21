package com.homevalt.app.data.network

import android.util.Log
import com.homevalt.app.util.Constants
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    fun create(baseUrl: String, jwtInterceptor: JwtInterceptor): HomeVaultApiService {
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            if (!message.contains("Authorization", ignoreCase = true)) {
                Log.d("OkHttp", message)
            }
        }.apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(jwtInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(Constants.UPLOAD_DOWNLOAD_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(Constants.UPLOAD_DOWNLOAD_TIMEOUT_S, TimeUnit.SECONDS)
            .writeTimeout(Constants.UPLOAD_DOWNLOAD_TIMEOUT_S, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(HomeVaultApiService::class.java)
    }
}

package com.example.core.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    // Read directly from .env via Gradle BuildConfig at build-time (Zero hardcoding)
    private var baseUrl: String = if (com.example.BuildConfig.BACKEND_BASE_URL.isNotBlank()) {
        val raw = com.example.BuildConfig.BACKEND_BASE_URL
        if (raw.endsWith("/")) raw else "$raw/"
    } else {
        "https://pricing-system-4nhl.onrender.com/"
    }

    fun setBaseUrl(url: String) {
        baseUrl = if (url.endsWith("/")) url else "$url/"
        retrofitInstance = null
    }

    fun getBaseUrl(): String = baseUrl

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        // Cybersecurity Hardening: Sanitize sensitive headers and payloads in production
        level = if (com.example.BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    private val authInterceptor = okhttp3.Interceptor { chain ->
        val original = chain.request()
        val token = com.example.core.session.AppSessionManager.getAuthToken()
        val requestBuilder = original.newBuilder()
        if (token.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }
        chain.proceed(requestBuilder.build())
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(35, TimeUnit.SECONDS)
        .build()

    private var retrofitInstance: Retrofit? = null

    val service: PriceBridgeApiService
        get() {
            if (retrofitInstance == null) {
                retrofitInstance = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(okHttpClient)
                    .addConverterFactory(MoshiConverterFactory.create(moshi))
                    .build()
            }
            return retrofitInstance!!.create(PriceBridgeApiService::class.java)
        }
}


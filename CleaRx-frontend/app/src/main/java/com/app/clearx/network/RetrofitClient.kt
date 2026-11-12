package com.app.clearx.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val validationClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    private val processingClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    @Volatile private var validationRetrofit: Retrofit? = null
    @Volatile private var processingRetrofit: Retrofit? = null
    @Volatile private var validationBaseUrl: String        = ""

    @Volatile private var processingBaseUrl: String = ""

    private fun buildRetrofit(baseUrl: String, client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    fun getService(baseUrl: String): ApiService {
        if (validationRetrofit == null || validationBaseUrl != baseUrl) {
            synchronized(this) {
                if (validationRetrofit == null || validationBaseUrl != baseUrl) {
                    validationBaseUrl    = baseUrl
                    validationRetrofit = buildRetrofit(baseUrl, validationClient)
                }
            }
        }
        return validationRetrofit!!.create(ApiService::class.java)
    }

    fun getProcessingService(baseUrl: String): ApiService {
        if (processingRetrofit == null || processingBaseUrl != baseUrl) {
            synchronized(this) {
                if (processingRetrofit == null || processingBaseUrl != baseUrl) {
                    processingBaseUrl    = baseUrl
                    processingRetrofit = buildRetrofit(baseUrl, processingClient)
                }
            }
        }
        return processingRetrofit!!.create(ApiService::class.java)
    }
}
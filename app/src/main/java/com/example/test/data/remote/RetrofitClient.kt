package com.example.test.data.remote

import com.example.test.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private val BASE_URL = BuildConfig.BASE_URL

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BASIC
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .dns(object: okhttp3.Dns {
            override fun lookup(host: String): List<java.net.InetAddress> {
            require(host in setOf("localhost","127.0.0.1","10.0.2.2")) { "Local demo host required" }
            return listOf(java.net.InetAddress.getByName(if(host=="localhost") "127.0.0.1" else host))
            }
        })
        .addInterceptor { chain ->
            require(DemoNetworkPolicy.allowed(chain.request().url.toString())) { "Local demo request required" }
            chain.proceed(chain.request())
        }
        .addInterceptor { chain ->
            val req = chain.request().newBuilder().apply {
                header("User-Agent", "Yukio/${BuildConfig.VERSION_NAME} Android")
            }.build()
            chain.proceed(req)
        }
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: ApiService by lazy { retrofit.create(ApiService::class.java) }
}

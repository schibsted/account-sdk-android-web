package com.schibsted.account.example

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

object HttpClient {
    @JvmStatic
    val instance: OkHttpClient = run {
        val logging = HttpLoggingInterceptor()
        logging.setLevel(HttpLoggingInterceptor.Level.BODY)
        OkHttpClient.Builder().addNetworkInterceptor(logging).build()
    }
}

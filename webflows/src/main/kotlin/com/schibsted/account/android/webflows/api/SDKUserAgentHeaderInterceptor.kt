package com.schibsted.account.android.webflows.api

import android.os.Build
import com.schibsted.account.android.webflows.BuildConfig
import okhttp3.Interceptor

internal class SDKUserAgentHeaderInterceptor : Interceptor {
    val userAgentHeaderValue: String = "AccountSDKAndroidWeb/${BuildConfig.VERSION_NAME} " +
            "(Linux; Android ${Build.VERSION.RELEASE}; API ${Build.VERSION.SDK_INT}; " +
            "${Build.MANUFACTURER}; ${Build.MODEL})"

    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request = chain.request().newBuilder()
            .header("User-Agent", userAgentHeaderValue)
            .build()
        return chain.proceed(request)
    }
}

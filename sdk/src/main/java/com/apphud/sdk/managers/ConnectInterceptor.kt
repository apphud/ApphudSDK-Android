package com.apphud.sdk.managers

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

internal class ConnectInterceptor  : Interceptor {
    private var CONNECT_TIMEOUT = 2
    private var isFirst = true

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request: Request = chain.request()
        if(isFirst){
            isFirst = false
        } else {
            CONNECT_TIMEOUT = 5
        }
        return chain
            .withConnectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .proceed(request)
    }
}

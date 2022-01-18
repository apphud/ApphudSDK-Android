package com.apphud.sdk.managers

import com.apphud.sdk.ApphudLog

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.lang.Exception


class HttpRetryInterceptor : Interceptor {
    companion object {
        private const val STEP = 5_000L
        private const val MAX_COUNT = 10
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        ApphudLog.log("HttpRetryInterceptor: Making request for the first time.")
        val request: Request = chain.request()
        var response: Response? = null
        var isSuccess = false
        var tryCount: Byte = 0
        while (!isSuccess && tryCount < MAX_COUNT) {
            try {
                Thread.sleep(STEP * tryCount)
                response = chain.proceed(request)
                ApphudLog.log("HttpRetryInterceptor: Response is: " + response)
                isSuccess = response.isSuccessful

                if(!isSuccess){
                    ApphudLog.log("HttpRetryInterceptor: Request was not successful: Retrying " + tryCount)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                ApphudLog.log("HttpRetryInterceptor: Request was not successful: {} . Retrying." + tryCount)
            } finally {
                if(!isSuccess) {
                    response?.close()
                }
                tryCount++
            }
        }
        if(!isSuccess && tryCount >= MAX_COUNT){
            ApphudLog.log("HttpRetryInterceptor: Request was not successful. Stopped.")
        }
        return response ?: chain.proceed(request)
    }
}
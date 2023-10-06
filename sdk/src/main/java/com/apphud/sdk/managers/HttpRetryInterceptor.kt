package com.apphud.sdk.managers

import com.apphud.sdk.ApphudInternal
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.storage.SharedPreferencesStorage

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.lang.Exception


class HttpRetryInterceptor : Interceptor {
    companion object {
        private const val STEP = 3_000L
        private const val MAX_COUNT = 30
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request: Request = chain.request()
        var response: Response? = null
        var isSuccess = false
        var tryCount: Byte = 0
        while (!isSuccess && tryCount < MAX_COUNT) {
            try {
                response = chain.proceed(request)
                isSuccess = response.isSuccessful

                if(!isSuccess){
                    ApphudLog.logE("Request (${request.url.encodedPath}) failed with code (${response.code}). Will retry in ${STEP/1000} seconds (${tryCount}).")

                    //Fallback status processing
                    val errors = listOf(408, 500, 502, 503)
                    if(response.code in errors){
                        if(request.url.toString().contains("customer") && SharedPreferencesStorage.needProcessFallback()){
                            ApphudInternal.processFallback()
                            SharedPreferencesStorage.fallbackMode = true
                            ApphudLog.log("Fallback: ENABLED")
                        }
                    }
                    Thread.sleep(STEP)
                }
            } catch (e: Exception) {
                ApphudLog.logE("Request (${request.url.encodedPath}) failed with code (${response?.code ?: 0}). Will retry in ${STEP/1000} seconds (${tryCount}).")
                Thread.sleep(STEP)
            } finally {
                if(!isSuccess) {
                    response?.close()
                }
                tryCount++
            }
        }
        if(!isSuccess && tryCount >= MAX_COUNT){
            ApphudLog.logE("Reached max number (${MAX_COUNT}) of (${request.url.encodedPath}) request retries. Exiting..")
        }
        return response ?: chain.proceed(request)
    }
}
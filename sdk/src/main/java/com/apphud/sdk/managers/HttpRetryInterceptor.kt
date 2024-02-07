package com.apphud.sdk.managers

import com.apphud.sdk.ApphudInternal
import com.apphud.sdk.ApphudInternal.FALLBACK_ERRORS
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.processFallbackError
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.lang.Exception
import java.net.SocketTimeoutException

class HttpRetryInterceptor : Interceptor {
    companion object {
        private var STEP = 3_000L
        private var MAX_COUNT = 7
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

                if (!isSuccess) {

                    if (response != null) {
                        val isBlocked = RequestManager.checkLock403(request, response)
                        if (isBlocked) {
                            return response
                        }
                        if (response.code == 429) {
                            STEP = 6_000L
                            MAX_COUNT = 1
                        }
                    }

                    ApphudLog.logE(
                        "Request (${request.url.encodedPath}) failed with code (${response.code}). Will retry in ${STEP / 1000} seconds ($tryCount).",
                    )

                    if (response.code in FALLBACK_ERRORS) {
                        ApphudInternal.processFallbackError(request)
                    }
                    Thread.sleep(STEP)
                }
            } catch (e: SocketTimeoutException) {
                ApphudInternal.processFallbackError(request)
                ApphudLog.logE(
                    "Request (${request.url.encodedPath}) failed with code (${response?.code ?: 0}). Will retry in ${STEP / 1000} seconds ($tryCount).",
                )
                Thread.sleep(STEP)
            } catch (e: Exception) {
                ApphudLog.logE(
                    "Request (${request.url.encodedPath}) failed with code (${response?.code ?: 0}). Will retry in ${STEP / 1000} seconds ($tryCount).",
                )
                Thread.sleep(STEP)
            } finally {
                tryCount++

                if (!isSuccess && tryCount < MAX_COUNT) {
                    response?.close()
                }
            }
        }
        if (!isSuccess) {
            ApphudLog.logE("Reached max number (${MAX_COUNT}) of (${request.url.encodedPath}) request retries. Exiting..")
        }
        return response ?: chain.proceed(request)
    }


}

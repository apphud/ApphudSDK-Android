package com.apphud.sdk.managers

import com.apphud.sdk.APPHUD_DEFAULT_RETRIES
import com.apphud.sdk.ApphudInternal
import com.apphud.sdk.ApphudInternal.FALLBACK_ERRORS
import com.apphud.sdk.ApphudInternal.fallbackMode
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.ApphudUtils
import com.apphud.sdk.fallbackHost
import com.apphud.sdk.processFallbackError
import com.apphud.sdk.tryFallbackHost
import com.apphud.sdk.withRemovedScheme
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.lang.Exception
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class HttpRetryInterceptor : Interceptor {
    companion object {
        private var STEP = 1_000L
        internal var MAX_COUNT = APPHUD_DEFAULT_RETRIES
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request: Request = chain.request()
        var response: Response? = null
        var isSuccess = false
        var tryCount: Int = 0
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
                        // do not retry 429
                        if (response.code == 429) {
                            STEP = 6_000L
                            MAX_COUNT = 1
                        } else if (response.code in 200..499) {
                            // do not retry 200..499 http codes
                            return response
                        }
                    }

                    if (response.code in FALLBACK_ERRORS) {
                        ApphudInternal.processFallbackError(request, isTimeout = false)
                        if (ApphudInternal.fallbackMode) {
                            tryCount = MAX_COUNT
                        }
                    }

                    ApphudLog.logE(
                        "Request (${request.url}) failed with code (${response.code}). Will retry in ${STEP / 1000} seconds ($tryCount).",
                    )

                    Thread.sleep(STEP)
                }
            } catch (e: SocketTimeoutException) {
                if (!ApphudInternal.shouldRetryException(e, request.url.encodedPath)) {
                    throw e
                }

                ApphudInternal.processFallbackError(request, isTimeout = true)
                ApphudLog.logE(
                    "Request (${request.url}) failed with SocketTimeoutException. Will retry in ${STEP / 1000} seconds ($tryCount).",
                )
                if (ApphudInternal.fallbackMode) {
                    throw e
                }
                Thread.sleep(STEP)
            } catch (e: UnknownHostException) {
                if (!ApphudInternal.shouldRetryException(e, request.url.encodedPath)) {
                    throw e
                }
                // do not retry when there is internet connection, but still unknown host issue
                if (ApphudUtils.isOnline(ApphudInternal.context)) {
                    tryCount = MAX_COUNT
                    ApphudInternal.coroutineScope.launch {
                        tryFallbackHost()
                    }
                } else {
                    ApphudLog.logE(
                        "Request (${request.url}) failed with UnknownHostException. Will retry in ${STEP / 1000} seconds ($tryCount).",
                    )
                    Thread.sleep(STEP)
                }
            } catch (e: Exception) {
                if (!ApphudInternal.shouldRetryException(e, request.url.encodedPath)) {
                    throw e
                }
                ApphudLog.logE(
                    "Request (${request.url}) failed with Exception. Will retry in ${STEP / 1000} seconds ($tryCount).",
                )
                Thread.sleep(STEP)
            } finally {
                tryCount++

                if (!isSuccess && tryCount < MAX_COUNT && !(response?.code in 401..403)) {
//                    response?.close()
                }

                if (fallbackHost != null && fallbackHost?.withRemovedScheme() != request.url.host) {
                    // invalid host, need to abort these requests
                    tryCount = MAX_COUNT
                    throw UnknownHostException("APPHUD_HOST_CHANGED")
                }
            }
        }
        if (!isSuccess) {
            ApphudLog.logE("Reached max number (${MAX_COUNT}) of (${request.url.encodedPath}) request retries. Exiting..")
        }
        if (response != null) {
            return response
        } else {
            ApphudLog.log("Performing one more request ${request.url.encodedPath}")
            return chain.proceed(request)
        }
    }


}

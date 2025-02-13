package com.apphud.sdk.managers

import com.apphud.sdk.APPHUD_DEFAULT_RETRIES
import com.apphud.sdk.APPHUD_NO_TIME_TO_RETRY
import com.apphud.sdk.ApphudInternal
import com.apphud.sdk.ApphudInternal.FALLBACK_ERRORS
import com.apphud.sdk.ApphudInternal.shouldRetryRequest
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

@Deprecated("")
internal class LegacyHttpRetryInterceptor : Interceptor {
    companion object {
        private var STEP = 2_000L
        internal var MAX_COUNT = APPHUD_DEFAULT_RETRIES
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request: Request = chain.request()
        var response: Response? = null
        var isSuccess = false
        var tryCount: Int = 0


        while (!isSuccess && (tryCount < MAX_COUNT && shouldRetryRequest(request.url.encodedPath))) {
            try {
                if (response != null) { response.close() }
                response = chain.proceed(request)
                isSuccess = response.isSuccessful

                if (!isSuccess) {

                    val isBlocked = RequestManager.checkLock403(request, response)
                    if (isBlocked) {
                        return response
                    }
                    // do not retry 429
                    if (response.code == 429) {
                        STEP = 6_000L
                        MAX_COUNT = 1
                    } else if (response.code in 200..402) {
                        // do not retry 200..401 http codes
                        return response
                    } else if (response.code == 422) {
                        // do not retry 422 http code
                        return response
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

                ApphudLog.logE("Request (${request.url}) failed with Exception ${e}")

                if (!ApphudInternal.shouldRetryRequest(request.url.encodedPath)) {
                    throw e
                }

                ApphudInternal.processFallbackError(request, isTimeout = true)
                ApphudLog.logE(
                    "Request (${request.url}) failed with SocketTimeoutException. Will retry in ${STEP / 1000} seconds ($tryCount).",
                )
                if (ApphudInternal.fallbackMode) {
                    throw e
                }
                if (request.url.encodedPath.endsWith("customers")) {
                    RequestManager.previousException = e
                }
                Thread.sleep(STEP)
            } catch (e: UnknownHostException) {
                ApphudLog.logE("Request (${request.url}) failed with Exception ${e}")

                if (!ApphudInternal.shouldRetryRequest(request.url.encodedPath)) {
                    throw e
                }
                if (request.url.encodedPath.endsWith("customers")) {
                    RequestManager.previousException = e
                }
                // do not retry when there is internet connection, but still unknown host issue
                if (ApphudUtils.hasInternetConnection(ApphudInternal.context)) {
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

                ApphudLog.logE("Request (${request.url}) failed with Exception ${e}")

                if (!ApphudInternal.shouldRetryRequest(request.url.encodedPath)) {
                    throw e
                }
                if (e is IOException && e.message == "Canceled") {
                    // do not update previous exception
                } else if (request.url.encodedPath.endsWith("customers")) {
                    RequestManager.previousException = e
                }

                Thread.sleep(STEP)
            } finally {
                if (request.url.encodedPath.endsWith("customers")) {
                    if(RequestManager.retries < tryCount) {
                        RequestManager.retries = tryCount
                    }
                }
                tryCount++

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
        } else if (ApphudInternal.shouldRetryRequest(request.url.encodedPath)) {
            ApphudLog.log("Performing one more request ${request.url.encodedPath}")
            return chain.proceed(request)
        } else if (request.url.encodedPath.endsWith("customers") || request.url.encodedPath.endsWith("products")){
             throw RequestManager.previousException ?: Exception(APPHUD_NO_TIME_TO_RETRY)
        } else {
             return chain.proceed(request)
        }
    }
}

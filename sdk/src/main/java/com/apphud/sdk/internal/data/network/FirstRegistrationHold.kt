package com.apphud.sdk.internal.data.network

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * While `legacy_device_id` is still to be sent (the first launch after an update): on v4 the backend
 * finds this installation only once a registration has carried it, so other v4 requests wait for the
 * launch's first registration to finish, successfully or not, for [LIMIT_MS] at most.
 */
internal class FirstRegistrationHold(
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    @Volatile
    private var gate: CountDownLatch? = null

    @Volatile
    private var deadline = 0L

    val isClosed: Boolean get() = gate != null

    @Synchronized
    fun close(limitMs: Long = LIMIT_MS) {
        deadline = now() + limitMs
        if (gate == null) gate = CountDownLatch(1)
    }

    @Synchronized
    fun open() {
        gate?.countDown()
        gate = null
    }

    /** Blocks the request's thread while closed: until [open], or the limit. */
    fun await() {
        val current = gate ?: return
        val left = deadline - now()
        if (left <= 0) return
        try {
            current.await(left, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    companion object {
        const val LIMIT_MS = 10_000L
    }
}

/** Registrations and purchases carry `legacy_device_id` and go on; every other v4 request waits on [hold]. */
internal class FirstRegistrationHoldInterceptor(
    private val hold: FirstRegistrationHold,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val path = chain.request().url.encodedPath
        if (path.contains("/v4/") && !path.endsWith("/customers") && !path.endsWith("/subscriptions")) hold.await()
        return chain.proceed(chain.request())
    }
}

package com.apphud.sdk.internal.data

import com.apphud.sdk.ApphudError
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.internal.util.runCatchingCancellable
import com.apphud.sdk.managers.RequestManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max

internal class AnalyticsTracker(
    private val coroutineScope: CoroutineScope,
    private val userRepository: UserRepository,
    private val timeProvider: () -> Long = System::currentTimeMillis,
) {
    @Volatile
    private var _sdkLaunchedAt: Long = 0L
    val sdkLaunchedAt: Long get() = _sdkLaunchedAt

    @Volatile
    private var _offeringsCalledAt: Long = 0L

    @Volatile
    private var _firstCustomerLoadedTime: Long? = null
    val isFirstCustomerLoaded: Boolean get() = _firstCustomerLoadedTime != null

    @Volatile
    private var _productsLoadedTime: Long? = null

    @Volatile
    private var _trackedAnalytics = false
    val trackedAnalytics: Boolean get() = _trackedAnalytics

    fun recordSdkLaunch() {
        _sdkLaunchedAt = timeProvider()
    }

    fun recordOfferingsCalled() {
        _offeringsCalledAt = timeProvider()
    }

    fun recordFirstCustomerLoaded() {
        if (_firstCustomerLoadedTime == null) {
            _firstCustomerLoadedTime = timeProvider()
        }
    }

    fun recordProductsLoaded(timeMs: Long) {
        _productsLoadedTime = timeMs
    }

    fun trackAnalytics(
        success: Boolean,
        latestError: ApphudError?,
        productCount: Int,
        billingResponseCode: Int,
    ) {
        if (_trackedAnalytics) {
            return
        }

        val currentUserId = userRepository.getUserId()
        val currentDeviceId = userRepository.getDeviceId()
        if (currentUserId == null || currentDeviceId == null) {
            ApphudLog.logE("Cannot track analytics: user not loaded or deviceId not set")
            return
        }

        _trackedAnalytics = true
        val totalLoad = (timeProvider() - _sdkLaunchedAt)
        val userLoad = if (_firstCustomerLoadedTime != null) (_firstCustomerLoadedTime!! - _sdkLaunchedAt) else 0
        val productsLoaded = _productsLoadedTime ?: 0
        ApphudLog.logI("SDK Benchmarks: User ${userLoad}ms, Products: ${productsLoaded}ms, Total: ${totalLoad}ms, Apphud Error: ${latestError?.message}, Billing Response Code: ${billingResponseCode}, ErrorCode: ${latestError?.errorCode}")
        coroutineScope.launch {
            runCatchingCancellable {
                RequestManager.sendPaywallLogs(
                    _sdkLaunchedAt,
                    productCount,
                    userLoad.toDouble(),
                    productsLoaded.toDouble(),
                    totalLoad.toDouble(),
                    latestError,
                    billingResponseCode,
                    success,
                    currentUserId,
                    currentDeviceId
                )
            }.onFailure { error ->
                ApphudLog.logE("Failed to send analytics: ${error.message}")
            }
        }
    }

    fun shouldRetryRequest(
        request: String,
        hasPendingCallbacks: Boolean,
        notifiedAboutPaywallsDidFullyLoaded: Boolean,
        didRegisterCustomerAtThisLaunch: Boolean,
        preferredTimeout: Double,
    ): Boolean {
        val diff = (timeProvider() - max(_offeringsCalledAt, _sdkLaunchedAt)) / 1000.0

        if (diff > preferredTimeout && hasPendingCallbacks && !notifiedAboutPaywallsDidFullyLoaded) {
            if (request.endsWith("products")) {
                ApphudLog.log("MAX TIMEOUT REACHED FOR $request")
                return false
            } else if (request.endsWith("customers") && !didRegisterCustomerAtThisLaunch) {
                ApphudLog.log("MAX TIMEOUT REACHED FOR $request")
                return false
            } else if (request.endsWith("billing")) {
                ApphudLog.log("MAX TIMEOUT REACHED FOR $request")
                return false
            }
        }

        return true
    }

    fun reset() {
        _sdkLaunchedAt = 0L
        _offeringsCalledAt = 0L
        _firstCustomerLoadedTime = null
        _productsLoadedTime = null
        _trackedAnalytics = false
    }
}

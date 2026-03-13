package com.apphud.sdk.internal.data

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsTrackerTest {

    private val userRepository: UserRepository = mockk {
        every { getUserId() } returns "test-user-id"
        every { getDeviceId() } returns "test-device-id"
    }

    private val tracker = AnalyticsTracker(
        coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        userRepository = userRepository,
    )

    // region trackAnalytics

    @Test
    fun `GIVEN already tracked EXPECT trackAnalytics is no-op`() {
        tracker.trackAnalytics(success = true, latestError = null, productCount = 5, billingResponseCode = 0)

        tracker.trackAnalytics(success = true, latestError = null, productCount = 5, billingResponseCode = 0)

        assertTrue(tracker.trackedAnalytics)
    }

    @Test
    fun `GIVEN userId null EXPECT trackAnalytics does not send`() {
        every { userRepository.getUserId() } returns null

        tracker.trackAnalytics(success = true, latestError = null, productCount = 5, billingResponseCode = 0)

        assertFalse(tracker.trackedAnalytics)
    }

    @Test
    fun `GIVEN valid state EXPECT trackedAnalytics becomes true`() {
        tracker.trackAnalytics(success = true, latestError = null, productCount = 5, billingResponseCode = 0)

        assertTrue(tracker.trackedAnalytics)
    }

    // endregion

    // region shouldRetryRequest

    @Test
    fun `GIVEN timeout exceeded and pending callbacks EXPECT shouldRetryRequest returns false for products`() {
        var currentTime = 1000L
        val tracker = AnalyticsTracker(
            coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            userRepository = userRepository,
            timeProvider = { currentTime },
        )
        tracker.recordSdkLaunch()
        tracker.recordOfferingsCalled()
        currentTime = 21_000L

        val result = tracker.shouldRetryRequest(
            request = "products",
            hasPendingCallbacks = true,
            notifiedAboutPaywallsDidFullyLoaded = false,
            didRegisterCustomerAtThisLaunch = false,
            preferredTimeout = 10.0,
        )

        assertFalse(result)
    }

    @Test
    fun `GIVEN within timeout EXPECT shouldRetryRequest returns true`() {
        var currentTime = 1000L
        val tracker = AnalyticsTracker(
            coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            userRepository = userRepository,
            timeProvider = { currentTime },
        )
        tracker.recordSdkLaunch()
        tracker.recordOfferingsCalled()

        val result = tracker.shouldRetryRequest(
            request = "products",
            hasPendingCallbacks = true,
            notifiedAboutPaywallsDidFullyLoaded = false,
            didRegisterCustomerAtThisLaunch = false,
            preferredTimeout = 10.0,
        )

        assertTrue(result)
    }

    @Test
    fun `GIVEN already notified EXPECT shouldRetryRequest returns true regardless of timeout`() {
        var currentTime = 1000L
        val tracker = AnalyticsTracker(
            coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            userRepository = userRepository,
            timeProvider = { currentTime },
        )
        tracker.recordSdkLaunch()
        tracker.recordOfferingsCalled()
        currentTime = 21_000L

        val result = tracker.shouldRetryRequest(
            request = "products",
            hasPendingCallbacks = true,
            notifiedAboutPaywallsDidFullyLoaded = true,
            didRegisterCustomerAtThisLaunch = false,
            preferredTimeout = 10.0,
        )

        assertTrue(result)
    }

    @Test
    fun `GIVEN timeout exceeded for customers but already registered EXPECT returns true`() {
        var currentTime = 1000L
        val tracker = AnalyticsTracker(
            coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            userRepository = userRepository,
            timeProvider = { currentTime },
        )
        tracker.recordSdkLaunch()
        tracker.recordOfferingsCalled()
        currentTime = 21_000L

        val result = tracker.shouldRetryRequest(
            request = "customers",
            hasPendingCallbacks = true,
            notifiedAboutPaywallsDidFullyLoaded = false,
            didRegisterCustomerAtThisLaunch = true,
            preferredTimeout = 10.0,
        )

        assertTrue(result)
    }

    // endregion

    // region reset

    @Test
    fun `GIVEN tracked state EXPECT reset clears tracking flag`() {
        tracker.trackAnalytics(success = true, latestError = null, productCount = 5, billingResponseCode = 0)
        tracker.recordFirstCustomerLoaded()
        tracker.recordProductsLoaded(67890L)

        tracker.reset()

        assertFalse(tracker.trackedAnalytics)
    }

    // endregion
}

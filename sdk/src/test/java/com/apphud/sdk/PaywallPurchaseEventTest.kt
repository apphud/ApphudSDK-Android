package com.apphud.sdk

import android.content.Context
import android.content.pm.ApplicationInfo
import com.android.billingclient.api.BillingClient
import com.apphud.sdk.internal.ServiceLocator
import com.apphud.sdk.internal.data.UserRepository
import com.apphud.sdk.internal.data.dto.PaywallEventDto
import com.apphud.sdk.internal.data.remote.RemoteRepository
import com.apphud.sdk.internal.domain.model.PurchasedPlan
import com.apphud.sdk.internal.domain.model.RequestIds
import com.apphud.sdk.managers.RequestManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** The properties of the three purchase paywall events, as they reach the events request. */
class PaywallPurchaseEventTest {

    private val sent = slot<PaywallEventDto>()
    private val repository: RemoteRepository = mockk {
        coEvery { trackEvent(capture(sent)) } returns Result.success(Unit)
    }
    private val userRepository: UserRepository = mockk {
        every { getRequestIds() } returns RequestIds(userId = "user", installationId = "installation", anonymousId = "anon")
        every { getCurrentUser() } returns mockk()
    }
    private val context: Context = mockk {
        every { applicationInfo } returns ApplicationInfo()
    }
    private val plan = PurchasedPlan("annual", "bundle")

    init {
        mockkObject(ServiceLocator.Companion, ApphudInternal)
        every { ServiceLocator.instance } returns mockk {
            every { remoteRepository } returns repository
            every { this@mockk.userRepository } returns this@PaywallPurchaseEventTest.userRepository
        }
        every { ApphudInternal.userRepository } returns userRepository
        RequestManager.setParams(context, "key")
    }

    @After
    fun tearDown() {
        RequestManager.cleanRegistration()
        unmockkAll()
    }

    private fun properties(vararg extra: Pair<String, String>) =
        mapOf("paywall_id" to "paywall", "product_id" to "pro") + extra

    private val planKeys = arrayOf("base_plan_id" to "annual", "product_bundle_id" to "bundle")

    @Test
    fun `GIVEN a plan EXPECT checkout initiated carries it`() = runTest {
        RequestManager.paywallCheckoutInitiated("paywall", null, "pro", null, plan)

        assertEquals(properties(*planKeys), sent.captured.properties)
    }

    @Test
    fun `GIVEN a plan EXPECT payment cancelled carries it`() = runTest {
        RequestManager.paywallPaymentCancelled("paywall", null, "pro", plan)

        assertEquals(properties(*planKeys), sent.captured.properties)
    }

    @Test
    fun `GIVEN a plan EXPECT payment error carries it`() = runTest {
        RequestManager.paywallPaymentError("paywall", null, "pro", "ERROR", plan)

        assertEquals(properties("error_message" to "ERROR", *planKeys), sent.captured.properties)
    }

    @Test
    fun `GIVEN a one-time product EXPECT no base plan key`() = runTest {
        RequestManager.paywallCheckoutInitiated("paywall", null, "pro", null, PurchasedPlan(null, "bundle"))

        assertEquals(properties("product_bundle_id" to "bundle"), sent.captured.properties)
    }

    @Test
    fun `GIVEN no plan EXPECT the properties of 3_7`() = runTest {
        RequestManager.paywallPaymentCancelled("paywall", null, "pro")

        assertEquals(properties(), sent.captured.properties)
    }

    @Test
    fun `GIVEN a billing failure EXPECT the payment error told the plan`() = runTest {
        mockkObject(RequestManager)
        val told = slot<PurchasedPlan>()
        coEvery { RequestManager.paywallPaymentError(any(), any(), any(), any(), capture(told)) } returns Unit
        coEvery { ApphudInternal.awaitUserRegistration(any()) } returns Unit
        every { ApphudInternal.coroutineScope } returns this

        ApphudInternal.paywallPaymentCancelled("paywall", null, "pro", BillingClient.BillingResponseCode.ERROR, plan)
        testScheduler.advanceUntilIdle()

        assertEquals(plan, told.captured)
    }
}

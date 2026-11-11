package com.apphud.sdk.internal

import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeResponseListener
import com.apphud.sdk.ApphudUtils
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BillingWrapperConsumeTest {

    private val token = "purchase-token"
    private val context: Context = mockk()
    private val builder: BillingClient.Builder = mockk()
    private val client: BillingClient = mockk {
        every { isReady } returns true
        every { consumeAsync(any(), any()) } answers {
            secondArg<ConsumeResponseListener>().onConsumeResponse(result(BillingClient.BillingResponseCode.OK), token)
        }
    }

    init {
        mockkObject(ApphudUtils)
        mockkStatic(BillingClient::class)
        every { ApphudUtils.isEmulator() } returns true
        every { ApphudUtils.logging } returns false
        every { BillingClient.newBuilder(context) } returns builder
        every { builder.enablePendingPurchases(any()) } returns builder
        every { builder.setListener(any()) } returns builder
        every { builder.build() } returns client
    }

    private val wrapper = BillingWrapper(context)

    @After
    fun tearDown() {
        unmockkAll()
    }

    // region consumeSync

    @Test
    fun `GIVEN connected billing EXPECT Google Play response code`() = runTest {
        assertEquals(BillingClient.BillingResponseCode.OK, wrapper.consumeSync(token).responseCode)
    }

    @Test
    fun `GIVEN billing cannot connect EXPECT the connection response code`() = runTest {
        every { client.isReady } returns false
        every { client.startConnection(any()) } answers {
            firstArg<BillingClientStateListener>().onBillingSetupFinished(result(BillingClient.BillingResponseCode.ERROR))
        }

        assertEquals(BillingClient.BillingResponseCode.ERROR, wrapper.consumeSync(token).responseCode)
    }

    @Test
    fun `GIVEN caller cancelled while billing connects EXPECT no fallback result`() = runTest {
        every { client.isReady } returns false
        every { client.startConnection(any()) } just runs
        var consumed: BillingResult? = null

        val job = launch { consumed = wrapper.consumeSync(token) }
        runCurrent()
        job.cancelAndJoin()

        assertNull(consumed)
    }

    // endregion

    private fun result(code: Int): BillingResult = BillingResult.newBuilder().setResponseCode(code).build()
}

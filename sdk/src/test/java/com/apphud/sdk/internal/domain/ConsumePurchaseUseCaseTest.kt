package com.apphud.sdk.internal.domain

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.apphud.sdk.ApphudError
import com.apphud.sdk.internal.BillingWrapper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsumePurchaseUseCaseTest {

    private val token = "purchase-token"

    private val billingWrapper: BillingWrapper = mockk {
        coEvery { consumeSync(any()) } returns billingResult(BillingClient.BillingResponseCode.OK)
    }
    private val useCase = ConsumePurchaseUseCase(billingWrapper)

    // region blank token

    @Test
    fun `GIVEN blank token EXPECT throws ApphudError`() = runTest {
        val error = runCatching { useCase(" ") }.exceptionOrNull()

        assertTrue(error is ApphudError)
    }

    @Test
    fun `GIVEN blank token EXPECT does not consume`() = runTest {
        runCatching { useCase(" ") }

        coVerify(exactly = 0) { billingWrapper.consumeSync(any()) }
    }

    // endregion

    // region valid token

    @Test
    fun `GIVEN valid token EXPECT consumes token`() = runTest {
        useCase(token)

        coVerify { billingWrapper.consumeSync(token) }
    }

    @Test
    fun `GIVEN billing accepts consumption EXPECT does not throw`() = runTest {
        val error = runCatching { useCase(token) }.exceptionOrNull()

        assertEquals(null, error)
    }

    @Test
    fun `GIVEN billing rejects consumption EXPECT throws error with billing response code`() = runTest {
        coEvery { billingWrapper.consumeSync(token) } returns
            billingResult(BillingClient.BillingResponseCode.ITEM_NOT_OWNED)

        val error = runCatching { useCase(token) }.exceptionOrNull() as ApphudError

        assertEquals(BillingClient.BillingResponseCode.ITEM_NOT_OWNED, error.errorCode)
    }

    // endregion

    private fun billingResult(code: Int): BillingResult =
        BillingResult.newBuilder().setResponseCode(code).build()
}

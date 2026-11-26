package com.apphud.sdk

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.domain.PurchaseRecordDetails
import com.apphud.sdk.internal.ApphudDispatchers
import com.apphud.sdk.internal.BillingWrapper
import com.apphud.sdk.internal.callback_status.PurchaseRestoredCallbackStatus
import com.apphud.sdk.internal.data.UserRepository
import com.apphud.sdk.managers.RequestManager
import com.apphud.sdk.storage.SharedPreferencesStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** `syncPurchasesReportingFailures`: success only when Google Play and the backend answered. */
class ObserverSyncResultTest {

    private fun purchase(id: String): Purchase = mockk {
        every { products } returns listOf(id)
        every { purchaseToken } returns "$id-token"
        every { purchaseTime } returns 0L
    }

    private fun record(purchase: Purchase): PurchaseRecordDetails {
        val id = purchase.products.first()
        return PurchaseRecordDetails(purchase, mockk<ProductDetails> { every { productId } returns id })
    }

    private val weekly = purchase("weekly")
    private val coins = purchase("coins")
    private val weeklyRecord = record(weekly)
    private val coinsRecord = record(coins)
    private val user: ApphudUser = mockk {
        every { subscriptions } returns emptyList()
        every { purchases } returns emptyList()
        every { placements } returns emptyList()
    }
    private val billing: BillingWrapper = mockk {
        coEvery { queryPurchasesSync() } returns (listOf(weekly) to BillingResponseCode.OK)
        coEvery { restoreSync(BillingClient.ProductType.SUBS, any()) } returns
            PurchaseRestoredCallbackStatus.Success(BillingClient.ProductType.SUBS, listOf(weeklyRecord))
        // Google Play answers the other type's query with an error when it knows none of the products.
        coEvery { restoreSync(BillingClient.ProductType.INAPP, any()) } returns
            PurchaseRestoredCallbackStatus.Error(BillingClient.ProductType.INAPP)
    }
    private val needSync = mutableListOf<Boolean>()
    private val storage: SharedPreferencesStorage = mockk {
        every { isNeedSync = capture(needSync) } just runs
    }

    init {
        mockkObject(ApphudInternal, RequestManager)
        every { ApphudInternal.billing } returns billing
        every { ApphudInternal.storage } returns storage
        every { ApphudInternal.userRepository } returns mockk<UserRepository> { every { getCurrentUser() } returns user }
        every { ApphudInternal.productDetails } returns emptyList()
        every { ApphudInternal.isIdentityV1 } returns false
        every { ApphudInternal.dispatchers } returns
            ApphudDispatchers(Dispatchers.Unconfined, Dispatchers.Unconfined, Dispatchers.Unconfined)
        every { ApphudInternal.notifyLoadingCompleted(any(), any(), any(), any()) } just runs
        coEvery { ApphudInternal.awaitUserRegistration(any()) } just runs
        coEvery { ApphudInternal.refreshEntitlements(any(), any()) } returns user
        coEvery { RequestManager.restorePurchases(any(), any(), any(), any()) } returns user
        ApphudInternal.prevPurchases.clear()
    }

    @After
    fun tearDown() {
        ApphudInternal.prevPurchases.clear()
        unmockkAll()
    }

    private suspend fun sync() = ApphudInternal.syncPurchasesReportingFailures()

    private fun assertSuccess(result: ApphudPurchasesRestoreResult) =
        assertTrue("$result", result is ApphudPurchasesRestoreResult.Success)

    private fun assertError(result: ApphudPurchasesRestoreResult) =
        assertTrue("$result", result is ApphudPurchasesRestoreResult.Error)

    private fun queryAnswers(purchases: List<Purchase>?, code: Int) {
        coEvery { billing.queryPurchasesSync() } returns (purchases to code)
    }

    @Test
    fun `GIVEN a new purchase EXPECT it sent in observer mode without restore`() = runTest {
        sync()

        coVerify(exactly = 1) { RequestManager.restorePurchases(null, listOf(weeklyRecord), true, false) }
    }

    @Test
    fun `GIVEN a new purchase the backend takes EXPECT success, the other type's error aside`() = runTest {
        assertSuccess(sync())
    }

    @Test
    fun `GIVEN identity version 1 EXPECT the same sync`() = runTest {
        every { ApphudInternal.isIdentityV1 } returns true

        sync()

        coVerify(exactly = 1) { RequestManager.restorePurchases(null, listOf(weeklyRecord), true, false) }
    }

    @Test
    fun `GIVEN no purchases EXPECT success`() = runTest {
        queryAnswers(emptyList(), BillingResponseCode.OK)

        assertSuccess(sync())
    }

    @Test
    fun `GIVEN a purchase already sent EXPECT success`() = runTest {
        ApphudInternal.prevPurchases.add(weeklyRecord)

        assertSuccess(sync())
    }

    @Test
    fun `GIVEN no purchases and an unreachable backend EXPECT an error`() = runTest {
        queryAnswers(emptyList(), BillingResponseCode.OK)
        coEvery { ApphudInternal.refreshEntitlements(any(), any()) } returns null

        assertError(sync())
    }

    @Test
    fun `GIVEN a purchase already sent and an unreachable backend EXPECT an error`() = runTest {
        ApphudInternal.prevPurchases.add(weeklyRecord)
        coEvery { ApphudInternal.refreshEntitlements(any(), any()) } returns null

        assertError(sync())
    }

    @Test
    fun `GIVEN a backend error EXPECT an error`() = runTest {
        coEvery { RequestManager.restorePurchases(any(), any(), any(), any()) } throws ApphudError("Server error", errorCode = 500)

        assertError(sync())
    }

    @Test
    fun `GIVEN a network error EXPECT an error`() = runTest {
        coEvery { RequestManager.restorePurchases(any(), any(), any(), any()) } throws IOException("offline")

        assertError(sync())
    }

    @Test
    fun `GIVEN no registration EXPECT an error`() = runTest {
        coEvery { ApphudInternal.awaitUserRegistration(any()) } throws ApphudError("Fallback mode")

        assertError(sync())
    }

    @Test
    fun `GIVEN Google Play unavailable EXPECT an error`() = runTest {
        queryAnswers(null, BillingResponseCode.BILLING_UNAVAILABLE)

        assertError(sync())
    }

    @Test
    fun `GIVEN Google Play unavailable EXPECT the sync still needed`() = runTest {
        queryAnswers(null, BillingResponseCode.BILLING_UNAVAILABLE)

        sync()

        assertEquals(listOf(true), needSync)
    }

    @Test
    fun `GIVEN one purchase query failed EXPECT the found purchases sent`() = runTest {
        queryAnswers(listOf(weekly), BillingResponseCode.SERVICE_UNAVAILABLE)

        sync()

        coVerify(exactly = 1) { RequestManager.restorePurchases(any(), listOf(weeklyRecord), true, false) }
    }

    @Test
    fun `GIVEN one purchase query failed EXPECT an error`() = runTest {
        queryAnswers(listOf(weekly), BillingResponseCode.SERVICE_UNAVAILABLE)

        assertError(sync())
    }

    @Test
    fun `GIVEN product details missing for one purchase EXPECT the other sent`() = runTest {
        queryAnswers(listOf(weekly, coins), BillingResponseCode.OK)

        sync()

        coVerify(exactly = 1) { RequestManager.restorePurchases(any(), listOf(weeklyRecord), true, false) }
    }

    @Test
    fun `GIVEN product details missing for one purchase EXPECT an error`() = runTest {
        queryAnswers(listOf(weekly, coins), BillingResponseCode.OK)

        assertError(sync())
    }

    @Test
    fun `GIVEN both types resolving their own products EXPECT success`() = runTest {
        queryAnswers(listOf(weekly, coins), BillingResponseCode.OK)
        coEvery { billing.restoreSync(BillingClient.ProductType.INAPP, any()) } returns
            PurchaseRestoredCallbackStatus.Success(BillingClient.ProductType.INAPP, listOf(coinsRecord))

        assertSuccess(sync())
    }

    @Test
    fun `GIVEN no product details at all EXPECT an error`() = runTest {
        queryAnswers(listOf(coins), BillingResponseCode.OK)
        coEvery { billing.restoreSync(BillingClient.ProductType.SUBS, any()) } returns
            PurchaseRestoredCallbackStatus.Error(BillingClient.ProductType.SUBS)

        assertError(sync())
    }

    @Test
    fun `GIVEN no product details at all EXPECT the sync still needed`() = runTest {
        queryAnswers(listOf(coins), BillingResponseCode.OK)
        coEvery { billing.restoreSync(BillingClient.ProductType.SUBS, any()) } returns
            PurchaseRestoredCallbackStatus.Error(BillingClient.ProductType.SUBS)

        sync()

        assertEquals(listOf(true), needSync)
    }

    @Test
    fun `GIVEN product details missing for one purchase EXPECT the sync still needed after sending the other`() = runTest {
        queryAnswers(listOf(weekly, coins), BillingResponseCode.OK)

        sync()

        assertEquals(true, needSync.last())
    }

    @Test
    fun `GIVEN one purchase query failed and nothing new EXPECT the sync still needed`() = runTest {
        queryAnswers(listOf(weekly), BillingResponseCode.SERVICE_UNAVAILABLE)
        ApphudInternal.prevPurchases.add(weeklyRecord)

        sync()

        assertEquals(true, needSync.last())
    }

    @Test
    fun `GIVEN a sync with nothing missing EXPECT it no longer needed`() = runTest {
        sync()

        assertEquals(false, needSync.last())
    }

    // syncPurchases() stays as in 3.7.2: it passes over what the reporting sync fails on.

    @Test
    fun `GIVEN Google Play unavailable EXPECT syncPurchases still successful`() = runTest {
        queryAnswers(null, BillingResponseCode.BILLING_UNAVAILABLE)

        assertSuccess(ApphudInternal.syncPurchases())
    }

    @Test
    fun `GIVEN an unreachable backend and nothing new EXPECT syncPurchases still successful`() = runTest {
        queryAnswers(emptyList(), BillingResponseCode.OK)
        coEvery { ApphudInternal.refreshEntitlements(any(), any()) } returns null

        assertSuccess(ApphudInternal.syncPurchases())
    }

    @Test
    fun `GIVEN product details missing for one purchase EXPECT syncPurchases still successful`() = runTest {
        queryAnswers(listOf(weekly, coins), BillingResponseCode.OK)

        assertSuccess(ApphudInternal.syncPurchases())
    }
}

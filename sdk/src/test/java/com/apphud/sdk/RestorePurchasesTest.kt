package com.apphud.sdk

import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.apphud.sdk.domain.PurchaseRecordDetails
import com.apphud.sdk.managers.RequestManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.runs
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RestorePurchasesTest {

    private val sent: PurchaseRecordDetails = mockk()
    private val other: PurchaseRecordDetails = mockk()

    @Before
    fun setUp() {
        mockkObject(ApphudInternal)
        mockkStatic("com.apphud.sdk.ApphudInternal_RestorePurchasesKt")
        mockkObject(RequestManager)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `GIVEN the restore the user asked for EXPECT a sync that sends restore`() = runTest {
        coEvery { ApphudInternal.syncPurchases(any(), any(), any(), any(), any()) } returns mockk()

        ApphudInternal.restorePurchases()

        coVerify { ApphudInternal.syncPurchases(observerMode = false, restore = true) }
    }

    @Test
    fun `GIVEN a restore of purchases already sent EXPECT the sync to send them as a restore`() = runTest {
        val details: ProductDetails = mockk { every { productId } returns "weekly" }
        val purchase: Purchase = mockk { every { products } returns listOf("weekly") }
        every { ApphudInternal.productDetails } returns listOf(details)
        coEvery { ApphudInternal.awaitUserRegistration() } just runs
        coEvery { ApphudInternal.sendPurchasesToApphud(any(), any(), any(), any(), any()) } returns mockk()

        ApphudInternal.syncPurchases(observerMode = false, unvalidatedPurchs = listOf(purchase), restore = true)

        coVerify { ApphudInternal.sendPurchasesToApphud(null, null, any(), false, true) }
    }

    @Test
    fun `GIVEN a restore EXPECT the request told so`() = runTest {
        coEvery { RequestManager.restorePurchases(any(), any(), any(), any()) } throws IllegalStateException("not sent")

        ApphudInternal.sendPurchasesToApphud(tempPurchaseRecordDetails = emptyList(), observerMode = false, restore = true)

        coVerify { RequestManager.restorePurchases(null, emptyList(), false, true) }
    }

    @Test
    fun `GIVEN an automatic sync EXPECT the request not told it is a restore`() = runTest {
        coEvery { RequestManager.restorePurchases(any(), any(), any(), any()) } throws IllegalStateException("not sent")

        ApphudInternal.sendPurchasesToApphud(tempPurchaseRecordDetails = emptyList(), observerMode = true)

        coVerify { RequestManager.restorePurchases(null, emptyList(), true, false) }
    }

    @Test
    fun `GIVEN a restore of purchases already sent EXPECT them sent again`() {
        assertFalse(skipsSync(restore = true, prevPurchases = listOf(sent), restoredPurchases = listOf(sent)))
    }

    @Test
    fun `GIVEN an automatic sync of purchases already sent EXPECT a skip`() {
        assertTrue(skipsSync(restore = false, prevPurchases = listOf(sent), restoredPurchases = listOf(sent)))
    }

    @Test
    fun `GIVEN an automatic sync of a new purchase EXPECT it sent`() {
        assertFalse(skipsSync(restore = false, prevPurchases = listOf(sent), restoredPurchases = listOf(sent, other)))
    }

    @Test
    fun `GIVEN nothing to send EXPECT a skip, a restore too`() {
        assertTrue(skipsSync(restore = true, prevPurchases = emptyList(), restoredPurchases = emptyList()))
        assertTrue(skipsSync(restore = false, prevPurchases = emptyList(), restoredPurchases = emptyList()))
    }
}

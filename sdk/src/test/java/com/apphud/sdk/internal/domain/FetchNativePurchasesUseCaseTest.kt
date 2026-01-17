package com.apphud.sdk.internal.domain

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import com.apphud.sdk.ApphudInternal
import com.apphud.sdk.domain.ApphudNonRenewingPurchase
import com.apphud.sdk.domain.ApphudSubscription
import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.internal.BillingWrapper
import com.apphud.sdk.internal.data.UserRepository
import com.apphud.sdk.syncPurchases
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FetchNativePurchasesUseCaseTest {

    private lateinit var billingWrapper: BillingWrapper
    private lateinit var userRepository: UserRepository
    private lateinit var useCase: FetchNativePurchasesUseCase

    private val mockUser: ApphudUser = mockk(relaxed = true)

    @Before
    fun setup() {
        billingWrapper = mockk(relaxed = true)
        userRepository = mockk(relaxed = true)
        useCase = FetchNativePurchasesUseCase(billingWrapper, userRepository)

        mockkObject(ApphudInternal)
        mockkStatic("com.apphud.sdk.ApphudInternal_RestorePurchasesKt")

        every { mockUser.subscriptions } returns emptyList()
        every { mockUser.purchases } returns emptyList()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `invoke should return empty list when no purchases`() = runTest {
        coEvery { billingWrapper.queryPurchasesSync() } returns Pair(null, BillingClient.BillingResponseCode.OK)

        val result = useCase()

        assertTrue("Should return empty list", result.first.isEmpty())
        assertEquals("Should return OK response code", BillingClient.BillingResponseCode.OK, result.second)
    }

    @Test
    fun `invoke should return purchases from billing`() = runTest {
        val mockPurchase: Purchase = mockk {
            every { purchaseToken } returns "token-1"
        }
        coEvery { billingWrapper.queryPurchasesSync() } returns Pair(listOf(mockPurchase), BillingClient.BillingResponseCode.OK)
        every { userRepository.getCurrentUser() } returns null

        val result = useCase(needSync = false)

        assertEquals("Should return one purchase", 1, result.first.size)
        assertEquals("Should return correct purchase", mockPurchase, result.first[0])
    }

    @Test
    fun `invoke should return correct response code`() = runTest {
        coEvery { billingWrapper.queryPurchasesSync() } returns Pair(null, BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)

        val result = useCase()

        assertEquals(
            "Should return SERVICE_UNAVAILABLE",
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            result.second
        )
    }

    @Test
    fun `invoke should filter already synced purchases`() = runTest {
        val syncedPurchase: Purchase = mockk {
            every { purchaseToken } returns "synced-token"
        }
        val newPurchase: Purchase = mockk {
            every { purchaseToken } returns "new-token"
        }

        val subscription: ApphudSubscription = mockk {
            every { purchaseToken } returns "synced-token"
        }
        every { mockUser.subscriptions } returns listOf(subscription)
        every { mockUser.purchases } returns emptyList()
        every { userRepository.getCurrentUser() } returns mockUser

        coEvery { billingWrapper.queryPurchasesSync() } returns Pair(
            listOf(syncedPurchase, newPurchase),
            BillingClient.BillingResponseCode.OK
        )
        coEvery { ApphudInternal.syncPurchases(any(), any(), any(), any()) } returns mockk()

        val result = useCase(needSync = true)

        assertEquals("Should return all purchases", 2, result.first.size)
        coVerify { ApphudInternal.syncPurchases(unvalidatedPurchs = listOf(newPurchase)) }
    }

    @Test
    fun `invoke should not sync when needSync is false`() = runTest {
        val mockPurchase: Purchase = mockk {
            every { purchaseToken } returns "new-token"
        }
        coEvery { billingWrapper.queryPurchasesSync() } returns Pair(listOf(mockPurchase), BillingClient.BillingResponseCode.OK)
        every { userRepository.getCurrentUser() } returns null

        useCase(needSync = false)

        coVerify(exactly = 0) { ApphudInternal.syncPurchases(any(), any(), any(), any()) }
    }

    @Test
    fun `invoke should not sync when all purchases already synced`() = runTest {
        val syncedPurchase: Purchase = mockk {
            every { purchaseToken } returns "synced-token"
        }

        val subscription: ApphudSubscription = mockk {
            every { purchaseToken } returns "synced-token"
        }
        every { mockUser.subscriptions } returns listOf(subscription)
        every { mockUser.purchases } returns emptyList()
        every { userRepository.getCurrentUser() } returns mockUser

        coEvery { billingWrapper.queryPurchasesSync() } returns Pair(listOf(syncedPurchase), BillingClient.BillingResponseCode.OK)

        useCase(needSync = true)

        coVerify(exactly = 0) { ApphudInternal.syncPurchases(any(), any(), any(), any()) }
    }

    @Test
    fun `invoke should filter purchases synced via non-renewing purchases`() = runTest {
        val syncedPurchase: Purchase = mockk {
            every { purchaseToken } returns "inapp-token"
        }
        val newPurchase: Purchase = mockk {
            every { purchaseToken } returns "new-token"
        }

        val nonRenewingPurchase: ApphudNonRenewingPurchase = mockk {
            every { purchaseToken } returns "inapp-token"
        }
        every { mockUser.subscriptions } returns emptyList()
        every { mockUser.purchases } returns listOf(nonRenewingPurchase)
        every { userRepository.getCurrentUser() } returns mockUser

        coEvery { billingWrapper.queryPurchasesSync() } returns Pair(
            listOf(syncedPurchase, newPurchase),
            BillingClient.BillingResponseCode.OK
        )
        coEvery { ApphudInternal.syncPurchases(any(), any(), any(), any()) } returns mockk()

        useCase(needSync = true)

        coVerify { ApphudInternal.syncPurchases(unvalidatedPurchs = listOf(newPurchase)) }
    }

    @Test
    fun `invoke should handle null user gracefully`() = runTest {
        val mockPurchase: Purchase = mockk {
            every { purchaseToken } returns "token-1"
        }
        coEvery { billingWrapper.queryPurchasesSync() } returns Pair(listOf(mockPurchase), BillingClient.BillingResponseCode.OK)
        every { userRepository.getCurrentUser() } returns null
        coEvery { ApphudInternal.syncPurchases(any(), any(), any(), any()) } returns mockk()

        val result = useCase(needSync = true)

        assertEquals("Should return purchases", 1, result.first.size)
        coVerify { ApphudInternal.syncPurchases(unvalidatedPurchs = listOf(mockPurchase)) }
    }

    @Test
    fun `invoke should sync all purchases when user has no known tokens`() = runTest {
        val purchase1: Purchase = mockk {
            every { purchaseToken } returns "token-1"
        }
        val purchase2: Purchase = mockk {
            every { purchaseToken } returns "token-2"
        }

        every { mockUser.subscriptions } returns emptyList()
        every { mockUser.purchases } returns emptyList()
        every { userRepository.getCurrentUser() } returns mockUser

        coEvery { billingWrapper.queryPurchasesSync() } returns Pair(
            listOf(purchase1, purchase2),
            BillingClient.BillingResponseCode.OK
        )
        coEvery { ApphudInternal.syncPurchases(any(), any(), any(), any()) } returns mockk()

        useCase(needSync = true)

        coVerify { ApphudInternal.syncPurchases(unvalidatedPurchs = listOf(purchase1, purchase2)) }
    }
}

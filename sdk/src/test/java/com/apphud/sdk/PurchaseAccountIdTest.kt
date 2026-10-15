package com.apphud.sdk

import com.android.billingclient.api.AccountIdentifiers
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.apphud.sdk.domain.ApphudSubscription
import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.internal.data.UserRepository
import com.apphud.sdk.internal.domain.model.ApphudIdentity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * `obfuscatedAccountId` is the installation id: the backend finds a Google purchase's owner only by
 * an installation key. Every account of the installation shares it, so no purchase carries another
 * account's id and the automatic sync skips none; `restore: false` keeps it with its owner.
 */
class PurchaseAccountIdTest {

    private val userRepository: UserRepository = mockk()

    @Before
    fun setUp() {
        mockkObject(ApphudInternal)
        mockkStatic("com.apphud.sdk.ApphudInternal_RestorePurchasesKt")
        every { ApphudInternal.userRepository } returns userRepository
        every { ApphudInternal.isIdentityV1 } returns false
        every { userRepository.getIdentity() } returns ApphudIdentity("installation-1", "anonymous-2", "bob")
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `GIVEN a purchase EXPECT the installation id as the obfuscated account id`() {
        assertEquals("installation-1", ApphudInternal.resolveObfuscatedAccountId(oldToken = null))
    }

    @Test
    fun `GIVEN identity version 1 EXPECT the device id as the obfuscated account id`() {
        every { ApphudInternal.isIdentityV1 } returns true
        every { userRepository.getDeviceId() } returns "device-1"

        assertEquals("device-1", ApphudInternal.resolveObfuscatedAccountId(oldToken = null))
    }

    @Test
    fun `GIVEN a plan change EXPECT the id of the subscription it replaces`() {
        val replaced: ApphudSubscription = mockk {
            every { purchaseToken } returns "old-token"
            every { obfuscatedDeviceId } returns "installation-0"
        }
        val user: ApphudUser = mockk { every { subscriptions } returns listOf(replaced) }
        every { userRepository.getCurrentUser() } returns user

        assertEquals("installation-0", ApphudInternal.resolveObfuscatedAccountId(oldToken = "old-token"))
    }

    @Test
    fun `GIVEN an automatic sync of purchases with another or no account id EXPECT them all sent without restore`() = runTest {
        val details: ProductDetails = mockk { every { productId } returns "weekly" }
        val older: Purchase = mockk {
            every { products } returns listOf("weekly")
            every { accountIdentifiers } returns null
        }
        val otherInstall: Purchase = mockk {
            every { products } returns listOf("weekly")
            every { accountIdentifiers } returns mockk<AccountIdentifiers> {
                every { obfuscatedAccountId } returns "installation-0"
            }
        }
        every { ApphudInternal.productDetails } returns listOf(details)
        coEvery { ApphudInternal.awaitUserRegistration() } just runs
        coEvery { ApphudInternal.sendPurchasesToApphud(any(), any(), any(), any(), any()) } returns mockk()

        ApphudInternal.syncPurchases(unvalidatedPurchs = listOf(older, otherInstall))

        coVerify {
            ApphudInternal.sendPurchasesToApphud(null, null, match { sent -> sent.map { it.purchase } == listOf(older, otherInstall) }, true, false)
        }
    }
}

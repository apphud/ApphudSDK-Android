package com.apphud.sdk

import android.content.Context
import android.content.SharedPreferences
import com.apphud.sdk.domain.ApphudKind
import com.apphud.sdk.domain.ApphudNonRenewingPurchase
import com.apphud.sdk.domain.ApphudSubscription
import com.apphud.sdk.domain.ApphudSubscriptionStatus
import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.internal.ServiceLocator
import com.apphud.sdk.internal.domain.model.ApiKey
import com.google.gson.GsonBuilder
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class ApphudHasPremiumAccessTest {

    private val mockEditor: SharedPreferences.Editor = mockk(relaxed = true)

    private val mockPreferences: SharedPreferences = mockk(relaxed = true) {
        every { edit() } returns mockEditor
    }

    private val mockContext: Context = mockk(relaxed = true) {
        every { getSharedPreferences(any(), any()) } returns mockPreferences
        every { applicationInfo } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        ApphudInternal.userId = null
        ServiceLocator.clearInstance()
    }

    @Test
    fun `hasPremiumAccess returns true when cached user has active subscription`() {
        val activeSubscription = createActiveSubscription()
        val cachedUser = createUserWithSubscriptions(listOf(activeSubscription))
        setupStorageWithCachedUser(cachedUser)
        createServiceLocator()
        ApphudInternal.userId = "test-user-id"

        val result = Apphud.hasPremiumAccess()

        assertTrue("Expected true for active subscription", result)
    }

    @Test
    fun `hasPremiumAccess returns false when cached user has no subscriptions`() {
        val cachedUser = createUserWithSubscriptions(emptyList())
        setupStorageWithCachedUser(cachedUser)
        createServiceLocator()

        val result = Apphud.hasPremiumAccess()

        assertFalse("Expected false when no subscriptions", result)
    }

    @Test
    fun `hasPremiumAccess returns false when no cached user`() {
        setupStorageWithCachedUser(null)
        createServiceLocator()

        val result = Apphud.hasPremiumAccess()

        assertFalse("Expected false when no cached user", result)
    }

    @Test
    fun `hasPremiumAccess returns true when cached user has active non-renewing purchase`() {
        val activePurchase = createActiveNonRenewingPurchase()
        val cachedUser = createUserWithPurchases(listOf(activePurchase))
        setupStorageWithCachedUser(cachedUser)
        createServiceLocator()
        ApphudInternal.userId = "test-user-id"

        val result = Apphud.hasPremiumAccess()

        assertTrue("Expected true for active non-renewing purchase", result)
    }

    private fun createActiveSubscription(): ApphudSubscription {
        return ApphudSubscription(
            status = ApphudSubscriptionStatus.REGULAR,
            productId = "premium_monthly",
            expiresAt = System.currentTimeMillis() + 86400000,
            startedAt = System.currentTimeMillis() - 86400000,
            cancelledAt = null,
            purchaseToken = "test-token",
            isInRetryBilling = false,
            isAutoRenewEnabled = true,
            isIntroductoryActivated = false,
            basePlanId = null,
            platform = "android",
            groupId = "group_1",
            kind = ApphudKind.AUTORENEWABLE
        )
    }

    private fun createActiveNonRenewingPurchase(): ApphudNonRenewingPurchase {
        return ApphudNonRenewingPurchase(
            productId = "lifetime_access",
            purchasedAt = System.currentTimeMillis() - 86400000,
            canceledAt = null,
            purchaseToken = "test-token",
            isConsumable = false,
            platform = "android"
        )
    }

    private fun createUserWithSubscriptions(subscriptions: List<ApphudSubscription>): ApphudUser {
        return ApphudUser(
            userId = "test-user-id",
            currencyCode = "USD",
            countryCode = "US",
            subscriptions = subscriptions,
            purchases = emptyList(),
            paywalls = emptyList(),
            placements = emptyList(),
            isTemporary = false
        )
    }

    private fun createUserWithPurchases(purchases: List<ApphudNonRenewingPurchase>): ApphudUser {
        return ApphudUser(
            userId = "test-user-id",
            currencyCode = "USD",
            countryCode = "US",
            subscriptions = emptyList(),
            purchases = purchases,
            paywalls = emptyList(),
            placements = emptyList(),
            isTemporary = false
        )
    }

    private fun setupStorageWithCachedUser(user: ApphudUser?) {
        val userJson = if (user != null) {
            GsonBuilder().serializeNulls().create().toJson(user)
        } else null

        every { mockPreferences.getString("APPHUD_USER_KEY", null) } returns userJson
        every { mockPreferences.getString("APPHUD_CACHE_VERSION", null) } returns "3"
    }

    private fun createServiceLocator() {
        val factory = ServiceLocator.ServiceLocatorInstanceFactory()
        factory.create(
            applicationContext = mockContext,
            ruleCallback = object : ApphudRuleCallback {},
            apiKey = ApiKey("test_api_key")
        )
    }
}

package com.apphud.sdk.internal.domain

import com.android.billingclient.api.Purchase
import com.apphud.sdk.ApphudInternal
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.internal.BillingWrapper
import com.apphud.sdk.internal.data.UserRepository
import com.apphud.sdk.syncPurchases

/**
 * UseCase for fetching native purchases from Google Play Billing.
 *
 * @param billingWrapper wrapper for Google Play Billing operations
 * @param userRepository repository for user data access
 */
internal class FetchNativePurchasesUseCase(
    private val billingWrapper: BillingWrapper,
    private val userRepository: UserRepository,
) {
    /**
     * Fetches native purchases from Google Play Billing.
     *
     * @param needSync if true, automatically syncs unsynced purchases with Apphud
     * @return Pair(list of purchases, BillingResponseCode)
     */
    suspend operator fun invoke(needSync: Boolean = true): Pair<List<Purchase>, Int> {
        val result = billingWrapper.queryPurchasesSync()
        val purchases = result.first ?: emptyList()
        val responseCode = result.second

        if (purchases.isNotEmpty()) {
            val notSyncedPurchases = filterNotSynced(purchases)
            if (needSync) {
                if (notSyncedPurchases.isNotEmpty()) {
                    ApphudInternal.syncPurchases(unvalidatedPurchs = notSyncedPurchases)
                } else {
                    ApphudLog.logI("Google Billing: All Tracked (${purchases.count()})")
                }
            }
        } else {
            ApphudLog.logI("Google Billing: No Active Purchases")
        }

        return Pair(purchases, responseCode)
    }

    private fun filterNotSynced(allPurchases: List<Purchase>): List<Purchase> {
        val user = userRepository.getCurrentUser()
        val allKnownTokens = (user?.subscriptions?.map { it.purchaseToken } ?: emptyList()) +
            (user?.purchases?.map { it.purchaseToken } ?: emptyList())
        return allPurchases.filter { !allKnownTokens.contains(it.purchaseToken) }
    }
}

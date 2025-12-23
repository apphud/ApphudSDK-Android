package com.apphud.sdk.domain

import com.apphud.sdk.ApphudInternal
import com.apphud.sdk.GroupId

data class ApphudGroup(
    internal val id: GroupId,
    /**
     * Name of permission group configured in Apphud dashboard.
     */
    val name: String,

    /**
     * for internal use only, use productIds()
     */
    internal val products: List<ApphudProduct>?
) {

    /**
     * Product IDs that belong to this permission group.
     */
    fun productIds(): List<String> {
        return products?.map { it.productId } ?: listOf()
    }

    /**
     * Returns `true` if this permission group has active subscription.
     * Keep in mind, that this method doesn't take into account non-renewing purchases.
     */
    fun hasAccess(): Boolean {
        ApphudInternal.userRepository.getCurrentUser()?.subscriptions?.forEach {
            if (it.isActive() && it.groupId == id) {
                return true
            }
        }
        return false
    }
}

package com.apphud.app.presentation

import com.android.billingclient.api.SkuDetails
import com.apphud.sdk.domain.ApphudNonRenewingPurchase
import com.apphud.sdk.domain.ApphudSubscription

class ProductModelMapper {

    fun map(details: SkuDetails) =
        ProductModel(
            productId = details.sku,
            details = details,
            subscription = null,
            purchase = null
        )

    fun map(purchase: ApphudNonRenewingPurchase) =
        ProductModel(
            productId = purchase.productId,
            details = null,
            subscription = null,
            purchase = purchase
        )

    fun map(subscription: ApphudSubscription) =
        ProductModel(
            productId = subscription.productId,
            details = null,
            subscription = subscription,
            purchase = null
        )

    fun map(product: ProductModel, details: SkuDetails) =
        product.copy(
            productId = product.productId,
            details = details
        )

    fun map(product: ProductModel, subscription: ApphudSubscription) =
        product.copy(
            productId = product.productId,
            subscription = subscription
        )

    fun map(product: ProductModel, purchase: ApphudNonRenewingPurchase) =
        product.copy(
            productId = product.productId,
            purchase = purchase
        )
}
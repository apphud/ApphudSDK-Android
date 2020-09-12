package ru.rosbank.mbdg.myapplication.presentation

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
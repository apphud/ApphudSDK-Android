package ru.rosbank.mbdg.myapplication

import com.android.billingclient.api.SkuDetails

interface ApphudListener {

    /**
    Returns array of `SkuDetails` objects after they are fetched from Billing. Note that you have to add all product identifiers in Apphud.

    You can use `productsDidFetchCallback` callback or observe for `didFetchProductsNotification()` or implement `apphudDidFetchStoreKitProducts` delegate method. Use whatever you like most.
     */
    fun apphudFetchSkuDetailsProducts(details: List<SkuDetails>)
}
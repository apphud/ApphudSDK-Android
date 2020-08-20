package ru.rosbank.mbdg.myapplication.domain

import com.android.billingclient.api.SkuDetails

data class ProductModel(
    val productId: String,
    val details: SkuDetails?
)
package ru.rosbank.mbdg.myapplication.presentation

import com.android.billingclient.api.SkuDetails

data class ProductModel(
    val productId: String,
    val details: SkuDetails?
)
package com.apphud.sdk.domain

import com.apphud.sdk.ProductId

data class Product(
    val id: String,
    val dbId: String,
    val groupId: String,
    val productId: ProductId
)
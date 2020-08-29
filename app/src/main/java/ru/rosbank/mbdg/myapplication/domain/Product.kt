package ru.rosbank.mbdg.myapplication.domain

import ru.rosbank.mbdg.myapplication.ProductId

data class Product(
    val id: String,
    val dbId: String,
    val groupId: String,
    val productId: ProductId
)
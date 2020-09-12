package com.apphud.sdk

import com.apphud.sdk.domain.Attribution
import com.apphud.sdk.domain.Customer
import com.apphud.sdk.domain.Product

typealias ApiKey = String
typealias UserId = String
typealias DeviceId = String
typealias ProductId = String

typealias Callback<T> = (T) -> Unit
typealias CustomerCallback = Callback<Customer>
typealias ProductsCallback = Callback<List<Product>>
typealias AttributionCallback = Callback<Attribution>
typealias PurchasedCallback = Callback<Customer>
package com.apphud.sdk

import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.Attribution
import com.apphud.sdk.domain.Customer
import com.apphud.sdk.domain.Product

typealias ApiKey = String
typealias UserId = String
typealias DeviceId = String
typealias ProductId = String

typealias Callback1<T> = (T) -> Unit
typealias Callback2<T1, T2> = (T1, T2) -> Unit
typealias CustomerCallback = Callback1<Customer>
typealias ProductsCallback = Callback1<List<Product>>
typealias AttributionCallback = Callback1<Attribution>
typealias PurchasedCallback = Callback1<Customer>
typealias PaywallCallback = Callback2<List<ApphudPaywall>?, ApphudError?>

typealias Milliseconds = Long
package com.apphud.sdk

import com.apphud.sdk.domain.*

typealias ApiKey = String
typealias UserId = String
typealias DeviceId = String
typealias GroupId = String
typealias ProductId = String

typealias Callback1<T> = (T) -> Unit
typealias Callback2<T1, T2> = (T1, T2) -> Unit
typealias CustomerCallback = Callback1<ApphudUser>
typealias ProductsCallback = Callback1<List<ApphudGroup>>
typealias AttributionCallback = Callback1<Attribution>
typealias PurchasedCallback = Callback2<ApphudUser?, ApphudError?>
typealias PaywallCallback = Callback2<List<ApphudPaywall>?, ApphudError?>

typealias Milliseconds = Long

typealias ApphudPurchasesRestoreCallback = (
    subscriptions: List<ApphudSubscription>?,
    purchases: List<ApphudNonRenewingPurchase>?,
    error: ApphudError?
) -> Unit
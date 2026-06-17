package com.apphud.sdk.internal.data.network

import com.apphud.sdk.ApphudUtils
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.atomic.AtomicReference

internal class UrlProvider {

    private val baseUrl = AtomicReference("https://gateway.apphud.com")

    val customersUrl: HttpUrl
        get() = "${baseUrl.get()}/v1/customers".toHttpUrl()

    val customerPropertiesUrl: HttpUrl
        get() = "${baseUrl.get()}/v1/customers/properties".toHttpUrl()

    val subscriptionsUrl: HttpUrl
        get() = "${baseUrl.get()}/v1/subscriptions".toHttpUrl()

    val productsUrl: HttpUrl
        get() = "${baseUrl.get()}/v2/products".toHttpUrl()

    val attributionUrl: HttpUrl
        get() = "${baseUrl.get()}/v2/customers/attribution".toHttpUrl()

    val deeplinkAttributionUrl: HttpUrl
        get() = "${baseUrl.get()}/v2/customers/deeplink_attribution".toHttpUrl()

    val connectHost: String
        get() = baseUrl.get()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("gateway.")
            .removePrefix("api.")

    val promotionsUrl: HttpUrl
        get() = "${baseUrl.get()}/v1/promotions".toHttpUrl()

    val eventsUrl: HttpUrl
        get() = "${baseUrl.get()}/v1/events".toHttpUrl()

    val notificationsReadUrl: HttpUrl
        get() = "${baseUrl.get()}/v2/notifications/read".toHttpUrl()

    val notificationsUrl: HttpUrl
        get() = "${baseUrl.get()}/v2/notifications".toHttpUrl()

    val renderPropertiesUrl: HttpUrl
        get() = "${baseUrl.get()}/v2/paywall_configs/items/render_properties".toHttpUrl()

    val previewScreenUrl: HttpUrl
        get() = "${baseUrl.get()}/preview_screen".toHttpUrl()

    fun updateBaseUrl(newUrl: String) {
        baseUrl.set(newUrl)
    }
}

package com.apphud.sdk.internal.data.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.atomic.AtomicReference

internal class UrlProvider {

    private val baseUrl = AtomicReference("https://gateway.apphud.com")

    val customersUrl: HttpUrl
        get() = "${baseUrl.get()}/v1/customers".toHttpUrl()

    val subscriptionsUrl: HttpUrl
        get() = "${baseUrl.get()}/v1/subscriptions".toHttpUrl()

    val productsUrl: HttpUrl
        get() = "${baseUrl.get()}/v2/products".toHttpUrl()

    val attributionUrl: HttpUrl
        get() = "${baseUrl.get()}/v1/attribution".toHttpUrl()

    val promotionsUrl: HttpUrl
        get() = "${baseUrl.get()}/v1/promotions".toHttpUrl()

    val eventsUrl: HttpUrl
        get() = "${baseUrl.get()}/v1/events".toHttpUrl()

    val notificationsReadUrl: HttpUrl
        get() = "${baseUrl.get()}/v2/notifications/read".toHttpUrl()

    val notificationsUrl: HttpUrl
        get() = "${baseUrl.get()}/v2/notifications".toHttpUrl()

    fun updateBaseUrl(newUrl: String) {
        baseUrl.set(newUrl)
    }
}

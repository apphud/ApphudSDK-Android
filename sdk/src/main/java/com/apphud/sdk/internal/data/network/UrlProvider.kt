package com.apphud.sdk.internal.data.network

import com.apphud.sdk.ApphudLog
import com.apphud.sdk.internal.data.dto.MetaDto
import com.apphud.sdk.storage.Storage
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.atomic.AtomicReference

internal class UrlProvider(
    private val storage: Storage,
) {

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

    val connectDomainUrl: String
        get() = storage.connectDomainUrl ?: DEFAULT_CONNECT_DOMAIN_URL

    fun updateConnectDomainUrl(meta: MetaDto?) {
        val connectUrl = meta?.connectUrl
        if (connectUrl.isNullOrEmpty()) return
        storage.connectDomainUrl = connectUrl
        ApphudLog.log("Updated Connect URL to : $connectUrl")
    }

    val promotionsUrl: HttpUrl
        get() = "${baseUrl.get()}/v1/promotions".toHttpUrl()

    /** Shared endpoint for paywall analytics and rule `$` events (mirrors iOS APIv2). */
    val eventsUrl: HttpUrl
        get() = "${baseUrl.get()}/v2/events".toHttpUrl()

    val pushTokenUrl: HttpUrl
        get() = "${baseUrl.get()}/v1/customers/push_token".toHttpUrl()

    val notificationsReadUrl: HttpUrl
        get() = "${baseUrl.get()}/v2/notifications/read".toHttpUrl()

    val notificationsUrl: HttpUrl
        get() = "${baseUrl.get()}/v2/notifications".toHttpUrl()

    val renderPropertiesUrl: HttpUrl
        get() = "${baseUrl.get()}/v2/paywall_configs/items/render_properties".toHttpUrl()

    fun paywallConfigUrl(identifier: String): HttpUrl =
        "${baseUrl.get()}/v3/paywall_configs".toHttpUrl()
            .newBuilder()
            .addPathSegment(identifier)
            .build()

    val previewScreenUrl: HttpUrl
        get() = "${baseUrl.get()}/preview_screen".toHttpUrl()

    fun updateBaseUrl(newUrl: String) {
        baseUrl.set(newUrl)
    }

    private companion object {
        private const val DEFAULT_CONNECT_DOMAIN_URL = "https://connect.aphd.cc"
    }
}

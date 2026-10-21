package com.apphud.sdk.internal.data.network

import com.apphud.sdk.storage.Storage
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

/** The SDK sends the anonymous identity, which only the `/v4` API accepts. */
class UrlProviderTest {

    private val urls = UrlProvider(mockk<Storage>(relaxed = true)).apply { updateBaseUrl("https://api.test") }

    @Test
    fun `GIVEN every SDK endpoint EXPECT the v4 API`() {
        val paths = listOf(
            urls.customersUrl,
            urls.customerPropertiesUrl,
            urls.subscriptionsUrl,
            urls.productsUrl,
            urls.attributionUrl,
            urls.deeplinkAttributionUrl,
            urls.promotionsUrl,
            urls.eventsUrl,
            urls.pushTokenUrl,
            urls.notificationsReadUrl,
            urls.notificationsUrl,
            urls.renderPropertiesUrl,
            urls.paywallConfigUrl("paywall"),
        ).map { it.encodedPath }

        assertEquals(emptyList<String>(), paths.filterNot { it.startsWith("/v4/") })
    }

    @Test
    fun `GIVEN the screen preview EXPECT no API version`() {
        assertEquals("/preview_screen", urls.previewScreenUrl.encodedPath)
    }
}

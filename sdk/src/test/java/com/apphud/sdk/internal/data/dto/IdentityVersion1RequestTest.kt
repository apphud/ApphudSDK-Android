package com.apphud.sdk.internal.data.dto

import com.apphud.sdk.body.PurchaseBody
import com.apphud.sdk.body.UserPropertiesBody
import com.apphud.sdk.internal.data.UserRepository
import com.apphud.sdk.internal.data.network.UrlProvider
import com.apphud.sdk.internal.data.remote.PurchaseBodyFactory
import com.apphud.sdk.internal.data.remote.RegistrationBodyFactory
import com.apphud.sdk.internal.domain.model.ApphudIdentity
import com.apphud.sdk.internal.domain.model.GetProductsParams
import com.apphud.sdk.internal.domain.model.RequestIds
import com.apphud.sdk.internal.provider.RegistrationProvider
import com.apphud.sdk.managers.RequestManager
import com.apphud.sdk.storage.Storage
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Identity version 1: every request has the keys SDK 3.6 sent, no more and no fewer, and goes to the
 * API version SDK 3.6 used. The key lists are the serialized names of the SDK 3.6 classes, checked with
 * the serializer requests go out with, which writes nulls.
 */
class IdentityVersion1RequestTest {

    private val gson = RequestManager.gson

    private fun keys(body: Any): Set<String> = gson.toJsonTree(body).asJsonObject.keySet()

    // region bodies

    @Test
    fun `GIVEN version 1 attribution EXPECT the keys of SDK 3_6`() {
        val body = AttributionRequestDto(
            deviceId = V1.deviceId, installationId = V1.installationId, anonymousId = V1.anonymousId,
            provider = "p", rawData = emptyMap(), attribution = emptyMap(),
        )

        assertEquals(setOf("device_id", "provider", "raw_data", "attribution", "package_name"), keys(body))
    }

    @Test
    fun `GIVEN version 1 deeplink attribution EXPECT the keys of SDK 3_6`() {
        val body = DeeplinkAttributionRequestDto(
            deviceId = V1.deviceId, installationId = V1.installationId, anonymousId = V1.anonymousId, bundleId = "b",
        )

        assertEquals(setOf("device_id", "bundle_id", "url", "visitor_id"), keys(body))
    }

    @Test
    fun `GIVEN version 1 promotional EXPECT the keys of SDK 3_6`() {
        val body = GrantPromotionalDto(
            duration = 1, userId = V1.userId, deviceId = V1.deviceId, installationId = V1.installationId,
            anonymousId = V1.anonymousId, productId = null, productGroupId = null,
        )

        assertEquals(setOf("duration", "user_id", "device_id", "product_id", "product_group_id"), keys(body))
    }

    @Test
    fun `GIVEN version 1 paywall event EXPECT the keys of SDK 3_6`() {
        val body = PaywallEventDto(
            name = "paywall_shown", userId = V1.userId, deviceId = V1.deviceId, installationId = V1.installationId,
            anonymousId = V1.anonymousId, environment = "sandbox", timestamp = 1L, properties = null,
        )

        assertEquals(setOf("name", "user_id", "device_id", "environment", "timestamp", "properties"), keys(body))
    }

    @Test
    fun `GIVEN version 1 push token EXPECT the keys of SDK 3_6`() {
        val body = PushTokenDto(
            deviceId = V1.deviceId, installationId = V1.installationId, anonymousId = V1.anonymousId, pushToken = "t",
        )

        assertEquals(setOf("device_id", "push_token"), keys(body))
    }

    @Test
    fun `GIVEN version 1 read notifications EXPECT the keys of SDK 3_6`() {
        val body = ReadNotificationsRequestDto(
            deviceId = V1.deviceId, installationId = V1.installationId, anonymousId = V1.anonymousId, ruleId = "r",
        )

        assertEquals(setOf("device_id", "rule_id"), keys(body))
    }

    @Test
    fun `GIVEN version 1 rule event EXPECT the keys of SDK 3_6`() {
        val body = RuleEventDto(
            deviceId = V1.deviceId, installationId = V1.installationId, anonymousId = V1.anonymousId,
            ruleId = "r", screenId = null, name = "n", properties = null,
        )

        assertEquals(setOf("device_id", "rule_id", "screen_id", "paywall_id", "name", "properties"), keys(body))
    }

    @Test
    fun `GIVEN version 1 user properties EXPECT the keys of SDK 3_6`() {
        val body = UserPropertiesBody(
            deviceId = V1.deviceId, installationId = V1.installationId, anonymousId = V1.anonymousId,
            properties = emptyList(), force = false,
        )

        assertEquals(setOf("device_id", "properties", "force"), keys(body))
    }

    @Test
    fun `GIVEN version 1 purchase EXPECT the keys of SDK 3_6 whatever version 2 would add`() {
        val userRepository: UserRepository = mockk { every { getRequestIds() } returns V1 }
        val factory = PurchaseBodyFactory(userRepository) { "OLD-DEVICE" }

        val restore = factory.create(null, emptyList(), observerMode = false, restore = true)
        val sync = factory.create(null, emptyList(), observerMode = true)

        assertEquals(setOf("device_id", "purchases", "package_name"), keys(restore))
        assertEquals(setOf("device_id", "purchases", "package_name"), keys(sync))
        assertEquals("DEVICE-1", gson.toJsonTree(restore).asJsonObject.get("device_id").asString)
    }

    @Test
    fun `GIVEN version 2 purchase EXPECT its ids, restore and the old device id`() {
        val userRepository: UserRepository = mockk { every { getRequestIds() } returns V2 }
        val factory = PurchaseBodyFactory(userRepository) { "OLD-DEVICE" }

        val json = gson.toJsonTree(factory.create(null, emptyList(), observerMode = false, restore = true)).asJsonObject

        assertEquals(
            setOf("installation_id", "anonymous_id", "user_id", "restore", "legacy_device_id", "purchases", "package_name"),
            json.keySet(),
        )
    }

    // endregion

    // region registration

    private val provider: RegistrationProvider = mockk(relaxed = true) {
        every { getRequestIds() } returns V1
        every { getFromWeb2Web() } returns false
        // Version 2 state left on the install: never sent by version 1.
        every { getLegacyDeviceId() } returns "OLD-DEVICE"
        every { getReinstall() } returns true
    }

    private fun registration(email: String? = null, userId: String? = null) =
        gson.toJsonTree(RegistrationBodyFactory(provider).create(needPlacements = true, isNew = true, email = email, userId = userId))
            .asJsonObject

    @Test
    fun `GIVEN version 1 registration EXPECT the keys of SDK 3_6`() {
        assertEquals(
            setOf(
                "locale", "sdk_version", "app_version", "device_family", "device_type", "platform", "os_version",
                "start_app_version", "idfv", "idfa", "android_id", "user_id", "device_id", "time_zone", "is_sandbox",
                "is_new", "need_placements", "first_seen", "sdk_launched_at", "request_time", "install_source",
                "observer_mode", "from_web2web", "email", "package_name",
            ),
            registration().keySet(),
        )
    }

    @Test
    fun `GIVEN version 1 registration EXPECT its device id and user id`() {
        val json = registration()

        assertEquals("DEVICE-1", json.get("device_id").asString)
        assertEquals("USER-1", json.get("user_id").asString)
        assertEquals(false, json.get("from_web2web").asBoolean)
    }

    @Test
    fun `GIVEN version 1 update of the user id EXPECT that user id sent`() {
        assertEquals("alice", registration(userId = "alice").get("user_id").asString)
    }

    @Test
    fun `GIVEN version 1 after a web-to-web attribution EXPECT from_web2web on every registration`() {
        every { provider.getFromWeb2Web() } returns true

        assertEquals(true, registration().get("from_web2web").asBoolean)
        assertEquals(true, registration(email = "a@b.c").get("from_web2web").asBoolean)
    }

    // endregion

    // region ids and API versions

    @Test
    fun `GIVEN version 1 ids EXPECT the device id only as query parameters`() {
        assertEquals(mapOf("device_id" to "DEVICE-1"), V1.requestParams)
        assertNull("user_id of version 2 only", V1.identifiedUserId)
    }

    @Test
    fun `GIVEN version 2 ids EXPECT installation and anonymous ids as query parameters`() {
        assertEquals(mapOf("installation_id" to "inst-1", "anonymous_id" to "anon-1"), V2.requestParams)
        assertEquals("alice", V2.identifiedUserId)
    }

    @Test
    fun `GIVEN version 1 products EXPECT the query of SDK 3_6`() {
        val params = GetProductsParams(requestTime = "1", deviceId = V1.deviceId, userId = V1.userId)

        assertEquals(V1, params.ids)
        assertEquals(listOf("device_id"), params.ids.requestParams.keys.toList())
    }

    @Test
    fun `GIVEN version 1 EXPECT the API versions of SDK 3_6`() {
        val urls = UrlProvider(mockk<Storage>(relaxed = true)) { true }.apply { updateBaseUrl("https://api.test") }

        assertEquals("/v1/customers", urls.customersUrl.encodedPath)
        assertEquals("/v1/customers/properties", urls.customerPropertiesUrl.encodedPath)
        assertEquals("/v1/subscriptions", urls.subscriptionsUrl.encodedPath)
        assertEquals("/v2/products", urls.productsUrl.encodedPath)
        assertEquals("/v2/customers/attribution", urls.attributionUrl.encodedPath)
        assertEquals("/v2/customers/deeplink_attribution", urls.deeplinkAttributionUrl.encodedPath)
        assertEquals("/v1/promotions", urls.promotionsUrl.encodedPath)
        assertEquals("/v2/events", urls.eventsUrl.encodedPath)
        assertEquals("/v1/customers/push_token", urls.pushTokenUrl.encodedPath)
        assertEquals("/v2/notifications/read", urls.notificationsReadUrl.encodedPath)
        assertEquals("/v2/notifications", urls.notificationsUrl.encodedPath)
        assertEquals("/v2/paywall_configs/items/render_properties", urls.renderPropertiesUrl.encodedPath)
        assertEquals("/v3/paywall_configs/paywall", urls.paywallConfigUrl("paywall").encodedPath)
    }

    // endregion

    private companion object {
        val V1 = RequestIds(deviceId = "DEVICE-1", userId = "USER-1")
        val V2 = ApphudIdentity("inst-1", "anon-1", "alice").requestIds
    }
}

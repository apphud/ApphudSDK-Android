package com.apphud.sdk.internal.data.dto

import com.apphud.sdk.body.PurchaseBody
import com.apphud.sdk.body.UserPropertiesBody
import com.apphud.sdk.internal.domain.model.ApphudIdentity
import com.apphud.sdk.managers.RequestManager
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every request that sent `device_id` sends `installation_id` and `anonymous_id` instead. Checked
 * with the serializer requests go out with, which writes nulls.
 */
class IdentityRequestFieldsTest {

    private val gson = RequestManager.gson

    private fun idKeys(body: Any): Set<String> =
        gson.toJsonTree(body).asJsonObject.keySet()
            .filter { it in setOf("device_id", "installation_id", "anonymous_id", "user_id") }
            .toSet()

    // region requests without a user id

    @Test
    fun `GIVEN purchase of an anonymous person EXPECT installation and anonymous ids only`() {
        val body = PurchaseBody(installationId = I, anonymousId = A, purchases = emptyList())

        assertEquals(NEW_IDS, idKeys(body))
    }

    @Test
    fun `GIVEN a purchase EXPECT the restore flag always sent`() {
        val body = PurchaseBody(installationId = I, anonymousId = A, restore = false, purchases = emptyList())

        assertEquals(false, gson.toJsonTree(body).asJsonObject.get("restore").asBoolean)
    }

    @Test
    fun `GIVEN purchase of an identified user EXPECT the user id too`() {
        val body = PurchaseBody(installationId = I, anonymousId = A, userId = "u", purchases = emptyList())

        assertEquals(NEW_IDS + "user_id", idKeys(body))
    }

    @Test
    fun `GIVEN user properties body EXPECT installation and anonymous ids only`() {
        val body = UserPropertiesBody(installationId = I, anonymousId = A, properties = emptyList(), force = false)

        assertEquals(NEW_IDS, idKeys(body))
    }

    @Test
    fun `GIVEN attribution body EXPECT installation and anonymous ids only`() {
        val body = AttributionRequestDto(installationId = I, anonymousId = A, provider = "p", rawData = emptyMap(), attribution = emptyMap())

        assertEquals(NEW_IDS, idKeys(body))
    }

    @Test
    fun `GIVEN deeplink attribution body EXPECT installation and anonymous ids only`() {
        val body = DeeplinkAttributionRequestDto(installationId = I, anonymousId = A, bundleId = "b")

        assertEquals(NEW_IDS, idKeys(body))
    }

    @Test
    fun `GIVEN push token body EXPECT installation and anonymous ids only`() {
        val body = PushTokenDto(installationId = I, anonymousId = A, pushToken = "t")

        assertEquals(NEW_IDS, idKeys(body))
    }

    @Test
    fun `GIVEN rule event body EXPECT installation and anonymous ids only`() {
        val body = RuleEventDto(installationId = I, anonymousId = A, ruleId = "r", screenId = null, name = "n", properties = null)

        assertEquals(NEW_IDS, idKeys(body))
    }

    @Test
    fun `GIVEN read notifications body EXPECT installation and anonymous ids only`() {
        val body = ReadNotificationsRequestDto(installationId = I, anonymousId = A, ruleId = "r")

        assertEquals(NEW_IDS, idKeys(body))
    }

    // endregion

    // region requests that also carry the user id

    @Test
    fun `GIVEN paywall event of an anonymous person EXPECT no user id`() {
        val body = PaywallEventDto("n", userId = null, installationId = I, anonymousId = A, environment = "e", timestamp = 0, properties = null)

        assertEquals(NEW_IDS, idKeys(body))
    }

    @Test
    fun `GIVEN paywall event of an identified user EXPECT the user id too`() {
        val body = PaywallEventDto("n", userId = "u", installationId = I, anonymousId = A, environment = "e", timestamp = 0, properties = null)

        assertEquals(NEW_IDS + "user_id", idKeys(body))
    }

    @Test
    fun `GIVEN promotional of an anonymous person EXPECT no user id`() {
        val body = GrantPromotionalDto(1, userId = null, installationId = I, anonymousId = A, productId = null, productGroupId = null)

        assertEquals(NEW_IDS, idKeys(body))
    }

    @Test
    fun `GIVEN identity EXPECT query params with installation and anonymous ids`() {
        assertEquals(mapOf("installation_id" to I, "anonymous_id" to A), ApphudIdentity(I, A, "u").requestParams)
    }

    // endregion

    private companion object {
        const val I = "installation-1"
        const val A = "anonymous-1"
        val NEW_IDS = setOf("installation_id", "anonymous_id")
    }
}

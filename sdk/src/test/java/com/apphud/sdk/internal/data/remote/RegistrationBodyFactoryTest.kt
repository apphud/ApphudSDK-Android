package com.apphud.sdk.internal.data.remote

import com.apphud.sdk.ApphudError
import com.apphud.sdk.internal.domain.model.ApphudIdentity
import com.apphud.sdk.internal.provider.RegistrationProvider
import com.apphud.sdk.managers.RequestManager
import com.google.gson.JsonObject
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistrationBodyFactoryTest {

    private val provider: RegistrationProvider = mockk(relaxed = true) {
        every { getIdentity() } returns ANONYMOUS
        every { getReinstall() } returns null
        every { getLegacyDeviceId() } returns null
    }

    private val factory = RegistrationBodyFactory(provider)

    private fun json(fromWeb2Web: Boolean = false, email: String? = null): JsonObject =
        // The serializer requests go out with, which writes nulls.
        RequestManager.gson.toJsonTree(factory.create(needPlacements = true, isNew = true, email = email, fromWeb2Web = fromWeb2Web)).asJsonObject

    // region new shape

    @Test
    fun `GIVEN anonymous EXPECT installation id sent`() {
        assertEquals("installation-1", json().get("installation_id").asString)
    }

    @Test
    fun `GIVEN anonymous EXPECT anonymous id sent`() {
        assertEquals("anonymous-1", json().get("anonymous_id").asString)
    }

    @Test
    fun `GIVEN anonymous EXPECT no user id sent`() {
        assertFalse(json().has("user_id"))
    }

    @Test
    fun `GIVEN new shape EXPECT no device id sent`() {
        assertFalse(json().has("device_id"))
    }

    @Test
    fun `GIVEN identified EXPECT the user id sent`() {
        every { provider.getIdentity() } returns ANONYMOUS.copy(userId = "alice")

        assertEquals("alice", json().get("user_id").asString)
    }

    @Test
    fun `GIVEN anonymous EXPECT the JSON sent has no user id or reinstall key`() {
        val sent = RequestManager.parser.toJson(factory.create(needPlacements = true, isNew = true))

        assertFalse(sent.contains("\"user_id\"") || sent.contains("\"reinstall\""))
    }

    @Test
    fun `GIVEN no email EXPECT the other nullable fields still sent as null`() {
        assertTrue(json().get("email").isJsonNull)
    }

    @Test
    fun `GIVEN new shape EXPECT no from web to web param`() {
        assertFalse(json().has("from_web2web"))
    }

    @Test
    fun `GIVEN reinstall EXPECT reinstall true sent`() {
        every { provider.getReinstall() } returns true

        assertTrue(json().get("reinstall").asBoolean)
    }

    @Test
    fun `GIVEN not a reinstall EXPECT no reinstall param`() {
        assertFalse(json().has("reinstall"))
    }

    @Test
    fun `GIVEN an update from an old version EXPECT the old device id sent as legacy device id`() {
        every { provider.getLegacyDeviceId() } returns "OLD"

        assertEquals("OLD", json().get("legacy_device_id").asString)
    }

    @Test
    fun `GIVEN no old device id to adopt EXPECT no legacy device id key`() {
        val sent = RequestManager.parser.toJson(factory.create(needPlacements = true, isNew = true))

        assertFalse(sent.contains("\"legacy_device_id\""))
    }

    @Test(expected = ApphudError::class)
    fun `GIVEN ids not resolved EXPECT error`() {
        every { provider.getIdentity() } returns null

        factory.create(needPlacements = true, isNew = true)
    }

    // endregion

    // region web-to-web by email

    @Test
    fun `GIVEN web to web by email EXPECT the installation id sent`() {
        assertEquals("installation-1", json(fromWeb2Web = true).get("installation_id").asString)
    }

    @Test
    fun `GIVEN web to web by email EXPECT the anonymous id sent`() {
        assertEquals("anonymous-1", json(fromWeb2Web = true).get("anonymous_id").asString)
    }

    @Test
    fun `GIVEN web to web by email EXPECT no device id`() {
        assertFalse(json(fromWeb2Web = true).has("device_id"))
    }

    @Test
    fun `GIVEN web to web by email of an anonymous person EXPECT no user id`() {
        assertFalse(json(fromWeb2Web = true).has("user_id"))
    }

    @Test
    fun `GIVEN web to web by email of an identified user EXPECT the user id`() {
        every { provider.getIdentity() } returns ANONYMOUS.copy(userId = "alice")

        assertEquals("alice", json(fromWeb2Web = true).get("user_id").asString)
    }

    @Test
    fun `GIVEN web to web by email EXPECT from web to web`() {
        assertTrue(json(fromWeb2Web = true, email = "a@b.c").get("from_web2web").asBoolean)
    }

    @Test
    fun `GIVEN web to web by email EXPECT the email sent`() {
        assertEquals("a@b.c", json(fromWeb2Web = true, email = "a@b.c").get("email").asString)
    }

    @Test
    fun `GIVEN web to web by email on a reinstall EXPECT reinstall still sent`() {
        every { provider.getReinstall() } returns true

        assertTrue(json(fromWeb2Web = true).get("reinstall").asBoolean)
    }

    // endregion

    private companion object {
        val ANONYMOUS = ApphudIdentity("installation-1", "anonymous-1", null)
    }
}

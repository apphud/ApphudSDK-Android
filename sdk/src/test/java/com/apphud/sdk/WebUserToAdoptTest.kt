package com.apphud.sdk

import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.internal.domain.model.ApphudIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The web user an email-only web-to-web request found becomes the SDK's user only when it is one. */
class WebUserToAdoptTest {

    private fun response(userId: String, customerId: String?) = ApphudUser(
        userId = userId,
        currencyCode = null,
        countryCode = null,
        subscriptions = emptyList(),
        purchases = emptyList(),
        placements = emptyList(),
        isTemporary = false,
        internalId = customerId,
    )

    @Test
    fun `GIVEN another customer with a user id EXPECT that user adopted`() {
        assertEquals("web-user", webUserToAdopt("customer-before", SENT, SENT, response("web-user", "web-customer")))
    }

    @Test
    fun `GIVEN the same customer back EXPECT nothing adopted`() {
        assertNull(webUserToAdopt("customer-before", SENT, SENT, response("other-install-id", "customer-before")))
    }

    @Test
    fun `GIVEN response user id equal to the anonymous id EXPECT nothing adopted`() {
        assertNull(webUserToAdopt("customer-before", SENT, SENT, response(SENT.anonymousId, "web-customer")))
    }

    @Test
    fun `GIVEN ids changed while the request was out EXPECT nothing adopted`() {
        val current = SENT.copy(anonymousId = "after-logout")

        assertNull(webUserToAdopt("customer-before", SENT, current, response("web-user", "web-customer")))
    }

    @Test
    fun `GIVEN empty user id EXPECT nothing adopted`() {
        assertNull(webUserToAdopt("customer-before", SENT, SENT, response("", "web-customer")))
    }

    @Test
    fun `GIVEN no customer id in the response EXPECT nothing adopted`() {
        assertNull(webUserToAdopt("customer-before", SENT, SENT, response("web-user", null)))
    }

    private companion object {
        val SENT = ApphudIdentity("installation-1", "anonymous-1", null)
    }
}

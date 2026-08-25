package com.apphud.sdk.internal.data.mapper

import com.apphud.sdk.domain.ApphudPaywallScreen
import com.apphud.sdk.internal.data.dto.ApphudPaywallDto
import com.apphud.sdk.internal.data.dto.ApphudPaywallScreenDto
import com.apphud.sdk.mappers.PaywallsMapperLegacy
import com.apphud.sdk.parser.GsonParser
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaywallsMapperTest {

    private val gson = Gson()
    private val mapper = PaywallsMapper(gson)

    @Test
    fun `GIVEN screen with name EXPECT paywall screenName filled`() {
        val paywall = mapper.map(createPaywallDto(screen = createScreenDto(name = "Onboarding paywall")))

        assertEquals("Onboarding paywall", paywall.screen?.name)
        assertEquals("Onboarding paywall", paywall.screenName)
    }

    @Test
    fun `GIVEN screen with null name EXPECT paywall screenName null`() {
        val paywall = mapper.map(createPaywallDto(screen = createScreenDto(name = null)))

        assertNull(paywall.screenName)
    }

    @Test
    fun `GIVEN paywall without screen EXPECT screenName null`() {
        val paywall = mapper.map(createPaywallDto(screen = null))

        assertNull(paywall.screen)
        assertNull(paywall.screenName)
    }

    @Test
    fun `GIVEN legacy mapper and screen with name EXPECT paywall screenName filled`() {
        val legacyMapper = PaywallsMapperLegacy(GsonParser(gson))

        val paywall = legacyMapper.map(createPaywallDto(screen = createScreenDto(name = "Onboarding paywall")))

        assertEquals("Onboarding paywall", paywall.screenName)
    }

    // Backend contract (backend MR !2226): `name` is a nullable string inside the `screen` object.
    @Test
    fun `GIVEN screen json with name EXPECT dto name parsed`() {
        val json = """{"id":"s1","name":"Onboarding paywall","default_url":"https://e.com","urls":{"en":"https://e.com"}}"""

        val dto = gson.fromJson(json, ApphudPaywallScreenDto::class.java)

        assertEquals("Onboarding paywall", dto.name)
    }

    @Test
    fun `GIVEN screen json without name key EXPECT dto name null`() {
        val json = """{"id":"s1","default_url":"https://e.com","urls":{"en":"https://e.com"}}"""

        val dto = gson.fromJson(json, ApphudPaywallScreenDto::class.java)

        assertNull(dto.name)
    }

    @Test
    fun `GIVEN screen json with explicit null name EXPECT dto name null`() {
        val json = """{"id":"s1","name":null,"default_url":"https://e.com","urls":{"en":"https://e.com"}}"""

        val dto = gson.fromJson(json, ApphudPaywallScreenDto::class.java)

        assertNull(dto.name)
    }

    // The SharedPreferences cache stores DOMAIN models via gson, bypassing DTOs and mappers.
    @Test
    fun `GIVEN legacy cached domain screen json without name EXPECT decodes with null name`() {
        val legacyCacheJson = """{"id":"s1","defaultUrl":"https://e.com","urls":{"en":"https://e.com"}}"""

        val screen = gson.fromJson(legacyCacheJson, ApphudPaywallScreen::class.java)

        assertEquals("https://e.com", screen.defaultUrl)
        assertNull(screen.name)
    }

    @Test
    fun `GIVEN domain screen with name EXPECT name survives cache round-trip`() {
        val screen = ApphudPaywallScreen(
            id = "s1",
            defaultUrl = "https://e.com",
            _urls = mapOf("en" to "https://e.com"),
            name = "Onboarding paywall",
        )

        val restored = gson.fromJson(gson.toJson(screen), ApphudPaywallScreen::class.java)

        assertEquals("Onboarding paywall", restored.name)
    }

    private fun createScreenDto(name: String?) =
        ApphudPaywallScreenDto(
            id = "screen-id",
            name = name,
            defaultURL = "https://example.com/en",
            urls = mapOf("en" to "https://example.com/en"),
        )

    private fun createPaywallDto(screen: ApphudPaywallScreenDto?) =
        ApphudPaywallDto(
            id = "paywall-id",
            name = "Paywall name",
            identifier = "main",
            default = false,
            json = "{}",
            items = emptyList(),
            experimentName = null,
            variationName = null,
            variationIdentifier = null,
            fromPaywall = null,
            screen = screen,
        )
}

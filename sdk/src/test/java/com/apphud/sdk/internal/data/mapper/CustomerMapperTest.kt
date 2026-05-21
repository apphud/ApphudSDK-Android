package com.apphud.sdk.internal.data.mapper

import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.internal.data.dto.CustomerDto
import com.apphud.sdk.internal.data.dto.ExperimentDto
import com.apphud.sdk.internal.data.dto.SchemeDto
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomerMapperTest {

    private val subscriptionMapper: SubscriptionMapper = mockk(relaxed = true)
    private val placementsMapper: PlacementsMapper = mockk(relaxed = true)
    private val mapper = CustomerMapper(subscriptionMapper, placementsMapper)

    @Test
    fun `GIVEN scheme exists without experiment variation and remote config EXPECT values replaced with nulls`() {
        val previousUser = createUser(
            experimentName = "Old experiment",
            variationName = "Old variation",
            targetingName = "Old audience",
            remoteConfigString = """{"old":"config"}""",
        )
        val customer = createCustomer(
            scheme = SchemeDto(
                name = "Reinstalled users",
                variationName = null,
                experiment = null,
                remoteConfig = null,
            ),
        )

        val result = mapper.map(customer, previousUser)

        assertNull(result.experimentName)
        assertNull(result.variationName)
        assertEquals("Reinstalled users", result.targetingName)
        assertNull(result.remoteConfigString)
    }

    @Test
    fun `GIVEN scheme missing EXPECT previous scheme-related values preserved`() {
        val previousUser = createUser(
            experimentName = "Old experiment",
            variationName = "Old variation",
            targetingName = "Old audience",
            remoteConfigString = """{"old":"config"}""",
        )
        val customer = createCustomer(scheme = null)

        val result = mapper.map(customer, previousUser)

        assertEquals("Old experiment", result.experimentName)
        assertEquals("Old variation", result.variationName)
        assertEquals("Old audience", result.targetingName)
        assertEquals("""{"old":"config"}""", result.remoteConfigString)
    }

    private fun createCustomer(scheme: SchemeDto?): CustomerDto =
        CustomerDto(
            userId = "user-id",
            subscriptions = emptyList(),
            currency = null,
            placements = null,
            internalId = null,
            totalDevicesCount = null,
            scheme = scheme,
        )

    private fun createUser(
        experimentName: String?,
        variationName: String?,
        targetingName: String?,
        remoteConfigString: String?,
    ): ApphudUser =
        ApphudUser(
            userId = "user-id",
            currencyCode = "USD",
            countryCode = "US",
            subscriptions = emptyList(),
            purchases = emptyList(),
            totalDevicesCount = 0,
            internalId = "internal-id",
            experimentName = experimentName,
            variationName = variationName,
            targetingName = targetingName,
            remoteConfigString = remoteConfigString,
            placements = emptyList(),
            isTemporary = false,
        )
}

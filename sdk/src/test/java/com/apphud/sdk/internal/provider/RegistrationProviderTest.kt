package com.apphud.sdk.internal.provider

import android.content.Context
import com.apphud.sdk.ApphudUtils
import com.apphud.sdk.internal.data.AnalyticsTracker
import com.apphud.sdk.internal.data.DeviceIdentifiersRepository
import com.apphud.sdk.internal.data.SdkRegistrationState
import com.apphud.sdk.internal.data.UserRepository
import com.apphud.sdk.internal.domain.model.DeviceIdentifiers
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class RegistrationProviderTest {

    private val deviceIdentifiersRepository: DeviceIdentifiersRepository = mockk()
    private val userRepository: UserRepository = mockk()
    private val applicationContext: Context = mockk()
    private val analyticsTracker: AnalyticsTracker = mockk {
        every { sdkLaunchTimeMs() } returns 0L
    }
    private val registrationState = SdkRegistrationState(observerMode = false)
    private val provider = RegistrationProvider(applicationContext, deviceIdentifiersRepository, userRepository, analyticsTracker = analyticsTracker, registrationState = registrationState)

    @Before
    fun setup() {
        mockkObject(ApphudUtils)
    }

    @After
    fun tearDown() {
        unmockkObject(ApphudUtils)
    }

    // region getIdfa

    @Test
    fun `GIVEN optOutOfTracking is true EXPECT returns null`() {
        every { ApphudUtils.optOutOfTracking } returns true

        assertNull(provider.getIdfa())
    }

    @Test
    fun `GIVEN advertisingId is abc EXPECT returns abc`() {
        every { ApphudUtils.optOutOfTracking } returns false
        every { deviceIdentifiersRepository.getIdentifiers() } returns DeviceIdentifiers(advertisingId = "abc")

        assertEquals("abc", provider.getIdfa())
    }

    @Test
    fun `GIVEN advertisingId is empty EXPECT returns null`() {
        every { ApphudUtils.optOutOfTracking } returns false
        every { deviceIdentifiersRepository.getIdentifiers() } returns DeviceIdentifiers(advertisingId = "")

        assertNull(provider.getIdfa())
    }

    // endregion

    // region getIdfv

    @Test
    fun `GIVEN optOutOfTracking is true EXPECT getIdfv returns null`() {
        every { ApphudUtils.optOutOfTracking } returns true

        assertNull(provider.getIdfv())
    }

    @Test
    fun `GIVEN appSetId is abc EXPECT returns abc`() {
        every { ApphudUtils.optOutOfTracking } returns false
        every { deviceIdentifiersRepository.getIdentifiers() } returns DeviceIdentifiers(appSetId = "abc")

        assertEquals("abc", provider.getIdfv())
    }

    @Test
    fun `GIVEN appSetId is null EXPECT returns null`() {
        every { ApphudUtils.optOutOfTracking } returns false
        every { deviceIdentifiersRepository.getIdentifiers() } returns DeviceIdentifiers(appSetId = null)

        assertNull(provider.getIdfv())
    }

    // endregion

    // region getAndroidId

    @Test
    fun `GIVEN optOutOfTracking is true EXPECT getAndroidId returns null`() {
        every { ApphudUtils.optOutOfTracking } returns true

        assertNull(provider.getAndroidId())
    }

    @Test
    fun `GIVEN androidId in cache is abc EXPECT returns abc without fallback`() {
        every { ApphudUtils.optOutOfTracking } returns false
        every { deviceIdentifiersRepository.getIdentifiers() } returns DeviceIdentifiers(androidId = "abc")

        assertEquals("abc", provider.getAndroidId())
    }

    @Test
    fun `GIVEN androidId in cache is null and fetchAndroidIdSync returns xyz EXPECT returns xyz`() {
        every { ApphudUtils.optOutOfTracking } returns false
        every { deviceIdentifiersRepository.getIdentifiers() } returns DeviceIdentifiers(androidId = null)
        every { deviceIdentifiersRepository.fetchAndroidIdSync() } returns "xyz"

        assertEquals("xyz", provider.getAndroidId())
    }

    @Test
    fun `GIVEN androidId in cache is null and fetchAndroidIdSync returns null EXPECT returns null`() {
        every { ApphudUtils.optOutOfTracking } returns false
        every { deviceIdentifiersRepository.getIdentifiers() } returns DeviceIdentifiers(androidId = null)
        every { deviceIdentifiersRepository.fetchAndroidIdSync() } returns null

        assertNull(provider.getAndroidId())
    }

    // endregion

    // region getDeviceType

    @Test
    fun `GIVEN optOutOfTracking is true EXPECT returns Restricted`() {
        every { ApphudUtils.optOutOfTracking } returns true

        assertEquals("Restricted", provider.getDeviceType())
    }

    // endregion
}

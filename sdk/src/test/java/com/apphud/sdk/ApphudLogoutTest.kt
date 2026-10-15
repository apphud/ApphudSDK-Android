package com.apphud.sdk

import android.content.Context
import android.content.SharedPreferences
import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.internal.ServiceLocator
import com.apphud.sdk.internal.domain.model.ApphudSuppliedIds
import io.mockk.every
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ApphudLogoutTest {

    private val prefsMap = mutableMapOf<String, Any?>()

    private val editor: SharedPreferences.Editor = mockk(relaxed = true) {
        every { putString(any(), any()) } answers {
            prefsMap[firstArg()] = secondArg<String?>()
            this@mockk
        }
        every { putLong(any(), any()) } answers {
            prefsMap[firstArg()] = secondArg<Long>()
            this@mockk
        }
        every { putBoolean(any(), any()) } answers {
            prefsMap[firstArg()] = secondArg<Boolean>()
            this@mockk
        }
        every { remove(any()) } answers {
            prefsMap.remove(firstArg<String>())
            this@mockk
        }
        every { commit() } returns true
        every { apply() } answers { }
    }

    private val preferences: SharedPreferences = mockk(relaxed = true) {
        every { getString(any(), any()) } answers {
            prefsMap[firstArg()] as? String ?: secondArg()
        }
        every { getLong(any(), any()) } answers {
            prefsMap[firstArg()] as? Long ?: secondArg()
        }
        every { getBoolean(any(), any()) } answers {
            prefsMap[firstArg()] as? Boolean ?: secondArg()
        }
        every { edit() } returns editor
    }

    private val mockContext: Context = mockk(relaxed = true) {
        every { getSharedPreferences(any(), any()) } returns preferences
        every { applicationInfo } returns mockk(relaxed = true)
    }

    @Before
    fun setUp() {
        prefsMap.clear()
        ServiceLocator.initAppScope(mockContext)
    }

    @After
    fun tearDown() {
        ApphudInternal.identityVersion = ApphudIdentityVersion.V1
        ServiceLocator.clearSession()
        ServiceLocator.clearInstance()
    }

    @Test
    fun `GIVEN cached user before start WHEN logout EXPECT storage user cleared`() {
        val storage = ServiceLocator.instance.storage
        storage.apphudUser = cachedUser()
        storage.userId = "cached-user-id"
        storage.deviceId = "cached-device-id"

        ApphudInternal.logout()

        assertNull(storage.apphudUser)
        assertNull(storage.userId)
        assertNull(storage.deviceId)
    }

    // The relaxed Context gives no readable install record, so the old device id is kept only as the
    // unsaved one here.
    @Test
    fun `GIVEN an old device id not yet adopted WHEN logout EXPECT it no longer sent`() {
        ApphudInternal.identityVersion = ApphudIdentityVersion.V2
        val reinstall = ServiceLocator.instance.reinstallRepository
        reinstall.start(unsavedLegacyDeviceId = "OLD")

        ApphudInternal.logout()

        assertNull("the old customer is the logged-out person's", reinstall.legacyDeviceId())
    }

    @Test
    fun `GIVEN an old device id not yet adopted WHEN logout on V1 EXPECT the V2 data untouched`() {
        ApphudInternal.identityVersion = ApphudIdentityVersion.V1
        val reinstall = ServiceLocator.instance.reinstallRepository
        reinstall.start(unsavedLegacyDeviceId = "OLD")

        ApphudInternal.logout()

        assertEquals("V1's logout of SDK 3.6 only", "OLD", reinstall.legacyDeviceId())
    }

    @Test
    fun `GIVEN identity version 2 WHEN logout EXPECT a new anonymous id and the device id of version 1 reset`() {
        ApphudInternal.identityVersion = ApphudIdentityVersion.V2
        val storage = ServiceLocator.instance.storage
        storage.anonymousId = "anonymous-1"
        storage.deviceId = "V1-DEVICE"

        ApphudInternal.logout()

        assertNull(storage.deviceId)
        assertEquals(true, storage.version1IdsReset)
        assertEquals(false, storage.anonymousId == "anonymous-1")
    }

    @Test
    fun `GIVEN identity version 1 EXPECT no version 2 ids from the public API`() {
        ServiceLocator.instance.identityStore.resolve(ApphudSuppliedIds.of(userId = "alice"))
        ServiceLocator.instance.reinstallRepository.useHostValue(true)

        assertNull(Apphud.installationId())
        assertNull(Apphud.anonymousId())
        assertNull(Apphud.isReinstall())

        ApphudInternal.identityVersion = ApphudIdentityVersion.V2
        assertEquals(true, Apphud.isReinstall())
        assertEquals(ServiceLocator.instance.identityStore.current?.installationId, Apphud.installationId())
        assertEquals(ServiceLocator.instance.identityStore.current?.anonymousId, Apphud.anonymousId())
    }

    @Test
    fun `GIVEN the app supplies the ids on version 2 WHEN its reinstall answer comes after start EXPECT it used`() {
        ApphudInternal.identityVersion = ApphudIdentityVersion.V2
        ServiceLocator.instance.reinstallRepository.useHostValue(null)

        Apphud.setReinstall(true)

        assertEquals(true, Apphud.isReinstall())
        assertEquals(true, ServiceLocator.instance.reinstallRepository.reinstallFlag())
    }

    @Test
    fun `GIVEN identity version 1 WHEN the app's reinstall answer comes EXPECT it ignored`() {
        ApphudInternal.identityVersion = ApphudIdentityVersion.V1
        ServiceLocator.instance.reinstallRepository.useHostValue(null)

        Apphud.setReinstall(true)

        assertNull(ServiceLocator.instance.reinstallRepository.isReinstall())
    }

    @Test
    fun `GIVEN identity version 1 EXPECT deviceId never the installation id`() {
        ServiceLocator.instance.identityStore.resolve(ApphudSuppliedIds.of(userId = "alice"))
        val installationId = ServiceLocator.instance.identityStore.current?.installationId

        // Before start SDK 3.6 has no device id to give either.
        assertNull(Apphud.deviceId())

        ApphudInternal.identityVersion = ApphudIdentityVersion.V2
        assertEquals(installationId, Apphud.deviceId())
    }

    @Test
    fun `GIVEN version 2 ran last and no start yet in this launch WHEN logout EXPECT the logout of version 2`() {
        ApphudInternal::class.java.getDeclaredField("identityVersionChosen").apply { isAccessible = true }
            .setBoolean(ApphudInternal, false)
        val storage = ServiceLocator.instance.storage
        storage.idsIdentityVersion = ApphudIdentityVersion.V2.name
        storage.anonymousId = "anonymous-1"
        storage.identifiedUserId = "alice"

        ApphudInternal.logout()

        assertNull("the logged-out user does not come back on the next start", storage.identifiedUserId)
        assertEquals(false, storage.anonymousId == "anonymous-1")
        assertEquals(true, storage.version1IdsReset)
    }

    @Test
    fun `GIVEN nothing ran yet WHEN logout before a start EXPECT the logout of SDK 3_6`() {
        ApphudInternal::class.java.getDeclaredField("identityVersionChosen").apply { isAccessible = true }
            .setBoolean(ApphudInternal, false)
        val storage = ServiceLocator.instance.storage
        storage.deviceId = "V1-DEVICE"

        ApphudInternal.logout()

        assertNull(storage.deviceId)
        assertEquals(true, storage.version1LoggedOut)
    }

    @Test
    fun `GIVEN identity version 1 WHEN logout EXPECT the logout of SDK 3_6 only`() {
        val storage = ServiceLocator.instance.storage
        storage.anonymousId = "anonymous-1"
        storage.deviceId = "V1-DEVICE"

        ApphudInternal.logout(anonymousId = "anonymous-2")

        assertNull(storage.deviceId)
        assertEquals("version 2's ids untouched", "anonymous-1", storage.anonymousId)
        assertEquals(false, storage.version1IdsReset)
    }

    @Test
    fun `GIVEN an old device id not yet adopted WHEN another user identifies EXPECT it no longer sent`() {
        // The listener is told on the main dispatcher.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            ApphudInternal.identityVersion = ApphudIdentityVersion.V2
            ServiceLocator.instance.identityStore.resolve(ApphudSuppliedIds.of(userId = "alice"))
            val reinstall = ServiceLocator.instance.reinstallRepository
            reinstall.start(unsavedLegacyDeviceId = "OLD")

            // The debug warning after a switch needs a started SDK: only the ids matter here.
            runCatching { ApphudInternal.applyIdentify("bob") }

            assertEquals("bob", ServiceLocator.instance.identityStore.current?.userId)
            assertNull("the old customer is not bob's", reinstall.legacyDeviceId())
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun cachedUser() = ApphudUser(
        userId = "cached-user-id",
        currencyCode = "USD",
        countryCode = "US",
        subscriptions = emptyList(),
        purchases = emptyList(),
        placements = emptyList(),
        isTemporary = false,
    )
}

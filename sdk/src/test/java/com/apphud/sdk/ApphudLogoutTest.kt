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
    fun `GIVEN an old device id not yet adopted WHEN logout EXPECT it still sent until a registration succeeds`() {
        val reinstall = ServiceLocator.instance.reinstallRepository
        reinstall.start(unsavedLegacyDeviceId = "OLD")

        ApphudInternal.logout()

        assertEquals("OLD", reinstall.legacyDeviceId())
    }

    @Test
    fun `GIVEN an old device id not yet adopted WHEN another user identifies EXPECT it still sent`() {
        // The listener is told on the main dispatcher.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            ServiceLocator.instance.identityStore.resolve(ApphudSuppliedIds.of(userId = "alice"))
            val reinstall = ServiceLocator.instance.reinstallRepository
            reinstall.start(unsavedLegacyDeviceId = "OLD")

            // The debug warning after a switch needs a started SDK: only the ids matter here.
            runCatching { ApphudInternal.applyIdentify("bob") }

            assertEquals("bob", ServiceLocator.instance.identityStore.current?.userId)
            assertEquals("OLD", reinstall.legacyDeviceId())
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

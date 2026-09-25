package com.apphud.sdk

import android.content.Context
import android.content.SharedPreferences
import com.apphud.sdk.internal.ServiceLocator
import com.apphud.sdk.internal.data.network.HostSwitcherInterceptor
import com.apphud.sdk.internal.data.network.HttpRetryInterceptor
import com.apphud.sdk.internal.data.network.SessionIdInterceptor
import com.apphud.sdk.internal.domain.model.ApiKey
import io.mockk.every
import io.mockk.mockk
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApphudSessionIdTest {

    private val prefsMap = mutableMapOf<String, Any?>()

    private val editor: SharedPreferences.Editor = mockk(relaxed = true) {
        every { putString(any(), any()) } answers {
            prefsMap[firstArg()] = secondArg<String?>()
            this@mockk
        }
        every { putInt(any(), any()) } answers {
            prefsMap[firstArg()] = secondArg<Int>()
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
    }

    private val preferences: SharedPreferences = mockk(relaxed = true) {
        every { getString(any(), any()) } answers { prefsMap[firstArg()] as? String ?: secondArg() }
        every { getInt(any(), any()) } answers { prefsMap[firstArg()] as? Int ?: secondArg() }
        every { getLong(any(), any()) } answers { prefsMap[firstArg()] as? Long ?: secondArg() }
        every { getBoolean(any(), any()) } answers { prefsMap[firstArg()] as? Boolean ?: secondArg() }
        every { edit() } returns editor
    }

    private val context: Context = mockk(relaxed = true) {
        every { getSharedPreferences(any(), any()) } returns preferences
        every { applicationInfo } returns mockk(relaxed = true)
    }

    @Before
    fun setUp() {
        prefsMap.clear()
        ServiceLocator.initAppScope(context)
    }

    @After
    fun tearDown() {
        ServiceLocator.clearSession()
        ServiceLocator.clearInstance()
    }

    private fun startSessionScope() {
        ServiceLocator.initSessionScope(
            apiKey = ApiKey("test_api_key"),
            ruleCallback = object : ApphudRuleCallback {},
            awaitUserRegistration = {},
        )
    }

    // Session id, then host switching and retries, so every attempt of a call keeps its id.
    private fun OkHttpClient.sessionInterceptorComesFirst(): Boolean {
        val types = interceptors.map { it::class }
        val session = types.indexOf(SessionIdInterceptor::class)
        return session >= 0 &&
            session < types.indexOf(HostSwitcherInterceptor::class) &&
            session < types.indexOf(HttpRetryInterceptor::class)
    }

    @Test
    fun `GIVEN setSessionId EXPECT sessionId returns the host id`() {
        Apphud.setSessionId("Host-Session-1")

        assertEquals("Host-Session-1", Apphud.sessionId())
    }

    @Test
    fun `GIVEN default mode WHEN logout EXPECT new session id`() {
        val id = Apphud.sessionId()

        ApphudInternal.logout()

        assertNotEquals(id, Apphud.sessionId())
    }

    @Test
    fun `GIVEN session number WHEN logout EXPECT number kept and incremented`() {
        ServiceLocator.instance.storage.clientSessionNumber = 3

        ApphudInternal.logout()

        assertEquals(4, ServiceLocator.instance.storage.clientSessionNumber)
    }

    @Test
    fun `GIVEN host id WHEN logout EXPECT host id kept`() {
        Apphud.setSessionId("host")

        ApphudInternal.logout()

        assertEquals("host", Apphud.sessionId())
    }

    @Test
    fun `GIVEN session scope EXPECT API client adds session id before host switch and retries`() {
        startSessionScope()

        assertTrue(ServiceLocator.instance.session.okHttpClient.sessionInterceptorComesFirst())
    }

    @Test
    fun `GIVEN session scope EXPECT screen client adds session id before host switch and retries`() {
        startSessionScope()

        assertTrue(ServiceLocator.instance.session.okHttpClientWithoutHeaders.sessionInterceptorComesFirst())
    }
}

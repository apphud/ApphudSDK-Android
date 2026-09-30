package com.apphud.sdk

import com.apphud.sdk.internal.data.UserPropertiesManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class ApphudPlatformTest {

    private val userPropertiesManager: UserPropertiesManager = mockk(relaxed = true)

    @Before
    fun setUp() {
        mockkObject(ApphudInternal)
        every { ApphudInternal.userPropertiesManager } returns userPropertiesManager
    }

    @After
    fun tearDown() {
        unmockkObject(ApphudInternal)
    }

    @Test
    fun `GIVEN platform accessor EXPECT the SDK implementation`() {
        assertSame(ApphudPlatform, Apphud.platform)
    }

    @Test
    fun `GIVEN key with attributes EXPECT they reach the user properties manager`() {
        Apphud.platform.setUserProperty(key = AttributedKey, value = "a", setOnce = true)

        verify {
            userPropertiesManager.setUserProperty(
                key = match { it.key == "platform_key" },
                value = "a",
                setOnce = true,
                increment = false,
                attributes = mapOf("extra" to "x"),
            )
        }
    }

    private object AttributedKey : PlatformUserPropertyKeyDescribing {
        override val name = "platform_key"
        override val attributes = mapOf("extra" to "x")
    }
}

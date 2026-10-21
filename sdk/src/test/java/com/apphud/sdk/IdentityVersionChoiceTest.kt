package com.apphud.sdk

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** The identity version is read once per launch: a later start (after logout) keeps the first choice. */
class IdentityVersionChoiceTest {

    private val chosen = ApphudInternal::class.java.getDeclaredField("identityVersionChosen").apply { isAccessible = true }

    @Before
    fun setUp() {
        ApphudInternal.identityVersion = ApphudIdentityVersion.V1
        chosen.setBoolean(ApphudInternal, false)
    }

    @After
    fun tearDown() {
        ApphudInternal.identityVersion = ApphudIdentityVersion.V1
        chosen.setBoolean(ApphudInternal, false)
    }

    @Test
    fun `GIVEN no start yet EXPECT version 1`() {
        assertEquals(ApphudIdentityVersion.V1, ApphudInternal.identityVersion)
    }

    @Test
    fun `GIVEN a first start on version 2 WHEN a later start asks for version 1 EXPECT version 2 kept`() {
        ApphudInternal.chooseIdentityVersion(ApphudIdentityVersion.V2)
        ApphudInternal.chooseIdentityVersion(ApphudIdentityVersion.V1)

        assertEquals(ApphudIdentityVersion.V2, ApphudInternal.identityVersion)
    }

    @Test
    fun `GIVEN a first start on version 1 WHEN a later start asks for version 2 EXPECT version 1 kept`() {
        ApphudInternal.chooseIdentityVersion(ApphudIdentityVersion.V1)
        ApphudInternal.chooseIdentityVersion(ApphudIdentityVersion.V2)

        assertEquals(ApphudIdentityVersion.V1, ApphudInternal.identityVersion)
    }
}

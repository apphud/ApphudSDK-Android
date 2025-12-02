package com.apphud.sdk.storage

import android.content.Context
import android.content.SharedPreferences
import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.ApphudPlacement
import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.parser.GsonParser
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SharedPreferencesStorageMigrationTest {

    private lateinit var context: Context
    private lateinit var preferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private val gson = GsonBuilder().setPrettyPrinting().serializeNulls().create()
    private val parser = GsonParser(gson)

    // Storage for simulating SharedPreferences
    private val prefsMap = mutableMapOf<String, Any?>()

    private companion object {
        const val PREFS_NAME = "apphud_storage"
        const val USER_ID_KEY = "userIdKey"
        const val APPHUD_USER_KEY = "APPHUD_USER_KEY"
        const val PAYWALLS_KEY = "PAYWALLS_KEY"
        const val PAYWALLS_TIMESTAMP_KEY = "PAYWALLS_TIMESTAMP_KEY"
        const val PLACEMENTS_KEY = "PLACEMENTS_KEY"
        const val PLACEMENTS_TIMESTAMP_KEY = "PLACEMENTS_TIMESTAMP_KEY"
        const val CACHE_VERSION_KEY = "APPHUD_CACHE_VERSION"
    }

    @Before
    fun setup() {
        prefsMap.clear()

        editor = mockk(relaxed = true) {
            every { putString(any(), any()) } answers {
                prefsMap[firstArg()] = secondArg<String?>()
                this@mockk
            }
            every { putLong(any(), any()) } answers {
                prefsMap[firstArg()] = secondArg<Long>()
                this@mockk
            }
            every { remove(any()) } answers {
                prefsMap.remove(firstArg<String>())
                this@mockk
            }
            every { commit() } returns true
            every { apply() } answers { }
        }

        preferences = mockk(relaxed = true) {
            every { getString(any(), any()) } answers {
                prefsMap[firstArg()] as? String ?: secondArg()
            }
            every { getLong(any(), any()) } answers {
                prefsMap[firstArg()] as? Long ?: secondArg()
            }
            every { edit() } returns editor
        }

        context = mockk(relaxed = true) {
            every { getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) } returns preferences
            every { applicationInfo } returns mockk(relaxed = true)
        }
    }

    @Test
    fun `migration from v2 to v3 should transfer paywalls to user when user has empty paywalls`() {
        // Arrange: Set up v2 cache with user (empty paywalls) and separate paywalls cache
        val userWithEmptyPaywalls = createTestUser(
            userId = "test-user-id",
            paywalls = emptyList(),
            placements = emptyList()
        )
        val legacyPaywalls = listOf(
            createTestPaywall("paywall-1", "main_paywall"),
            createTestPaywall("paywall-2", "settings_paywall")
        )
        val legacyPlacements = listOf(
            createTestPlacement("placement-1", "onboarding")
        )

        setupV2Cache(userWithEmptyPaywalls, legacyPaywalls, legacyPlacements)

        // Act: Initialize storage (triggers migration)
        SharedPreferencesStorage.getInstance(context)
        val isValid = SharedPreferencesStorage.validateCaches()

        // Assert
        assertTrue("Cache should be valid after migration", isValid)
        assertEquals("Cache version should be 3", "3", prefsMap[CACHE_VERSION_KEY])

        // Verify migrated user has paywalls
        val migratedUserJson = prefsMap[APPHUD_USER_KEY] as? String
        assertNotNull("User JSON should exist after migration", migratedUserJson)

        val migratedUser = parser.fromJson<ApphudUser>(
            migratedUserJson,
            object : TypeToken<ApphudUser>() {}.type
        )
        assertNotNull("User should be deserializable", migratedUser)
        assertEquals("User should have 2 paywalls", 2, migratedUser?.paywalls?.size)
        assertEquals("User should have 1 placement", 1, migratedUser?.placements?.size)
        assertEquals(
            "First paywall identifier should match",
            "main_paywall",
            migratedUser?.paywalls?.get(0)?.identifier
        )

        // Legacy keys should be cleared
        assertNull("Legacy paywalls key should be removed", prefsMap[PAYWALLS_KEY])
        assertNull("Legacy placements key should be removed", prefsMap[PLACEMENTS_KEY])
    }

    @Test
    fun `migration from v2 to v3 should not overwrite user paywalls if user already has paywalls`() {
        // Arrange: User already has paywalls, legacy cache also has paywalls
        val existingPaywalls = listOf(createTestPaywall("existing-1", "existing_paywall"))
        val userWithPaywalls = createTestUser(
            userId = "test-user-id",
            paywalls = existingPaywalls,
            placements = emptyList()
        )
        val legacyPaywalls = listOf(
            createTestPaywall("legacy-1", "legacy_paywall")
        )

        setupV2Cache(userWithPaywalls, legacyPaywalls, emptyList())

        // Act
        SharedPreferencesStorage.getInstance(context)
        val isValid = SharedPreferencesStorage.validateCaches()

        // Assert
        assertTrue("Cache should be valid after migration", isValid)

        val migratedUserJson = prefsMap[APPHUD_USER_KEY] as? String
        val migratedUser = parser.fromJson<ApphudUser>(
            migratedUserJson,
            object : TypeToken<ApphudUser>() {}.type
        )
        assertNotNull("User should exist after migration", migratedUser)
        assertEquals("User should keep original paywall count", 1, migratedUser?.paywalls?.size)
        assertEquals(
            "User should keep existing paywall identifier",
            "existing_paywall",
            migratedUser?.paywalls?.get(0)?.identifier
        )

        // Legacy keys should still be cleared
        assertNull("Legacy paywalls key should be removed", prefsMap[PAYWALLS_KEY])
    }

    @Test
    fun `migration from v2 to v3 should handle empty legacy cache`() {
        // Arrange: User exists, but no legacy paywalls/placements
        val user = createTestUser(
            userId = "test-user-id",
            paywalls = emptyList(),
            placements = emptyList()
        )

        setupV2CacheWithoutLegacyPaywalls(user)

        // Act
        SharedPreferencesStorage.getInstance(context)
        val isValid = SharedPreferencesStorage.validateCaches()

        // Assert
        assertTrue("Cache should be valid after migration", isValid)
        assertEquals("Cache version should be 3", "3", prefsMap[CACHE_VERSION_KEY])

        val migratedUserJson = prefsMap[APPHUD_USER_KEY] as? String
        val migratedUser = parser.fromJson<ApphudUser>(
            migratedUserJson,
            object : TypeToken<ApphudUser>() {}.type
        )
        assertNotNull("User should exist after migration", migratedUser)
        assertTrue("User paywalls should remain empty", migratedUser?.paywalls?.isEmpty() == true)
    }

    @Test
    fun `migration from v2 to v3 should handle missing user`() {
        // Arrange: No user, but legacy paywalls exist
        val legacyPaywalls = listOf(createTestPaywall("paywall-1", "orphan_paywall"))

        prefsMap[CACHE_VERSION_KEY] = "2"
        prefsMap[PAYWALLS_KEY] = parser.toJson(legacyPaywalls)
        prefsMap[PAYWALLS_TIMESTAMP_KEY] = System.currentTimeMillis()

        // Act
        SharedPreferencesStorage.getInstance(context)
        val isValid = SharedPreferencesStorage.validateCaches()

        // Assert
        assertTrue("Cache should be valid after migration (no user to migrate)", isValid)
        assertEquals("Cache version should be 3", "3", prefsMap[CACHE_VERSION_KEY])
        assertNull("User should remain null", prefsMap[APPHUD_USER_KEY])

        // Legacy keys should be cleared even without user
        assertNull("Legacy paywalls key should be removed", prefsMap[PAYWALLS_KEY])
    }

    @Test
    fun `validateCaches should clear all caches for invalid version`() {
        // Arrange: Invalid cache version (version 1)
        val user = createTestUser("test-user", emptyList(), emptyList())

        prefsMap[CACHE_VERSION_KEY] = "1"
        prefsMap[APPHUD_USER_KEY] = parser.toJson(user)
        prefsMap[PAYWALLS_KEY] = "some-data"

        // Act
        SharedPreferencesStorage.getInstance(context)
        val isValid = SharedPreferencesStorage.validateCaches()

        // Assert
        assertTrue("validateCaches should return false for invalid version", !isValid)
        assertEquals("Cache version should be updated to 3", "3", prefsMap[CACHE_VERSION_KEY])
        assertNull("Legacy paywalls should be cleared", prefsMap[PAYWALLS_KEY])
    }

    @Test
    fun `validateCaches should return true for current version without changes`() {
        // Arrange: Already on v3
        val user = createTestUser(
            userId = "test-user",
            paywalls = listOf(createTestPaywall("p1", "paywall")),
            placements = emptyList()
        )

        prefsMap[CACHE_VERSION_KEY] = "3"
        prefsMap[APPHUD_USER_KEY] = parser.toJson(user)

        // Act
        SharedPreferencesStorage.getInstance(context)
        val isValid = SharedPreferencesStorage.validateCaches()

        // Assert
        assertTrue("Cache should be valid for current version", isValid)

        val storedUserJson = prefsMap[APPHUD_USER_KEY] as? String
        val storedUser = parser.fromJson<ApphudUser>(
            storedUserJson,
            object : TypeToken<ApphudUser>() {}.type
        )
        assertNotNull("User should exist", storedUser)
        assertEquals("User paywalls should be preserved", 1, storedUser?.paywalls?.size)
    }

    @Test
    fun `validateCaches should handle null cache version`() {
        // Arrange: No cache version set (fresh install or corrupted)
        val user = createTestUser("test-user", emptyList(), emptyList())
        prefsMap[APPHUD_USER_KEY] = parser.toJson(user)
        // No CACHE_VERSION_KEY set

        // Act
        SharedPreferencesStorage.getInstance(context)
        val isValid = SharedPreferencesStorage.validateCaches()

        // Assert
        assertTrue("validateCaches should return false for null version", !isValid)
        assertEquals("Cache version should be set to 3", "3", prefsMap[CACHE_VERSION_KEY])
    }

    // Helper methods

    private fun createTestUser(
        userId: String,
        paywalls: List<ApphudPaywall>,
        placements: List<ApphudPlacement>,
    ): ApphudUser {
        return ApphudUser(
            userId = userId,
            currencyCode = "USD",
            countryCode = "US",
            subscriptions = emptyList(),
            purchases = emptyList(),
            paywalls = paywalls,
            placements = placements,
            isTemporary = false
        )
    }

    private fun createTestPaywall(id: String, identifier: String): ApphudPaywall {
        return ApphudPaywall(
            id = id,
            identifier = identifier,
            name = "Test Paywall $identifier",
            default = false,
            json = null,
            screen = null,
            experimentName = null,
            variationName = null,
            parentPaywallIdentifier = null,
            products = null,
            placementIdentifier = null,
            placementId = null
        )
    }

    private fun createTestPlacement(id: String, identifier: String): ApphudPlacement {
        return ApphudPlacement(
            id = id,
            identifier = identifier,
            paywall = createTestPaywall("inner-$id", "inner-$identifier")
        )
    }

    private fun setupV2Cache(
        user: ApphudUser,
        legacyPaywalls: List<ApphudPaywall>,
        legacyPlacements: List<ApphudPlacement>,
    ) {
        prefsMap[CACHE_VERSION_KEY] = "2"
        prefsMap[APPHUD_USER_KEY] = parser.toJson(user)
        prefsMap[USER_ID_KEY] = user.userId
        prefsMap[PAYWALLS_KEY] = parser.toJson(legacyPaywalls)
        prefsMap[PAYWALLS_TIMESTAMP_KEY] = System.currentTimeMillis()
        prefsMap[PLACEMENTS_KEY] = parser.toJson(legacyPlacements)
        prefsMap[PLACEMENTS_TIMESTAMP_KEY] = System.currentTimeMillis()
    }

    private fun setupV2CacheWithoutLegacyPaywalls(user: ApphudUser) {
        prefsMap[CACHE_VERSION_KEY] = "2"
        prefsMap[APPHUD_USER_KEY] = parser.toJson(user)
        prefsMap[USER_ID_KEY] = user.userId
    }
}

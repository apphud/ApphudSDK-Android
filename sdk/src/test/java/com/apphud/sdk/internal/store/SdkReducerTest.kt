package com.apphud.sdk.internal.store

import com.apphud.sdk.ApphudError
import com.apphud.sdk.domain.ApphudUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SdkReducerTest {

    private fun testUser(
        userId: String = "test_user",
        isTemporary: Boolean? = false,
    ) = ApphudUser(
        userId = userId,
        currencyCode = "USD",
        countryCode = "US",
        subscriptions = emptyList(),
        purchases = emptyList(),
        placements = emptyList(),
        isTemporary = isTemporary,
    )

    // region NotInitialized + StartInitialization(needRegistration=true)

    @Test
    fun `GIVEN NotInitialized and StartInitialization with needRegistration true EXPECT state becomes Registering`() {
        val (newState, _) = sdkReducer(
            SdkState.NotInitialized,
            SdkEvent.StartInitialization(
                apiKey = "api-key",
                userId = "user-1",
                needRegistration = true,
                isNew = true,
                cachedUser = null,
            ),
        )

        assertTrue(newState is SdkState.Registering)
    }

    @Test
    fun `GIVEN NotInitialized and StartInitialization with needRegistration true EXPECT Registering carries apiKey`() {
        val (newState, _) = sdkReducer(
            SdkState.NotInitialized,
            SdkEvent.StartInitialization(
                apiKey = "api-key",
                userId = "user-1",
                needRegistration = true,
                isNew = true,
                cachedUser = null,
            ),
        )

        assertEquals("api-key", (newState as SdkState.Registering).apiKey)
    }

    @Test
    fun `GIVEN NotInitialized and StartInitialization with needRegistration true EXPECT Registering carries userId`() {
        val (newState, _) = sdkReducer(
            SdkState.NotInitialized,
            SdkEvent.StartInitialization(
                apiKey = "api-key",
                userId = "user-1",
                needRegistration = true,
                isNew = true,
                cachedUser = null,
            ),
        )

        assertEquals("user-1", (newState as SdkState.Registering).userId)
    }

    @Test
    fun `GIVEN NotInitialized and StartInitialization with needRegistration true EXPECT PerformRegistration effect emitted`() {
        val (_, effects) = sdkReducer(
            SdkState.NotInitialized,
            SdkEvent.StartInitialization(
                apiKey = "api-key",
                userId = "user-1",
                needRegistration = true,
                isNew = true,
                cachedUser = null,
            ),
        )

        assertTrue(effects.any { it is SdkEffect.PerformRegistration })
    }

    @Test
    fun `GIVEN NotInitialized and StartInitialization with needRegistration true EXPECT PerformRegistration isForce is false`() {
        val (_, effects) = sdkReducer(
            SdkState.NotInitialized,
            SdkEvent.StartInitialization(
                apiKey = "api-key",
                userId = "user-1",
                needRegistration = true,
                isNew = true,
                cachedUser = null,
            ),
        )

        val effect = effects.filterIsInstance<SdkEffect.PerformRegistration>().single()
        assertEquals(false, effect.isForce)
    }

    // endregion

    // region NotInitialized + StartInitialization(needRegistration=false)

    @Test
    fun `GIVEN NotInitialized and StartInitialization with needRegistration false EXPECT state becomes Ready`() {
        val cachedUser = testUser()
        val (newState, _) = sdkReducer(
            SdkState.NotInitialized,
            SdkEvent.StartInitialization(
                apiKey = "api-key",
                userId = null,
                needRegistration = false,
                isNew = true,
                cachedUser = cachedUser,
            ),
        )

        assertTrue(newState is SdkState.Ready)
    }

    @Test
    fun `GIVEN NotInitialized and StartInitialization with needRegistration false EXPECT Ready contains cached user`() {
        val cachedUser = testUser()
        val (newState, _) = sdkReducer(
            SdkState.NotInitialized,
            SdkEvent.StartInitialization(
                apiKey = "api-key",
                userId = null,
                needRegistration = false,
                isNew = true,
                cachedUser = cachedUser,
            ),
        )

        assertEquals(cachedUser, (newState as SdkState.Ready).user)
    }

    @Test
    fun `GIVEN NotInitialized and StartInitialization with needRegistration false and no cached user EXPECT state becomes Registering`() {
        val (newState, effects) = sdkReducer(
            SdkState.NotInitialized,
            SdkEvent.StartInitialization(
                apiKey = "api-key",
                userId = "user-1",
                needRegistration = false,
                isNew = true,
                cachedUser = null,
            ),
        )

        assertTrue(newState is SdkState.Registering)
        assertTrue(effects.any { it is SdkEffect.PerformRegistration })
    }

    // endregion

    // region NotInitialized + invalid event (no-op)

    @Test
    fun `GIVEN NotInitialized and RegistrationSucceeded EXPECT state unchanged`() {
        val (newState, effects) = sdkReducer(
            SdkState.NotInitialized,
            SdkEvent.RegistrationSucceeded(testUser()),
        )

        assertTrue(newState is SdkState.NotInitialized)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `GIVEN NotInitialized and RetryRegistration EXPECT state unchanged`() {
        val (newState, effects) = sdkReducer(
            SdkState.NotInitialized,
            SdkEvent.RetryRegistration,
        )

        assertTrue(newState is SdkState.NotInitialized)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `GIVEN NotInitialized and SessionCleared EXPECT state unchanged`() {
        val (newState, effects) = sdkReducer(
            SdkState.NotInitialized,
            SdkEvent.SessionCleared,
        )

        assertTrue(newState is SdkState.NotInitialized)
        assertTrue(effects.isEmpty())
    }

    // endregion

    // region Registering + RegistrationSucceeded

    @Test
    fun `GIVEN Registering and RegistrationSucceeded EXPECT state becomes Ready`() {
        val user = testUser()
        val (newState, _) = sdkReducer(
            SdkState.Registering(apiKey = "api-key", userId = "user-1"),
            SdkEvent.RegistrationSucceeded(user),
        )

        assertTrue(newState is SdkState.Ready)
    }

    @Test
    fun `GIVEN Registering and RegistrationSucceeded EXPECT Ready contains returned user`() {
        val user = testUser()
        val (newState, _) = sdkReducer(
            SdkState.Registering(apiKey = "api-key", userId = "user-1"),
            SdkEvent.RegistrationSucceeded(user),
        )

        assertEquals(user, (newState as SdkState.Ready).user)
    }

    @Test
    fun `GIVEN Registering and RegistrationSucceeded EXPECT apiKey is preserved in Ready`() {
        val (newState, _) = sdkReducer(
            SdkState.Registering(apiKey = "api-key", userId = "user-1"),
            SdkEvent.RegistrationSucceeded(testUser()),
        )

        assertEquals("api-key", (newState as SdkState.Ready).apiKey)
    }

    // endregion

    // region Registering + RegistrationFailed

    @Test
    fun `GIVEN Registering and RegistrationFailed EXPECT state becomes Degraded`() {
        val error = ApphudError("network error")
        val (newState, _) = sdkReducer(
            SdkState.Registering(apiKey = "api-key", userId = "user-1"),
            SdkEvent.RegistrationFailed(error = error, cachedUser = null),
        )

        assertTrue(newState is SdkState.Degraded)
    }

    @Test
    fun `GIVEN Registering and RegistrationFailed EXPECT Degraded retryCount is 0`() {
        val error = ApphudError("network error")
        val (newState, _) = sdkReducer(
            SdkState.Registering(apiKey = "api-key", userId = "user-1"),
            SdkEvent.RegistrationFailed(error = error, cachedUser = null),
        )

        assertEquals(0, (newState as SdkState.Degraded).retryCount)
    }

    @Test
    fun `GIVEN Registering and RegistrationFailed with null cached user EXPECT Degraded fromFallback is false`() {
        val error = ApphudError("network error")
        val (newState, _) = sdkReducer(
            SdkState.Registering(apiKey = "api-key", userId = "user-1"),
            SdkEvent.RegistrationFailed(error = error, cachedUser = null),
        )

        assertEquals(false, (newState as SdkState.Degraded).fromFallback)
    }

    @Test
    fun `GIVEN Registering and RegistrationFailed with temporary cached user EXPECT Degraded fromFallback is true`() {
        val error = ApphudError("network error")
        val temporaryUser = testUser(isTemporary = true)
        val (newState, _) = sdkReducer(
            SdkState.Registering(apiKey = "api-key", userId = "user-1"),
            SdkEvent.RegistrationFailed(error = error, cachedUser = temporaryUser),
        )

        assertEquals(true, (newState as SdkState.Degraded).fromFallback)
    }

    @Test
    fun `GIVEN Registering and RegistrationFailed with non-temporary cached user EXPECT Degraded fromFallback is false`() {
        val error = ApphudError("network error")
        val nonTemporaryUser = testUser(isTemporary = false)
        val (newState, _) = sdkReducer(
            SdkState.Registering(apiKey = "api-key", userId = "user-1"),
            SdkEvent.RegistrationFailed(error = error, cachedUser = nonTemporaryUser),
        )

        assertEquals(false, (newState as SdkState.Degraded).fromFallback)
    }

    // endregion

    // region Registering + FallbackLoaded

    @Test
    fun `GIVEN Registering and FallbackLoaded EXPECT state becomes Ready`() {
        val user = testUser()
        val (newState, _) = sdkReducer(
            SdkState.Registering(apiKey = "api-key", userId = "user-1"),
            SdkEvent.FallbackLoaded(user),
        )

        assertTrue(newState is SdkState.Ready)
    }

    @Test
    fun `GIVEN Registering and FallbackLoaded EXPECT Ready fromFallback is true`() {
        val user = testUser()
        val (newState, _) = sdkReducer(
            SdkState.Registering(apiKey = "api-key", userId = "user-1"),
            SdkEvent.FallbackLoaded(user),
        )

        assertEquals(true, (newState as SdkState.Ready).fromFallback)
    }

    // endregion

    // region Registering + invalid event (no-op)

    @Test
    fun `GIVEN Registering and ForceRegistrationRequested EXPECT state unchanged`() {
        val initial = SdkState.Registering(apiKey = "api-key", userId = "user-1")
        val (newState, effects) = sdkReducer(
            initial,
            SdkEvent.ForceRegistrationRequested(apiKey = "api-key"),
        )

        assertEquals(initial, newState)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `GIVEN Registering and RetryRegistration EXPECT state unchanged`() {
        val initial = SdkState.Registering(apiKey = "api-key", userId = "user-1")
        val (newState, effects) = sdkReducer(
            initial,
            SdkEvent.RetryRegistration,
        )

        assertEquals(initial, newState)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `GIVEN Registering and SessionCleared EXPECT state becomes NotInitialized`() {
        val initial = SdkState.Registering(apiKey = "api-key", userId = "user-1")
        val (newState, effects) = sdkReducer(initial, SdkEvent.SessionCleared)
        assertTrue(newState is SdkState.NotInitialized)
        assertTrue(effects.isEmpty())
    }

    // endregion

    // region Ready + ForceRegistrationRequested

    @Test
    fun `GIVEN Ready and ForceRegistrationRequested EXPECT state becomes Registering`() {
        val (newState, _) = sdkReducer(
            SdkState.Ready(apiKey = "api-key", user = testUser()),
            SdkEvent.ForceRegistrationRequested(apiKey = "api-key", userId = "new-user", email = "a@b.com"),
        )

        assertTrue(newState is SdkState.Registering)
    }

    @Test
    fun `GIVEN Ready and ForceRegistrationRequested EXPECT Registering isForce is true`() {
        val (newState, _) = sdkReducer(
            SdkState.Ready(apiKey = "api-key", user = testUser()),
            SdkEvent.ForceRegistrationRequested(apiKey = "api-key", userId = "new-user", email = "a@b.com"),
        )

        assertEquals(true, (newState as SdkState.Registering).isForce)
    }

    @Test
    fun `GIVEN Ready and ForceRegistrationRequested EXPECT PerformRegistration isForce is true`() {
        val (_, effects) = sdkReducer(
            SdkState.Ready(apiKey = "api-key", user = testUser()),
            SdkEvent.ForceRegistrationRequested(apiKey = "api-key", userId = "new-user", email = "a@b.com"),
        )

        val effect = effects.filterIsInstance<SdkEffect.PerformRegistration>().single()
        assertEquals(true, effect.isForce)
    }

    @Test
    fun `GIVEN Ready and ForceRegistrationRequested EXPECT PerformRegistration carries userId`() {
        val (_, effects) = sdkReducer(
            SdkState.Ready(apiKey = "api-key", user = testUser()),
            SdkEvent.ForceRegistrationRequested(apiKey = "api-key", userId = "new-user", email = "a@b.com"),
        )

        val effect = effects.filterIsInstance<SdkEffect.PerformRegistration>().single()
        assertEquals("new-user", effect.userId)
    }

    @Test
    fun `GIVEN Ready and ForceRegistrationRequested EXPECT PerformRegistration carries email`() {
        val (_, effects) = sdkReducer(
            SdkState.Ready(apiKey = "api-key", user = testUser()),
            SdkEvent.ForceRegistrationRequested(apiKey = "api-key", userId = "new-user", email = "a@b.com"),
        )

        val effect = effects.filterIsInstance<SdkEffect.PerformRegistration>().single()
        assertEquals("a@b.com", effect.email)
    }

    // endregion

    // region Ready + FallbackDisabled

    @Test
    fun `GIVEN Ready with fromFallback true and FallbackDisabled EXPECT Ready fromFallback becomes false`() {
        val (newState, _) = sdkReducer(
            SdkState.Ready(apiKey = "api-key", user = testUser(), fromFallback = true),
            SdkEvent.FallbackDisabled,
        )

        assertEquals(false, (newState as SdkState.Ready).fromFallback)
    }

    @Test
    fun `GIVEN Ready and FallbackDisabled EXPECT state stays Ready`() {
        val (newState, _) = sdkReducer(
            SdkState.Ready(apiKey = "api-key", user = testUser(), fromFallback = true),
            SdkEvent.FallbackDisabled,
        )

        assertTrue(newState is SdkState.Ready)
    }

    // endregion

    // region Ready(fromFallback=true) + RegistrationSucceeded

    @Test
    fun `GIVEN Ready with fromFallback true and RegistrationSucceeded EXPECT Ready fromFallback becomes false`() {
        val realUser = testUser(userId = "real-user")
        val (newState, effects) = sdkReducer(
            SdkState.Ready(apiKey = "api-key", user = testUser(), fromFallback = true),
            SdkEvent.RegistrationSucceeded(realUser),
        )

        assertTrue(newState is SdkState.Ready)
        assertEquals(false, (newState as SdkState.Ready).fromFallback)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `GIVEN Ready with fromFallback true and RegistrationSucceeded EXPECT Ready contains real user`() {
        val realUser = testUser(userId = "real-user")
        val (newState, _) = sdkReducer(
            SdkState.Ready(apiKey = "api-key", user = testUser(), fromFallback = true),
            SdkEvent.RegistrationSucceeded(realUser),
        )

        assertEquals(realUser, (newState as SdkState.Ready).user)
    }

    // endregion

    // region Ready + SessionCleared

    @Test
    fun `GIVEN Ready and SessionCleared EXPECT state becomes NotInitialized`() {
        val (newState, effects) = sdkReducer(
            SdkState.Ready(apiKey = "api-key", user = testUser()),
            SdkEvent.SessionCleared,
        )

        assertTrue(newState is SdkState.NotInitialized)
        assertTrue(effects.isEmpty())
    }

    // endregion

    // region Ready + invalid event (no-op)

    @Test
    fun `GIVEN Ready without fromFallback and RegistrationSucceeded EXPECT Ready fromFallback is false`() {
        val newUser = testUser(userId = "new-user")
        val (newState, effects) = sdkReducer(
            SdkState.Ready(apiKey = "api-key", user = testUser()),
            SdkEvent.RegistrationSucceeded(newUser),
        )

        assertTrue(newState is SdkState.Ready)
        assertEquals(false, (newState as SdkState.Ready).fromFallback)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `GIVEN Ready and RetryRegistration EXPECT state unchanged`() {
        val initial = SdkState.Ready(apiKey = "api-key", user = testUser())
        val (newState, effects) = sdkReducer(
            initial,
            SdkEvent.RetryRegistration,
        )

        assertEquals(initial, newState)
        assertTrue(effects.isEmpty())
    }

    // endregion

    // region Degraded + RetryRegistration

    @Test
    fun `GIVEN Degraded and RetryRegistration EXPECT state becomes Registering`() {
        val (newState, _) = sdkReducer(
            SdkState.Degraded(apiKey = "api-key", user = null, lastError = null),
            SdkEvent.RetryRegistration,
        )

        assertTrue(newState is SdkState.Registering)
    }

    @Test
    fun `GIVEN Degraded and RetryRegistration EXPECT Registering carries apiKey from Degraded`() {
        val (newState, _) = sdkReducer(
            SdkState.Degraded(apiKey = "api-key", user = null, lastError = null),
            SdkEvent.RetryRegistration,
        )

        assertEquals("api-key", (newState as SdkState.Registering).apiKey)
    }

    @Test
    fun `GIVEN Degraded and RetryRegistration EXPECT Registering isForce is true`() {
        val (newState, _) = sdkReducer(
            SdkState.Degraded(apiKey = "api-key", user = null, lastError = null),
            SdkEvent.RetryRegistration,
        )

        assertEquals(true, (newState as SdkState.Registering).isForce)
    }

    @Test
    fun `GIVEN Degraded and RetryRegistration EXPECT PerformRegistration effect emitted`() {
        val (_, effects) = sdkReducer(
            SdkState.Degraded(apiKey = "api-key", user = null, lastError = null),
            SdkEvent.RetryRegistration,
        )

        assertTrue(effects.any { it is SdkEffect.PerformRegistration })
    }

    @Test
    fun `GIVEN Degraded and RetryRegistration EXPECT PerformRegistration isForce is true`() {
        val (_, effects) = sdkReducer(
            SdkState.Degraded(apiKey = "api-key", user = null, lastError = null),
            SdkEvent.RetryRegistration,
        )

        val effect = effects.filterIsInstance<SdkEffect.PerformRegistration>().single()
        assertEquals(true, effect.isForce)
    }

    // endregion

    // region Degraded + RegistrationSucceeded

    @Test
    fun `GIVEN Degraded and RegistrationSucceeded EXPECT state becomes Ready`() {
        val user = testUser()
        val (newState, _) = sdkReducer(
            SdkState.Degraded(apiKey = "api-key", user = null, lastError = null),
            SdkEvent.RegistrationSucceeded(user),
        )

        assertTrue(newState is SdkState.Ready)
    }

    @Test
    fun `GIVEN Degraded and RegistrationSucceeded EXPECT Ready contains returned user`() {
        val user = testUser()
        val (newState, _) = sdkReducer(
            SdkState.Degraded(apiKey = "api-key", user = null, lastError = null),
            SdkEvent.RegistrationSucceeded(user),
        )

        assertEquals(user, (newState as SdkState.Ready).user)
    }

    @Test
    fun `GIVEN Degraded and RegistrationSucceeded EXPECT apiKey preserved in Ready`() {
        val user = testUser()
        val (newState, _) = sdkReducer(
            SdkState.Degraded(apiKey = "api-key", user = null, lastError = null),
            SdkEvent.RegistrationSucceeded(user),
        )

        assertEquals("api-key", (newState as SdkState.Ready).apiKey)
    }

    // endregion

    // region Degraded + FallbackLoaded

    @Test
    fun `GIVEN Degraded and FallbackLoaded EXPECT state becomes Ready`() {
        val user = testUser()
        val (newState, _) = sdkReducer(
            SdkState.Degraded(apiKey = "api-key", user = null, lastError = null),
            SdkEvent.FallbackLoaded(user),
        )

        assertTrue(newState is SdkState.Ready)
    }

    @Test
    fun `GIVEN Degraded and FallbackLoaded EXPECT Ready fromFallback is true`() {
        val user = testUser()
        val (newState, _) = sdkReducer(
            SdkState.Degraded(apiKey = "api-key", user = null, lastError = null),
            SdkEvent.FallbackLoaded(user),
        )

        assertEquals(true, (newState as SdkState.Ready).fromFallback)
    }

    // endregion

    // region Degraded + SessionCleared

    @Test
    fun `GIVEN Degraded and SessionCleared EXPECT state becomes NotInitialized`() {
        val (newState, effects) = sdkReducer(
            SdkState.Degraded(apiKey = "api-key", user = null, lastError = null),
            SdkEvent.SessionCleared,
        )

        assertTrue(newState is SdkState.NotInitialized)
        assertTrue(effects.isEmpty())
    }

    // endregion

    // region Degraded + ForceRegistrationRequested

    @Test
    fun `GIVEN Degraded and ForceRegistrationRequested EXPECT state becomes Registering`() {
        val initial = SdkState.Degraded(apiKey = "api-key", user = null, lastError = null)
        val (newState, _) = sdkReducer(
            initial,
            SdkEvent.ForceRegistrationRequested(apiKey = "api-key"),
        )

        assertTrue(newState is SdkState.Registering)
    }

    @Test
    fun `GIVEN Degraded and ForceRegistrationRequested EXPECT Registering isForce is true`() {
        val initial = SdkState.Degraded(apiKey = "api-key", user = null, lastError = null)
        val (newState, _) = sdkReducer(
            initial,
            SdkEvent.ForceRegistrationRequested(apiKey = "api-key"),
        )

        assertEquals(true, (newState as SdkState.Registering).isForce)
    }

    @Test
    fun `GIVEN Degraded and ForceRegistrationRequested EXPECT PerformRegistration effect emitted`() {
        val initial = SdkState.Degraded(apiKey = "api-key", user = null, lastError = null)
        val (_, effects) = sdkReducer(
            initial,
            SdkEvent.ForceRegistrationRequested(apiKey = "api-key"),
        )

        assertTrue(effects.any { it is SdkEffect.PerformRegistration })
    }

    // endregion

    // region Degraded + invalid event (no-op)

    @Test
    fun `GIVEN Degraded and FallbackDisabled EXPECT state unchanged`() {
        val initial = SdkState.Degraded(apiKey = "api-key", user = null, lastError = null)
        val (newState, effects) = sdkReducer(
            initial,
            SdkEvent.FallbackDisabled,
        )

        assertEquals(initial, newState)
        assertTrue(effects.isEmpty())
    }

    // endregion

    // region apiKey preservation across transitions

    @Test
    fun `GIVEN Registering with specific apiKey and RegistrationSucceeded EXPECT apiKey preserved in Ready`() {
        val (newState, _) = sdkReducer(
            SdkState.Registering(apiKey = "unique-api-key", userId = null),
            SdkEvent.RegistrationSucceeded(testUser()),
        )

        assertEquals("unique-api-key", (newState as SdkState.Ready).apiKey)
    }

    @Test
    fun `GIVEN Degraded with specific apiKey and RetryRegistration EXPECT apiKey preserved in Registering`() {
        val (newState, _) = sdkReducer(
            SdkState.Degraded(apiKey = "unique-api-key", user = null, lastError = null),
            SdkEvent.RetryRegistration,
        )

        assertEquals("unique-api-key", (newState as SdkState.Registering).apiKey)
    }

    // endregion
}

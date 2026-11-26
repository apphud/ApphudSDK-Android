package com.apphud.sdk

import com.apphud.sdk.flutter.ApphudFlutter
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The wrapper-facing surface: one answer for every way a sync can end, and the identity version. */
@OptIn(ExperimentalCoroutinesApi::class)
class ApphudFlutterObserverSyncTest {

    private val chosen = ApphudInternal::class.java.getDeclaredField("identityVersionChosen").apply { isAccessible = true }
    private val dispatcher = StandardTestDispatcher()
    private val session = CoroutineScope(SupervisorJob() + dispatcher)
    private val answer = CompletableDeferred<ApphudPurchasesRestoreResult>()
    private val success = ApphudPurchasesRestoreResult.Success(emptyList(), emptyList())

    init {
        Dispatchers.setMain(dispatcher)
        resetIdentityVersion()
    }

    @After
    fun tearDown() {
        session.cancel()
        unmockkAll()
        resetIdentityVersion()
        Dispatchers.resetMain()
    }

    private fun resetIdentityVersion() {
        ApphudInternal.identityVersion = ApphudIdentityVersion.V1
        chosen.setBoolean(ApphudInternal, false)
    }

    private fun startedSession() {
        mockkObject(ApphudInternal)
        mockkStatic("com.apphud.sdk.ApphudInternal_RestorePurchasesKt")
        every { ApphudInternal.coroutineScope } returns session
        coEvery { ApphudInternal.syncPurchasesReportingFailures() } coAnswers { answer.await() }
    }

    private fun assertError(result: ApphudPurchasesRestoreResult?) =
        assertTrue("$result", result is ApphudPurchasesRestoreResult.Error)

    @Test
    fun `GIVEN no start EXPECT an error`() = runTest(dispatcher) {
        assertError(ApphudFlutter.syncPurchasesInObserverMode())
    }

    @Test
    fun `GIVEN the sync succeeds EXPECT its result`() = runTest(dispatcher) {
        startedSession()
        answer.complete(success)

        assertEquals(success, ApphudFlutter.syncPurchasesInObserverMode())
    }

    @Test
    fun `GIVEN the sync throws EXPECT an error`() = runTest(dispatcher) {
        startedSession()
        answer.completeExceptionally(ApphudError("boom"))

        assertError(ApphudFlutter.syncPurchasesInObserverMode())
    }

    @Test
    fun `GIVEN a logout during the sync EXPECT an error`() = runTest(dispatcher) {
        startedSession()
        var result: ApphudPurchasesRestoreResult? = null
        launch { result = ApphudFlutter.syncPurchasesInObserverMode() }
        advanceUntilIdle()

        session.cancel()
        advanceUntilIdle()

        assertError(result)
    }

    @Test
    fun `GIVEN the caller canceled EXPECT no result`() = runTest(dispatcher) {
        startedSession()
        var result: ApphudPurchasesRestoreResult? = null
        val caller: Job = launch { result = ApphudFlutter.syncPurchasesInObserverMode() }
        advanceUntilIdle()

        caller.cancel()
        advanceUntilIdle()

        assertNull(result)
    }

    @Test
    fun `GIVEN a callback EXPECT it called once with the result`() = runTest(dispatcher) {
        startedSession()
        val results = mutableListOf<ApphudPurchasesRestoreResult>()
        ApphudFlutter.syncPurchasesInObserverMode { results.add(it) }
        advanceUntilIdle()

        answer.complete(success)
        advanceUntilIdle()

        assertEquals(listOf(success), results)
    }

    @Test
    fun `GIVEN a callback and a logout during the sync EXPECT it called once with an error`() = runTest(dispatcher) {
        startedSession()
        val results = mutableListOf<ApphudPurchasesRestoreResult>()
        ApphudFlutter.syncPurchasesInObserverMode { results.add(it) }
        advanceUntilIdle()

        session.cancel()
        advanceUntilIdle()

        assertError(results.single())
    }

    @Test
    fun `GIVEN a callback and no start EXPECT it called with an error`() = runTest(dispatcher) {
        val result = CompletableDeferred<ApphudPurchasesRestoreResult>()

        ApphudFlutter.syncPurchasesInObserverMode { result.complete(it) }

        // The canceled session answers from its own thread.
        assertError(result.await())
    }

    @Test
    fun `GIVEN a callback before start WHEN start follows at once EXPECT an error`() = runTest(dispatcher) {
        startedSession()
        answer.complete(success)
        every { ApphudInternal.coroutineScope } returns CoroutineScope(dispatcher).also { it.cancel() }
        val results = mutableListOf<ApphudPurchasesRestoreResult>()
        ApphudFlutter.syncPurchasesInObserverMode { results.add(it) }

        // start() runs before the main thread gets to the callback's coroutine.
        every { ApphudInternal.coroutineScope } returns session
        advanceUntilIdle()

        assertError(results.single())
    }

    @Test
    fun `GIVEN no start EXPECT no identity version`() {
        assertNull(ApphudFlutter.identityVersion())
    }

    @Test
    fun `GIVEN a start on version 2 EXPECT version 2`() {
        ApphudInternal.chooseIdentityVersion(ApphudIdentityVersion.V2)

        assertEquals(ApphudIdentityVersion.V2, ApphudFlutter.identityVersion())
    }
}

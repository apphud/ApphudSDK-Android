package com.apphud.sdk.internal.presentation

import com.apphud.sdk.internal.SessionComponent
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
import org.junit.Assert.*
import org.junit.Test

class ScreenSessionBindingTest {
    private fun session(id: String): SessionComponent = mockk {
        every { screenSessionId } returns id
        every { coroutineScope } returns CoroutineScope(SupervisorJob())
    }

    @Test fun `GIVEN no session EXPECT restored screen rejected`() {
        assertNull(ScreenSessionBinding.create(MutableStateFlow(null), "old"))
    }
    @Test fun `GIVEN legacy intent without marker EXPECT rejected`() {
        assertNull(ScreenSessionBinding.create(MutableStateFlow(session("new")), null))
    }
    @Test fun `GIVEN previous process marker EXPECT rejected`() {
        assertNull(ScreenSessionBinding.create(MutableStateFlow(session("new")), "old"))
    }
    @Test fun `GIVEN matching active session EXPECT same dependencies captured`() {
        val session = session("current")
        assertSame(session, ScreenSessionBinding.create(MutableStateFlow(session), "current")!!.session)
    }
    @Test fun `GIVEN canceled session before state update EXPECT rejected`() {
        val session = session("current")
        session.coroutineScope.cancel()
        assertNull(ScreenSessionBinding.create(MutableStateFlow(session), "current"))
    }
    @Test fun `GIVEN logout before collector runs EXPECT callbacks rejected immediately`() {
        val session = session("current")
        val binding = ScreenSessionBinding.create(MutableStateFlow(session), "current")!!
        session.coroutineScope.cancel()
        assertFalse(binding.isCurrent)
    }
    @Test fun `GIVEN fast logout and restart EXPECT old binding invalid`() {
        val sessions = MutableStateFlow<SessionComponent?>(session("old"))
        val binding = ScreenSessionBinding.create(sessions, "old")!!
        sessions.value = session("new")
        assertFalse(binding.isCurrent)
    }
    @Test fun `GIVEN background and resume with same session EXPECT binding valid`() {
        val sessions = MutableStateFlow<SessionComponent?>(session("current"))
        val binding = ScreenSessionBinding.create(sessions, "current")!!
        sessions.value = binding.session
        assertTrue(binding.isCurrent)
    }
    @Test fun `GIVEN changed session before observer starts EXPECT end observed`() = runTest {
        val sessions = MutableStateFlow<SessionComponent?>(session("old"))
        val binding = ScreenSessionBinding.create(sessions, "old")!!
        sessions.value = session("new")
        val ended = async { binding.awaitEnd(); true }
        assertTrue(ended.await())
    }
}

package com.apphud.sdk.internal.data.network

import com.apphud.sdk.internal.data.ClientSessionRepository
import com.apphud.sdk.storage.ClientSessionStorage
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionIdInterceptorTest {

    private class FakeStorage : ClientSessionStorage {
        override var clientSessionId: String? = null
        override var clientSessionNumber: Int = 0
        override var clientSessionLastBackgroundAt: Long = 0L
    }

    private var idCounter = 0
    private val repository = ClientSessionRepository(FakeStorage(), newId = { "id-${++idCounter}" })
        .apply { onAppOpened() }
    private val recorded = mutableListOf<Request>()

    // Records each attempt and answers with the next status code.
    private fun terminal(statuses: List<Int> = listOf(200), onAttempt: (Int) -> Unit = {}) =
        Interceptor { chain ->
            val attempt = synchronized(recorded) {
                recorded.add(chain.request())
                recorded.size - 1
            }
            onAttempt(attempt)
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(statuses.getOrElse(attempt) { 200 })
                .message("stub")
                .body("{}".toResponseBody())
                .build()
        }

    private fun client(vararg interceptors: Interceptor): OkHttpClient =
        OkHttpClient.Builder().apply { interceptors.forEach { addInterceptor(it) } }.build()

    private fun call(client: OkHttpClient) {
        client.newCall(Request.Builder().url("https://gateway.apphud.com/v1/customers").build())
            .execute().close()
    }

    private val sessionInterceptor = SessionIdInterceptor { repository.sessionId() }

    @Test
    fun `GIVEN session id EXPECT request carries header`() {
        call(client(sessionInterceptor, terminal()))

        assertEquals(repository.sessionId(), recorded.single().header(SessionIdInterceptor.HEADER))
    }

    @Test
    fun `GIVEN retry after 500 and new session between attempts EXPECT both attempts carry the original id`() {
        val original = repository.sessionId()

        call(client(sessionInterceptor, HttpRetryInterceptor(), terminal(listOf(500, 200)) { attempt ->
            if (attempt == 0) repository.onLogout()
        }))

        assertEquals(listOf(original, original), recorded.map { it.header(SessionIdInterceptor.HEADER) })
    }

    @Test
    fun `GIVEN new session between two calls EXPECT second call carries new id`() {
        val client = client(sessionInterceptor, terminal())
        call(client)
        repository.onLogout()

        call(client)

        assertEquals(repository.sessionId(), recorded.last().header(SessionIdInterceptor.HEADER))
    }

    @Test
    fun `GIVEN host id set before first request EXPECT first request carries it`() {
        repository.setExternalSessionId("Host-Session-1")

        call(client(sessionInterceptor, terminal()))

        assertEquals("Host-Session-1", recorded.single().header(SessionIdInterceptor.HEADER))
    }

    @Test
    fun `GIVEN id that is not a valid header value EXPECT request sent without header`() {
        repository.setExternalSessionId("line\nbreak")

        call(client(sessionInterceptor, terminal()))

        assertNull(recorded.single().header(SessionIdInterceptor.HEADER))
    }
}

package com.apphud.sdk.internal.data.network

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class FirstRegistrationHoldTest {

    private val hold = FirstRegistrationHold()

    private fun chain(path: String): Interceptor.Chain = mockk {
        every { request() } returns Request.Builder().url("https://api.apphud.com$path").build()
        every { proceed(any()) } returns mockk<Response>()
    }

    /** Runs [block] on another thread; true when it finished within [ms]. */
    private fun finishesWithin(ms: Long, block: () -> Unit): Boolean {
        val done = CountDownLatch(1)
        thread { block(); done.countDown() }
        return done.await(ms, TimeUnit.MILLISECONDS)
    }

    @Test
    fun `GIVEN no hold EXPECT requests go at once`() {
        assertTrue(finishesWithin(1_000) { hold.await() })
    }

    @Test
    fun `GIVEN a hold EXPECT requests wait until the first registration finishes`() {
        hold.close()
        val done = CountDownLatch(1)
        thread { hold.await(); done.countDown() }

        assertFalse("still held", done.await(300, TimeUnit.MILLISECONDS))
        hold.open()
        assertTrue(done.await(1_000, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `GIVEN a second close while requests wait EXPECT the next open releases them`() {
        hold.close()
        val done = CountDownLatch(1)
        thread { hold.await(); done.countDown() }
        Thread.sleep(100)

        hold.close()
        hold.open()

        assertTrue(done.await(1_000, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `GIVEN a hold nobody opens EXPECT requests go after the limit`() {
        hold.close(limitMs = 200)

        assertTrue(finishesWithin(2_000) { hold.await() })
    }

    @Test
    fun `GIVEN a hold EXPECT registrations and purchases go at once`() {
        hold.close()
        val customers = chain("/v4/customers")
        val subscriptions = chain("/v4/subscriptions")

        assertTrue(finishesWithin(1_000) { FirstRegistrationHoldInterceptor(hold).intercept(customers) })
        assertTrue(finishesWithin(1_000) { FirstRegistrationHoldInterceptor(hold).intercept(subscriptions) })
    }

    @Test
    fun `GIVEN a hold EXPECT requests outside v4 go at once`() {
        hold.close()
        val preview = chain("/preview_screen/screen-1")

        assertTrue(finishesWithin(1_000) { FirstRegistrationHoldInterceptor(hold).intercept(preview) })
    }

    @Test
    fun `GIVEN a hold EXPECT other requests wait for it`() {
        hold.close()
        val attribution = chain("/v4/customers/attribution")
        val done = CountDownLatch(1)
        thread { FirstRegistrationHoldInterceptor(hold).intercept(attribution); done.countDown() }

        assertFalse("held", done.await(300, TimeUnit.MILLISECONDS))
        verify(exactly = 0) { attribution.proceed(any()) }
        hold.open()
        assertTrue(done.await(1_000, TimeUnit.MILLISECONDS))
        verify { attribution.proceed(any()) }
    }
}

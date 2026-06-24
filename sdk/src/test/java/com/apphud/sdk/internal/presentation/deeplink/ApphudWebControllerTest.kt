package com.apphud.sdk.internal.presentation.deeplink

import android.webkit.WebResourceRequest
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApphudWebControllerTest {

    private val apphudWebController = ApphudWebController()

    // region onReceivedError policy

    @Test
    fun `GIVEN main frame request EXPECT fail on received error`() {
        val request: WebResourceRequest = mockk {
            every { isForMainFrame } returns true
        }

        val shouldFail = apphudWebController.shouldFailOnReceivedError(request)

        assertTrue(shouldFail)
    }

    @Test
    fun `GIVEN subresource request EXPECT ignore received error`() {
        val request: WebResourceRequest = mockk {
            every { isForMainFrame } returns false
        }

        val shouldFail = apphudWebController.shouldFailOnReceivedError(request)

        assertFalse(shouldFail)
    }

    @Test
    fun `GIVEN null request EXPECT ignore received error`() {
        val shouldFail = apphudWebController.shouldFailOnReceivedError(request = null)

        assertFalse(shouldFail)
    }

    // endregion
}

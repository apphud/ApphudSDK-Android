package com.apphud.sdk.internal.domain.mapper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleScreenHtmlSanitizerTest {

    private val sampleHtml = """
        <!DOCTYPE html>
        <html>
        <head>
          <script src="https://static.apphud.com/assets/screen_redirect-abc123.js"></script>
          <style>#aph-screen-content { pointer-events: none; }
        #aph-screen-content.aph-ready { pointer-events: auto; }</style>
        </head>
        <body>
          <div id="aph-screen-content">
            <a class="screen__button" href="/action?type=post_feedback">Send</a>
          </div>
        </body>
        </html>
    """.trimIndent()

    @Test
    fun `GIVEN preview html EXPECT screen_redirect script removed`() {
        val result = RuleScreenHtmlSanitizer.sanitizeForInAppWebView(sampleHtml)

        assertFalse(result.contains("screen_redirect"))
    }

    @Test
    fun `GIVEN preview html EXPECT aph-ready class added`() {
        val result = RuleScreenHtmlSanitizer.sanitizeForInAppWebView(sampleHtml)

        assertTrue(result.contains("""id="aph-screen-content" class="aph-ready""""))
    }

    @Test
    fun `GIVEN preview html EXPECT in-app attribution ready flag injected`() {
        val result = RuleScreenHtmlSanitizer.sanitizeForInAppWebView(sampleHtml)

        assertTrue(result.contains("window.__aphAttributionReady=true"))
    }

    @Test
    fun `GIVEN already sanitized html EXPECT html unchanged`() {
        val sanitized = RuleScreenHtmlSanitizer.sanitizeForInAppWebView(sampleHtml)

        val result = RuleScreenHtmlSanitizer.sanitizeForInAppWebView(sanitized)

        assertTrue(result.contains("window.__aphAttributionReady=true"))
        assertFalse(result.contains("screen_redirect"))
    }
}

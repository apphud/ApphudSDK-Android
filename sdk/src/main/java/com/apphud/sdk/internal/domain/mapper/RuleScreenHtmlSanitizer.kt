package com.apphud.sdk.internal.domain.mapper

/**
 * Prepares legacy rule screen HTML for display inside the in-app WebView.
 *
 * Server preview HTML includes [screen_redirect.js] for web attribution flows. That script is not
 * needed in the SDK WebView and keeps the backdrop spinner visible until a 12s fallback.
 */
internal object RuleScreenHtmlSanitizer {

    private val SCREEN_REDIRECT_SCRIPT = Regex(
        """<script\s+[^>]*\bsrc\s*=\s*["'][^"']*screen_redirect[^"']*["'][^>]*>\s*</script>""",
        RegexOption.IGNORE_CASE,
    )

    private val APH_SCREEN_CONTENT_DIV = Regex(
        """<div\s+id=["']aph-screen-content["'](\s[^>]*)?>""",
        RegexOption.IGNORE_CASE,
    )

    private val APH_SCREEN_CONTENT_WITH_READY = Regex(
        """<div\s+id=["']aph-screen-content["'][^>]*\baph-ready\b""",
        RegexOption.IGNORE_CASE,
    )

    private const val IN_APP_READY_SCRIPT = "<script>window.__aphAttributionReady=true;</script>"

    fun sanitizeForInAppWebView(html: String): String {
        var result = SCREEN_REDIRECT_SCRIPT.replace(html, "")
        result = enableScreenInteraction(result)
        result = injectInAppReadyFlag(result)
        return result
    }

    private fun enableScreenInteraction(html: String): String {
        if (APH_SCREEN_CONTENT_WITH_READY.containsMatchIn(html)) return html

        return APH_SCREEN_CONTENT_DIV.replace(html) { match ->
            val extraAttrs = match.groupValues[1]
            if (extraAttrs.contains("class=", ignoreCase = true)) {
                match.value.replace(
                    Regex("""class\s*=\s*(["'])([^"']*)\1""", RegexOption.IGNORE_CASE),
                ) { classMatch ->
                    val quote = classMatch.groupValues[1]
                    val classes = classMatch.groupValues[2]
                    """class=$quote$classes aph-ready$quote"""
                }
            } else {
                """<div id="aph-screen-content"$extraAttrs class="aph-ready">"""
            }
        }
    }

    private fun injectInAppReadyFlag(html: String): String {
        if (html.contains(IN_APP_READY_SCRIPT)) return html

        val headTag = Regex("<head>", RegexOption.IGNORE_CASE)
        return when {
            headTag.containsMatchIn(html) ->
                html.replaceFirst(headTag, "<head>$IN_APP_READY_SCRIPT")
            else -> IN_APP_READY_SCRIPT + html
        }
    }
}

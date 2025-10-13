package com.apphud.sdk.internal.preloader.data.source

import android.webkit.CookieManager
import com.apphud.sdk.ApphudLog
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * CookieJar implementation that synchronizes cookies between OkHttp and WebView
 * This ensures that cookies set by OkHttp are available to WebView and vice versa
 */
internal class WebViewCookieJar : CookieJar {

    private val cookieManager: CookieManager by lazy {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(null, true)
        }
    }

    /**
     * Saves cookies from HTTP response to WebView CookieManager
     * Called by OkHttp after receiving response
     */
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        try {
            val urlString = url.toString()

            cookies.forEach { cookie ->
                val cookieString = buildCookieString(cookie)
                cookieManager.setCookie(urlString, cookieString)

                ApphudLog.log(
                    "[WebViewCookieJar] Saved cookie: ${cookie.name}=${cookie.value.take(20)}... " +
                    "for ${url.host}"
                )
            }

            // Ensure cookies are persisted
            cookieManager.flush()
        } catch (e: Exception) {
            ApphudLog.logE("[WebViewCookieJar] Error saving cookies: ${e.message}")
        }
    }

    /**
     * Loads cookies from WebView CookieManager for HTTP request
     * Called by OkHttp before sending request
     */
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return try {
            val urlString = url.toString()
            val cookieString = cookieManager.getCookie(urlString)

            if (cookieString.isNullOrEmpty()) {
                ApphudLog.log("[WebViewCookieJar] No cookies found for ${url.host}")
                emptyList()
            } else {
                val cookies = parseCookieString(cookieString, url)
                ApphudLog.log(
                    "[WebViewCookieJar] Loaded ${cookies.size} cookie(s) for ${url.host}"
                )
                cookies
            }
        } catch (e: Exception) {
            ApphudLog.logE("[WebViewCookieJar] Error loading cookies: ${e.message}")
            emptyList()
        }
    }

    /**
     * Builds cookie string in format suitable for WebView CookieManager
     * Format: "name=value; expires=...; path=...; domain=...; secure; httponly"
     */
    private fun buildCookieString(cookie: Cookie): String {
        return buildString {
            append("${cookie.name}=${cookie.value}")

            if (cookie.expiresAt != Long.MAX_VALUE) {
                // CookieManager expects expires in format: "Wdy, DD-Mon-YYYY HH:MM:SS GMT"
                // For simplicity, we'll use max-age if possible
                val maxAge = (cookie.expiresAt - System.currentTimeMillis()) / 1000
                if (maxAge > 0) {
                    append("; max-age=$maxAge")
                }
            }

            if (cookie.path.isNotEmpty()) {
                append("; path=${cookie.path}")
            }

            if (cookie.domain.isNotEmpty()) {
                append("; domain=${cookie.domain}")
            }

            if (cookie.secure) {
                append("; secure")
            }

            if (cookie.httpOnly) {
                append("; httponly")
            }

            if (cookie.hostOnly) {
                // Host-only cookies don't specify domain attribute
            }
        }
    }

    /**
     * Parses cookie string from WebView into OkHttp Cookie objects
     * Format: "name1=value1; name2=value2; name3=value3"
     */
    private fun parseCookieString(cookieString: String, url: HttpUrl): List<Cookie> {
        return try {
            cookieString.split(";").mapNotNull { part ->
                val trimmed = part.trim()
                val separatorIndex = trimmed.indexOf('=')

                if (separatorIndex > 0) {
                    val name = trimmed.substring(0, separatorIndex).trim()
                    val value = trimmed.substring(separatorIndex + 1).trim()

                    // Build a basic cookie - WebView doesn't provide full cookie attributes
                    Cookie.Builder()
                        .name(name)
                        .value(value)
                        .domain(url.host)
                        .path("/")
                        .build()
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            ApphudLog.logE("[WebViewCookieJar] Error parsing cookies: ${e.message}")
            emptyList()
        }
    }

    /**
     * Clears all cookies from WebView CookieManager
     */
    fun clearAllCookies() {
        try {
            cookieManager.removeAllCookies { success ->
                if (success) {
                    ApphudLog.log("[WebViewCookieJar] All cookies cleared successfully")
                } else {
                    ApphudLog.logE("[WebViewCookieJar] Failed to clear cookies")
                }
            }
            cookieManager.flush()
        } catch (e: Exception) {
            ApphudLog.logE("[WebViewCookieJar] Error clearing cookies: ${e.message}")
        }
    }

    /**
     * Clears cookies for specific URL
     */
    fun clearCookiesForUrl(url: String) {
        try {
            val cookieString = cookieManager.getCookie(url)

            if (!cookieString.isNullOrEmpty()) {
                cookieString.split(";").forEach { part ->
                    val name = part.split("=").firstOrNull()?.trim()
                    if (!name.isNullOrEmpty()) {
                        // Set cookie with expired date to delete it
                        cookieManager.setCookie(url, "$name=; max-age=0")
                    }
                }
                cookieManager.flush()
                ApphudLog.log("[WebViewCookieJar] Cookies cleared for: $url")
            }
        } catch (e: Exception) {
            ApphudLog.logE("[WebViewCookieJar] Error clearing cookies for URL: ${e.message}")
        }
    }
}

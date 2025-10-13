package com.apphud.sdk.internal.preloader.data.source

import com.apphud.sdk.ApphudLog
import java.net.URI
import java.net.URISyntaxException

/**
 * Parses HTML content to extract resource URLs (CSS, JS, images, fonts)
 * Uses regex patterns to find resources without requiring a full HTML parser
 */
internal class HtmlResourceParser {

    companion object {
        // Regex patterns for different resource types
        private val LINK_PATTERN = Regex("""<link[^>]+href=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        private val SCRIPT_PATTERN = Regex("""<script[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        private val IMG_PATTERN = Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)

        // CSS patterns (optional - for parsing CSS files)
        private val CSS_IMPORT_PATTERN = Regex("""@import\s+(?:url\()?["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        private val CSS_URL_PATTERN = Regex("""url\(["']?([^"')]+)["']?\)""", RegexOption.IGNORE_CASE)

        // Patterns to exclude
        private val DATA_URL_PATTERN = Regex("""^data:""", RegexOption.IGNORE_CASE)
        private val BLOB_URL_PATTERN = Regex("""^blob:""", RegexOption.IGNORE_CASE)
        private val JAVASCRIPT_PATTERN = Regex("""^javascript:""", RegexOption.IGNORE_CASE)
    }

    /**
     * Parses HTML content and returns list of absolute resource URLs
     * @param html HTML content to parse
     * @param baseUrl Base URL for resolving relative paths
     * @param parseCss Whether to also parse CSS content (default: false, to avoid complexity)
     * @return List of absolute resource URLs
     */
    fun parseResources(
        html: String,
        baseUrl: String,
        parseCss: Boolean = false
    ): List<String> {
        val startTime = System.currentTimeMillis()
        val resources = mutableSetOf<String>() // Use Set to avoid duplicates

        try {
            // Parse <link> tags (CSS, fonts, etc.)
            LINK_PATTERN.findAll(html).forEach { matchResult ->
                val url = matchResult.groupValues[1]
                resolveUrl(url, baseUrl)?.let { resources.add(it) }
            }

            // Parse <script> tags
            SCRIPT_PATTERN.findAll(html).forEach { matchResult ->
                val url = matchResult.groupValues[1]
                resolveUrl(url, baseUrl)?.let { resources.add(it) }
            }

            // Parse <img> tags
            IMG_PATTERN.findAll(html).forEach { matchResult ->
                val url = matchResult.groupValues[1]
                resolveUrl(url, baseUrl)?.let { resources.add(it) }
            }

            // Optionally parse CSS content (basic support)
            if (parseCss) {
                // Look for inline CSS in <style> tags
                val stylePattern = Regex(
                    """<style[^>]*>(.*?)</style>""",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                )
                stylePattern.findAll(html).forEach { matchResult ->
                    val cssContent = matchResult.groupValues[1]
                    parseCssUrls(cssContent, baseUrl).forEach { resources.add(it) }
                }
            }

            val duration = System.currentTimeMillis() - startTime
            ApphudLog.log(
                "[HtmlResourceParser] Parsed ${resources.size} resource(s) from HTML in ${duration}ms"
            )

            // Log resource breakdown by type
            val breakdown = resources.groupBy { url ->
                ResponseConverter.detectResourceType(url)
            }.mapValues { it.value.size }

            ApphudLog.log("[HtmlResourceParser] Resource breakdown: $breakdown")

        } catch (e: Exception) {
            ApphudLog.logE("[HtmlResourceParser] Error parsing HTML: ${e.message}")
        }

        return resources.toList()
    }

    /**
     * Parses CSS content for @import and url() references
     * @param css CSS content to parse
     * @param baseUrl Base URL for resolving relative paths
     * @return List of absolute resource URLs
     */
    private fun parseCssUrls(css: String, baseUrl: String): List<String> {
        val resources = mutableSetOf<String>()

        try {
            // Parse @import statements
            CSS_IMPORT_PATTERN.findAll(css).forEach { matchResult ->
                val url = matchResult.groupValues[1]
                resolveUrl(url, baseUrl)?.let { resources.add(it) }
            }

            // Parse url() references (fonts, images, etc.)
            CSS_URL_PATTERN.findAll(css).forEach { matchResult ->
                val url = matchResult.groupValues[1]
                resolveUrl(url, baseUrl)?.let { resources.add(it) }
            }
        } catch (e: Exception) {
            ApphudLog.logE("[HtmlResourceParser] Error parsing CSS: ${e.message}")
        }

        return resources.toList()
    }

    /**
     * Resolves relative URL to absolute URL using base URL
     * @param url URL to resolve (can be relative or absolute)
     * @param baseUrl Base URL to resolve against
     * @return Absolute URL or null if invalid/excluded
     */
    private fun resolveUrl(url: String, baseUrl: String): String? {
        try {
            val trimmedUrl = url.trim()

            // Skip data URLs, blob URLs, and javascript: URLs
            if (DATA_URL_PATTERN.containsMatchIn(trimmedUrl) ||
                BLOB_URL_PATTERN.containsMatchIn(trimmedUrl) ||
                JAVASCRIPT_PATTERN.containsMatchIn(trimmedUrl)) {
                return null
            }

            // Skip empty URLs
            if (trimmedUrl.isEmpty() || trimmedUrl == "#") {
                return null
            }

            // If already absolute, return as-is (after validation)
            if (trimmedUrl.startsWith("http://", ignoreCase = true) ||
                trimmedUrl.startsWith("https://", ignoreCase = true) ||
                trimmedUrl.startsWith("//", ignoreCase = true)) {

                // Handle protocol-relative URLs (//example.com/resource)
                return if (trimmedUrl.startsWith("//")) {
                    val baseUri = URI(baseUrl)
                    "${baseUri.scheme}:$trimmedUrl"
                } else {
                    trimmedUrl
                }
            }

            // Resolve relative URL against base URL
            val baseUri = URI(baseUrl)
            val resolvedUri = baseUri.resolve(trimmedUrl)

            return resolvedUri.toString()
        } catch (e: URISyntaxException) {
            ApphudLog.logE("[HtmlResourceParser] Invalid URL: $url - ${e.message}")
            return null
        } catch (e: Exception) {
            ApphudLog.logE("[HtmlResourceParser] Error resolving URL: $url - ${e.message}")
            return null
        }
    }

    /**
     * Filters resource URLs to only include critical resources
     * (useful for selective preloading)
     * @param urls List of resource URLs
     * @return Filtered list containing only CSS and JS resources
     */
    fun filterCriticalResources(urls: List<String>): List<String> {
        return urls.filter { url ->
            val type = ResponseConverter.detectResourceType(url)
            type == "css" || type == "js"
        }
    }

    /**
     * Filters resource URLs by type
     * @param urls List of resource URLs
     * @param types Set of types to include (e.g., setOf("css", "js", "image"))
     * @return Filtered list
     */
    fun filterResourcesByType(urls: List<String>, types: Set<String>): List<String> {
        return urls.filter { url ->
            val type = ResponseConverter.detectResourceType(url)
            type in types
        }
    }
}

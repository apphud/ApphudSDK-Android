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
     * Internal data class to track which resources came from which HTML tags
     */
    private data class ParsedResource(
        val url: String,
        val tagType: String // "link", "script", "img", "style"
    )

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
        val parsedResources = mutableListOf<ParsedResource>() // Track resources with tags
        val uniqueUrls = mutableSetOf<String>() // Use Set to avoid duplicates

        try {
            ApphudLog.log("[PRELOADER] [HtmlResourceParser] ======================================")
            ApphudLog.log("[PRELOADER] [HtmlResourceParser] Starting HTML parsing for: $baseUrl")

            // Parse <link> tags (CSS, fonts, etc.)
            var linkCount = 0
            LINK_PATTERN.findAll(html).forEach { matchResult ->
                val url = matchResult.groupValues[1]
                resolveUrl(url, baseUrl)?.let { resolvedUrl ->
                    if (uniqueUrls.add(resolvedUrl)) {
                        parsedResources.add(ParsedResource(resolvedUrl, "link"))
                        linkCount++
                        val resourceType = ResponseConverter.detectResourceType(resolvedUrl)
                        ApphudLog.log("[PRELOADER] [HtmlResourceParser]   <link> [${resourceType.uppercase()}] $resolvedUrl")
                    }
                }
            }
            if (linkCount > 0) {
                ApphudLog.log("[PRELOADER] [HtmlResourceParser] Found $linkCount resources from <link> tags")
            }

            // Parse <script> tags
            var scriptCount = 0
            SCRIPT_PATTERN.findAll(html).forEach { matchResult ->
                val url = matchResult.groupValues[1]
                resolveUrl(url, baseUrl)?.let { resolvedUrl ->
                    if (uniqueUrls.add(resolvedUrl)) {
                        parsedResources.add(ParsedResource(resolvedUrl, "script"))
                        scriptCount++
                        ApphudLog.log("[PRELOADER] [HtmlResourceParser]   <script> [JS] $resolvedUrl")
                    }
                }
            }
            if (scriptCount > 0) {
                ApphudLog.log("[PRELOADER] [HtmlResourceParser] Found $scriptCount resources from <script> tags")
            }

            // Parse <img> tags
            var imgCount = 0
            IMG_PATTERN.findAll(html).forEach { matchResult ->
                val url = matchResult.groupValues[1]
                resolveUrl(url, baseUrl)?.let { resolvedUrl ->
                    if (uniqueUrls.add(resolvedUrl)) {
                        parsedResources.add(ParsedResource(resolvedUrl, "img"))
                        imgCount++
                        ApphudLog.log("[PRELOADER] [HtmlResourceParser]   <img> [IMAGE] $resolvedUrl")
                    }
                }
            }
            if (imgCount > 0) {
                ApphudLog.log("[PRELOADER] [HtmlResourceParser] Found $imgCount resources from <img> tags")
            }

            // Optionally parse CSS content (basic support)
            if (parseCss) {
                var styleCount = 0
                // Look for inline CSS in <style> tags
                val stylePattern = Regex(
                    """<style[^>]*>(.*?)</style>""",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                )
                stylePattern.findAll(html).forEach { matchResult ->
                    val cssContent = matchResult.groupValues[1]
                    parseCssUrls(cssContent, baseUrl).forEach { resolvedUrl ->
                        if (uniqueUrls.add(resolvedUrl)) {
                            parsedResources.add(ParsedResource(resolvedUrl, "style"))
                            styleCount++
                            val resourceType = ResponseConverter.detectResourceType(resolvedUrl)
                            ApphudLog.log("[PRELOADER] [HtmlResourceParser]   <style> [${resourceType.uppercase()}] $resolvedUrl")
                        }
                    }
                }
                if (styleCount > 0) {
                    ApphudLog.log("[PRELOADER] [HtmlResourceParser] Found $styleCount resources from <style> tags")
                }
            }

            val duration = System.currentTimeMillis() - startTime

            // Log resource breakdown by tag type
            val tagBreakdown = parsedResources.groupBy { it.tagType }.mapValues { it.value.size }
            ApphudLog.log("[PRELOADER] [HtmlResourceParser] Resources by tag: $tagBreakdown")

            // Log resource breakdown by resource type (css, js, image, font, etc.)
            val resourceTypeBreakdown = parsedResources.groupBy { resource ->
                ResponseConverter.detectResourceType(resource.url)
            }.mapValues { it.value.size }
            ApphudLog.log("[PRELOADER] [HtmlResourceParser] Resources by type: $resourceTypeBreakdown")

            ApphudLog.log(
                "[PRELOADER] [HtmlResourceParser] Total: ${uniqueUrls.size} unique resource(s) parsed in ${duration}ms"
            )
            ApphudLog.log("[PRELOADER] [HtmlResourceParser] ======================================")

        } catch (e: Exception) {
            ApphudLog.logE("[PRELOADER] [HtmlResourceParser] Error parsing HTML: ${e.message}")
        }

        return uniqueUrls.toList()
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
            ApphudLog.logE("[PRELOADER] [HtmlResourceParser] Error parsing CSS: ${e.message}")
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
            ApphudLog.logE("[PRELOADER] [HtmlResourceParser] Invalid URL: $url - ${e.message}")
            return null
        } catch (e: Exception) {
            ApphudLog.logE("[PRELOADER] [HtmlResourceParser] Error resolving URL: $url - ${e.message}")
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

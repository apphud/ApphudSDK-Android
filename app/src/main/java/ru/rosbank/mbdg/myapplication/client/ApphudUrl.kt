package ru.rosbank.mbdg.myapplication.client

import java.net.URL

class ApphudUrl private constructor(val url: String) {

    companion object {
        private const val PREFIX = "?"
        private const val SEPARATOR = "&"
    }

    class Builder {
        var host: String? = null
            private set
        var version: String? = null
            private set
        var path: String? = null
            private set
        var params: Map<String, String> = emptyMap()
            private set

        fun host(host: String) = apply { this.host = host }
        fun version(version: String) = apply { this.version = version }
        fun path(path: String) = apply { this.path = path }
        fun params(params: Map<String, String>) = apply { this.params = params }

        private fun buildUrl() = buildString {
            append(host)
            append("/")
            append(version)
            append("/")
            append(path)
            append(params.toList().joinToString(prefix = PREFIX, separator = SEPARATOR) { pair ->
                "${pair.first}=${pair.second}"
            })
        }

        fun build() = ApphudUrl(buildUrl())
    }
}
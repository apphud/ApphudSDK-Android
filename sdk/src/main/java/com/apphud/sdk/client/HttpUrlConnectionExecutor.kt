package com.apphud.sdk.client

import com.apphud.sdk.ApphudLog
import com.apphud.sdk.isSuccess
import com.apphud.sdk.parser.Parser
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

class HttpUrlConnectionExecutor(
    private val host: String,
    private val version: String,
    private val parser: Parser
) : NetworkExecutor {

    override fun <O> call(config: RequestConfig): O = call(config, null)
    override fun <I, O> call(config: RequestConfig, input: I?): O = try {

        val apphudUrl = ApphudUrl.Builder()
            .host(host)
            .version(version)
            .path(config.path)
            .params(config.queries)
            .build()

        val url = URL(apphudUrl.url)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = config.requestType.name
        //TODO вынести в настройку
        connection.setRequestProperty("Accept", "application/json; utf-8")
        connection.setRequestProperty("Content-Type", "application/json; utf-8")
        connection.setRequestProperty("X-Platform", "android")
        connection.readTimeout = 10_000
        connection.connectTimeout = 10_000

        when (config.requestType) {
            RequestType.GET -> {
                ApphudLog.log("start ${config.requestType} request ${apphudUrl.url} without params")
                config.headers.forEach { entry ->
                    connection.setRequestProperty(entry.key, entry.value)
                }
            }
            else            -> {
                ApphudLog.log("start ${config.requestType} request ${apphudUrl.url} with params:\n ${parser.toJson(input)}")
                input?.let { source ->
                    connection.doOutput = true
                    connection.outputStream.use { stream ->
                        stream.write(parser.toJson(source).toByteArray(Charsets.UTF_8))
                    }
                }
            }
        }

        connection.connect()

        val response = when (connection.isSuccess) {
            true -> {
                val result = buildStringBy(connection.inputStream)
                val o = parser.fromJson<O>(result, config.type)
                ApphudLog.log("finish ${config.requestType} request ${apphudUrl.url} success with response:\n ${parser.toJson(o)}")
                o
            }
            else -> {
                val response = buildStringBy(connection.errorStream)
                ApphudLog.log("finish ${config.requestType} request ${apphudUrl.url} failed with code: ${connection.responseCode} response: $response")
                null
            }
        }

        connection.disconnect()

        response ?: error("failed response")
    } catch (e: Exception) {
        when (e) {
            is UnknownHostException,
            is SocketTimeoutException -> ApphudLog.log("finish with exception ${e.message}")
            else                      -> ApphudLog.log("finish with exception ${e.message}")
        }
        throw e
    }

    private fun buildStringBy(stream: InputStream): String {
        val reader = InputStreamReader(stream, Charsets.UTF_8)
        return BufferedReader(reader).use { buffer ->
            val response = StringBuilder()
            var line: String?
            while (buffer.readLine().also { line = it } != null) {
                response.append(line)
            }
            response.toString()
        }
    }
}
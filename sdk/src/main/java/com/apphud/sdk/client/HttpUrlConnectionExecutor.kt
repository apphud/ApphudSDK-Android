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
        config.headers.forEach { entry ->
            connection.setRequestProperty(entry.key, entry.value)
        }
        connection.readTimeout = 10_000
        connection.connectTimeout = 10_000

        when (config.requestType) {
            RequestType.GET -> ApphudLog.log("start ${config.requestType} request ${apphudUrl.url} without params")
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
                val response = buildStringBy(connection.inputStream)
                ApphudLog.log("finish ${config.requestType} request ${apphudUrl.url} success with response:\n ${buildPrettyPrintedBy(response)}")
                parser.fromJson<O>(response, config.type)
            }
            else -> {
                val response = buildStringBy(connection.errorStream)
                ApphudLog.log("finish ${config.requestType} request ${apphudUrl.url} failed with code: ${connection.responseCode} response: ${buildPrettyPrintedBy(response)}")
                null
            }
        }

        connection.disconnect()

        response ?: exception(connection.responseCode)
    } catch (e: Exception) {
        when (e) {
            is UnknownHostException,
            is SocketTimeoutException -> ApphudLog.log("finish with exception ${e.message}")
            else                      -> ApphudLog.log("finish with exception ${e.message}")
        }
        throw e
    }

    //Функция чтобы сырой ответ с сервера отображался в pretty printed стиле
    private fun buildPrettyPrintedBy(response: String) =
        parser.fromJson<Map<String, Any>>(response, Map::class.java)?.let { value ->
            parser.toJson(value)
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
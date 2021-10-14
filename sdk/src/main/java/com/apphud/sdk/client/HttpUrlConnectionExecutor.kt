package com.apphud.sdk.client

import android.view.View
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.ApphudUtils
import com.apphud.sdk.client.dto.AttributionDto
import com.apphud.sdk.client.dto.DataDto
import com.apphud.sdk.client.dto.ResponseDto
import com.apphud.sdk.isSuccess
import com.apphud.sdk.parser.Parser
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.HttpsURLConnection

class HttpUrlConnectionExecutor(
    private val host: String,
    private val version: String,
    private val parser: Parser
) : NetworkExecutor {

    /**
     * Set value for X-SDK header.
     */
    companion object Shared{
        var X_SDK: String = "android"
    }

    override fun <O> call(config: RequestConfig): O = call(config, null)
    override fun <I, O> call(config: RequestConfig, input: I?): O = try {

        val apphudUrl = ApphudUrl.Builder()
            .host(host)
            .version(version)
            .path(config.path)
            .params(config.queries)
            .build()

        val url = URL(apphudUrl.url)
        val connection = url.openConnection() as HttpsURLConnection
        connection.requestMethod = config.requestType.name
        //TODO move in the setting
        connection.setRequestProperty("Accept", "application/json; utf-8")
        connection.setRequestProperty("Content-Type", "application/json; utf-8")
        connection.setRequestProperty("X-Platform", "android")
        if(X_SDK.isNotEmpty()){
            connection.setRequestProperty("X-SDK", X_SDK)
        }

        config.headers.forEach { entry ->
            connection.setRequestProperty(entry.key, entry.value)
        }
        connection.readTimeout = 10_000
        connection.connectTimeout = 10_000

        when (config.requestType) {
            RequestType.GET -> ApphudLog.logI("start ${config.requestType} request ${apphudUrl.url} without params")
            else -> {
                ApphudLog.logI("start ${config.requestType} request ${apphudUrl.url} with params:\n ${
                    parser.toJson(input)
                }")
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
                val serverAnswer = buildStringBy(connection.inputStream)
                ApphudLog.logI(
                    "finish ${config.requestType} request ${apphudUrl.url} " +
                            "success with response:\n ${buildPrettyPrintedBy(serverAnswer)}")

                if(serverAnswer.isNotEmpty()){
                    parser.fromJson<O>(serverAnswer, config.type)
                }else{
                    //set success response if empty to finish requet
                    val attributionDto = AttributionDto(true)
                    val dataDto = DataDto(attributionDto)
                    val responseDto = ResponseDto(dataDto, null)
                    parser.fromJson<O>(parser.toJson(responseDto), config.type)
                }
            }
            else -> {
                val serverAnswer = buildStringBy(connection.errorStream)
                ApphudLog.logE(
                    message = "finish ${config.requestType} request ${apphudUrl.url} " +
                            "failed with code: ${connection.responseCode} response: ${
                                buildPrettyPrintedBy(serverAnswer)
                            }")
                when (connection.responseCode) {
                    422 -> {
                        parser.fromJson<O>(serverAnswer, config.type)
                    }
                    else -> {
                        null
                    }
                }
            }
        }

        connection.disconnect()

        response?: exception(connection.responseCode)
    } catch (e: Exception) {
        ApphudLog.logI("Throw with exception: $e")
        throw e
    }

    //A function to render the raw response from the server in pretty printed style
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

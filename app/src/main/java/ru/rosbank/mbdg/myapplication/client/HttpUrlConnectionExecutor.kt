package ru.rosbank.mbdg.myapplication.client

import android.util.Log
import ru.rosbank.mbdg.myapplication.isSuccess
import ru.rosbank.mbdg.myapplication.parser.Parser
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class HttpUrlConnectionExecutor(
    private val host: String,
    private val version: String,
    private val parser: Parser
) : NetworkExecutor {

    override fun <O> call(config: RequestConfig): O = call(config, null)
    override fun <I, O> call(config: RequestConfig, input: I?): O {

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
        connection.readTimeout = 30_000
        connection.connectTimeout = 30_000

        when (config.requestType) {
            RequestType.GET -> {
                config.headers.forEach { entry ->
                    connection.setRequestProperty(entry.key, entry.value)
                }
            }
            else            -> {
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
            true -> buildStringBy(connection.inputStream)
            else -> {
                val response = buildStringBy(connection.errorStream)
                Log.e("WOW", "error response: $response")
                Log.e("WOW", "failed code: ${connection.responseCode}")
                null
            }
        }
        connection.disconnect()

        return parser.fromJson<O>(response, config.type) ?: error("Something wrong")
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
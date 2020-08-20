package ru.rosbank.mbdg.myapplication.client

import android.util.Log
import ru.rosbank.mbdg.myapplication.isSuccess
import ru.rosbank.mbdg.myapplication.parser.Parser
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class HttpUrlConnectionExecutor(
    private val url: URL,
    private val parser: Parser
) : NetworkExecutor {

    override fun <O> call(config: RequestConfig): O = TODO("Not yet implemented")

    override fun <I, O> call(config: RequestConfig, input: I): O {

        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = config.requestType.name
        //TODO вынести в настройку
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Content-Type", "application/json; utf-8")

        when (config.requestType) {
            RequestType.GET -> Unit
            else            -> {
                connection.doOutput = true
                connection.outputStream.use { stream ->
                    stream.write(parser.toJson(input).toByteArray(Charsets.UTF_8))
                }
            }
        }

        connection.connect()

        val response = when (connection.isSuccess) {
            true -> {
                val reader = InputStreamReader(connection.inputStream, Charsets.UTF_8)
                BufferedReader(reader).use { buffer ->
                    val response = StringBuilder()
                    var line: String?
                    while (buffer.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    response.toString()
                }
            }
            else -> {
                Log.e("WOW", "failed code: ${connection.responseCode}")
                Log.e("WOW", "failed message: ${connection.responseMessage}")
                null
            }
        }
        connection.disconnect()

        val res = parser.fromJson<O>(response)
        return res ?: error("Something wrong")
    }
}
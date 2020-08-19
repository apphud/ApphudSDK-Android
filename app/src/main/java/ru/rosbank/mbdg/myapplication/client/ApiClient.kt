package ru.rosbank.mbdg.myapplication.client

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import ru.rosbank.mbdg.myapplication.client.dto.ResponseDto
import ru.rosbank.mbdg.myapplication.isSuccess
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.stream.Collectors

object ApiClient {

    const val API_KEY = "app_oBcXz2z9j8spKPL2T7sZwQaQN5Jzme"

    val url = URL("https://api.appfist.com/v1/customers")

    @RequiresApi(Build.VERSION_CODES.N)
    fun registration(body: String) {

        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"

        //add headers
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Content-Type", "application/json; utf-8")
//        connection.doInput = true
        connection.doOutput = true

        connection.outputStream.use { os ->
            os.write(body.toByteArray(Charsets.UTF_8))
        }

//        val writer = OutputStreamWriter(connection.outputStream)
//        writer.write(body)

        Log.e("WOW", "headerFields: ${connection.headerFields}")

        connection.connect()

        when (connection.responseCode) {
            in 200..299 -> {
                val reader = InputStreamReader(connection.inputStream, Charsets.UTF_8)
                val buffer = BufferedReader(reader)
                val separator = System.lineSeparator()
                val result = buffer.lines().collect(Collectors.joining(separator))
                Log.e("WOW", "response: $result")
            }
            else        -> {
                Log.e("WOW", "failed: ${connection.responseCode}")
                Log.e("WOW", "failed: ${connection.responseMessage}")
            }
        }

        connection.disconnect()
    }
}
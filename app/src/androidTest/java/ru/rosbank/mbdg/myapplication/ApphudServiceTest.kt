package ru.rosbank.mbdg.myapplication

import android.util.Log
import com.google.gson.Gson
import org.junit.Before
import org.junit.Test
import ru.rosbank.mbdg.myapplication.client.ApiClient
import ru.rosbank.mbdg.myapplication.client.ApphudService
import ru.rosbank.mbdg.myapplication.client.HttpUrlConnectionExecutor
import ru.rosbank.mbdg.myapplication.parser.GsonParser

class ApphudServiceTest {

    private val userId = "987654321"
    private val deviceId = "123456789"
    private lateinit var service: ApphudService

    @Before
    fun setup() {
        val executor = HttpUrlConnectionExecutor(
            host = ApiClient.host,
            parser = GsonParser(Gson())
        )
        service = ApphudService(executor)
    }

    @Test
    fun registrationTest() {

        val body = mkRegistrationBody(
            apiKey = ApiClient.API_KEY,
            userId = userId,
            deviceId = deviceId
        )
        service.registration(body).data.results
    }

    @Test
    fun productsTest() {
        val products = service.products(ApiClient.API_KEY)
        Log.e("WOW", "load products: ${products.data.results}")
    }
}
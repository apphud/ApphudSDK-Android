package ru.rosbank.mbdg.myapplication

import android.util.Log
import com.google.gson.Gson
import org.junit.Before
import org.junit.Test
import ru.rosbank.mbdg.myapplication.body.*
import ru.rosbank.mbdg.myapplication.client.ApiClient
import ru.rosbank.mbdg.myapplication.client.ApphudService
import ru.rosbank.mbdg.myapplication.client.HttpUrlConnectionExecutor
import ru.rosbank.mbdg.myapplication.parser.GsonParser

class ApphudServiceTest {

//    private val userId = "987654321"
//    private val deviceId = "123456789"

    private val userId = "cleaner_303"
    private val deviceId = "cleaner_303"
    private lateinit var service: ApphudService

    @Before
    fun setup() {
        val executor = HttpUrlConnectionExecutor(
            host = ApiClient.host,
            version = ApphudVersion.V1,
            parser = GsonParser(Gson())
        )
        service = ApphudService(ApiClient.API_KEY, executor)
    }

    @Test
    fun registrationTest() {

        val body = mkRegistrationBody(
            userId = userId,
            deviceId = deviceId
        )
        service.registration(body).data.results
    }

    @Test
    fun productsTest() {
        val products = service.products()
        Log.e("WOW", "load products: ${products.data.results}")
    }

    @Test
    fun adjustTest() {
        val body = AttributionBody(deviceId, adjust_data = AdjustData("key", "value"))
        val response = service.send(body)
        Log.e("WOW", "send attribution result: ${response.data.results}")
    }

    @Test
    fun facebookTest() {
        val body = AttributionBody(deviceId, facebook_data = FacebookData())
        val response = service.send(body)
        Log.e("WOW", "send attribution result: ${response.data.results}")
    }

    @Test
    fun appsflyerTest() {
        val body = AttributionBody(
            deviceId,
            appsflyer_data = AppsflyerData("key", "value"),
            appsflyer_id = "AppsflyerId"
        )
        val response = service.send(body)
        Log.e("WOW", "send attribution result: ${response.data.results}")
    }

    @Test
    fun pushTest() {
        val body = PushBody(
            device_id = deviceId,
            push_token = "This is push token."
        )
        val response = service.send(body)
        Log.e("WOW", "send push result: ${response.data.results}")
    }

    @Test
    fun purchaseTest() {
        val body = PurchaseBody(
            order_id = "test order_id",
            device_id = deviceId,
            product_id = "test product_id",
            purchase_token = "test purchase_token",
            price_currency_code = "RUB",
            price_amount_micros = 111,
            subscription_period = "test subscription_period"
        )
        val response = service.purchase(body)
        Log.e("WOW", "send push result: ${response.data.results}")
    }
}
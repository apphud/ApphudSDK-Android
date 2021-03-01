package com.apphud.sdk

import android.util.Log
import com.apphud.sdk.body.*
import com.apphud.sdk.client.ApiClient
import com.apphud.sdk.client.ApphudService
import com.apphud.sdk.client.HttpUrlConnectionExecutor
import com.apphud.sdk.parser.GsonParser
import com.google.gson.Gson
import org.junit.Before
import org.junit.Test

class ApphudServiceTest {

//    private val userId = "987654321"
//    private val deviceId = "123456789"

    private val userId = "cleaner_303"
    private val deviceId = "cleaner_303"
    private val API_KEY = "app_oBcXz2z9j8spKPL2T7sZwQaQN5Jzme"
    private lateinit var service: ApphudService

    @Before
    fun setup() {
        val executor = HttpUrlConnectionExecutor(
            host = ApiClient.host,
            version = ApphudVersion.V1,
            parser = GsonParser(Gson())
        )
        service = ApphudService(API_KEY, executor)
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
        val body = AttributionBody(
            deviceId,
            adjust_data = mapOf("key" to "value")
        )
        val response = service.send(body)
        Log.e("WOW", "send attribution result: ${response.data.results}")
    }

    @Test
    fun facebookTest() {
        val body = AttributionBody(
            deviceId,
            facebook_data = mapOf("key" to "value")
        )
        val response = service.send(body)
        Log.e("WOW", "send attribution result: ${response.data.results}")
    }

    @Test
    fun appsflyerTest() {
        val body = AttributionBody(
            deviceId,
            appsflyer_data = mapOf("key" to "value"),
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

        val item = PurchaseItemBody(
            order_id = "test order_id",
            product_id = "test product_id",
            purchase_token = "test purchase_token",
            price_currency_code = "RUB",
            price_amount_micros = 111,
            subscription_period = "test subscription_period"
        )
        val body = PurchaseBody(
            device_id = deviceId,
            purchases = listOf(item)
        )
        val response = service.purchase(body)
        Log.e("WOW", "send push result: ${response.data.results}")
    }

    @Test
    fun userPropertiesTest() {
        val body = UserPropertiesBody(
            device_id = deviceId,
            properties = listOf(
                mapOf(
                    "set_once" to true,
                    "kind" to "string",
                    "value" to "user4@example.com",
                    "name" to "\$email"
                ),
                mapOf(
                    "kind" to "integer",
                    "set_once" to false,
                    "name" to "\$age",
                    "value" to 31
                ),
                mapOf(
                    "set_once" to false,
                    "value" to true,
                    "name" to "custom_test_property_1",
                    "kind" to "boolean"
                ),
                mapOf(
                    "set_once" to false,
                    "value" to "gay",
                    "name" to "\$gender",
                    "kind" to "string"
                ),
                mapOf(
                    "name" to "custom_email",
                    "value" to "user2@example.com",
                    "kind" to "string",
                    "set_once" to true
                )
            )
        )
        val response = service.sendUserProperties(body)
        Log.e("WOW", "send user properties result: ${response.data.results}")
    }
}
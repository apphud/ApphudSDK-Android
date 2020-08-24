package ru.rosbank.mbdg.myapplication

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.GsonBuilder
import ru.rosbank.mbdg.myapplication.body.RegistrationBody
import ru.rosbank.mbdg.myapplication.client.ApiClient
import ru.rosbank.mbdg.myapplication.client.ApphudService
import ru.rosbank.mbdg.myapplication.client.HttpUrlConnectionExecutor
import ru.rosbank.mbdg.myapplication.client.NetworkExecutor
import ru.rosbank.mbdg.myapplication.client.dto.ProductDto
import ru.rosbank.mbdg.myapplication.mappers.CustomerMapper
import ru.rosbank.mbdg.myapplication.mappers.SubscriptionMapper
import ru.rosbank.mbdg.myapplication.parser.GsonParser
import ru.rosbank.mbdg.myapplication.parser.Parser
import ru.rosbank.mbdg.myapplication.storage.SharedPreferencesStorage
import ru.rosbank.mbdg.myapplication.storage.Storage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Deprecated("Don't use it")
object Apphud {

    private const val deviceId = "123456789"
    private val parser: Parser = GsonParser(GsonBuilder()
        .serializeNulls()
        .create())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val manager: NetworkExecutor = HttpUrlConnectionExecutor(ApiClient.host, ApphudVersion.V1, parser)
    private val service = ApphudService(ApiClient.API_KEY, manager)
    private val storage: Storage = SharedPreferencesStorage(App.app, parser)
    private val mapper = CustomerMapper(SubscriptionMapper())
    private val handler = Handler(Looper.getMainLooper())

    var onLoaded: ((List<ProductDto>) -> Unit)? = null

    fun start(apiKey: String) = initialization(apiKey, null)
    fun start(apiKey: String, userId: String) = initialization(apiKey, userId)

    private fun initialization(apiKey: String, userId: String?) {

        executor.execute {
            startRegistration(apiKey, userId)
            loadProducts()
        }
    }

    private fun startRegistration(apiKey: String, userId: String?) {
        val body = RegistrationBody(
            locale = "ru_RU",
            sdk_version = "1.0",
            app_version = "1.0.0",
            device_family = "Android",
            platform = "Android",
            device_type = "DEVICE_TYPE",
            os_version = "6.0.1",
            start_app_version = "1.0",
            idfv = "11112222",
            idfa = "22221111",
            user_id = userId,
            device_id = deviceId,
            time_zone = "UTF"
        )
        val response = service.registration(body)
        when {
            response.errors != null -> Log.e("WOW", "Failed registration")
            response.data.results != null -> {
                val result = response.data.results
                val customer = mapper.map(result)
                Log.e("WOW", "customer: $customer")
                storage.customer = customer
                storage.userId = result.user_id
                storage.deviceId = Apphud.deviceId
            }
            else -> Log.e("WOW", "registration full data response: $response")
        }
    }

    private fun loadProducts() {
        val response = service.products()
        when {
            response.errors != null       -> Log.e("WOW", "Failed load products from Apphud")
            response.data.results != null -> handler.post { onLoaded?.invoke(response.data.results) }
            else -> Log.e("WOW", "load products full data response: $response")
        }
    }
}
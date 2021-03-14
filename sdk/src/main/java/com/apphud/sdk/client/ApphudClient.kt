package com.apphud.sdk.client

import com.apphud.sdk.*
import com.apphud.sdk.body.*
import com.apphud.sdk.mappers.AttributionMapper
import com.apphud.sdk.mappers.CustomerMapper
import com.apphud.sdk.mappers.ProductMapper
import com.apphud.sdk.mappers.SubscriptionMapper
import com.apphud.sdk.parser.Parser
import com.apphud.sdk.tasks.*

internal class ApphudClient(apiKey: ApiKey, parser: Parser) {

    //TODO Про эти мапперы класс ApphudClient знать не должен
    private val customerMapper = CustomerMapper(SubscriptionMapper())
    private val productMapper = ProductMapper()
    private val attributionMapper = AttributionMapper()

    private val thread = ThreadsUtils()
    private val executor: NetworkExecutor = HttpUrlConnectionExecutor(ApiClient.host, ApphudVersion.V1, parser)
    private val service = ApphudService(apiKey, executor)

    fun registrationUser(body: RegistrationBody, callback: CustomerCallback) {
        val callable = RegistrationCallable(body, service)
        thread.registration(callable) { response ->
            when (response.data.results) {
                null -> ApphudLog.log("Response success but result is null")
                else -> callback.invoke(customerMapper.map(response.data.results))
            }
        }
    }

    fun allProducts(callback: ProductsCallback) {
        val callable = ProductsCallable(service)
        thread.allProducts(LoopRunnable(callable) { response ->
            when (response.data.results) {
                null -> ApphudLog.log("Response success but result is null")
                else -> callback.invoke(response.data.results.map(productMapper::map))
            }
        })
    }

    fun send(body: AttributionBody, callback: AttributionCallback) {
        val callable = AttributionCallable(body, service)
        thread.execute(LoopRunnable(callable) { response ->
            when (response.data.results) {
                null -> ApphudLog.log("Response success but result is null")
                else -> callback.invoke(attributionMapper.map(response.data.results))
            }
        })
    }

    fun send(body: PushBody, callback: AttributionCallback) {
        val callable = PushCallable(body, service)
        thread.execute(LoopRunnable(callable) { response ->
            when (response.data.results) {
                null -> ApphudLog.log("Response success but result is null")
                else -> callback.invoke(attributionMapper.map(response.data.results))
            }
        })
    }

    fun purchased(body: PurchaseBody, callback: PurchasedCallback) {
        val callable = PurchaseCallable(body, service)
        thread.execute(LoopRunnable(callable) { response ->
            when (response.data.results) {
                null -> ApphudLog.log("Response success but result is null")
                else -> callback.invoke(customerMapper.map(response.data.results))
            }
        })
    }

    fun userProperties(body: UserPropertiesBody, callback: AttributionCallback) {
        val callable = UserPropertiesCallable(body, service)
        thread.execute(LoopRunnable(callable) { response ->
            when (response.data.results) {
                null -> ApphudLog.log("Response success but result is null")
                else -> callback.invoke(attributionMapper.map(response.data.results))
            }
        })
    }
}
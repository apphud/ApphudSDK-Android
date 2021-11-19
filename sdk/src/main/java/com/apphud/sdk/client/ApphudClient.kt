package com.apphud.sdk.client

import com.apphud.sdk.*
import com.apphud.sdk.body.*
import com.apphud.sdk.mappers.*
import com.apphud.sdk.parser.Parser
import com.apphud.sdk.tasks.*

internal class ApphudClient(apiKey: ApiKey, private val parser: Parser) {

    //TODO Про эти мапперы класс ApphudClient знать не должен
    private val productMapper = ProductMapper()
    private val paywallsMapper = PaywallsMapper(parser)
    private val attributionMapper = AttributionMapper()
    private val customerMapper = CustomerMapper(SubscriptionMapper(), paywallsMapper)

    private val thread = ThreadsUtils()
    private val executorV1: NetworkExecutor = HttpUrlConnectionExecutor(ApphudVersion.V1, parser)
    private val serviceV1 = ApphudServiceV1(apiKey, executorV1)

    //Used in getProducts & getPaywalls
    private val executorV2: NetworkExecutor = HttpUrlConnectionExecutor(ApphudVersion.V2, parser)
    private val serviceV2 = ApphudServiceV2(apiKey, executorV2)

    fun registrationUser(body: RegistrationBody, callback: CustomerCallback) {
        val callable = RegistrationCallable(body, serviceV1)
        thread.registration(callable) { response ->
            when (response.data.results) {
                null -> ApphudLog.log("Response success but result is null")
                else -> callback.invoke(customerMapper.map(response.data.results))
            }
        }
    }

    fun allProducts(callback: ProductsCallback) {
        val callable = ProductsCallable(serviceV2)
        thread.allProducts(LoopRunnable(callable) { response ->
            when (response.data.results) {
                null -> ApphudLog.log("Response success but result is null")
                else -> callback.invoke(productMapper.map(response.data.results))
            }
        })
    }

    fun send(body: AttributionBody, callback: AttributionCallback) {
        val callable = AttributionCallable(body, serviceV1)
        thread.execute(LoopRunnable(callable) { response ->
            when (response.data.results) {
                null -> ApphudLog.log("Response success but result is null")
                else -> callback.invoke(attributionMapper.map(response.data.results))
            }
        })
    }

    fun send(body: PushBody, callback: AttributionCallback) {
        val callable = PushCallable(body, serviceV1)
        thread.execute(LoopRunnable(callable) { response ->
            when (response.data.results) {
                null -> ApphudLog.log("Response success but result is null")
                else -> callback.invoke(attributionMapper.map(response.data.results))
            }
        })
    }

    fun purchased(body: PurchaseBody, callback: PurchasedCallback) {
        val callable = PurchaseCallable(body, serviceV1)
        thread.execute(LoopRunnable(callable) { response ->
            when (response.data.results) {
                null -> {
                    ApphudLog.log("Response success but result is null: + ${response.errors.toString()}")
                    val code = if(response.errors?.toString()?.contains("PUB key nor PRIV") == true) 422 else null
                    callback.invoke(null, ApphudError(message = response.errors.toString(), errorCode = code))
                }
                else -> {
                    callback.invoke(customerMapper.map(response.data.results), null)
                }
            }
        })
    }

    fun userProperties(body: UserPropertiesBody, callback: AttributionCallback) {
        val callable = UserPropertiesCallable(body, serviceV1)
        thread.execute(LoopRunnable(callable) { response ->
            when (response.data.results) {
                null -> ApphudLog.log("Response success but result is null")
                else -> callback.invoke(attributionMapper.map(response.data.results))
            }
        })
    }

    fun paywalls(body: DeviceIdBody, callback: PaywallCallback) {
        val callable = PaywallsCallable(body, serviceV2)
        thread.execute(LoopRunnable(callable) { response ->
            when (response.data.results) {
                null -> {
                    ApphudLog.log("Response success but result is null: + ${response.errors.toString()}")
                    callback.invoke(null, ApphudError(message = response.errors.toString()))
                }
                else -> {
                    callback.invoke(paywallsMapper.map(response.data.results), null)
                }
            }
        })
    }

    /**
     * For internal use only
     * */
    fun sendErrorLogs(body: ErrorLogsBody){
        val callable = ErrorLogsCallable(body, serviceV1)
        thread.execute(LoopRunnable(callable) { response ->
            when (response.data.results) {
                null -> { ApphudLog.logI("Error logs was not send") }
                else -> { ApphudLog.logI("Error logs was send successfully") }
            }
        })
    }

    /**
     * Send Paywall Events to Apphud Server
     * */
    fun trackPaywallEvent(body: PaywallEventBody){
        val callable = PaywallEventCallable(body, serviceV1)
        thread.execute(LoopRunnable(callable) { response ->
            when (response.data.results) {
                null -> { ApphudLog.log("Paywall Event log was not send") }
                else -> { ApphudLog.log("Paywall Event log was send successfully") }
            }
        })
    }
}
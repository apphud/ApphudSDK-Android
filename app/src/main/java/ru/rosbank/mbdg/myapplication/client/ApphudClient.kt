package ru.rosbank.mbdg.myapplication.client

import ru.rosbank.mbdg.myapplication.*
import ru.rosbank.mbdg.myapplication.body.AttributionBody
import ru.rosbank.mbdg.myapplication.body.PushBody
import ru.rosbank.mbdg.myapplication.body.RegistrationBody
import ru.rosbank.mbdg.myapplication.mappers.AttributionMapper
import ru.rosbank.mbdg.myapplication.mappers.CustomerMapper
import ru.rosbank.mbdg.myapplication.mappers.ProductMapper
import ru.rosbank.mbdg.myapplication.mappers.SubscriptionMapper
import ru.rosbank.mbdg.myapplication.parser.Parser
import ru.rosbank.mbdg.myapplication.tasks.*
import java.util.concurrent.Executors
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal class ApphudClient(apiKey: ApiKey, parser: Parser) {

    companion object {
        private const val capacity = 10
    }

    //TODO Про эти мапперы класс ApphudClient знать не должен
    private val mapper = CustomerMapper(SubscriptionMapper())
    private val productMapper = ProductMapper()
    private val attributionMapper = AttributionMapper()

    private val executor = HttpUrlConnectionExecutor(ApiClient.host, ApphudVersion.V1, parser)
    private val service = ApphudService(apiKey, executor)

    private val queue = PriorityBlockingQueue<Runnable>(capacity, PriorityComparator())
    private val pool = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        queue,
        Executors.defaultThreadFactory(),
        ThreadPoolExecutor.CallerRunsPolicy()
    )

    fun registrationUser(body: RegistrationBody, callback: CustomerCallback) {
        val callable = RegistrationCallable(body, service)
        pool.execute(LoopRunnable(callable) { response ->
            when (response.data.results) {
                null -> ApphudLog.log("Response success but result is null")
                else -> callback.invoke(mapper.map(response.data.results))
            }
        })
    }

    fun allProducts(callback: ProductsCallback) {
        val callable = ProductsCallable(service)
        pool.execute(LoopRunnable(callable) { response ->
            when (response.data.results) {
                null -> ApphudLog.log("Response success but result is null")
                else -> callback.invoke(response.data.results.map(productMapper::map))
            }
        })
    }

    fun send(body: AttributionBody, callback: AttributionCallback) {
        val callable = AttributionCallable(body, service)
        pool.execute(LoopRunnable(callable) { response ->
            when (response.data.results) {
                null -> ApphudLog.log("Response success but result is null")
                else -> callback.invoke(attributionMapper.map(response.data.results))
            }
        })
    }

    fun send(body: PushBody, callback: AttributionCallback) {
        val callable = PushCallable(body, service)
        pool.execute(LoopRunnable(callable) { response ->
            when (response.data.results) {
                null -> ApphudLog.log("Response success but result is null")
                else -> callback.invoke(attributionMapper.map(response.data.results))
            }
        })
    }
}
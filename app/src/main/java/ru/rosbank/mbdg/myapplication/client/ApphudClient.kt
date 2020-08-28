package ru.rosbank.mbdg.myapplication.client

import android.util.Log
import ru.rosbank.mbdg.myapplication.ApiKey
import ru.rosbank.mbdg.myapplication.ApphudVersion
import ru.rosbank.mbdg.myapplication.body.AttributionBody
import ru.rosbank.mbdg.myapplication.body.PushBody
import ru.rosbank.mbdg.myapplication.body.RegistrationBody
import ru.rosbank.mbdg.myapplication.client.dto.AttributionDto
import ru.rosbank.mbdg.myapplication.client.dto.CustomerDto
import ru.rosbank.mbdg.myapplication.client.dto.ProductDto
import ru.rosbank.mbdg.myapplication.client.dto.ResponseDto
import ru.rosbank.mbdg.myapplication.parser.Parser
import ru.rosbank.mbdg.myapplication.tasks.*
import java.util.concurrent.*

internal class ApphudClient(apiKey: ApiKey, parser: Parser) {

    companion object {
        private const val capacity = 10
    }

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

    fun registrationUser(body: RegistrationBody) {
        val callable = RegistrationCallable(body, service)
        val callback = object : Callback<ResponseDto<CustomerDto>> {
            override fun finish(result: ResponseDto<CustomerDto>) {
                Log.e("WOW", "result $result")
            }
        }
        pool.execute(LoopRunnable(callable, callback))
        pool.execute(LoggingRunnable("Registration"))
    }

    fun allProducts() {
        val callback = object : Callback<ResponseDto<List<ProductDto>>> {
            override fun finish(result: ResponseDto<List<ProductDto>>) {
                Log.e("WOW", "result $result")
            }
        }
        pool.execute(LoopRunnable(ProductsCallable(service), callback))
        pool.execute(LoggingRunnable("Products"))
    }

    fun send(body: AttributionBody) {
        val callable = AttributionCallable(body, service)
        val callback = object : Callback<ResponseDto<AttributionDto>> {
            override fun finish(result: ResponseDto<AttributionDto>) {
                Log.e("WOW", "result $result")
            }
        }
        pool.execute(LoopRunnable(callable, callback))
        pool.execute(LoggingRunnable("Attribution"))
    }

    fun send(body: PushBody) {
        val callable = PushCallable(body, service)
        val callback = object : Callback<ResponseDto<AttributionDto>> {
            override fun finish(result: ResponseDto<AttributionDto>) {
                Log.e("WOW", "result $result")
            }
        }
        pool.execute(LoopRunnable(callable, callback))
        pool.execute(LoggingRunnable("Push"))
    }
}
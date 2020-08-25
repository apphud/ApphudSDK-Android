package ru.rosbank.mbdg.myapplication.client

import android.util.Log
import ru.rosbank.mbdg.myapplication.ApiKey
import ru.rosbank.mbdg.myapplication.ApphudVersion
import ru.rosbank.mbdg.myapplication.body.RegistrationBody
import ru.rosbank.mbdg.myapplication.client.dto.CustomerDto
import ru.rosbank.mbdg.myapplication.client.dto.ResponseDto
import ru.rosbank.mbdg.myapplication.parser.Parser
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

class LoopRunnable : Callable<Unit> {

    var index: Int = 0

    override fun call() {
        val name = Thread.currentThread().name
        while (index < 50) {
            Log.e("WOW", "index: $index $name")
            index++
            Thread.sleep(100)
        }
    }
}

internal class ApphudClient(apiKey: ApiKey, parser: Parser) {

    private val executor = HttpUrlConnectionExecutor(ApiClient.host, ApphudVersion.V1, parser)
    private val service = ApphudService(apiKey, executor)
    private val executors = Executors.newSingleThreadExecutor()

    @Throws(InterruptedException::class, ExecutionException::class)
    fun registrationUser(body: RegistrationBody) =
        executors.submit<ResponseDto<CustomerDto>> { service.registration(body) }.get()?.data?.results
}
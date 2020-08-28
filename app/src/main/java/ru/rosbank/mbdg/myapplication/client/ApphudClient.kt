package ru.rosbank.mbdg.myapplication.client

import android.util.Log
import ru.rosbank.mbdg.myapplication.ApiKey
import ru.rosbank.mbdg.myapplication.ApphudVersion
import ru.rosbank.mbdg.myapplication.body.RegistrationBody
import ru.rosbank.mbdg.myapplication.parser.Parser
import ru.rosbank.mbdg.myapplication.tasks.PriorityComparator
import ru.rosbank.mbdg.myapplication.tasks.PriorityRunnable
import java.util.concurrent.*

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

class OtherRunnable : PriorityRunnable {

    var index: Int = 0

    override val priority: Int = Int.MAX_VALUE
    override fun run() {

        val name = Thread.currentThread().name
        while (index < 50) {
            Log.e("WOW", "index: $index $name")
            index++
            Thread.sleep(100)
        }
    }
}

class RegistrationRunnable(val title: String, val count: Int, val block: ((Boolean) -> Unit)? = null) :
    PriorityRunnable {

    override val priority: Int = Int.MIN_VALUE
    override fun run() {
        val name = Thread.currentThread().name
        Log.e("WOW", "RegistrationRunnable $count $title BEFORE: $name")
        Thread.sleep(2_000)
        Log.e("WOW", "RegistrationRunnable $count $title AFTER: $name")
        when {
            count < 3 -> block?.invoke(false)
            else      -> block?.invoke(true)
        }
    }
}

internal class ApphudClient(apiKey: ApiKey, parser: Parser) {

    private val executor = HttpUrlConnectionExecutor(ApiClient.host, ApphudVersion.V1, parser)
    private val service = ApphudService(apiKey, executor)
    private val executors = Executors.newSingleThreadExecutor()

    private val queue = PriorityBlockingQueue<Runnable>(1, PriorityComparator())
    private val pool = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        queue,
        Executors.defaultThreadFactory(),
        ThreadPoolExecutor.CallerRunsPolicy()
    )

    private var replay: ((Boolean) -> Unit)? = null

    var index = 1

    fun registrationUser(body: RegistrationBody) {
        
        if (replay == null) {
            replay = { success ->
                when (success) {
                    true -> replay == null
                    else -> pool.execute(RegistrationRunnable("SECOND", ++index, block = replay))
                }
            }
        }

//        executors.submit<ResponseDto<CustomerDto>> { service.registration(body) }.get()?.data?.results
//        executors.schedule(LoopRunnable(), 10_000, TimeUnit.MILLISECONDS)

        pool.execute(RegistrationRunnable("FIRST", index, replay))
        pool.execute(OtherRunnable())
    }
}
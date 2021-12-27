package com.apphud.sdk.tasks

import com.apphud.sdk.body.BenchmarkBody
import com.apphud.sdk.client.ApphudServiceV2
import com.apphud.sdk.client.dto.AttributionDto
import com.apphud.sdk.client.dto.ResponseDto

internal class BenchmarkLogsCallable(
    private val body: BenchmarkBody,
    private val serviceV2: ApphudServiceV2
) : PriorityCallable<ResponseDto<AttributionDto>> {
    override val priority: Int = Int.MAX_VALUE
    override fun call(): ResponseDto<AttributionDto> = serviceV2.sendBenchmarkLogs(body)
    private var _counter: Int = LoopRunnable.COUNT
    override var counter: Int
        get() = _counter
        set(value) {
            _counter = value
        }
}

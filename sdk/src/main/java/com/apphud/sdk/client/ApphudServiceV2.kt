package com.apphud.sdk.client

import com.apphud.sdk.ApiKey
import com.apphud.sdk.ApphudInternal
import com.apphud.sdk.body.DeviceIdBody
import com.apphud.sdk.client.dto.ApphudGroupDto
import com.apphud.sdk.client.dto.ApphudPaywallDto
import com.apphud.sdk.client.dto.ResponseDto
import com.google.gson.reflect.TypeToken

/**
 * Сервис для работы с apphud api
 *
 * Пока используется TypeToken из библиотеки Gson. Нужно будет посмотреть как от этого уйти
 */
class ApphudServiceV2(
    private val apiKey: ApiKey,
    private val executor: NetworkExecutor
) {

    companion object {
        private const val API_KEY = "api_key"
        private const val device_id = "device_id"
    }

    /**
     * Получение идентификаторов продуктов
     */
    fun products(): ResponseDto<List<ApphudGroupDto>> =
        executor.call(
            RequestConfig(
                path = "products",
                type = object : TypeToken<ResponseDto<List<ApphudGroupDto>>>(){}.type,
                queries = mapOf(API_KEY to apiKey),
                requestType = RequestType.GET
            )
        )

    /**
     * Receiving Paywalls
     */
    fun getPaywalls(body: DeviceIdBody): ResponseDto<List<ApphudPaywallDto>> =
        executor.call(
            RequestConfig(
                path = "paywall_configs",
                type = object : TypeToken<ResponseDto<List<ApphudPaywallDto>>>(){}.type,
                queries = mapOf(API_KEY to apiKey, device_id to body.device_id),
                requestType = RequestType.GET
            )
        )
}
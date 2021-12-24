package com.apphud.sdk.client

import com.apphud.sdk.ApiKey
import com.apphud.sdk.body.*
import com.apphud.sdk.client.dto.AttributionDto
import com.apphud.sdk.client.dto.CustomerDto
import com.apphud.sdk.client.dto.ResponseDto
import com.google.gson.reflect.TypeToken

/**
 * Сервис для работы с apphud api
 *
 * Пока используется TypeToken из библиотеки Gson. Нужно будет посмотреть как от этого уйти
 */
class ApphudServiceV1(
    private val apiKey: ApiKey,
    private val executor: NetworkExecutor
) {
    /**
     * Регистрация юзера
     */
    fun registration(body: RegistrationBody): ResponseDto<CustomerDto> = executor.call(
        RequestConfig(
            path = "customers",
            type = object : TypeToken<ResponseDto<CustomerDto>>() {}.type,
            apiKey = apiKey,
            requestType = RequestType.POST
        ),
        body
    )

    /**
     * Отправка атрибуции
     */
    fun send(body: AttributionBody): ResponseDto<AttributionDto> =
        executor.call(
            RequestConfig(
                path = "customers/attribution",
                type = object : TypeToken<ResponseDto<AttributionDto>>(){}.type,
                apiKey = apiKey,
                requestType = RequestType.POST
            ),
            body
        )

    /**
     * Отправка пуш токена
     */
    fun send(body: PushBody): ResponseDto<AttributionDto> =
        executor.call(
            RequestConfig(
                path = "customers/push_token",
                type = object : TypeToken<ResponseDto<AttributionDto>>(){}.type,
                apiKey = apiKey,
                requestType = RequestType.PUT
            ),
            body
        )

    /**
     * Отправка данных после успешной покупки
     */
    fun purchase(body: PurchaseBody): ResponseDto<CustomerDto> =
        executor.call(
            RequestConfig(
                path = "subscriptions",
                type = object : TypeToken<ResponseDto<CustomerDto>>() {}.type,
                apiKey = apiKey,
                requestType = RequestType.POST
            ),
            body
        )

    /**
     * Отправка user property
     */
    fun sendUserProperties(body: UserPropertiesBody): ResponseDto<AttributionDto> =
        executor.call(
            RequestConfig(
                path = "customers/properties",
                type = object : TypeToken<ResponseDto<AttributionDto>>(){}.type,
                apiKey = apiKey,
                requestType = RequestType.POST
            ),
            body
        )

    /**
     * Sending Error Logs
     */
    fun sendErrorLogs(body: ErrorLogsBody): ResponseDto<AttributionDto> =
        executor.call(
            RequestConfig(
                path = "logs",
                type = object : TypeToken<ResponseDto<AttributionDto>>(){}.type,
                apiKey = apiKey,
                requestType = RequestType.POST
            ),
            body
        )

    fun sendPaywallEvent(body: PaywallEventBody): ResponseDto<AttributionDto> =
        executor.call(
            RequestConfig(
                path = "events",
                type = object : TypeToken<ResponseDto<AttributionDto>>(){}.type,
                apiKey = apiKey,
                requestType = RequestType.POST
            ),
            body
        )

    fun sendPromotionalRequest(body: GrantPromotionalBody): ResponseDto<CustomerDto> =
        executor.call(
            RequestConfig(
                path = "promotions",
                type = object : TypeToken<ResponseDto<CustomerDto>>(){}.type,
                apiKey = apiKey,
                requestType = RequestType.POST
            ),
            body
        )
}
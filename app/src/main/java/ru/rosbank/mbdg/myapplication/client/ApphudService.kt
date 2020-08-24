package ru.rosbank.mbdg.myapplication.client

import com.google.gson.reflect.TypeToken
import ru.rosbank.mbdg.myapplication.ApiKey
import ru.rosbank.mbdg.myapplication.body.AttributionBody
import ru.rosbank.mbdg.myapplication.body.PushBody
import ru.rosbank.mbdg.myapplication.body.RegistrationBody
import ru.rosbank.mbdg.myapplication.client.dto.AttributionDto
import ru.rosbank.mbdg.myapplication.client.dto.CustomerDto
import ru.rosbank.mbdg.myapplication.client.dto.ProductDto
import ru.rosbank.mbdg.myapplication.client.dto.ResponseDto


/**
 * Сервис для работы с apphud api
 *
 * Пока используется TypeToken из библиотеки Gson. Нужно будет посмотреть как от этого уйти
 */
class ApphudService(
    private val apiKey: ApiKey,
    private val executor: NetworkExecutor
) {

    companion object {
        private const val API_KEY = "api_key"
    }

    /**
     * Регистрация юзера
     */
    fun registration(body: RegistrationBody): ResponseDto<CustomerDto> = executor.call(
        RequestConfig(
            path = "customers",
            type = object : TypeToken<ResponseDto<CustomerDto>>() {}.type,
            queries = mapOf(API_KEY to apiKey),
            requestType = RequestType.POST
        ),
        body
    )

    /**
     * Получение идентификаторов продуктов
     */
    fun products(): ResponseDto<List<ProductDto>> =
        executor.call(
            RequestConfig(
                path = "products",
                type = object : TypeToken<ResponseDto<List<ProductDto>>>(){}.type,
                queries = mapOf(API_KEY to apiKey),
                requestType = RequestType.GET
            )
        )

    /**
     * Отправка атрибуции
     */
    fun send(body: AttributionBody): ResponseDto<AttributionDto> =
        executor.call(
            RequestConfig(
                path = "customers/attribution",
                type = object : TypeToken<ResponseDto<AttributionDto>>(){}.type,
                queries = mapOf(API_KEY to apiKey),
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
                queries = mapOf(API_KEY to apiKey),
                requestType = RequestType.PUT
            ),
            body
        )
}
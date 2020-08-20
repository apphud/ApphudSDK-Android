package ru.rosbank.mbdg.myapplication.client

import com.google.gson.reflect.TypeToken
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
    private val executor: NetworkExecutor
) {

    /**
     * Регистрация юзера
     */
    fun registration(body: RegistrationBody): ResponseDto<CustomerDto> = executor.call(
        RequestConfig(
            path = "/v1/customers",
            type = object : TypeToken<ResponseDto<CustomerDto>>() {}.type,
            requestType = RequestType.POST
        ),
        body
    )

    /**
     * Получение идентификаторов продуктов
     */
    fun products(apiKey: String): ResponseDto<List<ProductDto>> =
        executor.call(
            RequestConfig(
                path = "/v1/products?api_key=$apiKey",
                type = object : TypeToken<ResponseDto<List<ProductDto>>>(){}.type,
                requestType = RequestType.GET
            )
        )

    /**
     * Отправка атрибуции
     */
    fun send(body: AttributionBody): ResponseDto<AttributionDto> =
        executor.call(
            RequestConfig(
                path = "/v1/customers/attribution",
                type = object : TypeToken<ResponseDto<AttributionDto>>(){}.type,
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
                path = "/v1/customers/push_token",
                type = object : TypeToken<ResponseDto<AttributionDto>>(){}.type,
                requestType = RequestType.POST
            ),
            body
        )
}
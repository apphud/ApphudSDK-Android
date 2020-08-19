package ru.rosbank.mbdg.myapplication.client

import ru.rosbank.mbdg.myapplication.body.AttributionBody
import ru.rosbank.mbdg.myapplication.body.PushBody
import ru.rosbank.mbdg.myapplication.client.dto.AttributionDto
import ru.rosbank.mbdg.myapplication.client.dto.CustomerDto
import ru.rosbank.mbdg.myapplication.client.dto.ProductDto
import ru.rosbank.mbdg.myapplication.client.dto.ResponseDto
import ru.rosbank.mbdg.myapplication.body.RegistrationBody

/**
 * Сервис для работы с apphud api
 */
class ApphudService(
    private val executor: NetworkExecutor
) {

    /**
     * Регистрация юзера
     */
    fun registration(body: RegistrationBody): ResponseDto<CustomerDto> =
        executor.call(RequestConfig(path = "/v1/customers", requestType = RequestType.POST), body)

    /**
     * Получение идентификаторов продуктов
     */
    fun products(): ResponseDto<List<ProductDto>> =
        executor.call(RequestConfig(path = "/v1/products", requestType = RequestType.GET))

    /**
     * Отправка атрибуции
     */
    fun send(body: AttributionBody): ResponseDto<AttributionDto> =
        executor.call(RequestConfig(path = "/v1/customers/attribution ", requestType = RequestType.POST), body)

    /**
     * Отправка пуш токена
     */
    fun send(body: PushBody): ResponseDto<AttributionDto> =
        executor.call(RequestConfig(path = "/v1/customers/push_token", requestType = RequestType.POST), body)
}
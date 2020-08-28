package ru.rosbank.mbdg.myapplication.tasks

import ru.rosbank.mbdg.myapplication.body.RegistrationBody
import ru.rosbank.mbdg.myapplication.client.ApphudService
import ru.rosbank.mbdg.myapplication.client.dto.CustomerDto
import ru.rosbank.mbdg.myapplication.client.dto.ResponseDto

class RegistrationCallable(
    private val body: RegistrationBody,
    private val service: ApphudService
) : PriorityCallable<ResponseDto<CustomerDto>> {
    override val priority: Int = Int.MIN_VALUE
    override fun call(): ResponseDto<CustomerDto> = service.registration(body)
}



package ru.rosbank.mbdg.myapplication

import com.google.gson.Gson
import org.junit.Before
import org.junit.Test
import ru.rosbank.mbdg.myapplication.client.ApiClient
import ru.rosbank.mbdg.myapplication.client.ApphudService
import ru.rosbank.mbdg.myapplication.client.HttpUrlConnectionExecutor
import ru.rosbank.mbdg.myapplication.parser.GsonParser

class ApphudServiceTest {

    private lateinit var service: ApphudService

    @Before
    fun before() {
        val executor = HttpUrlConnectionExecutor(
            url = ApiClient.url,
            parser = GsonParser(Gson())
        )
        service = ApphudService(executor)
    }

    @Test
    fun registrationTest() {

        val body = mkRegistrationBody(ApiClient.API_KEY)
        service.registration(body)
    }
}
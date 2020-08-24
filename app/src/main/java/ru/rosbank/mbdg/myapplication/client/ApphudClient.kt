package ru.rosbank.mbdg.myapplication.client

import android.util.Log
import ru.rosbank.mbdg.myapplication.ApiKey
import ru.rosbank.mbdg.myapplication.Apphud
import ru.rosbank.mbdg.myapplication.ApphudInternal
import ru.rosbank.mbdg.myapplication.ApphudVersion
import ru.rosbank.mbdg.myapplication.body.RegistrationBody
import ru.rosbank.mbdg.myapplication.parser.Parser
import java.util.concurrent.Executors

class ApphudClient(apiKey: ApiKey, parser: Parser) {

    private val executor= HttpUrlConnectionExecutor(ApiClient.host, ApphudVersion.V1, parser)
    private val service = ApphudService(apiKey, executor)
    private val executors = Executors.newSingleThreadExecutor()

    fun registrationUser() {
        val body = RegistrationBody(
            locale = "ru_RU",
            sdk_version = "1.0",
            app_version = "1.0.0",
            device_family = "Android",
            platform = "Android",
            device_type = "DEVICE_TYPE",
            os_version = "6.0.1",
            start_app_version = "1.0",
            idfv = "11112222",
            idfa = "22221111",
            user_id = ApphudInternal.userId,
            device_id = ApphudInternal.deviceId,
            time_zone = "UTF"
        )
        executors.execute {
            val response = service.registration(body)
            Log.e("WOW", "results: ${response.data.results}")
        }
    }
}
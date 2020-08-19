package ru.rosbank.mbdg.myapplication

import ru.rosbank.mbdg.myapplication.body.RegistrationBody

fun mkRegistrationBody(apiKey: String) =
    RegistrationBody(
        locale = "ru_RU",
//                    country_iso_code = "RU",
        sdk_version = "1.0",
        app_version = "1.0.0",
        device_family = "Android",
        platform = "Android",
        device_type = "DEVICE_TYPE",
        os_version = "6.0.1",
        start_app_version = "1.0",
        idfv = "11112222",
        idfa = "22221111",
        user_id = "1234567890",
        device_id = "9876543210",
        time_zone = "UTF",
        api_key = apiKey
    )
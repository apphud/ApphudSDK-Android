package com.apphud.sdk

import android.util.Log
import com.apphud.sdk.body.RegistrationBody
import org.json.JSONObject
import org.junit.Test

class RegistrationBodyTest {

    @Test
    fun toJsonTest() {

        val body = RegistrationBody(
            locale = "locale",
            sdk_version = "sdk_version",
            app_version = "app_version",
            device_id = "device_id",
            device_type = "device_type",
            device_family = "device_family",
            platform = "platform",
            os_version = "os_version",
            start_app_version = "start_app_version",
            idfv = null,
            idfa = null,
            user_id = "user_id",
            time_zone = "time_zone"
        )

//        val rr = ResponseDto<RegistrationBody>(
//            data = DataDto(body),
//            errors = emptyList()
//        )
//        val rrr = rr::class.java
//
//        val r = ResponseDto::class.java
        val result = body.toJson()
//
//        val gson = Gson()
//        val response = gson.fromJson(result, rrr)

        //ResponseDto::class.java.typeParameters.first().bounds

        val parserrrr = JSONObject(result)

        Log.e("WOW", "result: $result")
    }
}
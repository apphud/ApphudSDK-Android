package ru.rosbank.mbdg.myapplication

import android.content.Context
import android.net.ConnectivityManager
import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import ru.rosbank.mbdg.myapplication.body.RegistrationBody
import ru.rosbank.mbdg.myapplication.client.ApphudClient
import ru.rosbank.mbdg.myapplication.parser.GsonParser
import ru.rosbank.mbdg.myapplication.parser.Parser
import ru.rosbank.mbdg.myapplication.storage.SharedPreferencesStorage
import ru.rosbank.mbdg.myapplication.storage.Storage
import java.util.*

internal object ApphudInternal {

    private lateinit var parser: Parser
    private lateinit var storage: Storage
    private val handler: Handler = Handler(Looper.getMainLooper())
    private var client: ApphudClient? = null

    internal lateinit var userId: UserId
    internal lateinit var deviceId: DeviceId

    lateinit var context: Context

    internal fun initialize(apiKey: ApiKey, userId: UserId?, deviceId: DeviceId?) {

        initDependencies(apiKey = apiKey)

        this.userId = updateUser(id = userId)
        this.deviceId = updateDevice(id = deviceId)

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
        client?.registrationUser(body)
    }

    private fun updateUser(id: UserId?): UserId {

        val userId = when (id) {
            null -> storage.userId ?: UUID.randomUUID().toString()
            else -> id
        }
        storage.userId = userId

        return userId
    }
    private fun updateDevice(id: DeviceId?): DeviceId {

        val deviceId = when (id) {
            null -> storage.deviceId ?: UUID.randomUUID().toString()
            else -> id
        }
        storage.deviceId = deviceId

        return deviceId
    }

    private fun initDependencies(apiKey: ApiKey) {
        parser = GsonParser(Gson())
        storage = SharedPreferencesStorage(context, parser)
        if (client == null) {
            client = ApphudClient(apiKey, parser)
        }
    }

//    private fun connection() : () -> Boolean = {
//        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
//        manager.activeNetworkInfo != null && manager.activeNetworkInfo?.isConnected ?: false
//        true
//    }

    //private boolean isNetworkConnected() {
    //    ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    //
    //    return cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isConnected();
    //}
}
package ru.rosbank.mbdg.myapplication

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import ru.rosbank.mbdg.myapplication.client.*
import ru.rosbank.mbdg.myapplication.parser.GsonParser
import ru.rosbank.mbdg.myapplication.parser.Parser
import ru.rosbank.mbdg.myapplication.storage.SharedPreferencesStorage
import ru.rosbank.mbdg.myapplication.storage.Storage
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal object ApphudInternal {

    private lateinit var parser: Parser
    private lateinit var storage: Storage
    private lateinit var handler: Handler
    private lateinit var client: ApphudClient

    internal lateinit var userId: UserId
    internal lateinit var deviceId: DeviceId

    lateinit var context: Context

    internal fun initialize(apiKey: ApiKey, userId: UserId?, deviceId: DeviceId?) {

        initDependencies(apiKey = apiKey)

        this.userId = updateUser(id = userId)
        this.deviceId = updateDevice(id = deviceId)

        client.registrationUser()
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
        handler = Handler(Looper.getMainLooper())
        client = ApphudClient(apiKey, parser)
    }
}
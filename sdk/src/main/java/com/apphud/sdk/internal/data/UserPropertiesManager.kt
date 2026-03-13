package com.apphud.sdk.internal.data

import com.apphud.sdk.ApphudError
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.ApphudUserProperty
import com.apphud.sdk.ApphudUserPropertyKey
import com.apphud.sdk.body.UserPropertiesBody
import com.apphud.sdk.internal.util.runCatchingCancellable
import com.apphud.sdk.managers.RequestManager
import com.apphud.sdk.storage.SharedPreferencesStorage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

internal class UserPropertiesManager(
    private val coroutineScope: CoroutineScope,
    private val userRepository: UserRepository,
    private val storage: SharedPreferencesStorage,
    private val awaitUserRegistration: suspend () -> Unit,
) {
    private val pendingUserProperties = ConcurrentHashMap<String, ApphudUserProperty>()

    @Volatile
    private var updateUserPropertiesJob: Job? = null

    @Volatile
    private var setNeedsToUpdateUserProperties: Boolean = false
        set(value) {
            field = value
            if (value) {
                updateUserPropertiesJob?.cancel()
                updateUserPropertiesJob = coroutineScope.launch {
                    runCatchingCancellable {
                        delay(1000L)
                        if (userRepository.getCurrentUser() != null) {
                            updateUserProperties()
                        } else {
                            setNeedsToUpdateUserProperties = true
                        }
                    }.onFailure { error ->
                        ApphudLog.logE("Error in updateUserProperties job: ${error.message}")
                    }
                }
            } else {
                updateUserPropertiesJob?.cancel()
                updateUserPropertiesJob = null
            }
        }

    @Volatile
    internal var isUpdatingProperties = false

    fun setUserProperty(
        key: ApphudUserPropertyKey,
        value: Any?,
        setOnce: Boolean,
        increment: Boolean,
    ) {
        val typeString = getType(value)
        if (typeString == "unknown") {
            val type = value?.let { value::class.java.name } ?: "unknown"
            val message =
                "For key '${key.key}' invalid property type: '$type' for 'value'. Must be one of: [Int, Float, Double, Boolean, String or null]"
            ApphudLog.logE(message)
            return
        }
        if (increment && !(typeString == "integer" || typeString == "float")) {
            val type = value?.let { value::class.java.name } ?: "unknown"
            val message =
                "For key '${key.key}' invalid increment property type: '$type' for 'value'. Must be one of: [Int, Float or Double]"
            ApphudLog.logE(message)
            return
        }

        val property =
            ApphudUserProperty(
                key = key.key,
                value = value,
                increment = increment,
                setOnce = setOnce,
                type = typeString,
            )

        if (!storage.needSendProperty(property)) {
            return
        }

        pendingUserProperties[property.key] = property
        setNeedsToUpdateUserProperties = true
    }

    suspend fun forceFlushUserProperties(force: Boolean): Boolean {
        setNeedsToUpdateUserProperties = false

        if (pendingUserProperties.isEmpty()) {
            return false
        }

        if (isUpdatingProperties && !force) {
            return false
        }
        isUpdatingProperties = true

        try {
            runCatchingCancellable { awaitUserRegistration() }
                .onFailure { error ->
                    ApphudLog.logE("Failed to update user properties: ${error.message}")
                    return false
                }

            val properties = mutableListOf<Map<String, Any?>>()
            val sentPropertiesForSave = mutableListOf<ApphudUserProperty>()

            pendingUserProperties.forEach {
                properties.add(it.value.toJSON()!!)
                if (!it.value.increment && it.value.value != null) {
                    sentPropertiesForSave.add(it.value)
                }
            }

            val body = UserPropertiesBody(
                userRepository.getDeviceId() ?: throw ApphudError("SDK not initialized"),
                properties,
                force
            )

            return withContext(Dispatchers.IO) {
                runCatchingCancellable { RequestManager.postUserProperties(body) }
                    .fold(
                        onSuccess = { userProperties ->
                            if (userProperties.success) {
                                val propertiesInStorage = storage.properties
                                sentPropertiesForSave.forEach {
                                    propertiesInStorage?.put(it.key, it)
                                }
                                storage.properties = propertiesInStorage

                                pendingUserProperties.clear()

                                ApphudLog.logI("User Properties successfully updated.")
                                true
                            } else {
                                ApphudLog.logE("User Properties update failed with errors")
                                false
                            }
                        },
                        onFailure = {
                            ApphudLog.logE("Failed to update user properties: ${it.message}")
                            false
                        }
                    )
            }
        } finally {
            isUpdatingProperties = false
        }
    }

    private suspend fun updateUserProperties() {
        forceFlushUserProperties(false)
    }

    fun flushIfNeeded() {
        if (pendingUserProperties.isNotEmpty() && setNeedsToUpdateUserProperties) {
            coroutineScope.launch {
                updateUserProperties()
            }
        }
    }

    fun clear() {
        if (pendingUserProperties.isNotEmpty()) {
            ApphudLog.log("Clearing ${pendingUserProperties.size} unsent user properties")
        }
        pendingUserProperties.clear()
        setNeedsToUpdateUserProperties = false
    }

    private fun getType(value: Any?): String {
        return when (value) {
            is String -> "string"
            is Boolean -> "boolean"
            is Int -> "integer"
            is Float, is Double -> "float"
            null -> "null"
            else -> "unknown"
        }
    }
}

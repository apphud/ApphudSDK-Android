package com.apphud.sdk

import com.apphud.sdk.ApphudAttributionProvider.ADJUST
import com.apphud.sdk.ApphudAttributionProvider.APPSFLYER
import com.apphud.sdk.ApphudAttributionProvider.BRANCH
import com.apphud.sdk.ApphudAttributionProvider.CUSTOM
import com.apphud.sdk.ApphudAttributionProvider.FACEBOOK
import com.apphud.sdk.ApphudAttributionProvider.FIREBASE
import com.apphud.sdk.ApphudAttributionProvider.SINGULAR
import com.apphud.sdk.ApphudAttributionProvider.TENJIN
import com.apphud.sdk.ApphudAttributionProvider.TIKTOK
import com.apphud.sdk.ApphudAttributionProvider.VOLUUM
import com.apphud.sdk.domain.AdjustInfo
import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.domain.AppsflyerInfo
import com.apphud.sdk.domain.FacebookInfo
import com.apphud.sdk.internal.data.dto.AttributionRequestDto
import com.apphud.sdk.internal.util.runCatchingCancellable
import com.apphud.sdk.managers.RequestManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun ApphudInternal.setAttribution(
    apphudAttributionData: ApphudAttributionData,
    provider: ApphudAttributionProvider,
    identifier: String? = null,
) {
    when (provider) {
        APPSFLYER -> {
            val temporary = storage.appsflyer
            when {
                temporary == null -> Unit
                (temporary.id == identifier) && (temporary.data == apphudAttributionData.rawData) -> {
                    ApphudLog.logI("Already submitted the same AppsFlyer attribution, skipping")
                    return
                }
            }
        }
        FACEBOOK -> {
            val temporary = storage.facebook
            when {
                temporary == null -> Unit
                temporary.data == apphudAttributionData.rawData -> {
                    ApphudLog.logI("Already submitted the same Facebook attribution, skipping")
                    return
                }
            }
        }
        FIREBASE -> {
            if (storage.firebase == identifier) {
                ApphudLog.logI("Already submitted the same Firebase attribution, skipping")
                return
            }
        }
        ADJUST -> {
            val temporary = storage.adjust
            when {
                temporary == null -> Unit
                (temporary.adid == identifier) && (temporary.adjustData == apphudAttributionData.rawData) -> {
                    ApphudLog.logI("Already submitted the same Adjust attribution, skipping")
                    return
                }
            }
        }
        else -> Unit
    }

    val providerIdPair: Pair<String, Any>? = when (provider) {
        FACEBOOK -> identifier?.let { "fb_anon_id" to it }
        FIREBASE -> identifier?.let { "firebase_id" to it }
        APPSFLYER -> identifier?.let { "appsflyer_id" to it }
        ADJUST -> identifier?.let { "adid" to it }
        CUSTOM -> identifier?.let { "identifier" to it }
        VOLUUM -> identifier?.let { "identifier" to it }
        SINGULAR -> identifier?.let { "identifier" to it }
        TENJIN -> identifier?.let { "identifier" to it }
        TIKTOK -> identifier?.let { "identifier" to it }
        BRANCH -> identifier?.let { "identifier" to it }
    }

    coroutineScope.launch {
        runCatchingCancellable {
            awaitUserRegistration()

            val mergedRawData = apphudAttributionData.rawData.toMutableMap().apply {
                providerIdPair?.let { (key, value) -> put(key, value) }
            }

            val requestBody = AttributionRequestDto(
                deviceId = userRepository.getDeviceId() ?: throw ApphudError("deviceId is null"),
                packageName = context.packageName,
                provider = provider.value,
                rawData = mergedRawData,
                attribution = listOf(
                    "ad_network" to apphudAttributionData.adNetwork,
                    "channel" to apphudAttributionData.channel,
                    "campaign" to apphudAttributionData.campaign,
                    "ad_set" to apphudAttributionData.adSet,
                    "creative" to apphudAttributionData.creative,
                    "keyword" to apphudAttributionData.keyword,
                    "custom_1" to apphudAttributionData.custom1,
                    "custom_2" to apphudAttributionData.custom2,
                )
                    .mapNotNull { (key, value) ->
                        value?.let { key to value }
                    }
                    .toMap()
            )
            RequestManager.send(requestBody)
                .onSuccess {
                    withContext(Dispatchers.Main) {
                        when (provider) {
                            APPSFLYER -> {
                                storage.appsflyer = AppsflyerInfo(
                                    id = identifier,
                                    data = apphudAttributionData.rawData,
                                )
                            }

                            FACEBOOK -> {
                                storage.facebook = FacebookInfo(apphudAttributionData.rawData)
                            }

                            FIREBASE -> {
                                storage.firebase = identifier
                            }

                            ADJUST -> {
                                storage.adjust = AdjustInfo(
                                    adid = identifier,
                                    adjustData = apphudAttributionData.rawData,
                                )
                            }

                            CUSTOM,
                            BRANCH,
                            SINGULAR,
                            TENJIN,
                            TIKTOK,
                            VOLUUM,
                                -> Unit
                        }
                    }
                }.onFailure { error ->
                    ApphudLog.logE(message = error.message.orEmpty())
                }
        }.onFailure { error ->
            ApphudLog.logE("Error setting attribution: ${error.message}")
        }
    }
}

internal suspend fun ApphudInternal.tryWebAttribution(
    data: Map<String, Any>,
): Pair<Boolean, ApphudUser?> {
    val userId = (data["aph_user_id"] as? String) ?: (data["apphud_user_id"] as? String)
    val email = (data["email"] as? String) ?: (data["apphud_user_email"] as? String)

    if (userId.isNullOrEmpty() && email.isNullOrEmpty()) {
        return false to userRepository.getCurrentUser()
    }

    runCatchingCancellable { awaitUserRegistration() }

    val user = userRepository.getCurrentUser() ?: return false to null
    val currentUserId = userRepository.getUserId() ?: return false to null
    fromWeb2Web = true

    return when {
        !userId.isNullOrEmpty() -> {
            if (currentUserId == userId) {
                ApphudLog.logI("Already web2web user, skipping")
                true to user
            } else {
                ApphudLog.logI("Trying to attribute from web by User ID: $userId")
                true to updateUserId(userId, web2Web = true)
            }
        }

        !email.isNullOrEmpty() -> {
            ApphudLog.logI("Trying to attribute from web by email: $email")
            true to updateUserId(currentUserId, email = email, web2Web = true)
        }

        else -> false to null
    }
}
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
import com.apphud.sdk.managers.RequestManager
import kotlinx.coroutines.launch

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

    performWhenUserRegistered { error ->
        error?.let {
            ApphudLog.logE(it.message)
        } ?: run {
            coroutineScope.launch(errorHandler) {
                val mergedRawData = apphudAttributionData.rawData.toMutableMap().apply {
                    providerIdPair?.let { (key, value) -> put(key, value) }
                }

                val requestBody = AttributionRequestDto(
                    deviceId = deviceId,
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
                RequestManager.send(requestBody) { _, error ->
                    mainScope.launch {
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
                        error?.let {
                            ApphudLog.logE(message = it.message)
                        }
                    }
                }
            }
        }
    }
}

internal fun ApphudInternal.tryWebAttribution(data: Map<String, Any>, callback: (Boolean, ApphudUser?) -> Unit) {

    val userId = (data["aph_user_id"] as? String) ?: (data["apphud_user_id"] as? String)
    val email = (data["email"] as? String) ?: (data["apphud_user_email"] as? String)

    if (userId.isNullOrEmpty() && email.isNullOrEmpty()) {
        callback.invoke(false, currentUser)
        return
    }

    performWhenUserRegistered { error ->
        currentUser?.let { user ->
            fromWeb2Web = true

            if (!userId.isNullOrEmpty()) {

                if (user.userId == userId) {
                    ApphudLog.logI("Already web2web user, skipping")
                    callback.invoke(true, user)
                    return@let
                }

                ApphudLog.logI("Trying to attribute from web by User ID: $userId")
                updateUserId(userId, web2Web = true) {
                    if (it?.userId == userId) {
                        callback.invoke(true, it)
                    } else {
                        callback.invoke(false, it)
                    }
                }
            } else if (!email.isNullOrEmpty()) {
                ApphudLog.logI("Trying to attribute from web by email: $email")
                updateUserId(user.userId, email = email, web2Web = true) {
                    if (it?.userId == userId) {
                        callback.invoke(true, it)
                    } else {
                        callback.invoke(false, it)
                    }
                }
            }
        } ?: run {
            callback.invoke(false, currentUser)
        }
    }
}
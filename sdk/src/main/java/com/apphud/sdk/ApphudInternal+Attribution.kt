package com.apphud.sdk

import com.apphud.sdk.body.AttributionBody
import com.apphud.sdk.domain.AdjustInfo
import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.domain.AppsflyerInfo
import com.apphud.sdk.domain.FacebookInfo
import com.apphud.sdk.managers.RequestManager
import kotlinx.coroutines.launch

internal fun ApphudInternal.addAttribution(
    provider: ApphudAttributionProvider,
    data: Map<String, Any>? = null,
    identifier: String? = null,
) {
    val body =
        when (provider) {
            ApphudAttributionProvider.adjust ->
                AttributionBody(
                    deviceId = deviceId,
                    adid = identifier,
                    adjustData = data ?: emptyMap(),
                )
            ApphudAttributionProvider.facebook -> {
                val map =
                    mutableMapOf<String, Any>("fb_device" to true)
                        .also { map -> data?.let { map.putAll(it) } }
                        .toMap()
                AttributionBody(
                    deviceId = deviceId,
                    facebookData = map,
                )
            }
            ApphudAttributionProvider.appsFlyer ->
                when (identifier) {
                    null -> null
                    else ->
                        AttributionBody(
                            deviceId = deviceId,
                            appsflyerId = identifier,
                            appsflyerData = data,
                        )
                }
            ApphudAttributionProvider.firebase ->
                when (identifier) {
                    null -> null
                    else ->
                        AttributionBody(
                            deviceId = deviceId,
                            firebaseId = identifier,
                        )
                }
            ApphudAttributionProvider.custom -> {
                AttributionBody(
                    deviceId = deviceId,
                    attributionData = data?.toMutableMap().also { map ->
                        identifier?.let { map?.set("attribution_identifier", it) }
                    }
                )
            }
        }

    when (provider) {
        ApphudAttributionProvider.appsFlyer -> {
            val temporary = storage.appsflyer
            when {
                temporary == null -> Unit
                (temporary.id == body?.appsflyerId) && (temporary.data == body?.appsflyerData) -> {
                    ApphudLog.logI("Already submitted the same AppsFlyer attribution, skipping")
                    return
                }
            }
        }
        ApphudAttributionProvider.facebook -> {
            val temporary = storage.facebook
            when {
                temporary == null -> Unit
                temporary.data == body?.facebookData -> {
                    ApphudLog.logI("Already submitted the same Facebook attribution, skipping")
                    return
                }
            }
        }
        ApphudAttributionProvider.firebase -> {
            if (storage.firebase == body?.firebaseId) {
                ApphudLog.logI("Already submitted the same Firebase attribution, skipping")
                return
            }
        }
        ApphudAttributionProvider.adjust -> {
            val temporary = storage.adjust
            when {
                temporary == null -> Unit
                (temporary.adid == body?.adid) && (temporary.adjustData == body?.adjustData) -> {
                    ApphudLog.logI("Already submitted the same Adjust attribution, skipping")
                    return
                }
            }
        }
        ApphudAttributionProvider.custom -> {
            // do nothing
        }
    }

    performWhenUserRegistered { error ->
        error?.let {
            ApphudLog.logE(it.message)
        } ?: run {
            body?.let {
                coroutineScope.launch(errorHandler) {
                    RequestManager.send(it) { attribution, error ->
                        mainScope.launch {
                            when (provider) {
                                ApphudAttributionProvider.appsFlyer -> {
                                    val temporary = storage.appsflyer
                                    storage.appsflyer =
                                        when {
                                            temporary == null ->
                                                AppsflyerInfo(
                                                    id = body.appsflyerId,
                                                    data = body.appsflyerData,
                                                )

                                            (temporary.id != body.appsflyerId) || (temporary.data != body.appsflyerData) ->
                                                AppsflyerInfo(
                                                    id = body.appsflyerId,
                                                    data = body.appsflyerData,
                                                )

                                            else -> temporary
                                        }
                                }

                                ApphudAttributionProvider.facebook -> {
                                    val temporary = storage.facebook
                                    storage.facebook =
                                        when {
                                            temporary == null -> FacebookInfo(body.facebookData)
                                            temporary.data != body.facebookData -> FacebookInfo(body.facebookData)
                                            else -> temporary
                                        }
                                }

                                ApphudAttributionProvider.firebase -> {
                                    val temporary = storage.firebase
                                    storage.firebase =
                                        when {
                                            temporary == null -> body.firebaseId
                                            temporary != body.firebaseId -> body.firebaseId
                                            else -> temporary
                                        }
                                }

                                ApphudAttributionProvider.adjust -> {
                                    val temporary = storage.adjust
                                    storage.adjust =
                                        when {
                                            temporary == null ->
                                                AdjustInfo(
                                                    adid = body.adid,
                                                    adjustData = body.adjustData,
                                                )

                                            (temporary.adid != body.adid) || (temporary.adjustData != body.adjustData) ->
                                                AdjustInfo(
                                                    adid = body.adid,
                                                    adjustData = body.adjustData,
                                                )

                                            else -> temporary
                                        }
                                }

                                ApphudAttributionProvider.custom -> {
                                    // do nothing
                                }
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
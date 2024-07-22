package com.apphud.sdk

import com.apphud.sdk.body.AttributionBody
import com.apphud.sdk.domain.AdjustInfo
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
                    device_id = deviceId,
                    adid = identifier,
                    adjust_data = data ?: emptyMap(),
                )
            ApphudAttributionProvider.facebook -> {
                val map =
                    mutableMapOf<String, Any>("fb_device" to true)
                        .also { map -> data?.let { map.putAll(it) } }
                        .toMap()
                AttributionBody(
                    device_id = deviceId,
                    facebook_data = map,
                )
            }
            ApphudAttributionProvider.appsFlyer ->
                when (identifier) {
                    null -> null
                    else ->
                        AttributionBody(
                            device_id = deviceId,
                            appsflyer_id = identifier,
                            appsflyer_data = data,
                        )
                }
            ApphudAttributionProvider.firebase ->
                when (identifier) {
                    null -> null
                    else ->
                        AttributionBody(
                            device_id = deviceId,
                            firebase_id = identifier,
                        )
                }
            ApphudAttributionProvider.custom -> {
                AttributionBody(
                    device_id = deviceId,
                    attribution_data = data?.toMutableMap().also { map ->
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
                (temporary.id == body?.appsflyer_id) && (temporary.data == body?.appsflyer_data) -> {
                    ApphudLog.logI("Already submitted the same AppsFlyer attribution, skipping")
                    return
                }
            }
        }
        ApphudAttributionProvider.facebook -> {
            val temporary = storage.facebook
            when {
                temporary == null -> Unit
                temporary.data == body?.facebook_data -> {
                    ApphudLog.logI("Already submitted the same Facebook attribution, skipping")
                    return
                }
            }
        }
        ApphudAttributionProvider.firebase -> {
            if (storage.firebase == body?.firebase_id) {
                ApphudLog.logI("Already submitted the same Firebase attribution, skipping")
                return
            }
        }
        ApphudAttributionProvider.adjust -> {
            val temporary = storage.adjust
            when {
                temporary == null -> Unit
                (temporary.adid == body?.adid) && (temporary.adjust_data == body?.adjust_data) -> {
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
                                                    id = body.appsflyer_id,
                                                    data = body.appsflyer_data,
                                                )

                                            (temporary.id != body.appsflyer_id) || (temporary.data != body.appsflyer_data) ->
                                                AppsflyerInfo(
                                                    id = body.appsflyer_id,
                                                    data = body.appsflyer_data,
                                                )

                                            else -> temporary
                                        }
                                }

                                ApphudAttributionProvider.facebook -> {
                                    val temporary = storage.facebook
                                    storage.facebook =
                                        when {
                                            temporary == null -> FacebookInfo(body.facebook_data)
                                            temporary.data != body.facebook_data -> FacebookInfo(body.facebook_data)
                                            else -> temporary
                                        }
                                }

                                ApphudAttributionProvider.firebase -> {
                                    val temporary = storage.firebase
                                    storage.firebase =
                                        when {
                                            temporary == null -> body.firebase_id
                                            temporary != body.firebase_id -> body.firebase_id
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
                                                    adjust_data = body.adjust_data,
                                                )

                                            (temporary.adid != body.adid) || (temporary.adjust_data != body.adjust_data) ->
                                                AdjustInfo(
                                                    adid = body.adid,
                                                    adjust_data = body.adjust_data,
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

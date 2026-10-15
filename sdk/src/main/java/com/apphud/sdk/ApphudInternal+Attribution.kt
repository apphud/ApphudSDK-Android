package com.apphud.sdk

import com.apphud.sdk.ApphudAttributionProvider.ADJUST
import com.apphud.sdk.ApphudAttributionProvider.APPSFLYER
import com.apphud.sdk.ApphudAttributionProvider.BRANCH
import com.apphud.sdk.ApphudAttributionProvider.GOOGLE
import com.apphud.sdk.ApphudAttributionProvider.CUSTOM
import com.apphud.sdk.ApphudAttributionProvider.FACEBOOK
import com.apphud.sdk.ApphudAttributionProvider.FIREBASE
import com.apphud.sdk.ApphudAttributionProvider.SINGULAR
import com.apphud.sdk.ApphudAttributionProvider.TENJIN
import com.apphud.sdk.ApphudAttributionProvider.TIKTOK
import com.apphud.sdk.ApphudAttributionProvider.VOLUUM
import android.app.Activity
import android.net.Uri
import com.apphud.sdk.domain.AdjustInfo
import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.internal.domain.model.ApphudIdentity
import com.apphud.sdk.domain.AppsflyerInfo
import com.apphud.sdk.domain.FacebookInfo
import com.apphud.sdk.internal.ServiceLocator
import com.apphud.sdk.internal.data.dto.AttributionRequestDto
import com.apphud.sdk.internal.presentation.deeplink.ApphudWebController
import com.apphud.sdk.internal.util.runCatchingCancellable
import com.apphud.sdk.managers.RequestManager
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
        GOOGLE -> identifier?.let { "identifier" to it }
    }

    coroutineScope.launch {
        runCatchingCancellable {
            awaitUserRegistration()

            val mergedRawData = apphudAttributionData.rawData.toMutableMap().apply {
                providerIdPair?.let { (key, value) -> put(key, value) }
            }

            val ids = userRepository.getRequestIds() ?: throw ApphudError("SDK not initialized")
            val requestBody = AttributionRequestDto(
                deviceId = ids.deviceId,
                installationId = ids.installationId,
                anonymousId = ids.anonymousId,
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
                    withContext(dispatchers.main) {
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
                            GOOGLE,
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

internal fun ApphudInternal.handleOpenUri(uri: Uri) {
    coroutineScope.launch {
        val attribution = startDeeplinkAttributionRequest(url = uri.toString(), visitorId = null)
        notifyDeeplinkAttribution(attribution, ApphudDeeplinkAttributionKind.DIRECT, uri)
    }
}

internal fun ApphudInternal.requestDeferredDeeplinkAttribution(activity: Activity) {
    val apiKey = currentApiKey
    if (apiKey == null) {
        ApphudLog.logE("requestDeferredDeeplinkAttribution called before Apphud.start")
        return
    }

    coroutineScope.launch(dispatchers.main) {
        // Version 2 sends its installation id as `device_id`.
        val deviceId = if (isIdentityV1) userRepository.getDeviceId().orEmpty() else userRepository.getIdentity()?.installationId.orEmpty()
        val urlProvider = ServiceLocator.instance.urlProvider
        ApphudWebController().present(
            activity,
            apiKey,
            deviceId,
            urlProvider.connectDomainUrl,
            urlProvider.connectHost,
        ) { visitorId ->
            coroutineScope.launch {
                val attribution = startDeeplinkAttributionRequest(url = null, visitorId = visitorId)
                notifyDeeplinkAttribution(attribution, ApphudDeeplinkAttributionKind.DEFERRED, null)
            }
        }
    }
}

private suspend fun ApphudInternal.startDeeplinkAttributionRequest(
    url: String?,
    visitorId: String?,
): Map<String, Any> {
    return runCatchingCancellable {
        awaitUserRegistration()
        RequestManager.deeplinkAttribution(url = url, visitorId = visitorId)
    }.getOrElse { error ->
        ApphudLog.logE("Error in deeplink attribution: ${error.message}")
        null
    } ?: emptyMap()
}

internal suspend fun ApphudInternal.tryWebAttribution(
    data: Map<String, Any>,
): Pair<Boolean, ApphudUser?> {
    if (isIdentityV1) return tryWebAttributionV1(data)
    val userId = (data["aph_user_id"] as? String) ?: (data["apphud_user_id"] as? String)
    val email = (data["email"] as? String) ?: (data["apphud_user_email"] as? String)

    if (userId.isNullOrEmpty() && email.isNullOrEmpty()) {
        return false to userRepository.getCurrentUser()
    }

    runCatchingCancellable { awaitUserRegistration() }

    val user = userRepository.getCurrentUser() ?: return false to null

    return when {
        !userId.isNullOrEmpty() -> {
            if (userRepository.getIdentifiedUserId() == userId) {
                ApphudLog.logI("Already web2web user, skipping")
                true to user
            } else {
                // The web user's id is known: a plain identify, no from_web2web.
                ApphudLog.logI("Trying to attribute from web by User ID: $userId")
                true to updateUserId(userId)
            }
        }

        !email.isNullOrEmpty() -> {
            ApphudLog.logI("Trying to attribute from web by email: $email")
            true to attributeFromWebByEmail(email)
        }

        else -> false to null
    }
}

/** Identity version 1: the web user as SDK 3.6 attributed it. */
private suspend fun ApphudInternal.tryWebAttributionV1(
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
                true to updateUserIdV1(userId, web2Web = true)
            }
        }

        !email.isNullOrEmpty() -> {
            ApphudLog.logI("Trying to attribute from web by email: $email")
            true to updateUserIdV1(currentUserId, email = email, web2Web = true)
        }

        else -> false to null
    }
}

/**
 * Email only: a registration with the email and the usual ids; the response names the web user, who
 * becomes the SDK's user as an identify would. The anonymous customer merges into it on the next request.
 */
private suspend fun ApphudInternal.attributeFromWebByEmail(email: String): ApphudUser? {
    // In fallback mode the local user is made up: no customer id to compare against.
    val adoptFrom = if (fallbackMode) null else userRepository.getCurrentUser()?.internalId to userRepository.getIdentity()
    val response = registerWebOrIdentified(email = email) ?: return userRepository.getCurrentUser()
    if (adoptFrom != null) {
        webUserToAdopt(
            customerBefore = adoptFrom.first,
            sent = adoptFrom.second,
            current = userRepository.getIdentity(),
            response = response,
        )?.let { applyIdentify(it) }
    }
    return userRepository.getCurrentUser()
}

/**
 * The web user an email-only web-to-web request found: the response is another customer than
 * before. Without a match the backend returns the same customer, possibly with another install's id
 * as its user_id, which is no user to adopt. Only while the ids are those the request was sent with
 * (no logout or identify in between).
 */
internal fun webUserToAdopt(
    customerBefore: String?,
    sent: ApphudIdentity?,
    current: ApphudIdentity?,
    response: ApphudUser,
): String? {
    if (sent == null || sent != current) return null
    val userId = response.userId.takeUnless { it.isEmpty() || it == current.anonymousId } ?: return null
    val customerId = response.internalId?.takeUnless { it.isEmpty() || it == customerBefore } ?: return null
    return userId.takeIf { customerId.isNotEmpty() }
}
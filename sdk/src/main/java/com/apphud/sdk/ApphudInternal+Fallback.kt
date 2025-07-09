package com.apphud.sdk

import android.content.Context
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.domain.FallbackJsonObject
import com.apphud.sdk.internal.ServiceLocator
import com.apphud.sdk.mappers.PaywallsMapperLegacy
import com.apphud.sdk.parser.GsonParser
import com.apphud.sdk.parser.Parser
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import java.io.IOException

private val gson = GsonBuilder().serializeNulls().create()
private val parser: Parser = GsonParser(gson)
private val paywallsMapperLegacy = PaywallsMapperLegacy(parser)
internal var processedFallbackData = false


internal fun ApphudInternal.processFallbackData(callback: PaywallCallback) {
    try {
        if (currentUser == null) {
            val tempUser = ApphudUser(
                userId, "", "", listOf(), listOf(), listOf(),
                listOf(), true,
            )
            kotlinx.coroutines.runBlocking { userRepository.updateUser(tempUser) }
            ApphudLog.log("Fallback: user created: $userId")
        }

        processedFallbackData = true

        // read paywalls from cache
        var ids = getPaywalls().map { it.products?.map { it.productId } ?: listOf() }.flatten()
        if (ids.isEmpty()) {
            // read from json file
            val jsonFileString = getJsonDataFromAsset(context, "apphud_paywalls_fallback.json")
            val gson = Gson()
            val contentType = object : TypeToken<FallbackJsonObject>() {}.type
            val fallbackJson: FallbackJsonObject = gson.fromJson(jsonFileString, contentType)
            val paywallToParse = paywallsMapperLegacy.map(fallbackJson.data.results)
            ids = paywallToParse.map { it.products?.map { it.productId } ?: listOf() }.flatten()
            cachePaywalls(paywallToParse)
        }

        if (ids.isEmpty()) {
            val error = ApphudError("Invalid Paywalls Fallback File")
            mainScope.launch {
                callback(null, error)
            }
            return
        }

        fallbackMode = true
        didRegisterCustomerAtThisLaunch = false
        isRegisteringUser = false
        ApphudLog.log("Fallback: ENABLED")
        coroutineScope.launch {
            val response = fetchDetails(ids, loadingAll = true)
            val error = if (response.first == BillingResponseCode.OK) {
                null
            } else (if (response.first == APPHUD_NO_REQUEST) ApphudError(
                "Paywalls load error",
                errorCode = response.first
            ) else ApphudError("Google Billing error", errorCode = response.first))
            val details = response.second ?: productDetails
            mainScope.launch {
                notifyLoadingCompleted(
                    customerLoaded = currentUser,
                    productDetailsLoaded = details,
                    fromFallback = true,
                    fromCache = true
                )
                callback(getPaywalls(), error)
            }
        }
    } catch (ex: Exception) {
        val error = ApphudError("Fallback Mode Failed: ${ex.message}")
        ApphudLog.logE("Fallback Mode Failed: ${ex.message}")
        mainScope.launch {
            callback(null, error)
        }
    }
}

private fun getJsonDataFromAsset(
    context: Context,
    fileName: String,
): String? {
    val jsonString: String
    try {
        jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
    } catch (ioException: IOException) {
        ioException.printStackTrace()
        return null
    }
    return jsonString
}

internal fun ApphudInternal.disableFallback() {
    if (currentUser?.isTemporary == true) {
        return
    }
    fallbackMode = false
    processedFallbackData = false
    ApphudLog.log("Fallback: DISABLED")
    coroutineScope.launch(errorHandler) {
        // if fallback raised on start, there no product groups, so reload products and details
        if (productGroups.isEmpty()) {
            ApphudLog.log("Fallback: reload products")
            loadProducts()
        }
        if (ServiceLocator.instance.sharedPreferencesStorage.isNeedSync) {
            syncPurchases()
        }
    }
}

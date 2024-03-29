package com.apphud.sdk

import android.content.Context
import android.content.SharedPreferences
import com.apphud.sdk.client.ApiClient
import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.domain.FallbackJsonObject
import com.apphud.sdk.managers.RequestManager
import com.apphud.sdk.mappers.PaywallsMapper
import com.apphud.sdk.parser.GsonParser
import com.apphud.sdk.parser.Parser
import com.apphud.sdk.storage.SharedPreferencesStorage
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import okhttp3.Request
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL

internal fun String.withRemovedScheme(): String {
    return replace("https://", "")
}

private val gson = GsonBuilder().serializeNulls().create()
private val parser: Parser = GsonParser(gson)
private val paywallsMapper = PaywallsMapper(parser)
internal var fallbackHost: String? = null
internal var processedFallbackData = false
internal fun ApphudInternal.processFallbackError(request: Request, isTimeout: Boolean) {
    if ((request.url.encodedPath.endsWith("/customers") ||
        request.url.encodedPath.endsWith("/subscriptions") ||
                request.url.encodedPath.endsWith("/products"))
        && !processedFallbackData) {

        if (fallbackHost?.withRemovedScheme() == request.url.host) {
            processFallbackData()
        } else {
            coroutineScope.launch {
                tryFallbackHost()
                if (fallbackHost == null) {
                    processFallbackData()
                }
            }
        }
    }
}

internal fun tryFallbackHost() {
    val host = RequestManager.fetchFallbackHost()
    host?.let {
        if (isValidUrl(it)) {
            fallbackHost = it
            ApphudInternal.fallbackMode = true
            ApiClient.host = fallbackHost!!
            ApphudLog.logE("Fallback to host $fallbackHost")
            ApphudInternal.isRegisteringUser = false
            ApphudInternal.refreshPaywallsIfNeeded()
        }
    }
}

fun isValidUrl(urlString: String): Boolean {
    return try {
        URL(urlString)
        true
    } catch (e: MalformedURLException) {
        false
    }
}

private fun ApphudInternal.processFallbackData() {
    try {
        if (currentUser == null) {
            currentUser =
                ApphudUser(
                    userId, "", "", listOf(), listOf(), listOf(),
                    listOf(), true,
                )
            ApphudLog.log("Fallback: user created: $userId")
        }

        processedFallbackData = true

        // read paywalls from cache
        var ids = paywalls.map { it.products?.map { it.productId } ?: listOf() }.flatten()
        if (ids.isEmpty()) {
            // read from json file
            val jsonFileString = getJsonDataFromAsset(context, "apphud_paywalls_fallback.json")
            val gson = Gson()
            val contentType = object : TypeToken<FallbackJsonObject>() {}.type
            val fallbackJson: FallbackJsonObject = gson.fromJson(jsonFileString, contentType)
            val paywallToParse = paywallsMapper.map(fallbackJson.data.results)
            ids = paywallToParse.map { it.products?.map { it.productId } ?: listOf() }.flatten()
            cachePaywalls(paywallToParse)
        }

        if (ids.isEmpty()) { return }

        fallbackMode = true
        didRegisterCustomerAtThisLaunch = false
        ApphudLog.log("Fallback: ENABLED")
        coroutineScope.launch {
            fetchDetails(ids)
            mainScope.launch {
                notifyLoadingCompleted(
                    customerLoaded = currentUser,
                    productDetailsLoaded = productDetails,
                    fromFallback = true,
                )
            }
        }
    } catch (ex: Exception) {
        ApphudLog.logE("Fallback Mode Failed: ${ex.message}")
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
    fallbackMode = false
    processedFallbackData = false
    ApphudLog.log("Fallback: DISABLED")
    coroutineScope.launch(errorHandler) {
        if (productGroups.isEmpty()) { // if fallback raised on start, there no product groups, so reload products and details
            ApphudLog.log("Fallback: reload products")
            loadProducts()
        }
    }
    if (storage.isNeedSync) {
        syncPurchases { _, _, _ ->  }
    }
}

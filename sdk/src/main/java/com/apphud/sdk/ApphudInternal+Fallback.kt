package com.apphud.sdk

import android.content.Context
import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.domain.FallbackJsonObject
import com.apphud.sdk.mappers.PaywallsMapper
import com.apphud.sdk.parser.GsonParser
import com.apphud.sdk.parser.Parser
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import okhttp3.Request
import java.io.IOException

private val gson = GsonBuilder().serializeNulls().create()
private val parser: Parser = GsonParser(gson)
private val paywallsMapper = PaywallsMapper(parser)

internal fun ApphudInternal.processFallbackError(request: Request) {
    if (request.url.encodedPath.endsWith("/customers") && !fallbackMode) {
        processFallbackData()
    }
}

private fun ApphudInternal.processFallbackData() {
    coroutineScope.launch(errorHandler) {
        try {
            if (currentUser == null) {
                currentUser =
                    ApphudUser(
                        userId, "", "", listOf(), listOf(), listOf(),
                        listOf(), true,
                    )
                ApphudLog.log("Fallback: user created: $userId")
            }

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

            fallbackMode = true
            didRegisterCustomerAtThisLaunch = false
            ApphudLog.log("Fallback: ENABLED")
            fetchDetails(ids)
            mainScope.launch {
                notifyLoadingCompleted(
                    customerLoaded = currentUser,
                    productDetailsLoaded = productDetails,
                    fromFallback = true,
                )
            }
        } catch (ex: Exception) {
            ApphudLog.logE("Fallback: ${ex.message}")
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
    fallbackMode = false
    ApphudLog.log("Fallback: DISABLED")
    coroutineScope.launch(errorHandler) {
        if (productGroups.isEmpty()) { // if fallback raised on start, there no product groups, so reload products and details
            ApphudLog.log("Fallback: reload products")
            loadProducts()
        }
    }
}

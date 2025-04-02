package com.apphud.sdk.managers

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PackageInfoFlags
import android.os.Build
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import com.apphud.sdk.*
import com.apphud.sdk.body.*
import com.apphud.sdk.client.*
import com.apphud.sdk.domain.*
import com.apphud.sdk.internal.ServiceLocator
import com.apphud.sdk.internal.data.dto.AttributionRequestDto
import com.apphud.sdk.internal.data.dto.GrantPromotionalDto
import com.apphud.sdk.internal.data.dto.PaywallEventDto
import com.apphud.sdk.internal.data.mapper.ProductMapper
import com.apphud.sdk.internal.domain.model.GetProductsParams
import com.apphud.sdk.internal.domain.model.PurchaseContext
import com.apphud.sdk.internal.util.runCatchingCancellable
import com.apphud.sdk.managers.AdvertisingIdManager.AdInfo
import com.apphud.sdk.mappers.*
import com.apphud.sdk.parser.GsonParser
import com.apphud.sdk.parser.Parser
import com.apphud.sdk.storage.SharedPreferencesStorage
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import org.json.JSONException
import org.json.JSONObject
import java.net.SocketTimeoutException
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

internal object RequestManager {
    private const val MUST_REGISTER_ERROR =
        " :You must call the Apphud.start method once when your application starts before calling any other methods."

    val BILLING_VERSION: Int = 5
    val currentUser: ApphudUser?
        get() = ApphudInternal.currentUser

    val gson = GsonBuilder().serializeNulls().create()
    val parser: Parser = GsonParser(gson)

    private val productMapper = ProductMapper()
    private val paywallsMapperLegacy = PaywallsMapperLegacy(parser)
    private val attributionMapper = AttributionMapper()
    private val placementsMapperLegacy = PlacementsMapperLegacy(parser)
    private val customerMapperLegacy =
        CustomerMapperLegacy(SubscriptionMapperLegacy(), paywallsMapperLegacy, placementsMapperLegacy)

    // TODO to be settled
    private var apiKey: String? = null
    lateinit var applicationContext: Context
    internal lateinit var storage: SharedPreferencesStorage
    var previousException: java.lang.Exception? = null
    var retries: Int = 0

    fun setParams(
        applicationContext: Context,
        apiKey: String? = null,
    ) {
        this.applicationContext = applicationContext
        apiKey?.let { this.apiKey = it }
        this.storage = SharedPreferencesStorage
    }

    fun cleanRegistration() {
        apiKey = null
    }

    private fun canPerformRequest(): Boolean {
        return ::applicationContext.isInitialized &&
                apiKey != null
    }

    private fun getOkHttpClient(
        request: Request,
        retry: Boolean = true,
    ): OkHttpClient {
        val retryInterceptor = LegacyHttpRetryInterceptor()
        val legacyHeadersInterceptor = LegacyHeadersInterceptor(apiKey)
        /*val logging = HttpLoggingInterceptor {
            if (parser.isJson(it)) {
                buildPrettyPrintedBy(it)?.let { formattedJsonString ->
                    ApphudLog.logI(formattedJsonString)
                } ?: run {
                    ApphudLog.logI(it)
                }
            } else {
                ApphudLog.logI(it)
            }
        }

        if (BuildConfig.DEBUG) {
            logging.level = HttpLoggingInterceptor.Level.BODY //BODY
        } else {
            logging.level = HttpLoggingInterceptor.Level.NONE
        }*/

        val callTimeout = if (request.method == "POST" && request.url.toString().contains("subscriptions"))
            30 else 0

        val builder =
            OkHttpClient.Builder()
                //.connectTimeout(APPHUD_DEFAULT_HTTP_CONNECT_TIMEOUT, TimeUnit.SECONDS)
                .callTimeout(callTimeout.toLong(), TimeUnit.SECONDS)
        if (retry) builder.addInterceptor(retryInterceptor)
        builder.addInterceptor(ConnectInterceptor())
        builder.addNetworkInterceptor(legacyHeadersInterceptor)
        //builder.addNetworkInterceptor(logging)

        return builder.build()
    }

    private fun logRequestStart(request: Request) {
        try {
            var body: String? = ""
            if (ApphudUtils.httpLogging) {
                request.body?.let {
                    val buffer = Buffer()
                    it.writeTo(buffer)

                    body = buffer.readString(Charset.forName("UTF-8"))
                    body?.let {
                        if (parser.isJson(it)) {
                            body = buildPrettyPrintedBy(it)
                        }
                    }

                    body?.let {
                        if (it.isNotEmpty()) {
                            body = "\n" + it
                        }
                    } ?: {
                        body = ""
                    }
                }
            }
            ApphudLog.logI("Start " + request.method + " request " + request.url + " with params:" + body)
        } catch (ex: Exception) {
            ApphudLog.logE("Failed to log request: " + ex.message)
        }
    }

    private fun logRequestFinish(
        request: Request,
        responseBody: String?,
        responseCode: Int,
    ) {
        var outputBody = ""
        if (ApphudUtils.httpLogging) {
            outputBody = buildPrettyPrintedBy(responseBody ?: "") ?: ""
        }

        ApphudLog.logI(
            "Finished " + request.method + " request " + request.url + " with response: " + responseCode + "\n" + outputBody,
        )
    }

    internal fun checkLock403(
        request: Request,
        response: Response,
    ): Boolean {
        if (response.code == 403 && request.method == "POST" && request.url.encodedPath.endsWith("/customers")) {
            LegacyHeadersInterceptor.isBlocked = true
            ApphudLog.logE("Unable to perform API requests, because your account has been suspended.")
        }

        return LegacyHeadersInterceptor.isBlocked
    }

    suspend fun registrationSync(
        needPaywalls: Boolean,
        isNew: Boolean,
        forceRegistration: Boolean = false,
        userId: UserId? = null,
        email: String? = null,
    ): ApphudUser? =
        suspendCancellableCoroutine { continuation ->
            if (!canPerformRequest()) {
                ApphudLog.logE("registrationSync $MUST_REGISTER_ERROR")
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }

            if (currentUser == null || forceRegistration) {
                registrationLegacy(needPaywalls, isNew, forceRegistration, userId, email) { customer, error ->
                    if (continuation.isActive) {
                        continuation.resume(customer)
                    }
                }
            } else {
                if (continuation.isActive) {
                    continuation.resume(currentUser)
                }
            }
        }

    private suspend fun registration(
        needPaywalls: Boolean,
        isNew: Boolean,
        forceRegistration: Boolean = false,
        userId: UserId? = null,
        email: String? = null,
    ): ApphudUser {
        if (!canPerformRequest()) {
            ApphudLog.logE(::registrationLegacy.name + MUST_REGISTER_ERROR)
            throw ApphudError("SDK not initialized")
        }

        val currentUserLocal = currentUser
        return if (currentUserLocal == null || forceRegistration) {
            val repository = ServiceLocator.instance.remoteRepository


            val getCustomersResult = runBlocking(Dispatchers.IO) {
                repository.getCustomers(needPaywalls, isNew, userId, email)
            }

            getCustomersResult
                .getOrElse { t ->
                    if (t is SocketTimeoutException) ApphudInternal.processFallbackData { _, _ -> }

                    throw if (t is ApphudError) {
                        t
                    } else {
                        ApphudError.from(t)
                    }
                }
        } else {
            currentUserLocal
        }
    }

    @Suppress("LongParameterList")
    @Synchronized
    fun registrationLegacy(
        needPaywalls: Boolean,
        isNew: Boolean,
        forceRegistration: Boolean = false,
        userId: UserId? = null,
        email: String? = null,
        completionHandler: (ApphudUser?, ApphudError?) -> Unit,
    ) {
        if (!canPerformRequest()) {
            ApphudLog.logE(::registrationLegacy.name + MUST_REGISTER_ERROR)
            return
        }

        runBlocking {
            runCatchingCancellable {
                registration(needPaywalls, isNew, forceRegistration, userId, email)
            }
                .onFailure { throwable ->
                    val error = if (throwable is ApphudError) {
                        throwable
                    } else {
                        ApphudError.from(throwable)
                    }
                    completionHandler(null, error)
                }
                .onSuccess {
                    completionHandler(it, null)
                }
        }
    }

    suspend fun allProducts(): List<ApphudGroup> {
        val params = GetProductsParams(
            System.currentTimeMillis().toString(),
            ApphudInternal.deviceId,
            ApphudInternal.userId,
        )
        val repository = ServiceLocator.instance.remoteRepository

        return repository.getProducts(params).getOrThrow()
    }

    internal suspend fun purchased(
        purchaseContext: PurchaseContext,
    ): ApphudUser {
        if (!canPerformRequest()) {
            ApphudLog.logE(::registrationLegacy.name + MUST_REGISTER_ERROR)
            throw ApphudError("SDK not initialized")
        }

        if (currentUser == null) {
            registration(needPaywalls = true, isNew = true)
        }

        val remoteRepository = ServiceLocator.instance.remoteRepository

        return remoteRepository.getPurchased(purchaseContext).getOrThrow()
    }

    suspend fun restorePurchases(
        apphudProduct: ApphudProduct? = null,
        purchaseRecordDetailsSet: List<PurchaseRecordDetails>,
        observerMode: Boolean,
    ): ApphudUser {
        if (!canPerformRequest()) {
            val message = "restorePurchases $MUST_REGISTER_ERROR"
            ApphudLog.logE(message)
            error(message)
        }

        if (currentUser == null) {
            registration(needPaywalls = true, isNew = true)
        }

        val repository = ServiceLocator.instance.remoteRepository
        return repository.restorePurchased(apphudProduct, purchaseRecordDetailsSet, observerMode).getOrThrow()
    }

    internal suspend fun send(
        attributionRequestBody: AttributionRequestDto,
    ): Result<Attribution> {

        if (!canPerformRequest()) {
            ApphudLog.logE(::send.name + MUST_REGISTER_ERROR)
            throw ApphudError("SDK not initialized")
        }

        return runCatchingCancellable {
            val repository = ServiceLocator.instance.remoteRepository

            if (currentUser == null) {
                registration(needPaywalls = true, isNew = true)
            }

            repository.sendAttribution(attributionRequestBody).getOrThrow()
        }
    }

    internal suspend fun postUserProperties(
        userPropertiesBody: UserPropertiesBody,
    ): Attribution {
        if (!canPerformRequest()) {
            ApphudLog.logE(::postUserProperties.name + MUST_REGISTER_ERROR)
            throw ApphudError("SDK not initialized")
        }

        if (currentUser == null) {
            registration(needPaywalls = true, isNew = true)
        }

        val userRemoteRepository = ServiceLocator.instance.userRemoteRepository

        return userRemoteRepository.setUserProperties(userPropertiesBody).getOrThrow()
    }

    fun fetchFallbackHost(): String? {
        val url = "https://apphud.blob.core.windows.net/apphud-gateway/fallback.txt"
        val client = OkHttpClient()

        val request = Request.Builder().url(url).build()
        var response: Response? = null
        try {
            response = client.newCall(request).execute()
        } catch (ex: Exception) {
            ApphudLog.logE("Unable to load fallback host")
        }

        response?.let {
            if (it.isSuccessful) {
                return it.body?.string()
            }
        }

        ApphudLog.logE("Fallback host not available")

        return null
    }

    suspend fun grantPromotional(
        daysCount: Int,
        productId: String?,
        permissionGroup: ApphudGroup?,
    ): Result<ApphudUser> {
        if (!canPerformRequest()) {
            ApphudLog.logE(::grantPromotional.name + MUST_REGISTER_ERROR)
            throw ApphudError("SDK not initialized")
        }

        return runCatchingCancellable {
            if (currentUser == null) {
                registration(needPaywalls = true, isNew = true)
            }

            val repository = ServiceLocator.instance.remoteRepository

            val grantPromotionalDto = GrantPromotionalDto(
                duration = daysCount,
                userId = ApphudInternal.userId,
                deviceId = ApphudInternal.deviceId,
                productId = productId,
                productGroupId = permissionGroup?.id
            )

            repository.grantPromotional(grantPromotionalDto).getOrThrow()
        }
    }

    suspend fun paywallShown(paywall: ApphudPaywall) {
        trackPaywallEvent(
            makePaywallEventBody(
                name = "paywall_shown",
                paywallId = paywall.id,
                placementId = paywall.placementId,
            ),
        )
    }

    suspend fun paywallClosed(paywall: ApphudPaywall) {
        trackPaywallEvent(
            makePaywallEventBody(
                name = "paywall_closed",
                paywallId = paywall.id,
                placementId = paywall.placementId,
            ),
        )
    }

    suspend fun sendPaywallLogs(
        launchedAt: Long,
        count: Int,
        userBenchmark: Double,
        productsBenchmark: Double,
        totalBenchmark: Double,
        error: ApphudError?,
        productsResponseCode: Int,
        success: Boolean,
    ) {
        trackPaywallEvent(
            makePaywallLogsBody(
                launchedAt,
                count,
                userBenchmark,
                productsBenchmark,
                totalBenchmark,
                error,
                productsResponseCode,
                success
            )
        )
    }

    suspend fun paywallCheckoutInitiated(
        paywallId: String?,
        placementId: String?,
        productId: String?,
    ) {
        trackPaywallEvent(
            makePaywallEventBody(
                name = "paywall_checkout_initiated",
                paywallId = paywallId,
                placementId = placementId,
                productId = productId,
            ),
        )
    }

    suspend fun paywallPaymentCancelled(
        paywallId: String?,
        placementId: String?,
        productId: String?,
    ) {
        trackPaywallEvent(
            makePaywallEventBody(
                name = "paywall_payment_cancelled",
                paywallId = paywallId,
                placementId = placementId,
                productId = productId,
            ),
        )
    }

    suspend fun paywallPaymentError(
        paywallId: String?,
        placementId: String?,
        productId: String?,
        errorMessage: String?,
    ) {
        trackPaywallEvent(
            makePaywallEventBody(
                name = "paywall_payment_error",
                paywallId = paywallId,
                placementId = placementId,
                productId = productId,
                errorMessage = errorMessage,
            ),
        )
    }

    private suspend fun trackPaywallEvent(body: PaywallEventDto) {
        if (!canPerformRequest()) {
            ApphudLog.logE(::trackPaywallEvent.name + MUST_REGISTER_ERROR)
            throw ApphudError("SDK not initialized")
        }

        runCatchingCancellable {
            if (currentUser == null) {
                registration(needPaywalls = true, isNew = true)
            }

            val repository = ServiceLocator.instance.remoteRepository

            repository.trackEvent(body).getOrThrow()
            ApphudLog.logI("Paywall Event log was sent successfully")
        }.onFailure { throwable ->
            val error = if (throwable is ApphudError) throwable else ApphudError.from(throwable)
            ApphudLog.logE("Failed to send paywall event: ${error.message}")
        }
    }

    private fun makePaywallEventBody(
        name: String,
        paywallId: String?,
        placementId: String?,
        productId: String? = null,
        errorMessage: String? = null,
    ): PaywallEventDto {
        val properties = mutableMapOf<String, Any>()
        paywallId?.let { properties.put("paywall_id", it) }
        productId?.let { properties.put("product_id", it) }
        placementId?.let { properties.put("placement_id", it) }
        errorMessage?.let { properties.put("error_message", it) }

        return PaywallEventDto(
            name = name,
            userId = ApphudInternal.userId,
            deviceId = ApphudInternal.deviceId,
            environment = if (applicationContext.isDebuggable()) "sandbox" else "production",
            timestamp = System.currentTimeMillis(),
            properties = properties.ifEmpty { null }
        )
    }

    private fun makePaywallLogsBody(
        launchedAt: Long,
        productsCount: Int,
        userLoadTime: Double,
        productsLoadTime: Double,
        totalLoadTime: Double,
        error: ApphudError?,
        productsResponseCode: Int,
        success: Boolean,
    ): PaywallEventDto {
        val properties = mutableMapOf<String, Any>()
        properties["launched_at"] = launchedAt
        properties["total_load_time"] = totalLoadTime
        properties["user_load_time"] = userLoadTime
        properties["products_load_time"] = productsLoadTime
        properties["products_count"] = productsCount
        properties["result"] =
            if (success && productsResponseCode == 0 && productsCount > 0 && error == null) "no_issues" else "has_issues"
        properties["offerings_callback"] = if (success) "no_offerings_error" else "has_offerings_error"
        properties["api_key"] = apiKey ?: ""
        error?.let {
            properties["error_code"] = it.errorCode ?: 0
            properties["error_message"] = it.message
        }
        if (productsResponseCode != 0) {
            properties["billing_error_code"] = productsResponseCode
        }
        if (retries > 0) {
            properties["failed_attempts"] = retries
        }

        return PaywallEventDto(
            name = "paywall_products_loaded",
            userId = ApphudInternal.userId,
            deviceId = ApphudInternal.deviceId,
            environment = if (applicationContext.isDebuggable()) "sandbox" else "production",
            timestamp = System.currentTimeMillis(),
            properties = properties.ifEmpty { null }
        )
    }

    private fun getInstallationDate(): Long? {
        var dateInSecond: Long? = null
        try {
            this.applicationContext.packageManager?.let { manager ->
                dateInSecond =
                    manager.getPackageInfo(this.applicationContext.packageName, 0).firstInstallTime / 1000L
            }
        } catch (ex: Exception) {
            ex.message?.let {
                ApphudLog.logE(it)
            }
        }
        return dateInSecond
    }

    private fun grantPromotionalBody(
        daysCount: Int,
        productId: String? = null,
        permissionGroup: ApphudGroup? = null,
    ): GrantPromotionalDto {
        return GrantPromotionalDto(
            duration = daysCount,
            userId = ApphudInternal.userId,
            deviceId = ApphudInternal.deviceId,
            productId = productId,
            productGroupId = permissionGroup?.id,
        )
    }

    private fun buildPrettyPrintedBy(jsonString: String): String? {
        var jsonObject: JSONObject? = null
        try {
            jsonObject = JSONObject(jsonString)
        } catch (ignored: JSONException) {
        }
        try {
            if (jsonObject != null) {
                return jsonObject.toString(4)
            }
        } catch (ignored: JSONException) {
        }
        return null
    }

    suspend fun fetchAdvertisingId(): String? =
        suspendCancellableCoroutine { continuation ->
            if (hasPermission("com.google.android.gms.permission.AD_ID")) {
                var advId: String? = null
                try {
                    val adInfo: AdInfo = AdvertisingIdManager.getAdvertisingIdInfo(applicationContext)
                    advId = adInfo.id
                } catch (e: java.lang.Exception) {
                    ApphudLog.logE("Finish load advertisingId: $e")
                }

                if (continuation.isActive) {
                    continuation.resume(advId)
                }
            } else {
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
        }

    private fun hasPermission(permission: String): Boolean {
        try {
            var pInfo =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    applicationContext.packageManager.getPackageInfo(
                        ApphudUtils.packageName,
                        PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()),
                    )
                } else {
                    applicationContext.packageManager.getPackageInfo(
                        ApphudUtils.packageName,
                        PackageManager.GET_PERMISSIONS
                    )
                }

            if (pInfo.requestedPermissions != null) {
                for (p in pInfo.requestedPermissions) {
                    if (p == permission) {
                        return true
                    }
                }
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
        return false
    }
}

fun ProductDetails.priceCurrencyCode(): String? {
    val res: String? =
        if (this.productType == BillingClient.ProductType.SUBS) {
            this.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.priceCurrencyCode
        } else {
            this.oneTimePurchaseOfferDetails?.priceCurrencyCode
        }
    return res
}

fun ProductDetails.priceAmountMicros(): Long? {
    if (this.productType == BillingClient.ProductType.SUBS) {
        return null
    } else {
        return this.oneTimePurchaseOfferDetails?.priceAmountMicros
    }
}

fun ProductDetails.subscriptionPeriod(): String? {
    val res: String? =
        if (this.productType == BillingClient.ProductType.SUBS) {
            if (this.subscriptionOfferDetails?.size == 1 && this.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.size == 1) {
                this.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.billingPeriod
            } else {
                null
            }
        } else {
            null
        }
    return res
}

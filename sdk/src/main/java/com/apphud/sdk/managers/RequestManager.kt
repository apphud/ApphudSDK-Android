package com.apphud.sdk.managers

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PackageInfoFlags
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.apphud.sdk.*
import com.apphud.sdk.ApphudInternal.fallbackMode
import com.apphud.sdk.body.*
import com.apphud.sdk.client.*
import com.apphud.sdk.client.dto.*
import com.apphud.sdk.domain.*
import com.apphud.sdk.managers.AdvertisingIdManager.AdInfo
import com.apphud.sdk.mappers.*
import com.apphud.sdk.parser.GsonParser
import com.apphud.sdk.parser.Parser
import com.apphud.sdk.storage.SharedPreferencesStorage
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

object RequestManager {
    private const val MUST_REGISTER_ERROR =
        " :You must call the Apphud.start method once when your application starts before calling any other methods."

    val BILLING_VERSION: Int = 5
    val currentUser: ApphudUser?
        get() = ApphudInternal.currentUser

    val gson = GsonBuilder().serializeNulls().create()
    val parser: Parser = GsonParser(gson)

    private val productMapper = ProductMapper()
    private val paywallsMapper = PaywallsMapper(parser)
    private val attributionMapper = AttributionMapper()
    private val placementsMapper = PlacementsMapper(parser)
    private val customerMapper = CustomerMapper(SubscriptionMapper(), paywallsMapper, placementsMapper)

    // TODO to be settled
    private var apiKey: String? = null
    lateinit var applicationContext: Context
    lateinit var storage: SharedPreferencesStorage

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
        val retryInterceptor = HttpRetryInterceptor()
        val headersInterceptor = HeadersInterceptor(apiKey)
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
            logging.level = HttpLoggingInterceptor.Level.NONE //BODY
        } else {
            logging.level = HttpLoggingInterceptor.Level.NONE
        }*/

        var readTimeout: Long = ApiClient.readTimeout
        if (request.method == "POST" && request.url.toString().contains("subscriptions")) {
            readTimeout = 30L
        }

        val builder =
            OkHttpClient.Builder()
                .readTimeout(readTimeout, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .connectTimeout(10, TimeUnit.SECONDS)
        if (retry) builder.addInterceptor(retryInterceptor)
        builder.addNetworkInterceptor(headersInterceptor)
        // builder.addNetworkInterceptor(logging)

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
            ApphudLog.logE(ex.message ?: "")
        }
    }

    private fun logRequestFinish(
        request: Request,
        responseBody: String?,
        responseCode: Int
    ) {
        var outputBody = ""
        if (ApphudUtils.httpLogging) {
            outputBody = buildPrettyPrintedBy(responseBody ?: "") ?: ""
        }

        ApphudLog.logI(
            "Finished " + request.method + " request " + request.url + " with response: " + responseCode + "\n" + outputBody,
        )
    }

    private fun performRequest(
        client: OkHttpClient,
        request: Request,
        completionHandler: (String?, ApphudError?) -> Unit,
    ) {
        try {
            if (HeadersInterceptor.isBlocked) {
                val message =
                    "Unable to perform API requests, because your account has been suspended."
                ApphudLog.logE(message)
                completionHandler(null, ApphudError(message))
            } else if (true) {
                logRequestStart(request)

                val start = Date()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                val finish = Date()
                val diff = (finish.time - start.time)
                ApphudLog.logBenchmark(
                    request.url.encodedPath,
                    diff,
                )

                logRequestFinish(request, responseBody, response.code)

                if (response.isSuccessful) {
                    responseBody?.let {
                        completionHandler(it, null)
                    } ?: run {
                        completionHandler(null, ApphudError("Request failed", null, response.code))
                    }
                } else {
                    checkLock403(request, response)
                    val message =
                        "finish ${request.method} request ${request.url} " +
                            "failed with code: ${response.code} response: ${
                                buildPrettyPrintedBy(responseBody.toString())
                            }"
                    completionHandler(null, ApphudError(message, null, response.code))
                }

                response.close()

            } else {
                val message = "No Internet connection"
                ApphudLog.logE(message)
                completionHandler(null, ApphudError(message))
            }
        } catch (e: SocketTimeoutException) {
            ApphudInternal.processFallbackError(request)
            val message = e.message ?: "Undefined error"
            completionHandler(null, ApphudError(message, null, APPHUD_ERROR_TIMEOUT))
        } catch (e: IOException) {
            val message = e.message ?: "Undefined error"
            completionHandler(null, ApphudError(message))
        }
    }

    @Throws(Exception::class)
    fun performRequestSync(
        client: OkHttpClient,
        request: Request,
    ): String {
        if (HeadersInterceptor.isBlocked) {
            val message = "SDK networking is locked until application restart"
            ApphudLog.logE(message)
            throw Exception(message)
        } else if (true) {
            logRequestStart(request)

            val start = Date()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            val finish = Date()
            val diff = (finish.time - start.time)
            ApphudLog.logBenchmark(
                request.url.encodedPath,
                diff,
            )

            logRequestFinish(request, responseBody, response.code)

            response.close()

            if (response.isSuccessful) {
                return responseBody ?: ""
            } else {
                checkLock403(request, response)
                val message =
                    "finish ${request.method} request ${request.url} " +
                        "failed with code: ${response.code} response: ${
                            buildPrettyPrintedBy(responseBody ?: "")
                        }"
                ApphudLog.logE(message)
                throw Exception(message)
            }
        } else {
            val message = "No Internet connection"
            ApphudLog.logE(message)
            throw Exception(message)
        }
    }

    internal fun checkLock403(
        request: Request,
        response: Response,
    ): Boolean {
        if (response.code == 403 && request.method == "POST" && request.url.encodedPath.endsWith("/customers")) {
            HeadersInterceptor.isBlocked = true
            ApphudLog.logE("Unable to perform API requests, because your account has been suspended.")
        }

        return HeadersInterceptor.isBlocked
    }

    private fun makeRequest(
        request: Request,
        retry: Boolean = true,
        completionHandler: (String?, ApphudError?) -> Unit,
    ) {
        val httpClient = getOkHttpClient(request, retry)
        performRequest(httpClient, request, completionHandler)
    }

    private fun makeUserRegisteredRequest(
        request: Request,
        retry: Boolean = true,
        completionHandler: (String?, ApphudError?) -> Unit,
    ) {
        val httpClient = getOkHttpClient(request, retry)
        if (currentUser == null) {
            registration(true, true) { customer, error ->
                customer?.let {
                    performRequest(httpClient, request, completionHandler)
                } ?: run {
                    completionHandler(null, error)
                }
            }
        } else {
            performRequest(httpClient, request, completionHandler)
        }
    }

    private fun buildPostRequest(
        url: URL,
        params: Any,
    ): Request {
        val json = parser.toJson(params)
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = json.toRequestBody(mediaType)

        val request = Request.Builder()
        return request.url(url)
            .post(requestBody)
            .build()
    }

    private fun buildGetRequest(url: URL): Request {
        val request = Request.Builder()
        return request.url(url)
            .get()
            .build()
    }

    suspend fun registrationSync(
        needPaywalls: Boolean,
        isNew: Boolean,
        forceRegistration: Boolean = false,
    ): ApphudUser? =
        suspendCancellableCoroutine { continuation ->
            if (!canPerformRequest()) {
                ApphudLog.logE("registrationSync $MUST_REGISTER_ERROR")
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }

            if (currentUser == null || forceRegistration) {
                registration(needPaywalls, isNew, forceRegistration) { customer, error ->
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

    @Synchronized
    fun registration(
        needPaywalls: Boolean,
        isNew: Boolean,
        forceRegistration: Boolean = false,
        completionHandler: (ApphudUser?, ApphudError?) -> Unit,
    ) {
        if (!canPerformRequest()) {
            ApphudLog.logE(::registration.name + MUST_REGISTER_ERROR)
            return
        }

        if (currentUser == null || forceRegistration) {
            val apphudUrl =
                ApphudUrl.Builder()
                    .host(HeadersInterceptor.HOST)
                    .version(ApphudVersion.V1)
                    .path("customers")
                    .build()

            val request = buildPostRequest(URL(apphudUrl.url), mkRegistrationBody(needPaywalls, isNew))
            val httpClient = getOkHttpClient(request, !fallbackMode)
            try {
                val serverResponse = performRequestSync(httpClient, request)
                val responseDto: ResponseDto<CustomerDto>? =
                    parser.fromJson<ResponseDto<CustomerDto>>(
                        serverResponse,
                        object : TypeToken<ResponseDto<CustomerDto>>() {}.type,
                    )

                responseDto?.let { cDto ->
                    val currentUser =
                        cDto.data.results?.let { customerObj ->
                            customerMapper.map(customerObj)
                        }
                    completionHandler(currentUser, null)
                } ?: run {
                    completionHandler(null, ApphudError("Registration failed"))
                }
            } catch (e: SocketTimeoutException) {
                ApphudInternal.processFallbackError(request)
                val message = e.message ?: "Registration failed"
                completionHandler(null, ApphudError(message, null, APPHUD_ERROR_TIMEOUT))
            } catch (ex: UnknownHostException) {
                val message = ex.message ?: "Registration failed"
                completionHandler(null, ApphudError(message, null, APPHUD_ERROR_NO_INTERNET))
            } catch (ex: Exception) {
                val message = ex.message ?: "Registration failed"
                completionHandler(null, ApphudError(message))
            }
        } else {
            completionHandler(currentUser, null)
        }
    }

    suspend fun allProducts(): List<ApphudGroup>? =
        suspendCancellableCoroutine { continuation ->
            val apphudUrl =
                ApphudUrl.Builder()
                    .host(HeadersInterceptor.HOST)
                    .version(ApphudVersion.V2)
                    .path("products")
                    .build()

            val request = buildGetRequest(URL(apphudUrl.url))

            makeRequest(request) { serverResponse, error ->
                serverResponse?.let {
                    val responseDto: ResponseDto<List<ApphudGroupDto>>? =
                        parser.fromJson<ResponseDto<List<ApphudGroupDto>>>(serverResponse, object : TypeToken<ResponseDto<List<ApphudGroupDto>>>() {}.type)
                    responseDto?.let { response ->
                        val productsList = response.data.results?.let { it1 -> productMapper.map(it1) }
                        if (continuation.isActive) {
                            continuation.resume(productsList)
                        }
                    } ?: run {
                        ApphudLog.logE("Failed to load products")
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }
                } ?: run {
                    if (error != null) {
                        ApphudLog.logE(error.message)
                    }
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
            }
        }

    fun purchased(
        purchase: Purchase,
        apphudProduct: ApphudProduct?,
        offerToken: String?,
        oldToken: String?,
        completionHandler: (ApphudUser?, ApphudError?) -> Unit,
    ) {
        if (!canPerformRequest()) {
            ApphudLog.logE(::purchased.name + MUST_REGISTER_ERROR)
            return
        }

        val apphudUrl =
            ApphudUrl.Builder()
                .host(HeadersInterceptor.HOST)
                .version(ApphudVersion.V1)
                .path("subscriptions")
                .build()

        val purchaseBody =
            apphudProduct?.let {
                makePurchaseBody(purchase, it.productDetails, it.paywallId, it.placementId, it.id, offerToken, oldToken)
            }
        if (purchaseBody == null) {
            val message =
                "ProductsDetails and ApphudProduct can not be null at the same time"
            ApphudLog.logE(message = message)
            completionHandler.invoke(null, ApphudError(message))
            return
        }

        val request = buildPostRequest(URL(apphudUrl.url), purchaseBody)

        makeUserRegisteredRequest(request, !fallbackMode) { serverResponse, error ->
            serverResponse?.let {
                val responseDto: ResponseDto<CustomerDto>? =
                    parser.fromJson<ResponseDto<CustomerDto>>(
                        serverResponse,
                        object : TypeToken<ResponseDto<CustomerDto>>() {}.type,
                    )
                responseDto?.let { cDto ->
                    val currentUser =
                        cDto.data.results?.let { customerObj ->
                            customerMapper.map(customerObj)
                        }
                    completionHandler(currentUser, null)
                } ?: run {
                    completionHandler(null, ApphudError("Purchase failed"))
                }
            } ?: run {
                completionHandler(null, error)
            }
        }
    }

    suspend fun restorePurchasesSync(
        apphudProduct: ApphudProduct? = null,
        purchaseRecordDetailsSet: List<PurchaseRecordDetails>?,
        purchase: Purchase?,
        productDetails: ProductDetails?,
        offerIdToken: String?,
        observerMode: Boolean,
    ): ApphudUser? =
        suspendCancellableCoroutine { continuation ->
            if (!canPerformRequest()) {
                ApphudLog.logE("restorePurchasesSync $MUST_REGISTER_ERROR")
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }

            val apphudUrl =
                ApphudUrl.Builder()
                    .host(HeadersInterceptor.HOST)
                    .version(ApphudVersion.V1)
                    .path("subscriptions")
                    .build()

            val purchaseBody =
                if (purchaseRecordDetailsSet != null) {
                    makeRestorePurchasesBody(
                        apphudProduct,
                        purchaseRecordDetailsSet,
                        observerMode,
                    )
                } else if (purchase != null && productDetails != null) {
                    makeTrackPurchasesBody(
                        apphudProduct,
                        purchase,
                        productDetails,
                        offerIdToken,
                        observerMode,
                    )
                } else {
                    null
                }

            purchaseBody?.let {
                val request = buildPostRequest(URL(apphudUrl.url), it)
                makeUserRegisteredRequest(request, !fallbackMode) { serverResponse, _ ->
                    serverResponse?.let {
                        val responseDto: ResponseDto<CustomerDto>? =
                            parser.fromJson<ResponseDto<CustomerDto>>(
                                serverResponse,
                                object : TypeToken<ResponseDto<CustomerDto>>() {}.type,
                            )
                        responseDto?.let { cDto ->
                            val currentUser =
                                cDto.data.results?.let { customerObj ->
                                    customerMapper.map(customerObj)
                                }
                            if (continuation.isActive) {
                                continuation.resume(currentUser)
                            }
                        } ?: run {
                            if (continuation.isActive) {
                                continuation.resume(null)
                            }
                        }
                    } ?: run {
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }
                }
            } ?: run {
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
        }

    fun send(
        attributionBody: AttributionBody,
        completionHandler: (Attribution?, ApphudError?) -> Unit,
    ) {
        if (!canPerformRequest()) {
            ApphudLog.logE(::send.name + MUST_REGISTER_ERROR)
            return
        }

        val apphudUrl =
            ApphudUrl.Builder()
                .host(HeadersInterceptor.HOST)
                .version(ApphudVersion.V1)
                .path("customers/attribution")
                .build()

        val request = buildPostRequest(URL(apphudUrl.url), attributionBody)

        makeUserRegisteredRequest(request) { serverResponse, error ->
            serverResponse?.let {
                val responseDto: ResponseDto<AttributionDto>? =
                    parser.fromJson<ResponseDto<AttributionDto>>(
                        serverResponse,
                        object : TypeToken<ResponseDto<AttributionDto>>() {}.type,
                    )
                responseDto?.let { response ->
                    val attribution = response.data.results?.let { it1 -> attributionMapper.map(it1) }
                    completionHandler(attribution, null)
                } ?: run {
                    completionHandler(null, ApphudError("Failed to send attribution"))
                }
            } ?: run {
                completionHandler(null, error)
            }
        }
    }

    fun userProperties(
        userPropertiesBody: UserPropertiesBody,
        completionHandler: (Attribution?, ApphudError?) -> Unit,
    ) {
        if (!canPerformRequest()) {
            ApphudLog.logE(::userProperties.name + MUST_REGISTER_ERROR)
            return
        }

        val apphudUrl =
            ApphudUrl.Builder()
                .host(HeadersInterceptor.HOST)
                .version(ApphudVersion.V1)
                .path("customers/properties")
                .build()

        val request = buildPostRequest(URL(apphudUrl.url), userPropertiesBody)

        makeUserRegisteredRequest(request) { serverResponse, error ->
            serverResponse?.let {
                val responseDto: ResponseDto<AttributionDto>? =
                    parser.fromJson<ResponseDto<AttributionDto>>(
                        serverResponse,
                        object : TypeToken<ResponseDto<AttributionDto>>() {}.type,
                    )
                responseDto?.let { response ->
                    val attribution = response.data.results?.let { it1 -> attributionMapper.map(it1) }
                    completionHandler(attribution, null)
                } ?: run {
                    completionHandler(null, ApphudError("Failed to send properties"))
                }
            } ?: run {
                completionHandler(null, error)
            }
        }
    }

    fun grantPromotional(
        daysCount: Int,
        productId: String?,
        permissionGroup: ApphudGroup?,
        completionHandler: (ApphudUser?, ApphudError?) -> Unit,
    ) {
        if (!canPerformRequest()) {
            ApphudLog.logE(::grantPromotional.name + MUST_REGISTER_ERROR)
            return
        }

        val apphudUrl =
            ApphudUrl.Builder()
                .host(HeadersInterceptor.HOST)
                .version(ApphudVersion.V1)
                .path("promotions")
                .build()

        val request = buildPostRequest(URL(apphudUrl.url), grantPromotionalBody(daysCount, productId, permissionGroup))
        val httpClient = getOkHttpClient(request)
        try {
            val serverResponse = performRequestSync(httpClient, request)
            val responseDto: ResponseDto<CustomerDto>? =
                parser.fromJson<ResponseDto<CustomerDto>>(
                    serverResponse,
                    object : TypeToken<ResponseDto<CustomerDto>>() {}.type,
                )

            responseDto?.let { cDto ->
                val currentUser =
                    cDto.data.results?.let { customerObj ->
                        customerMapper.map(customerObj)
                    }
                completionHandler(currentUser, null)
            } ?: run {
                completionHandler(null, ApphudError("Promotional request failed"))
            }
        } catch (ex: Exception) {
            val message = ex.message ?: "Undefined error"
            completionHandler(null, ApphudError(message))
        }
    }

    fun paywallShown(paywall: ApphudPaywall) {
        trackPaywallEvent(
            makePaywallEventBody(
                name = "paywall_shown",
                paywallId = paywall.id,
                placementId = paywall.placementId,
            ),
        )
    }

    fun paywallClosed(paywall: ApphudPaywall) {
        trackPaywallEvent(
            makePaywallEventBody(
                name = "paywall_closed",
                paywallId = paywall.id,
                placementId = paywall.placementId,
            ),
        )
    }

    fun paywallCheckoutInitiated(
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

    fun paywallPaymentCancelled(
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

    fun paywallPaymentError(
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

    private fun trackPaywallEvent(body: PaywallEventBody) {
        if (!canPerformRequest()) {
            ApphudLog.logE(::trackPaywallEvent.name + MUST_REGISTER_ERROR)
            return
        }

        val apphudUrl =
            ApphudUrl.Builder()
                .host(HeadersInterceptor.HOST)
                .version(ApphudVersion.V1)
                .path("events")
                .build()

        val request = buildPostRequest(URL(apphudUrl.url), body)

        makeUserRegisteredRequest(request) { serverResponse, error ->
            serverResponse?.let {
                ApphudLog.logI("Paywall Event log was send successfully")
            } ?: run {
                ApphudLog.logI("Failed to send paywall event")
            }
            error?.let {
                ApphudLog.logE("Paywall Event log was not send")
            }
        }
    }

    fun sendErrorLogs(message: String) {
        if (!canPerformRequest()) {
            ApphudLog.logE(::sendErrorLogs.name + MUST_REGISTER_ERROR)
            return
        }

        val body = makeErrorLogsBody(message, ApphudUtils.packageName)

        val apphudUrl =
            ApphudUrl.Builder()
                .host(HeadersInterceptor.HOST)
                .version(ApphudVersion.V1)
                .path("logs")
                .build()

        val request = buildPostRequest(URL(apphudUrl.url), body)

        makeRequest(request, false) { _, error ->
            error?.let {
                ApphudLog.logE("Error logs was not send")
            } ?: run {
                ApphudLog.logI("Error logs was send successfully")
            }
        }
    }

    fun sendBenchmarkLogs(body: BenchmarkBody) {
        if (!canPerformRequest()) {
            ApphudLog.logE(::sendErrorLogs.name + MUST_REGISTER_ERROR)
            return
        }

        val apphudUrl =
            ApphudUrl.Builder()
                .host(HeadersInterceptor.HOST)
                .version(ApphudVersion.V2)
                .path("logs")
                .build()

        val request = buildPostRequest(URL(apphudUrl.url), body)

        makeRequest(request, false) { _, error ->
            error?.let {
                ApphudLog.logE("Benchmark logs is not sent")
            }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            if (capabilities != null) {
                when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                        return true
                    }
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                        return true
                    }
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                        return true
                    }
                }
            }
        } else {
            val activeNetworkInfo = connectivityManager.activeNetworkInfo
            if (activeNetworkInfo != null && activeNetworkInfo.isConnected) {
                return true
            }
        }
        return false
    }

    private fun makePaywallEventBody(
        name: String,
        paywallId: String?,
        placementId: String?,
        productId: String? = null,
        errorMessage: String? = null,
    ): PaywallEventBody {
        val properties = mutableMapOf<String, Any>()
        paywallId?.let { properties.put("paywall_id", it) }
        productId?.let { properties.put("product_id", it) }
        placementId?.let { properties.put("placement_id", it) }
        errorMessage?.let { properties.put("error_message", it) }

        return PaywallEventBody(
            name = name,
            user_id = ApphudInternal.userId,
            device_id = ApphudInternal.deviceId,
            environment = if (applicationContext.isDebuggable()) "sandbox" else "production",
            timestamp = System.currentTimeMillis(),
            properties = properties.ifEmpty { null },
        )
    }

    private fun mkRegistrationBody(
        needPaywalls: Boolean,
        isNew: Boolean,
    ): RegistrationBody {
        val deviceIds = storage.deviceIdentifiers
        val idfa = deviceIds[0]
        val appSetId = deviceIds[1]
        val androidId = deviceIds[2]

        return RegistrationBody(
            locale = Locale.getDefault().toString(),
            sdk_version = HeadersInterceptor.X_SDK_VERSION,
            app_version = this.applicationContext.buildAppVersion(),
            device_family = Build.MANUFACTURER,
            platform = "Android",
            device_type = if (ApphudUtils.optOutOfTracking) "Restricted" else Build.MODEL,
            os_version = Build.VERSION.RELEASE,
            start_app_version = this.applicationContext.buildAppVersion(),
            idfv = if (ApphudUtils.optOutOfTracking || appSetId.isEmpty()) null else appSetId,
            idfa = if (ApphudUtils.optOutOfTracking || idfa.isEmpty()) null else idfa,
            android_id = if (ApphudUtils.optOutOfTracking || androidId.isEmpty()) null else androidId,
            user_id = ApphudInternal.userId,
            device_id = ApphudInternal.deviceId,
            time_zone = TimeZone.getDefault().id,
            is_sandbox = this.applicationContext.isDebuggable(),
            is_new = isNew,
            need_paywalls = needPaywalls,
            need_placements = needPaywalls,
            first_seen = getInstallationDate(),
        )
    }

    private fun getInstallationDate(): Long? {
        var dateInSecond: Long? = null
        try {
            this.applicationContext.packageManager?.let { manager ->
                dateInSecond = manager.getPackageInfo(this.applicationContext.packageName, 0).firstInstallTime / 1000L
            }
        } catch (ex: Exception) {
            ex.message?.let {
                ApphudLog.logE(it)
            }
        }
        return dateInSecond
    }

    private fun makePurchaseBody(
        purchase: Purchase,
        productDetails: ProductDetails?,
        paywall_id: String?,
        placement_id: String?,
        apphud_product_id: String?,
        offerIdToken: String?,
        oldToken: String?,
    ): PurchaseBody {
        return PurchaseBody(
            device_id = ApphudInternal.deviceId,
            purchases =
                listOf(
                    PurchaseItemBody(
                        order_id = purchase.orderId,
                        product_id = productDetails?.let { productDetails.productId } ?: purchase.products.first(),
                        purchase_token = purchase.purchaseToken,
                        price_currency_code = productDetails?.priceCurrencyCode(),
                        price_amount_micros = productDetails?.priceAmountMicros(),
                        subscription_period = productDetails?.subscriptionPeriod(),
                        paywall_id = paywall_id,
                        placement_id = placement_id,
                        product_bundle_id = apphud_product_id,
                        observer_mode = false,
                        billing_version = BILLING_VERSION,
                        purchase_time = purchase.purchaseTime,
                        product_info = productDetails?.let { ProductInfo(productDetails, offerIdToken) },
                        product_type = productDetails?.productType,
                    ),
                ),
        )
    }

    private val ONE_HOUR = 3600_000L

    private fun makeRestorePurchasesBody(
        apphudProduct: ApphudProduct? = null,
        purchases: List<PurchaseRecordDetails>,
        observerMode: Boolean,
    ) = PurchaseBody(
        device_id = ApphudInternal.deviceId,
        purchases =
            purchases.map { purchase ->
                PurchaseItemBody(
                    order_id = null,
                    product_id = purchase.details.productId,
                    purchase_token = purchase.record.purchaseToken,
                    price_currency_code = purchase.details.priceCurrencyCode(),
                    price_amount_micros =
                        if ((System.currentTimeMillis() - purchase.record.purchaseTime) < ONE_HOUR) {
                            purchase.details.priceAmountMicros()
                        } else {
                            null
                        },
                    subscription_period = purchase.details.subscriptionPeriod(),
                    paywall_id = if (apphudProduct?.productDetails?.productId == purchase.details.productId) apphudProduct.paywallId else null,
                    placement_id = if (apphudProduct?.productDetails?.productId == purchase.details.productId) apphudProduct.placementId else null,
                    product_bundle_id = if (apphudProduct?.productDetails?.productId == purchase.details.productId) apphudProduct.id else null,
                    observer_mode = observerMode,
                    billing_version = BILLING_VERSION,
                    purchase_time = purchase.record.purchaseTime,
                    product_info = null,
                    product_type = purchase.details.productType,
                )
            }.sortedByDescending { it.purchase_time },
    )

    private fun makeTrackPurchasesBody(
        apphudProduct: ApphudProduct? = null,
        purchase: Purchase,
        productDetails: ProductDetails,
        offerIdToken: String?,
        observerMode: Boolean,
    ) = PurchaseBody(
        device_id = ApphudInternal.deviceId,
        purchases =
            listOf(
                PurchaseItemBody(
                    order_id = purchase.orderId,
                    product_id = purchase.products.first(),
                    purchase_token = purchase.purchaseToken,
                    price_currency_code = productDetails.priceCurrencyCode(),
                    price_amount_micros = productDetails.priceAmountMicros(),
                    subscription_period = productDetails.subscriptionPeriod(),
                    paywall_id = if (apphudProduct?.productDetails?.productId == purchase.products.first()) apphudProduct?.paywallId else null,
                    placement_id = if (apphudProduct?.productDetails?.productId == purchase.products.first()) apphudProduct?.placementId else null,
                    product_bundle_id = if (apphudProduct?.productDetails?.productId == purchase.products.first()) apphudProduct?.id else null,
                    observer_mode = observerMode,
                    billing_version = BILLING_VERSION,
                    purchase_time = purchase.purchaseTime,
                    product_info = ProductInfo(productDetails, offerIdToken),
                    product_type = productDetails.productType,
                ),
            ),
    )

    internal fun makeErrorLogsBody(
        message: String,
        apphud_product_id: String? = null,
    ) = ErrorLogsBody(
        message = message,
        bundle_id = apphud_product_id,
        user_id = ApphudInternal.userId,
        device_id = ApphudInternal.deviceId,
        environment = if (applicationContext.isDebuggable()) "sandbox" else "production",
        timestamp = System.currentTimeMillis(),
    )

    internal fun grantPromotionalBody(
        daysCount: Int,
        productId: String? = null,
        permissionGroup: ApphudGroup? = null,
    ): GrantPromotionalBody {
        return GrantPromotionalBody(
            duration = daysCount,
            user_id = ApphudInternal.userId,
            device_id = ApphudInternal.deviceId,
            product_id = productId,
            product_group_id = permissionGroup?.id,
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
                    applicationContext.packageManager.getPackageInfo(ApphudUtils.packageName, PackageManager.GET_PERMISSIONS)
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

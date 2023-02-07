package com.apphud.sdk.managers

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import com.apphud.sdk.*
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
import java.net.URL
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume


object RequestManager {
    private const val MUST_REGISTER_ERROR =
        " :You must call the Apphud.start method once when your application starts before calling any other methods."

    var currentUser: Customer? = null

    val gson = GsonBuilder().serializeNulls().create()
    val parser: Parser = GsonParser(gson)

    private val productMapper = ProductMapper()
    private val paywallsMapper = PaywallsMapper(parser)
    private val attributionMapper = AttributionMapper()
    private val customerMapper = CustomerMapper(SubscriptionMapper(), paywallsMapper)

    //TODO to be settled
    private var apiKey: String? = null
    lateinit var userId: UserId
    lateinit var deviceId: DeviceId
    lateinit var applicationContext: Context
    lateinit var storage: SharedPreferencesStorage

    var advertisingId: String? = null
        get() = storage.advertisingId
        set(value) {
            field = value
            if (storage.advertisingId != value) {
                storage.advertisingId = value
                ApphudLog.log("advertisingId = $advertisingId is fetched and saved")
            }
        }

    fun setParams(
        applicationContext: Context,
        userId: UserId,
        deviceId: DeviceId,
        apiKey: String? = null
    ) {
        this.applicationContext = applicationContext
        this.userId = userId
        this.deviceId = deviceId
        apiKey?.let {
            this.apiKey = it
        }
        this.storage = SharedPreferencesStorage(this.applicationContext, parser)
        currentUser = null
    }

    fun cleanRegistration() {
        currentUser = null
        advertisingId = null
        apiKey = null
    }

    private fun canPerformRequest(): Boolean {
        return ::applicationContext.isInitialized
                && ::userId.isInitialized
                && ::deviceId.isInitialized
                && apiKey != null
    }

    private fun getOkHttpClient( request: Request, retry: Boolean = true): OkHttpClient {
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

        var readTimeout: Long = 10L
        if (request.method == "POST" && request.url.toString().contains("subscriptions")) {
            readTimeout = 30L
        }

        var builder = OkHttpClient.Builder()
            .readTimeout(readTimeout, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
        if (retry) builder.addInterceptor(retryInterceptor)
        builder.addNetworkInterceptor(headersInterceptor)
        //builder.addNetworkInterceptor(logging)
        
        return builder.build()
    }

    private fun logRequestStart(request: Request) {
        try {
            var body: String? = ""
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
            ApphudLog.logI("Start " + request.method + " request " + request.url + " with params:" + body)
        } catch (ex: Exception) {
            ApphudLog.logE(ex.message ?: "")
        }
    }

    private fun logRequestFinish(request: Request, response: Response) {
        try {
            val responseBody = response.body
            val source = responseBody?.source()
            source?.request(Long.MAX_VALUE)

            val buffer = source?.buffer?.clone()?.readString(Charset.forName("UTF-8"))
            var outputBody = ""
            buffer?.let {
                if (parser.isJson(buffer)) {
                    outputBody = buildPrettyPrintedBy(it) ?: ""
                }
            }

            ApphudLog.logI("Finished " + request.method + " request " + request.url + " with response: " + response.code + "\n" + outputBody)
        } catch (ex: Exception) {
            ApphudLog.logE(ex.message ?: "")
        }
    }

    private fun performRequest(
        client: OkHttpClient,
        request: Request,
        completionHandler: (String?, ApphudError?) -> Unit
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
                val finish = Date()
                val diff = (finish.time - start.time)
                ApphudLog.logBenchmark(
                    request.url.encodedPath,
                    diff
                )

                logRequestFinish(request, response)

                if (response.isSuccessful) {
                    response.body?.let {
                        completionHandler(it.string(), null)
                    } ?: run {
                        completionHandler(null, ApphudError("Request failed", null, response.code))
                    }
                } else {
                    checkLock403(request, response)
                    val message = "finish ${request.method} request ${request.url} " +
                            "failed with code: ${response.code} response: ${
                                buildPrettyPrintedBy(response.body.toString())
                            }"
                    completionHandler(null, ApphudError(message, null, response.code))
                }
            } else {
                val message = "No Internet connection"
                ApphudLog.logE(message)
                completionHandler(null, ApphudError(message))
            }
        } catch (e: IOException) {
            val message = e.message ?: "Undefined error"
            completionHandler(null, ApphudError(message))
        }
    }

    @Throws(Exception::class)
    fun performRequestSync(client: OkHttpClient, request: Request): String {
        if (HeadersInterceptor.isBlocked) {
            val message = "SDK networking is locked until application restart"
            ApphudLog.logE(message)
            throw Exception(message)
        } else if (true) {
            logRequestStart(request)

            val start = Date()
            val response = client.newCall(request).execute()
            val finish = Date()
            val diff = (finish.time - start.time)
            ApphudLog.logBenchmark(
                request.url.encodedPath,
                diff
            )

            logRequestFinish(request, response)

            val responseBody = response.body!!.string()
            if (response.isSuccessful) {
                return responseBody
            } else {
                checkLock403(request, response)
                val message = "finish ${request.method} request ${request.url} " +
                        "failed with code: ${response.code} response: ${
                            buildPrettyPrintedBy(responseBody)
                        }"
                throw Exception(message)
            }
        } else {
            val message = "No Internet connection"
            ApphudLog.logE(message)
            throw Exception(message)
        }
    }

    private fun checkLock403(request: Request, response: Response) {
        if (response.code == 403 && request.method == "POST" && request.url.encodedPath.endsWith("/customers")) {
            HeadersInterceptor.isBlocked = true
        }
    }

    private fun makeRequest(
        request: Request,
        retry: Boolean = true,
        completionHandler: (String?, ApphudError?) -> Unit
    ) {
        val httpClient = getOkHttpClient(request, retry)
        performRequest(httpClient, request, completionHandler)
    }

    private fun makeUserRegisteredRequest(
        request: Request,
        completionHandler: (String?, ApphudError?) -> Unit
    ) {
        val httpClient = getOkHttpClient(request)
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
        params: Any
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

    suspend fun fetchAdvertisingId(): String? =
        suspendCancellableCoroutine { continuation ->
            var advId :String? = null
            if (ApphudUtils.adTracking) {
                try {
                    val adInfo: AdInfo = AdvertisingIdManager.getAdvertisingIdInfo(applicationContext)
                    advId = adInfo.id
                } catch (e: java.lang.Exception) {
                    ApphudLog.logE("finish load advertisingId $e")
                }
            }
            if(continuation.isActive) {
                continuation.resume(advId)
            }
        }

    suspend fun registrationSync(
        needPaywalls: Boolean,
        isNew: Boolean,
        forceRegistration: Boolean = false
    ): Customer? =
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
        completionHandler: (Customer?, ApphudError?) -> Unit
    ) {
        if (!canPerformRequest()) {
            ApphudLog.logE(::registration.name + MUST_REGISTER_ERROR)
            return
        }

        if (currentUser == null || forceRegistration) {
            val apphudUrl = ApphudUrl.Builder()
                .host(HeadersInterceptor.HOST)
                .version(ApphudVersion.V1)
                .path("customers")
                .build()

            val request =
                buildPostRequest(URL(apphudUrl.url), mkRegistrationBody(needPaywalls, isNew))
            val httpClient = getOkHttpClient(request)
            try {
                val serverResponse = performRequestSync(httpClient, request)
                val type = TypeToken.getParameterized(ResponseDto::class.java, CustomerDto::class.java).type
                val responseDto: ResponseDto<CustomerDto>? =
                    parser.fromJson<ResponseDto<CustomerDto>>(
                        serverResponse,
                        type
                    )

                responseDto?.let { cDto ->
                    currentUser = cDto.data.results?.let { customerObj ->
                        customerMapper.map(customerObj)
                    }
                    completionHandler(currentUser, null)
                } ?: run {
                    completionHandler(null, ApphudError("Registration failed"))
                }
            } catch (ex: Exception) {
                val message = ex.message ?: "Undefined error"
                completionHandler(null, ApphudError(message))
            }
        } else {
            completionHandler(currentUser, null)
        }
    }

    suspend fun allProducts(): List<ApphudGroup>? =
        suspendCancellableCoroutine { continuation ->
            val apphudUrl = ApphudUrl.Builder()
                .host(HeadersInterceptor.HOST)
                .version(ApphudVersion.V2)
                .path("products")
                .build()

            val request = buildGetRequest(URL(apphudUrl.url))

            makeRequest(request) { serverResponse, error ->
                serverResponse?.let {
                    val typeList = TypeToken.getParameterized(List::class.java, ApphudGroupDto::class.java).type
                    val type = TypeToken.getParameterized(ResponseDto::class.java, typeList).type
                    val responseDto: ResponseDto<List<ApphudGroupDto>>? =
                        parser.fromJson<ResponseDto<List<ApphudGroupDto>>>(
                            serverResponse,
                            type
                        )
                    responseDto?.let { response ->
                        val productsList =
                            response.data.results?.let { it1 -> productMapper.map(it1) }
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
        details: SkuDetails?,
        apphudProduct: ApphudProduct?,
        completionHandler: (Customer?, ApphudError?) -> Unit
    ) {
        if (!canPerformRequest()) {
            ApphudLog.logE(::purchased.name + MUST_REGISTER_ERROR)
            return
        }

        val apphudUrl = ApphudUrl.Builder()
            .host(HeadersInterceptor.HOST)
            .version(ApphudVersion.V1)
            .path("subscriptions")
            .build()

        val purchaseBody = details?.let { makePurchaseBody(purchase, it, null, null) }
            ?: apphudProduct?.let {
                makePurchaseBody(
                    purchase,
                    it.skuDetails,
                    it.paywall_id,
                    it.id
                )
            }
        if (purchaseBody == null) {
            val message =
                "SkuDetails and ApphudProduct can not be null at the same time" + apphudProduct?.let { " [Apphud product ID: " + it.id + "]" }
            ApphudLog.logE(message = message)
            completionHandler.invoke(null, ApphudError(message))
            return
        }

        val request = buildPostRequest(URL(apphudUrl.url), purchaseBody)

        makeUserRegisteredRequest(request) { serverResponse, error ->
            serverResponse?.let {
                val type = TypeToken.getParameterized(ResponseDto::class.java, CustomerDto::class.java).type
                val responseDto: ResponseDto<CustomerDto>? =
                    parser.fromJson<ResponseDto<CustomerDto>>(
                        serverResponse,
                        type
                    )
                responseDto?.let { cDto ->
                    currentUser = cDto.data.results?.let { customerObj ->
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

    fun restorePurchases(
        apphudProduct: ApphudProduct? = null,
        purchaseRecordDetailsSet: Set<PurchaseRecordDetails>,
        observerMode: Boolean,
        completionHandler: (Customer?, ApphudError?) -> Unit
    ) {
        if (!canPerformRequest()) {
            ApphudLog.logE(::restorePurchases.name + MUST_REGISTER_ERROR)
            return
        }

        val apphudUrl = ApphudUrl.Builder()
            .host(HeadersInterceptor.HOST)
            .version(ApphudVersion.V1)
            .path("subscriptions")
            .build()

        val purchaseBody =
            makeRestorePurchasesBody(apphudProduct, purchaseRecordDetailsSet.toList(), observerMode)

        val request = buildPostRequest(URL(apphudUrl.url), purchaseBody)

        makeUserRegisteredRequest(request) { serverResponse, error ->
            serverResponse?.let {
                val type = TypeToken.getParameterized(ResponseDto::class.java, CustomerDto::class.java).type
                val responseDto: ResponseDto<CustomerDto>? =
                    parser.fromJson<ResponseDto<CustomerDto>>(
                        serverResponse,
                        type
                    )
                responseDto?.let { cDto ->
                    currentUser = cDto.data.results?.let { customerObj ->
                        customerMapper.map(customerObj)
                    }
                    completionHandler(currentUser, null)
                } ?: run {
                    completionHandler(null, ApphudError("Failed to restore purchases"))
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
        skuProduct: SkuDetails?,
        observerMode: Boolean
    ): Customer? =
        suspendCancellableCoroutine { continuation ->
            if (!canPerformRequest()) {
                ApphudLog.logE("restorePurchasesSync $MUST_REGISTER_ERROR")
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }

            val apphudUrl = ApphudUrl.Builder()
                .host(HeadersInterceptor.HOST)
                .version(ApphudVersion.V1)
                .path("subscriptions")
                .build()

            val purchaseBody = if(purchaseRecordDetailsSet != null){
                makeRestorePurchasesBody(
                    apphudProduct,
                    purchaseRecordDetailsSet,
                    observerMode
                )
            }else if(purchase != null && skuProduct != null){
                makeTrackPurchasesBody(
                    apphudProduct,
                    purchase,
                    skuProduct,
                    observerMode
                )
            } else null

            purchaseBody?.let{
                val request = buildPostRequest(URL(apphudUrl.url), it)
                makeUserRegisteredRequest(request) { serverResponse, error ->
                    serverResponse?.let {
                        val type = TypeToken.getParameterized(ResponseDto::class.java, CustomerDto::class.java).type
                        val responseDto: ResponseDto<CustomerDto>? =
                            parser.fromJson<ResponseDto<CustomerDto>>(
                                serverResponse,
                                type
                            )
                        responseDto?.let { cDto ->
                            currentUser = cDto.data.results?.let { customerObj ->
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
            }?: run{
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
        }

    fun send(attributionBody: AttributionBody,  completionHandler: (Attribution?, ApphudError?) -> Unit){
        if(!canPerformRequest()) {
            ApphudLog.logE(::send.name + MUST_REGISTER_ERROR)
            return
        }

        val apphudUrl = ApphudUrl.Builder()
            .host(HeadersInterceptor.HOST)
            .version(ApphudVersion.V1)
            .path("customers/attribution")
            .build()

        val request = buildPostRequest(URL(apphudUrl.url), attributionBody)

        makeUserRegisteredRequest(request) { serverResponse, error ->
            serverResponse?.let {
                val type = TypeToken.getParameterized(ResponseDto::class.java, AttributionDto::class.java).type
                val responseDto: ResponseDto<AttributionDto>? =
                    parser.fromJson<ResponseDto<AttributionDto>>(
                        serverResponse,
                        type
                    )
                responseDto?.let{ response ->
                    val attribution = response.data.results?.let { it1 -> attributionMapper.map(it1) }
                    completionHandler(attribution, null)
                }?: run {
                    completionHandler(null, ApphudError("Failed to send attribution"))
                }
            } ?: run {
                completionHandler(null, error)
            }
        }
    }

    fun userProperties(userPropertiesBody: UserPropertiesBody, completionHandler: (Attribution?, ApphudError?) -> Unit){
        if(!canPerformRequest()) {
            ApphudLog.logE(::userProperties.name + MUST_REGISTER_ERROR)
            return
        }

        val apphudUrl = ApphudUrl.Builder()
            .host(HeadersInterceptor.HOST)
            .version(ApphudVersion.V1)
            .path("customers/properties")
            .build()

        val request = buildPostRequest(URL(apphudUrl.url), userPropertiesBody)

        makeUserRegisteredRequest(request) { serverResponse, error ->
            serverResponse?.let {
                val type = TypeToken.getParameterized(ResponseDto::class.java, AttributionDto::class.java).type
                val responseDto: ResponseDto<AttributionDto>? =
                    parser.fromJson<ResponseDto<AttributionDto>>(
                        serverResponse,
                        type
                    )
                responseDto?.let{ response ->
                    val attribution = response.data.results?.let { it1 -> attributionMapper.map(it1) }
                    completionHandler(attribution, null)
                }?: run {
                    completionHandler(null, ApphudError("Failed to send properties"))
                }
            } ?: run {
                completionHandler(null, error)
            }
        }
    }

    fun grantPromotional(daysCount: Int, productId: String?, permissionGroup: ApphudGroup?, completionHandler: (Customer?, ApphudError?) -> Unit) {
        if(!canPerformRequest()) {
            ApphudLog.logE(::grantPromotional.name + MUST_REGISTER_ERROR)
            return
        }

        val apphudUrl = ApphudUrl.Builder()
            .host(HeadersInterceptor.HOST)
            .version(ApphudVersion.V1)
            .path("promotions")
            .build()

        val request = buildPostRequest(URL(apphudUrl.url), grantPromotionalBody(daysCount, productId, permissionGroup))
        val httpClient = getOkHttpClient(request)
        try {
            val serverResponse = performRequestSync(httpClient, request)
            val type = TypeToken.getParameterized(ResponseDto::class.java, CustomerDto::class.java).type
            val responseDto: ResponseDto<CustomerDto>? =
                parser.fromJson<ResponseDto<CustomerDto>>(
                    serverResponse,
                    type
                )

            responseDto?.let { cDto ->
                currentUser = cDto.data.results?.let { customerObj ->
                    customerMapper.map(customerObj)
                }
                completionHandler(currentUser, null)
            } ?: run {
                completionHandler(null, ApphudError("Promotional request failed"))
            }
        } catch (ex: Exception) {
            val message = ex.message?:"Undefined error"
            completionHandler(null,  ApphudError(message))
        }
    }

    fun paywallShown(paywall: ApphudPaywall) {
        trackPaywallEvent(
            makePaywallEventBody(
                name = "paywall_shown",
                paywall_id = paywall.id
            )
        )
    }

    fun paywallClosed(paywall: ApphudPaywall) {
        trackPaywallEvent(
            makePaywallEventBody(
                name = "paywall_closed",
                paywall_id = paywall.id
            )
        )
    }

    fun paywallCheckoutInitiated(paywall_id: String?, product_id: String?) {
        trackPaywallEvent(
            makePaywallEventBody(
                name = "paywall_checkout_initiated",
                paywall_id = paywall_id,
                product_id = product_id
            )
        )
    }

    fun paywallPaymentCancelled(paywall_id: String?, product_id: String?) {
        trackPaywallEvent(
            makePaywallEventBody(
                name = "paywall_payment_cancelled",
                paywall_id = paywall_id,
                product_id = product_id
            )
        )
    }

    fun paywallPaymentError(paywall_id: String?, product_id: String?, error_code: String?) {
        trackPaywallEvent(
            makePaywallEventBody(
                name = "paywall_payment_error",
                paywall_id = paywall_id,
                product_id = product_id,
                error_code = error_code
            )
        )
    }

    private fun trackPaywallEvent(body: PaywallEventBody) {
        if(!canPerformRequest()) {
            ApphudLog.logE(::trackPaywallEvent.name + MUST_REGISTER_ERROR)
            return
        }

        val apphudUrl = ApphudUrl.Builder()
            .host(HeadersInterceptor.HOST)
            .version(ApphudVersion.V1)
            .path("events")
            .build()

        val request = buildPostRequest(URL(apphudUrl.url), body)

        makeUserRegisteredRequest(request) { serverResponse, error ->
            serverResponse?.let {
                    ApphudLog.logI("Paywall Event log was send successfully")
                }?: run {
                    ApphudLog.logI("Failed to send paywall event")
                }
            error?.let {
                ApphudLog.logE("Paywall Event log was not send")
            }
        }
    }

    fun sendErrorLogs(message: String){
        if(!canPerformRequest()) {
            ApphudLog.logE(::sendErrorLogs.name + MUST_REGISTER_ERROR)
            return
        }

        val body = makeErrorLogsBody(message, ApphudUtils.packageName)

        val apphudUrl = ApphudUrl.Builder()
            .host(HeadersInterceptor.HOST)
            .version(ApphudVersion.V1)
            .path("logs")
            .build()

        val request = buildPostRequest(URL(apphudUrl.url), body)

        makeRequest(request, false) { _, error ->
            error?.let {
                ApphudLog.logE("Error logs was not send")
            }?:run{
                ApphudLog.logI("Error logs was send successfully")
            }
        }
    }

    fun sendBenchmarkLogs(body: BenchmarkBody){
        if(!canPerformRequest()) {
            ApphudLog.logE(::sendErrorLogs.name + MUST_REGISTER_ERROR)
            return
        }

        val apphudUrl = ApphudUrl.Builder()
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

    private fun makePaywallEventBody(name: String, paywall_id: String? = null, product_id: String? = null, error_code: String? = null): PaywallEventBody {
        val properties = mutableMapOf<String, Any>()
        paywall_id?.let { properties.put("paywall_id", it) }
        product_id?.let { properties.put("product_id", it) }
        error_code?.let { properties.put("error_code", it) }
        return PaywallEventBody(
            name = name,
            user_id = userId,
            device_id = deviceId,
            environment = if (applicationContext.isDebuggable()) "sandbox" else "production",
            timestamp = System.currentTimeMillis(),
            properties = if (properties.isNotEmpty()) properties else null
        )
    }

    private fun mkRegistrationBody(needPaywalls: Boolean, isNew: Boolean) =
        RegistrationBody(
            locale = Locale.getDefault().toString(),
            sdk_version = BuildConfig.VERSION_NAME,
            app_version =  this.applicationContext.buildAppVersion(),
            device_family = Build.MANUFACTURER,
            platform = "Android",
            device_type = Build.MODEL,
            os_version = Build.VERSION.RELEASE,
            start_app_version = this.applicationContext.buildAppVersion(),
            idfv = null,
            idfa = if (ApphudUtils.adTracking) advertisingId else null,
            user_id = userId,
            device_id = deviceId,
            time_zone = TimeZone.getDefault().id,
            is_sandbox = this.applicationContext.isDebuggable(),
            is_new = isNew,
            need_paywalls = needPaywalls,
            first_seen = getInstallationDate()
        )

    private fun getInstallationDate() :Long?{
        var dateInSecond :Long? = null
        try{
            this.applicationContext.packageManager?.let{ manager ->
                dateInSecond = manager.getPackageInfo(this.applicationContext.packageName, 0).firstInstallTime/1000L
            }
        }catch(ex: Exception){
            ex.message?.let{
                ApphudLog.logE(it)
            }
        }
        return dateInSecond
    }

    private fun makePurchaseBody(
        purchase: Purchase,
        details: SkuDetails?,
        paywall_id: String?,
        apphud_product_id: String?
    ) =
        PurchaseBody(
            device_id = deviceId,
            purchases = listOf(
                PurchaseItemBody(
                    order_id = purchase.orderId,
                    product_id = details?.let { details.sku } ?: purchase.skus.first(),
                    purchase_token = purchase.purchaseToken,
                    price_currency_code = details?.priceCurrencyCode,
                    price_amount_micros = details?.priceAmountMicros,
                    subscription_period = details?.subscriptionPeriod,
                    paywall_id = paywall_id,
                    product_bundle_id = apphud_product_id,
                    observer_mode = false
                )
            )
        )

    private fun makeRestorePurchasesBody(apphudProduct: ApphudProduct? = null, purchases: List<PurchaseRecordDetails>, observerMode: Boolean) =
        PurchaseBody(
            device_id = deviceId,
            purchases = purchases.map { purchase ->
                PurchaseItemBody(
                    order_id = null,
                    product_id = purchase.details.sku,
                    purchase_token = purchase.record.purchaseToken,
                    price_currency_code = purchase.details.priceCurrencyCode,
                    price_amount_micros = purchase.details.priceAmountMicros,
                    subscription_period = purchase.details.subscriptionPeriod,
                    paywall_id = if(apphudProduct?.skuDetails?.sku == purchase.details.sku) apphudProduct.paywall_id else null,
                    product_bundle_id = if(apphudProduct?.skuDetails?.sku == purchase.details.sku) apphudProduct.id else null,
                    observer_mode = observerMode
                )
            }
        )

    private fun makeTrackPurchasesBody(apphudProduct: ApphudProduct? = null, purchase: Purchase, skuProduct: SkuDetails?, observerMode: Boolean) =
        PurchaseBody(
            device_id = deviceId,
            purchases = listOf(
                PurchaseItemBody(
                    order_id = purchase.orderId,
                    product_id = purchase.skus.first(),
                    purchase_token = purchase.purchaseToken,
                    price_currency_code = skuProduct?.priceCurrencyCode,
                    price_amount_micros = skuProduct?.priceAmountMicros,
                    subscription_period = skuProduct?.let{ if(it.subscriptionPeriod.isNotEmpty()) it.subscriptionPeriod else null},
                    paywall_id = if(apphudProduct?.skuDetails?.sku == purchase.skus.first()) apphudProduct?.paywall_id else null,
                    product_bundle_id = if(apphudProduct?.skuDetails?.sku == purchase.skus.first()) apphudProduct?.id else null,
                    observer_mode = observerMode
                )
            )
        )

    internal fun makeErrorLogsBody(message: String, apphud_product_id: String? = null) =
        ErrorLogsBody(
            message = message,
            bundle_id = apphud_product_id,
            user_id = userId,
            device_id = deviceId,
            environment = if (applicationContext.isDebuggable()) "sandbox" else "production",
            timestamp = System.currentTimeMillis()
        )

    internal fun grantPromotionalBody(daysCount: Int, productId: String? = null, permissionGroup: ApphudGroup? = null): GrantPromotionalBody {
        return GrantPromotionalBody(
            duration = daysCount,
            user_id = userId,
            device_id = deviceId,
            product_id = productId,
            product_group_id = permissionGroup?.id
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
}
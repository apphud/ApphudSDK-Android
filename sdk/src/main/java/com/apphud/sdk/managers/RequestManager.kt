package com.apphud.sdk.managers

import android.content.Context
import android.content.res.Resources
import android.os.Build
import androidx.core.os.ConfigurationCompat
import com.apphud.sdk.*
import com.apphud.sdk.ApphudUtils
import com.apphud.sdk.ApphudVersion
import com.apphud.sdk.body.RegistrationBody
import com.apphud.sdk.client.ApiClient
import com.apphud.sdk.client.ApphudUrl
import com.apphud.sdk.client.dto.ApphudGroupDto
import com.apphud.sdk.client.dto.ApphudPaywallDto
import com.apphud.sdk.client.dto.CustomerDto
import com.apphud.sdk.client.dto.ResponseDto
import com.apphud.sdk.domain.ApphudGroup
import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.Customer
import com.apphud.sdk.mappers.*
import com.apphud.sdk.parser.GsonParser
import com.apphud.sdk.parser.Parser
import com.apphud.sdk.storage.SharedPreferencesStorage
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.lang.Thread.sleep
import java.net.URL
import java.util.*

object RequestManager {
    private const val API_KEY = "api_key"
    private const val MUST_REGISTER_ERROR = " :You must call the Apphud.start method once when your application starts before calling any other methods."

    private var currentUser: Customer? = null
    val parser: Parser = GsonParser(Gson())

    private val productMapper = ProductMapper()
    private val paywallsMapper = PaywallsMapper(parser)
    private val attributionMapper = AttributionMapper()
    private val customerMapper = CustomerMapper(SubscriptionMapper(), paywallsMapper)

    //TODO to be settled
    lateinit var userId: UserId
    lateinit var deviceId: DeviceId
    lateinit var apiKey: String
    lateinit var applicationContext: Context
    lateinit var storage: SharedPreferencesStorage

    private var advertisingId: String? = null
        get() = storage.advertisingId
        set(value) {
            field = value
            if (storage.advertisingId != value) {
                storage.advertisingId = value
                ApphudLog.log("advertisingId = $advertisingId is fetched and saved")
            }
            ApphudLog.log("advertisingId: continue registration")
        }

    fun setParams(applicationContext: Context, userId: UserId, deviceId: DeviceId, apiKey: String){
        this.applicationContext = applicationContext
        this.userId = userId
        this.deviceId = deviceId
        this.apiKey = apiKey
        this.storage = SharedPreferencesStorage(this.applicationContext, parser)
        currentUser = null
    }

    fun cleanRegistration(){
        currentUser = null
        advertisingId = null
    }

    fun canPerformRequest(): Boolean{
        return ::applicationContext.isInitialized
                && ::userId.isInitialized
                && ::deviceId.isInitialized
                && ::apiKey.isInitialized
    }

    fun getOkHttpClient(): OkHttpClient {
        val headersInterceptor = HeadersInterceptor()
        val logging = HttpLoggingInterceptor(
            HttpLoggingInterceptor.Logger {
                ApphudLog.logI(it)
            })

        if (BuildConfig.DEBUG) {
            logging.level = HttpLoggingInterceptor.Level.BODY;
        }else{
            logging.level = HttpLoggingInterceptor.Level.NONE
        }

        var builder = OkHttpClient.Builder()

        builder.addNetworkInterceptor(headersInterceptor)
        builder.addNetworkInterceptor(logging)

        return builder.build()
    }

    fun performRequest(client: OkHttpClient, request: Request, completionHandler: (String?, Error?) -> Unit){
        var error: Error? = null
        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.let {
                    completionHandler(it.string(), null)
                }?: run{
                    completionHandler(null, Error("Response success but result is null"))
                }
            } else {
                val  message = "finish ${request.method} request ${request.url} " +
                        "failed with code: ${response.code} response: ${
                            buildPrettyPrintedBy(response.body.toString())
                        }"
                error = java.lang.Error(message)
                completionHandler(null, error)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            completionHandler(null,  Error(e.message))
        }
    }

    @Throws(Exception::class)
    fun performRequestSync(client: OkHttpClient, request: Request): String{
        val response = client.newCall(request).execute()
        val responseBody = response.body!!.string()
        if (response.isSuccessful) {
            return responseBody
        } else {
            val  message = "finish ${request.method} request ${request.url} " +
                    "failed with code: ${response.code} response: ${
                        buildPrettyPrintedBy(responseBody)
                    }"
            throw Exception(message)
        }
    }

    fun makeRequest(request: Request, completionHandler: (String?, Error?) -> Unit) {
        val httpClient = getOkHttpClient()
        performRequest(httpClient, request, completionHandler)
    }

    fun makeUserRegisteredRequest(request: Request, completionHandler: (String?, Error?) -> Unit) {
        val httpClient = getOkHttpClient()
        if(currentUser == null){
            registration(true, true) { customer, error ->
                customer?.let {
                    performRequest(httpClient, request, completionHandler)
                } ?: run {
                    completionHandler(null, error)
                }
            }
        }else {
            performRequest(httpClient, request, completionHandler)
        }
    }

    fun buildPostRequest(
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

    fun buildPutRequest(url: URL, params: Any): Request {
        val json = parser.toJson(params)
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = json.toRequestBody(mediaType)

        return Request.Builder()
            .url(url)
            .put(requestBody)
            .build()
    }

    fun buildGetRequest(url: URL): Request {
        val request = Request.Builder()
        return request.url(url)
            .get()
            .build()
    }

    @Synchronized
    fun registration(needPaywalls: Boolean, isNew: Boolean, completionHandler: (Customer?, Error?) -> Unit) {
        if(currentUser == null) {
            //Load advertising id
            if (ApphudUtils.adTracking) {
                try {
                    ApphudLog.logI("start load advertisingId")
                    advertisingId = AdvertisingIdClient.getAdvertisingIdInfo(applicationContext).id
                    ApphudLog.logI("success load advertisingId: $advertisingId")
                } catch (e: IOException) {
                    ApphudLog.logI("finish load advertisingId $e")
                } catch (e: IllegalStateException) {
                    ApphudLog.logI("finish load advertisingId $e")
                } catch (e: GooglePlayServicesNotAvailableException) {
                    ApphudLog.logI("finish load advertisingId $e")
                } catch (e: GooglePlayServicesRepairableException) {
                    ApphudLog.logI("finish load advertisingId $e")
                }
            }

            val apphudUrl = ApphudUrl.Builder()
                .host(ApiClient.host)
                .version(ApphudVersion.V1)
                .path("customers")
                .params(mapOf(API_KEY to apiKey))
                .build()

            val request = buildPostRequest(URL(apphudUrl.url), mkRegistrationBody(needPaywalls, isNew))
            val httpClient = getOkHttpClient()
            try {
                val serverResponse = performRequestSync(httpClient, request)
                val responseDto: ResponseDto<CustomerDto>? =
                    parser.fromJson<ResponseDto<CustomerDto>>(
                        serverResponse,
                        object : TypeToken<ResponseDto<CustomerDto>>() {}.type
                    )

                responseDto?.let { cDto ->

                    //TODO test registration error
                    //completionHandler(null, Error("Test error"))

                    currentUser = cDto.data.results?.let { customerObj ->
                        customerMapper.map(customerObj)
                    }
                    completionHandler(currentUser, null)
                } ?: run {
                    completionHandler(null, Error("Response success but result is null"))
                }
            } catch (ex: Exception) {
                completionHandler(null, Error(ex.message))
            }
        }else{
            completionHandler(currentUser, null)
        }
    }

    fun getPaywalls(completionHandler: (List<ApphudPaywall>?, Error?) -> Unit) {
        if(!canPerformRequest()) {
            ApphudLog.logE(::getPaywalls.name + MUST_REGISTER_ERROR)
            return
        }

        val apphudUrl = ApphudUrl.Builder()
            .host(ApiClient.host)
            .version(ApphudVersion.V2)
            .path("paywall_configs")
            .params(mapOf(API_KEY to apiKey))
            .build()

        val request = buildGetRequest(URL(apphudUrl.url))

        makeUserRegisteredRequest(request) { serverResponse, error ->
            serverResponse?.let {
                val responseDto: ResponseDto<List<ApphudPaywallDto>>? =
                    parser.fromJson<ResponseDto<List<ApphudPaywallDto>>>(serverResponse, object: TypeToken<ResponseDto<List<ApphudPaywallDto>>>(){}.type)
                responseDto?.let{ response ->
                    val paywallsList = response.data.results?.let { it1 -> paywallsMapper.map(it1) }
                    completionHandler(paywallsList, null)
                }?: run{
                    completionHandler(null, Error("Response success but result is null"))
                }
            } ?: run {
                completionHandler(null, error)
            }
        }
    }

    fun allProducts(completionHandler: (List<ApphudGroup>?, Error?) -> Unit) {
        if(!canPerformRequest()) {
            ApphudLog.logE(::allProducts.name + MUST_REGISTER_ERROR)
            return
        }

        val apphudUrl = ApphudUrl.Builder()
            .host(ApiClient.host)
            .version(ApphudVersion.V2)
            .path("products")
            .params(mapOf(API_KEY to apiKey))
            .build()

        val request = buildGetRequest(URL(apphudUrl.url))

        makeUserRegisteredRequest(request) { serverResponse, error ->
            serverResponse?.let {
                val responseDto: ResponseDto<List<ApphudGroupDto>>? =
                    parser.fromJson<ResponseDto<List<ApphudGroupDto>>>(serverResponse, object: TypeToken<ResponseDto<List<ApphudGroupDto>>>(){}.type)
                responseDto?.let{ response ->
                    val productsList = response.data.results?.let { it1 -> productMapper.map(it1) }
                    completionHandler(productsList, null)
                }?: run{
                    completionHandler(null, Error("Response success but result is null"))
                }
            } ?: run {
                completionHandler(null, error)
            }
        }
    }

    private fun mkRegistrationBody(needPaywalls: Boolean, isNew: Boolean) =
        RegistrationBody(
            locale = ConfigurationCompat.getLocales(Resources.getSystem().configuration).get(0).toString(),
            sdk_version = com.android.billingclient.BuildConfig.VERSION_NAME,
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
            need_paywalls = needPaywalls
        )

    //A function to render the raw response from the server in pretty printed style
    private fun buildPrettyPrintedBy(response: String) =
        parser.fromJson<Map<String, Any>>(response, Map::class.java)?.let { value ->
            parser.toJson(value)
        }

    private fun buildStringBy(stream: InputStream): String {
        val reader = InputStreamReader(stream, Charsets.UTF_8)
        return BufferedReader(reader).use { buffer ->
            val response = StringBuilder()
            var line: String?
            while (buffer.readLine().also { line = it } != null) {
                response.append(line)
            }
            response.toString()
        }
    }
}
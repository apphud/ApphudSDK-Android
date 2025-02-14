package com.apphud.sdk.internal.data.remote

import com.apphud.sdk.APPHUD_ERROR_NO_INTERNET
import com.apphud.sdk.ApphudError
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.UserId
import com.apphud.sdk.domain.ApphudGroup
import com.apphud.sdk.domain.ApphudProduct
import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.domain.PurchaseRecordDetails
import com.apphud.sdk.internal.data.dto.ApphudGroupDto
import com.apphud.sdk.internal.data.dto.CustomerDto
import com.apphud.sdk.internal.data.dto.ResponseDto
import com.apphud.sdk.internal.data.mapper.CustomerMapper
import com.apphud.sdk.internal.data.mapper.ProductMapper
import com.apphud.sdk.internal.domain.model.GetProductsParams
import com.apphud.sdk.internal.domain.model.PurchaseContext
import com.apphud.sdk.internal.util.runCatchingCancellable
import com.apphud.sdk.managers.RequestManager.parser
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal class RemoteRepository(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val customerMapper: CustomerMapper,
    private val purchaseBodyFactory: PurchaseBodyFactory,
    private val registrationBodyFactory: RegistrationBodyFactory,
    private val productMapper: ProductMapper,
) {

    suspend fun getCustomers(
        needPaywalls: Boolean,
        isNew: Boolean,
        userId: UserId? = null,
        email: String? = null,
    ): Result<ApphudUser> =
        runCatchingCancellable {
            val request =
                buildPostRequest(CUSTOMERS_URL, registrationBodyFactory.create(needPaywalls, isNew, userId, email))
            executeForResponse<CustomerDto>(request)
        }
            .recoverCatching { e ->
                val message = e.message ?: "Registration failed"
                throw ApphudError(message, null, APPHUD_ERROR_NO_INTERNET, e)
            }
            .mapCatching { response ->
                response.data.results?.let { customerDto ->
                    customerMapper.map(customerDto)
                } ?: throw ApphudError("Registration failed")
            }

    suspend fun getPurchased(purchaseContext: PurchaseContext): Result<ApphudUser> =
        runCatchingCancellable {
            val request =
                buildPostRequest(SUBSCRIPTIONS_URL, purchaseBodyFactory.create(purchaseContext))
            executeForResponse<CustomerDto>(request)
        }
            .recoverCatching { e ->
                val message = e.message ?: "Purchase failed"
                throw ApphudError(message, null, APPHUD_ERROR_NO_INTERNET, e)
            }
            .mapCatching { response ->
                response.data.results?.let { customerDto ->
                    customerMapper.map(customerDto)
                } ?: throw ApphudError("Purchase failed")
            }

    suspend fun restorePurchased(
        apphudProduct: ApphudProduct? = null,
        purchases: List<PurchaseRecordDetails>,
        observerMode: Boolean,
    ): Result<ApphudUser> =
        runCatchingCancellable {
            val request =
                buildPostRequest(
                    SUBSCRIPTIONS_URL,
                    purchaseBodyFactory.create(apphudProduct, purchases, observerMode)
                )
            executeForResponse<CustomerDto>(request)
        }
            .recoverCatching { e ->
                val message = e.message ?: "Purchase failed"
                throw ApphudError(message, null, APPHUD_ERROR_NO_INTERNET, e)
            }
            .mapCatching { response ->
                response.data.results?.let { customerDto ->
                    customerMapper.map(customerDto)
                } ?: throw ApphudError("Purchase failed")
            }

    suspend fun getProducts(getProductsParams: GetProductsParams): Result<List<ApphudGroup>> =
        runCatchingCancellable {
            val paramsMap = mapOf(
                "request_time" to getProductsParams.requestTime,
                "device_id" to getProductsParams.deviceId,
                "user_id" to getProductsParams.userId,
            )
            val request = buildGetRequest(PRODUCTS_URL, paramsMap)
            executeForResponse<List<ApphudGroupDto>>(request)
        }
            .recoverCatching { e ->
                val message = e.message ?: "Purchase failed"
                throw ApphudError(message, null, APPHUD_ERROR_NO_INTERNET, e)
            }
            .mapCatching { response ->
                response.data.results?.let { customerDto ->
                    productMapper.map(customerDto)
                } ?: throw ApphudError("Purchase failed")
            }

    private suspend inline fun <reified T> executeForResponse(request: Request): ResponseDto<T> =
        withContext(Dispatchers.IO) {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val message =
                        "finish ${request.method} request ${request.url} " +
                                "failed with code: ${response.code} response: ${
                                    response.body?.string()
                                }"
                    ApphudLog.logE(message)
                    error(message)
                }
                val json = response.body?.string() ?: error(
                    "finish ${request.method} request ${request.url} with empty body"
                )
                val type = object : TypeToken<ResponseDto<T>>() {}.type
                gson.fromJson(json, type)
            }
        }

    private fun buildPostRequest(
        url: HttpUrl,
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

    private fun buildGetRequest(url: HttpUrl, params: Map<String, String>): Request {
        val httpUrl = url.newBuilder().apply {
            params.forEach { (key, value) ->
                addQueryParameter(key, value)
            }
        }.build()

        val request = Request.Builder()
        return request.url(httpUrl)
            .get()
            .build()
    }

    private companion object {
        val CUSTOMERS_URL = "https://gateway.apphud.com/v1/customers".toHttpUrl()
        val SUBSCRIPTIONS_URL = "https://gateway.apphud.com/v1/subscriptions".toHttpUrl()
        val PRODUCTS_URL = "https://gateway.apphud.com/v2/products".toHttpUrl()
    }
}
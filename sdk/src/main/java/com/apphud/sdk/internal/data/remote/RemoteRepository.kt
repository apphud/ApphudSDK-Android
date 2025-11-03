package com.apphud.sdk.internal.data.remote

import android.util.Log
import com.apphud.sdk.APPHUD_ERROR_NO_INTERNET
import com.apphud.sdk.ApphudError
import com.apphud.sdk.UserId
import com.apphud.sdk.domain.ApphudGroup
import com.apphud.sdk.domain.ApphudProduct
import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.domain.Attribution
import com.apphud.sdk.domain.PurchaseRecordDetails
import com.apphud.sdk.internal.data.dto.ApphudGroupDto
import com.apphud.sdk.internal.data.dto.AttributionDto
import com.apphud.sdk.internal.data.dto.AttributionRequestDto
import com.apphud.sdk.internal.data.dto.CustomerDto
import com.apphud.sdk.internal.data.dto.GrantPromotionalDto
import com.apphud.sdk.internal.data.dto.NotificationDto
import com.apphud.sdk.internal.data.dto.PaywallEventDto
import com.apphud.sdk.internal.data.dto.ReadNotificationsRequestDto
import com.apphud.sdk.internal.data.mapper.CustomerMapper
import com.apphud.sdk.internal.data.mapper.ProductMapper
import com.apphud.sdk.internal.domain.mapper.NotificationMapper
import com.apphud.sdk.internal.domain.model.GetProductsParams
import com.apphud.sdk.internal.domain.model.Notification
import com.apphud.sdk.internal.domain.model.PurchaseContext
import com.apphud.sdk.internal.util.mapCatchingCancellable
import com.apphud.sdk.internal.data.network.UrlProvider
import com.apphud.sdk.internal.util.recoverCatchingCancellable
import com.apphud.sdk.internal.util.runCatchingCancellable
import com.apphud.sdk.mappers.AttributionMapper
import com.google.gson.Gson
import okhttp3.OkHttpClient

@Suppress("LongParameterList")
internal class RemoteRepository(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val customerMapper: CustomerMapper,
    private val purchaseBodyFactory: PurchaseBodyFactory,
    private val registrationBodyFactory: RegistrationBodyFactory,
    private val productMapper: ProductMapper,
    private val attributionMapper: AttributionMapper,
    private val notificationMapper: NotificationMapper,
    private val urlProvider: UrlProvider,
) {

    suspend fun getCustomers(
        needPaywalls: Boolean,
        isNew: Boolean,
        userId: UserId? = null,
        email: String? = null,
    ): Result<ApphudUser> =
        runCatchingCancellable {
            val request =
                buildPostRequest(urlProvider.customersUrl, registrationBodyFactory.create(needPaywalls, isNew, userId, email))
            executeForResponse<CustomerDto>(okHttpClient, gson, request)
        }
            .recoverCatchingCancellable { e ->
                val message = e.message ?: "Registration failed"
                throw ApphudError(message, null, APPHUD_ERROR_NO_INTERNET, e)
            }
            .mapCatchingCancellable { response ->
                response.data.results?.let { customerDto ->
                    customerMapper.map(customerDto)
                } ?: throw ApphudError("Registration failed")
            }

    suspend fun getPurchased(purchaseContext: PurchaseContext): Result<ApphudUser> =
        runCatchingCancellable {
            val request =
                buildPostRequest(urlProvider.subscriptionsUrl, purchaseBodyFactory.create(purchaseContext))
            executeForResponse<CustomerDto>(okHttpClient, gson, request)
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
                    urlProvider.subscriptionsUrl,
                    purchaseBodyFactory.create(apphudProduct, purchases, observerMode)
                )
            executeForResponse<CustomerDto>(okHttpClient, gson, request)
        }
            .recoverCatching { e ->
                val message = e.message ?: "Restore purchase failed"
                throw ApphudError(message, null, APPHUD_ERROR_NO_INTERNET, e)
            }
            .mapCatching { response ->
                response.data.results?.let { customerDto ->
                    customerMapper.map(customerDto)
                } ?: throw ApphudError("Restore purchase failed")
            }

    suspend fun getProducts(getProductsParams: GetProductsParams): Result<List<ApphudGroup>> =
        runCatchingCancellable {
            val paramsMap = mapOf(
                "request_time" to getProductsParams.requestTime,
                "device_id" to getProductsParams.deviceId,
                "user_id" to getProductsParams.userId,
            )
            val request = buildGetRequest(urlProvider.productsUrl, paramsMap)
            executeForResponse<List<ApphudGroupDto>>(okHttpClient, gson, request)
        }
            .recoverCatching { e ->
                val message = e.message ?: "Parse products failed"
                throw ApphudError(message, null, APPHUD_ERROR_NO_INTERNET, e)
            }
            .mapCatching { response ->
                response.data.results?.let { customerDto ->
                    productMapper.map(customerDto)
                } ?: throw ApphudError("Parse products failed")
            }

    suspend fun sendAttribution(
        attributionRequestBody: AttributionRequestDto,
    ): Result<Attribution> =
        runCatchingCancellable {
            val request = buildPostRequest(urlProvider.attributionUrl, attributionRequestBody)
            executeForResponse<AttributionDto>(okHttpClient, gson, request)
        }
            .recoverCatching { e ->
                val message = e.message ?: "Failed to send attribution"
                throw ApphudError(message, null, APPHUD_ERROR_NO_INTERNET, e)
            }
            .mapCatching { response ->
                response.data.results?.let { attributionDto ->
                    attributionMapper.map(attributionDto)
                } ?: throw ApphudError("Failed to send attribution")
            }

    suspend fun grantPromotional(
        grantPromotionalDto: GrantPromotionalDto,
    ): Result<ApphudUser> =
        runCatchingCancellable {
            val request = buildPostRequest(urlProvider.promotionsUrl, grantPromotionalDto)
            executeForResponse<CustomerDto>(okHttpClient, gson, request)
        }
            .recoverCatching { e ->
                val message = e.message ?: "Promotional grant failed"
                throw ApphudError(message, null, APPHUD_ERROR_NO_INTERNET, e)
            }
            .mapCatching { response ->
                response.data.results?.let { customerDto ->
                    customerMapper.map(customerDto)
                } ?: throw ApphudError("Promotional grant failed")
            }

    suspend fun trackEvent(event: PaywallEventDto): Result<Unit> =
        runCatchingCancellable {
            val request = buildPostRequest(urlProvider.eventsUrl, event)
            executeForResponse<Unit>(okHttpClient, gson, request)
        }
            .recoverCatching { e ->
                val message = e.message ?: "Failed to track paywall event"
                throw ApphudError(message, null, APPHUD_ERROR_NO_INTERNET, e)
            }
            .map { }

    suspend fun getNotifications(deviceId: String): Result<List<Notification>> =
        runCatchingCancellable {
            val paramsMap = mapOf(
                "device_id" to deviceId,
            )
            val request = buildGetRequest(urlProvider.notificationsUrl, paramsMap)
            executeForResponse<List<NotificationDto>>(okHttpClient, gson, request)
        }
            .recoverCatching { e ->
                val message = e.message ?: "Failed to get notifications"
                throw ApphudError(message, null, APPHUD_ERROR_NO_INTERNET, e)
            }
            .mapCatching { response ->
                response.data.results?.let { notificationsDto ->
                    notificationMapper.map(notificationsDto)
                } ?: throw ApphudError("Failed to get notifications")
            }

    suspend fun readAllNotifications(ruleId: String, deviceId: String): Result<Unit> =
        runCatchingCancellable {
            val requestDto = ReadNotificationsRequestDto(
                deviceId = deviceId,
                ruleId = ruleId
            )
            val request = buildPostRequest(urlProvider.notificationsReadUrl, requestDto)
            executeForResponse<Unit>(okHttpClient, gson, request)
        }
            .recoverCatching { e ->
                val message = e.message ?: "Failed to mark notifications as read"
                throw ApphudError(message, null, APPHUD_ERROR_NO_INTERNET, e)
            }
            .map { }
}
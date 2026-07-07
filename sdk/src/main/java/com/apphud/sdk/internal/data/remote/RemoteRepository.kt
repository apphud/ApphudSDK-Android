package com.apphud.sdk.internal.data.remote

import android.util.Log
import com.apphud.sdk.APPHUD_ERROR_NO_INTERNET
import com.apphud.sdk.ApphudError
import com.apphud.sdk.UserId
import com.apphud.sdk.domain.ApphudGroup
import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.ApphudProduct
import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.domain.Attribution
import com.apphud.sdk.domain.PurchaseRecordDetails
import com.apphud.sdk.internal.data.dto.ApphudGroupDto
import com.apphud.sdk.internal.data.dto.ApphudPaywallDto
import com.apphud.sdk.internal.data.dto.AttributionDto
import com.apphud.sdk.internal.data.dto.AttributionRequestDto
import com.apphud.sdk.internal.data.dto.CustomerDto
import com.apphud.sdk.internal.data.dto.DeeplinkAttributionRequestDto
import com.apphud.sdk.internal.data.dto.GrantPromotionalDto
import com.apphud.sdk.internal.data.dto.NotificationDto
import com.apphud.sdk.internal.data.dto.PaywallEventDto
import com.apphud.sdk.internal.data.dto.PushTokenDto
import com.apphud.sdk.internal.data.dto.ReadNotificationsRequestDto
import com.apphud.sdk.internal.data.dto.RuleEventDto
import com.apphud.sdk.internal.data.mapper.CustomerMapper
import com.apphud.sdk.internal.data.mapper.PaywallsMapper
import com.apphud.sdk.internal.data.mapper.ProductMapper
import com.apphud.sdk.internal.domain.mapper.NotificationMapper
import com.apphud.sdk.internal.domain.model.GetProductsParams
import com.apphud.sdk.internal.domain.model.Notification
import com.apphud.sdk.internal.domain.model.PurchaseContext
import com.apphud.sdk.internal.util.mapCatchingCancellable
import com.apphud.sdk.internal.ApphudDispatchers
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
    private val paywallsMapper: PaywallsMapper,
    private val urlProvider: UrlProvider,
    private val dispatchers: ApphudDispatchers,
) {

    private val previousUser: ApphudUser?
        get() = runCatching {
            com.apphud.sdk.internal.ServiceLocator.instance.userRepository.getCurrentUser()
        }.getOrNull()

    suspend fun getCustomers(
        needPlacements: Boolean,
        isNew: Boolean,
        userId: UserId? = null,
        email: String? = null,
    ): Result<ApphudUser> =
        runCatchingCancellable {
            val request =
                buildPostRequest(urlProvider.customersUrl, registrationBodyFactory.create(needPlacements, isNew, userId, email))
            executeForResponse<CustomerDto>(okHttpClient, gson, request, dispatchers.io)
        }
            .recoverCatchingCancellable { e ->
                val message = e.message ?: "Registration failed"
                throw ApphudError.from(message, originalCause = e)
            }
            .mapCatchingCancellable { response ->
                urlProvider.updateConnectDomainUrl(response.data.meta)
                response.data.results?.let { customerDto ->
                    customerMapper.map(customerDto, previousUser)
                } ?: throw ApphudError("Registration failed")
            }

    suspend fun getPurchased(purchaseContext: PurchaseContext): Result<ApphudUser> =
        runCatchingCancellable {
            val request =
                buildPostRequest(urlProvider.subscriptionsUrl, purchaseBodyFactory.create(purchaseContext))
            executeForResponse<CustomerDto>(okHttpClient, gson, request, dispatchers.io)
        }
            .recoverCatching { e ->
                val message = e.message ?: "Purchase failed"
                throw ApphudError(message, originalCause = e)
            }
            .mapCatching { response ->
                urlProvider.updateConnectDomainUrl(response.data.meta)
                response.data.results?.let { customerDto ->
                    customerMapper.map(customerDto, previousUser)
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
            executeForResponse<CustomerDto>(okHttpClient, gson, request, dispatchers.io)
        }
            .recoverCatching { e ->
                val message = e.message ?: "Restore purchase failed"
                throw ApphudError(message, originalCause = e)
            }
            .mapCatching { response ->
                urlProvider.updateConnectDomainUrl(response.data.meta)
                response.data.results?.let { customerDto ->
                    customerMapper.map(customerDto, previousUser)
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
            executeForResponse<List<ApphudGroupDto>>(okHttpClient, gson, request, dispatchers.io)
        }
            .recoverCatching { e ->
                val message = e.message ?: "Parse products failed"
                throw ApphudError(message, originalCause = e)
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
            executeForResponse<AttributionDto>(okHttpClient, gson, request, dispatchers.io)
        }
            .recoverCatching { e ->
                val message = e.message ?: "Failed to send attribution"
                throw ApphudError(message, originalCause = e)
            }
            .mapCatching { response ->
                response.data.results?.let { attributionDto ->
                    attributionMapper.map(attributionDto)
                } ?: throw ApphudError("Failed to send attribution")
            }

    suspend fun deeplinkAttribution(
        deviceId: String,
        bundleId: String,
        url: String? = null,
        visitorId: String? = null,
    ): Result<Map<String, Any>?> =
        runCatchingCancellable {
            val request = buildPostRequest(
                urlProvider.deeplinkAttributionUrl,
                DeeplinkAttributionRequestDto(
                    deviceId = deviceId,
                    bundleId = bundleId,
                    url = url,
                    visitorId = visitorId,
                ),
            )
            executeForRawMap(okHttpClient, gson, request, dispatchers.io)
        }
            .recoverCatching { e ->
                val message = e.message ?: "Failed to fetch deeplink attribution"
                throw ApphudError(message, originalCause = e)
            }
            .mapCatching { response ->
                @Suppress("UNCHECKED_CAST")
                val data = response?.get("data") as? Map<String, Any>
                @Suppress("UNCHECKED_CAST")
                data?.get("results") as? Map<String, Any>
            }

    suspend fun grantPromotional(
        grantPromotionalDto: GrantPromotionalDto,
    ): Result<ApphudUser> =
        runCatchingCancellable {
            val request = buildPostRequest(urlProvider.promotionsUrl, grantPromotionalDto)
            executeForResponse<CustomerDto>(okHttpClient, gson, request, dispatchers.io)
        }
            .recoverCatching { e ->
                val message = e.message ?: "Promotional grant failed"
                throw ApphudError(message, originalCause = e)
            }
            .mapCatching { response ->
                urlProvider.updateConnectDomainUrl(response.data.meta)
                response.data.results?.let { customerDto ->
                    customerMapper.map(customerDto, previousUser)
                } ?: throw ApphudError("Promotional grant failed")
            }

    suspend fun trackEvent(event: PaywallEventDto): Result<Unit> =
        runCatchingCancellable {
            val request = buildPostRequest(urlProvider.eventsUrl, event)
            executeForResponse<Unit>(okHttpClient, gson, request, dispatchers.io)
        }
            .recoverCatching { e ->
                val message = e.message ?: "Failed to track paywall event"
                throw ApphudError(message, originalCause = e)
            }
            .map { }

    suspend fun getNotifications(deviceId: String): Result<List<Notification>> =
        runCatchingCancellable {
            val paramsMap = mapOf(
                "device_id" to deviceId,
            )
            val request = buildGetRequest(urlProvider.notificationsUrl, paramsMap)
            executeForResponse<List<NotificationDto>>(okHttpClient, gson, request, dispatchers.io)
        }
            .recoverCatching { e ->
                val message = e.message ?: "Failed to get notifications"
                throw ApphudError(message, originalCause = e)
            }
            .mapCatching { response ->
                response.data.results?.let { notificationsDto ->
                    notificationMapper.map(notificationsDto)
                } ?: throw ApphudError("Failed to get notifications")
            }

    suspend fun getPaywall(identifier: String, deviceId: String): Result<ApphudPaywall> =
        runCatchingCancellable {
            val request = buildGetRequest(
                urlProvider.paywallConfigUrl(identifier),
                mapOf("device_id" to deviceId),
            )
            executeForResponse<ApphudPaywallDto>(okHttpClient, gson, request, dispatchers.io)
        }
            .recoverCatchingCancellable { e ->
                val message = e.message ?: "Failed to fetch paywall config"
                throw ApphudError(message, originalCause = e)
            }
            .mapCatchingCancellable { response ->
                response.data.results?.let { paywallDto ->
                    paywallsMapper.map(paywallDto)
                } ?: throw ApphudError("Failed to fetch paywall config")
            }

    suspend fun trackRuleEvent(event: RuleEventDto): Result<Unit> =
        runCatchingCancellable {
            val request = buildPostRequest(urlProvider.ruleEventsUrl, event)
            executeForResponse<Unit>(okHttpClient, gson, request, dispatchers.io)
        }
            .recoverCatching { e ->
                val message = e.message ?: "Failed to track rule event"
                throw ApphudError(message, originalCause = e)
            }
            .map { }

    suspend fun submitPushToken(deviceId: String, token: String): Result<Unit> =
        runCatchingCancellable {
            val request = buildPutRequest(
                urlProvider.pushTokenUrl,
                PushTokenDto(deviceId = deviceId, pushToken = token),
            )
            executeForResponse<Unit>(okHttpClient, gson, request, dispatchers.io)
        }
            .recoverCatching { e ->
                val message = e.message ?: "Failed to submit push token"
                throw ApphudError(message, originalCause = e)
            }
            .map { }

    suspend fun readAllNotifications(ruleId: String, deviceId: String): Result<Unit> =
        runCatchingCancellable {
            val requestDto = ReadNotificationsRequestDto(
                deviceId = deviceId,
                ruleId = ruleId
            )
            val request = buildPostRequest(urlProvider.notificationsReadUrl, requestDto)
            executeForResponse<Unit>(okHttpClient, gson, request, dispatchers.io)
        }
            .recoverCatching { e ->
                val message = e.message ?: "Failed to mark notifications as read"
                throw ApphudError(message, originalCause = e)
            }
            .map { }
}
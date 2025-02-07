package com.apphud.sdk.internal.data.remote

import com.apphud.sdk.APPHUD_ERROR_NO_INTERNET
import com.apphud.sdk.ApphudError
import com.apphud.sdk.ApphudInternal
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.UserId
import com.apphud.sdk.body.RegistrationBody
import com.apphud.sdk.client.dto.CustomerDto
import com.apphud.sdk.client.dto.ResponseDto
import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.internal.data.mapper.CustomerMapper
import com.apphud.sdk.internal.provider.RegistrationProvider
import com.apphud.sdk.internal.util.runCatchingCancellable
import com.apphud.sdk.managers.RequestManager.parser
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URL

internal class RemoteRepository(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val registrationProvider: RegistrationProvider,
    private val customerMapper: CustomerMapper,
) {

    suspend fun getCustomers(
        needPaywalls: Boolean,
        isNew: Boolean,
        userId: UserId? = null,
        email: String? = null,
    ): Result<ApphudUser> =
        runCatchingCancellable {
            val request =
                buildPostRequest(URL(CUSTOMERS_URL), createRegistrationBody(needPaywalls, isNew, userId, email))
            executeForResponse(request, CustomerDto::class.java)
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

    private suspend fun <T> executeForResponse(request: Request, clazz: Class<T>): ResponseDto<T> =
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
                val type = TypeToken.getParameterized(ResponseDto::class.java, clazz).type
                gson.fromJson(json, type)
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

    private fun createRegistrationBody(
        needPaywalls: Boolean,
        isNew: Boolean,
        userId: UserId? = null,
        email: String? = null,
    ): RegistrationBody =
        RegistrationBody(
            locale = registrationProvider.getLocale(),
            sdkVersion = registrationProvider.getSdkVersion(),
            appVersion = registrationProvider.getAppVersion(),
            deviceFamily = registrationProvider.getDeviceFamily(),
            platform = registrationProvider.getPlatform(),
            deviceType = registrationProvider.getDeviceType(),
            osVersion = registrationProvider.getOsVersion(),
            startAppVersion = registrationProvider.getStartAppVersion(),
            idfv = registrationProvider.getIdfv(),
            idfa = registrationProvider.getIdfa(),
            androidId = registrationProvider.getAndroidId(),
            userId = userId ?: ApphudInternal.userId,
            deviceId = registrationProvider.getDeviceId(),
            timeZone = registrationProvider.getTimeZone(),
            isSandbox = registrationProvider.isSandbox(),
            isNew = isNew,
            needPaywalls = needPaywalls,
            needPlacements = needPaywalls,
            firstSeen = registrationProvider.getFirstSeen(),
            sdkLaunchedAt = registrationProvider.getSdkLaunchedAt(),
            requestTime = registrationProvider.getRequestTime(),
            installSource = registrationProvider.getInstallSource(),
            observerMode = registrationProvider.getObserverMode(),
            fromWeb2web = registrationProvider.getFromWeb2Web(),
            email = email
        )

    private companion object {
        const val CUSTOMERS_URL = "https://gateway.apphud.com/v1/customers"
    }
}
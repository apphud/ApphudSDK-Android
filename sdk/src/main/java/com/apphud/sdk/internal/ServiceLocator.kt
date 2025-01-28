package com.apphud.sdk.internal

import android.content.Context
import com.apphud.sdk.internal.data.network.HeadersInterceptor
import com.apphud.sdk.internal.data.network.HostSwitcherInterceptor
import com.apphud.sdk.internal.data.network.HttpRetryInterceptor
import com.apphud.sdk.internal.domain.model.ApiKey
import com.apphud.sdk.internal.provider.RegistrationProvider
import com.apphud.sdk.internal.remote.RemoteRepository
import com.apphud.sdk.storage.SharedPreferencesStorage
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

internal class ServiceLocator private constructor(
    private val applicationContext: Context,
    private val apiKey: ApiKey,
) {

    private val gson: Gson = Gson()

    private val registrationProvider: RegistrationProvider =
        RegistrationProvider(applicationContext, SharedPreferencesStorage)

    private val okHttpClient: OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (registrationProvider.isSandbox()) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.BASIC
                }
            })
            .addInterceptor(HeadersInterceptor(apiKey))
            .addInterceptor(HostSwitcherInterceptor(OkHttpClient()))
            .addInterceptor(HttpRetryInterceptor())
            .build()

    val remoteRepository: RemoteRepository =
        RemoteRepository(okHttpClient, gson, registrationProvider)

    internal class ServiceLocatorInstanceFactory {

        fun create(
            applicationContext: Context,
            apiKey: ApiKey,
        ): ServiceLocator = synchronized(ServiceLocatorInstanceFactory::class.java) {
            if (_instance != null) {
                error("Instance already exist")
            }

            ServiceLocator(
                applicationContext = applicationContext,
                apiKey = apiKey
            ).also { serviceLocator ->
                _instance = serviceLocator
            }
        }
    }

    companion object {
        @Volatile
        private var _instance: ServiceLocator? = null

        val instance: ServiceLocator
            get() = _instance ?: throw IllegalStateException(
                """To get an instance of the ServiceLocator, you must first call
                   Apphud.start()""".trimIndent(),
            )
    }
}
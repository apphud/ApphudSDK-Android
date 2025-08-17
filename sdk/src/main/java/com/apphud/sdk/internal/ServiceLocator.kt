package com.apphud.sdk.internal

import android.content.Context
import com.apphud.sdk.ApphudInternal
import com.apphud.sdk.ApphudRuleCallback
import com.apphud.sdk.internal.data.local.LifecycleRepository
import com.apphud.sdk.internal.data.local.LocalRulesScreenRepository
import com.apphud.sdk.internal.data.mapper.CustomerMapper
import com.apphud.sdk.internal.data.mapper.PaywallsMapper
import com.apphud.sdk.internal.data.mapper.PlacementsMapper
import com.apphud.sdk.internal.data.mapper.ProductMapper
import com.apphud.sdk.internal.data.mapper.RuleScreenMapper
import com.apphud.sdk.internal.data.mapper.SubscriptionMapper
import com.apphud.sdk.internal.data.network.HeadersInterceptor
import com.apphud.sdk.internal.data.network.HostSwitcherInterceptor
import com.apphud.sdk.internal.data.network.HttpRetryInterceptor
import com.apphud.sdk.internal.data.remote.PurchaseBodyFactory
import com.apphud.sdk.internal.data.remote.RegistrationBodyFactory
import com.apphud.sdk.internal.data.remote.RemoteRepository
import com.apphud.sdk.internal.data.remote.ScreenRemoteRepository
import com.apphud.sdk.internal.data.remote.UserRemoteRepository
import com.apphud.sdk.internal.domain.FetchMostActualRuleScreenUseCase
import com.apphud.sdk.internal.domain.FetchRulesScreenUseCase
import com.apphud.sdk.internal.domain.RenderPaywallPropertiesUseCase
import com.apphud.sdk.internal.domain.mapper.DateTimeMapper
import com.apphud.sdk.internal.domain.mapper.NotificationMapper
import com.apphud.sdk.internal.domain.model.ApiKey
import com.apphud.sdk.internal.presentation.rule.RuleController
import com.apphud.sdk.internal.provider.RegistrationProvider
import com.apphud.sdk.mappers.AttributionMapper
import com.apphud.sdk.storage.SharedPreferencesStorage
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

internal class ServiceLocator(
    private val applicationContext: Context,
    val ruleCallback: ApphudRuleCallback,
    private val apiKey: ApiKey,
) {

    private val gson: Gson = Gson()

    private val paywallsMapper = PaywallsMapper(gson)
    private val placementsMapper = PlacementsMapper(paywallsMapper)
    private val subscriptionMapper = SubscriptionMapper()
    private val customerMapper =
        CustomerMapper(subscriptionMapper, paywallsMapper, placementsMapper)

    private val registrationProvider: RegistrationProvider =
        RegistrationProvider(applicationContext, SharedPreferencesStorage)

    private val okHttpClient: OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level =
                        if (registrationProvider.isSandbox()) {
                            HttpLoggingInterceptor.Level.BODY
                        } else {
                            HttpLoggingInterceptor.Level.BASIC
                        }
                }
            )
            .addInterceptor(HeadersInterceptor(apiKey))
            .addInterceptor(HostSwitcherInterceptor(OkHttpClient()))
            .addInterceptor(HttpRetryInterceptor())
            .build()

    private val okHttpClientWithoutHeaders: OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level =
                        if (registrationProvider.isSandbox()) {
                            HttpLoggingInterceptor.Level.BODY
                        } else {
                            HttpLoggingInterceptor.Level.BASIC
                        }
                }
            )
            .addInterceptor(HostSwitcherInterceptor(OkHttpClient()))
            .addInterceptor(HttpRetryInterceptor())
            .build()

    val remoteRepository: RemoteRepository =
        RemoteRepository(
            okHttpClient = okHttpClient,
            gson = gson,
            customerMapper = customerMapper,
            purchaseBodyFactory = PurchaseBodyFactory(),
            registrationBodyFactory = RegistrationBodyFactory(registrationProvider),
            productMapper = ProductMapper(),
            attributionMapper = AttributionMapper(),
            notificationMapper = NotificationMapper(),
        )

    private val screenRemoteRepository: ScreenRemoteRepository =
        ScreenRemoteRepository(
            okHttpClient = okHttpClientWithoutHeaders,
            gson = gson,
            apiKey = apiKey
        )

    val localRulesScreenRepository: LocalRulesScreenRepository =
        LocalRulesScreenRepository(
            context = applicationContext,
            gson = gson,
            ruleScreenMapper = RuleScreenMapper()
        )

    val lifecycleRepository: LifecycleRepository = LifecycleRepository()

    val userRemoteRepository: UserRemoteRepository =
        UserRemoteRepository(
            okHttpClient = okHttpClient,
            gson = gson,
            attributionMapper = AttributionMapper()
        )

    val fetchRulesScreenUseCase: FetchRulesScreenUseCase =
        FetchRulesScreenUseCase(
            remoteRepository = remoteRepository,
            screenRemoteRepository = screenRemoteRepository,
            localRulesScreenRepository = localRulesScreenRepository,
            dateTimeMapper = DateTimeMapper(),
        )

    private val fetchMostActualRuleScreenUseCase: FetchMostActualRuleScreenUseCase =
        FetchMostActualRuleScreenUseCase(
            localRulesScreenRepository = localRulesScreenRepository,
        )

    val renderPaywallPropertiesUseCase: RenderPaywallPropertiesUseCase =
        RenderPaywallPropertiesUseCase()

    val ruleController: RuleController =
        RuleController(
            context = applicationContext,
            fetchRulesScreenUseCase = fetchRulesScreenUseCase,
            fetchMostActualRuleScreenUseCase = fetchMostActualRuleScreenUseCase,
            coroutineScope = ApphudInternal.coroutineScope,
            lifecycleRepository = lifecycleRepository,
            localRulesScreenRepository = localRulesScreenRepository,
            ruleCallback = ruleCallback,
        )

    internal class ServiceLocatorInstanceFactory {

        fun create(
            applicationContext: Context,
            ruleCallback: ApphudRuleCallback,
            apiKey: ApiKey,
        ): ServiceLocator =
            synchronized(ServiceLocatorInstanceFactory::class.java) {
                if (_instance != null) {
                    error("Instance already exist")
                }

                ServiceLocator(
                    applicationContext = applicationContext,
                    ruleCallback = ruleCallback,
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
            get() =
                _instance
                    ?: throw IllegalStateException(
                        """To get an instance of the ServiceLocator, you must first call
                   Apphud.start()""".trimIndent(),
                    )
    }
}

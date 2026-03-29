package com.apphud.sdk.internal

import com.apphud.sdk.ApphudRuleCallback
import com.apphud.sdk.internal.data.AnalyticsTracker
import com.apphud.sdk.internal.data.ProductRepository
import com.apphud.sdk.internal.data.UserDataSource
import com.apphud.sdk.internal.data.UserPropertiesManager
import com.apphud.sdk.internal.data.UserRepository
import com.apphud.sdk.internal.data.local.PaywallRepository
import com.apphud.sdk.internal.data.mapper.ProductMapper
import com.apphud.sdk.internal.data.mapper.RenderResultMapper
import com.apphud.sdk.internal.data.network.HeadersInterceptor
import com.apphud.sdk.internal.data.network.HttpRetryInterceptor
import com.apphud.sdk.internal.data.network.TimeoutInterceptor
import com.apphud.sdk.internal.data.remote.PurchaseBodyFactory
import com.apphud.sdk.internal.data.remote.RegistrationBodyFactory
import com.apphud.sdk.internal.data.remote.RemoteRepository
import com.apphud.sdk.internal.data.remote.RenderRemoteRepository
import com.apphud.sdk.internal.data.remote.ScreenRemoteRepository
import com.apphud.sdk.internal.data.remote.UserRemoteRepository
import com.apphud.sdk.internal.data.serializer.RenderItemsSerializer
import com.apphud.sdk.internal.domain.CollectDeviceIdentifiersUseCase
import com.apphud.sdk.internal.domain.DeviceIdentifiersInteractor
import com.apphud.sdk.internal.domain.FetchMostActualRuleScreenUseCase
import com.apphud.sdk.internal.domain.FetchNativePurchasesUseCase
import com.apphud.sdk.internal.domain.FetchRulesScreenUseCase
import com.apphud.sdk.internal.domain.RegistrationUseCase
import com.apphud.sdk.internal.domain.RenderPaywallPropertiesUseCase
import com.apphud.sdk.internal.domain.ResolveCredentialsUseCase
import com.apphud.sdk.internal.domain.mapper.DateTimeMapper
import com.apphud.sdk.internal.domain.mapper.NotificationMapper
import com.apphud.sdk.internal.domain.model.ApiKey
import com.apphud.sdk.internal.presentation.rule.RuleController
import com.apphud.sdk.internal.provider.RegistrationProvider
import com.apphud.sdk.managers.RequestManager
import com.apphud.sdk.mappers.AttributionMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

internal class SessionComponent(
    val appScope: AppScopeComponent,
    private val apiKey: ApiKey,
    val ruleCallback: ApphudRuleCallback,
    awaitUserRegistration: suspend () -> Unit,
) {

    val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + appScope.dispatchers.io)

    fun cancel() {
        coroutineScope.cancel()
    }

    val userDataSource: UserDataSource = UserDataSource(appScope.storage)

    val userRepository: UserRepository = UserRepository(userDataSource)

    val analyticsTracker: AnalyticsTracker = AnalyticsTracker(
        coroutineScope = coroutineScope,
        userRepository = userRepository,
    )

    private val registrationProvider: RegistrationProvider =
        RegistrationProvider(
            appScope.applicationContext,
            appScope.deviceIdentifiersRepository,
            userRepository,
            analyticsTracker = analyticsTracker,
        )

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
            .addInterceptor(TimeoutInterceptor())
            .addInterceptor(appScope.hostSwitcherInterceptor)
            .addInterceptor(HttpRetryInterceptor())
            .addInterceptor(appScope.prettyLoggingInterceptor)
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
            .addInterceptor(TimeoutInterceptor())
            .addInterceptor(appScope.hostSwitcherInterceptorWithoutHeaders)
            .addInterceptor(HttpRetryInterceptor())
            .addInterceptor(appScope.prettyLoggingInterceptor)
            .build()

    val remoteRepository: RemoteRepository =
        RemoteRepository(
            okHttpClient = okHttpClient,
            gson = appScope.gson,
            customerMapper = appScope.customerMapper,
            purchaseBodyFactory = PurchaseBodyFactory(userRepository),
            registrationBodyFactory = RegistrationBodyFactory(registrationProvider),
            productMapper = ProductMapper(),
            attributionMapper = AttributionMapper(),
            notificationMapper = NotificationMapper(),
            urlProvider = appScope.urlProvider,
            dispatchers = appScope.dispatchers,
        )

    private val screenRemoteRepository: ScreenRemoteRepository =
        ScreenRemoteRepository(
            okHttpClient = okHttpClientWithoutHeaders,
            gson = appScope.gson,
            apiKey = apiKey,
            dispatchers = appScope.dispatchers,
        )

    val userRemoteRepository: UserRemoteRepository =
        UserRemoteRepository(
            okHttpClient = okHttpClient,
            gson = appScope.gson,
            attributionMapper = AttributionMapper(),
            dispatchers = appScope.dispatchers,
        )

    private val renderResultMapper: RenderResultMapper = RenderResultMapper()

    val renderRemoteRepository: RenderRemoteRepository =
        RenderRemoteRepository(
            okHttpClient = okHttpClient,
            gson = appScope.gson,
            renderResultMapper = renderResultMapper,
            dispatchers = appScope.dispatchers,
        )

    val fetchRulesScreenUseCase: FetchRulesScreenUseCase =
        FetchRulesScreenUseCase(
            remoteRepository = remoteRepository,
            screenRemoteRepository = screenRemoteRepository,
            localRulesScreenRepository = appScope.localRulesScreenRepository,
            dateTimeMapper = DateTimeMapper(),
        )

    private val fetchMostActualRuleScreenUseCase: FetchMostActualRuleScreenUseCase =
        FetchMostActualRuleScreenUseCase(
            localRulesScreenRepository = appScope.localRulesScreenRepository,
        )

    val renderPaywallPropertiesUseCase: RenderPaywallPropertiesUseCase =
        RenderPaywallPropertiesUseCase(renderRemoteRepository)

    val paywallRepository: PaywallRepository = PaywallRepository()

    val renderItemsSerializer: RenderItemsSerializer = RenderItemsSerializer(appScope.gson)

    val renderResultMapperWithSerializer: RenderResultMapper = RenderResultMapper(renderItemsSerializer)

    val ruleController: RuleController =
        RuleController(
            context = appScope.applicationContext,
            fetchRulesScreenUseCase = fetchRulesScreenUseCase,
            fetchMostActualRuleScreenUseCase = fetchMostActualRuleScreenUseCase,
            coroutineScope = coroutineScope,
            lifecycleRepository = appScope.lifecycleRepository,
            localRulesScreenRepository = appScope.localRulesScreenRepository,
            ruleCallback = ruleCallback,
            dispatchers = appScope.dispatchers,
        )

    val paywallEventManager: PaywallEventManager = PaywallEventManager()

    val productRepository: ProductRepository = ProductRepository()

    val userPropertiesManager: UserPropertiesManager = UserPropertiesManager(
        coroutineScope = coroutineScope,
        userRepository = userRepository,
        storage = appScope.storage,
        awaitUserRegistration = awaitUserRegistration,
        dispatchers = appScope.dispatchers,
    )

    val offeringsCallbackManager: OfferingsCallbackManager = OfferingsCallbackManager(
        userRepository = userRepository,
        productRepository = productRepository,
        analyticsTracker = analyticsTracker,
    )

    val resolveCredentialsUseCase: ResolveCredentialsUseCase =
        ResolveCredentialsUseCase(userRepository = userRepository)

    val registrationUseCase: RegistrationUseCase =
        RegistrationUseCase(
            userRepository = userRepository,
            userDataSource = userDataSource,
            requestManager = RequestManager
        )

    val collectDeviceIdentifiersUseCase: CollectDeviceIdentifiersUseCase =
        CollectDeviceIdentifiersUseCase(appScope.deviceIdentifiersRepository)

    val deviceIdentifiersInteractor: DeviceIdentifiersInteractor =
        DeviceIdentifiersInteractor(collectDeviceIdentifiersUseCase, registrationUseCase)

    val fetchNativePurchasesUseCase: FetchNativePurchasesUseCase by lazy {
        FetchNativePurchasesUseCase(
            billingWrapper = appScope.billingWrapper,
            userRepository = userRepository,
        )
    }
}

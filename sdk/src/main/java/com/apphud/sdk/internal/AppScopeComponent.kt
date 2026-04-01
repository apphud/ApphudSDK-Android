package com.apphud.sdk.internal

import android.content.Context
import com.apphud.sdk.internal.data.DeviceIdentifiersDataSource
import com.apphud.sdk.internal.data.DeviceIdentifiersRepository
import com.apphud.sdk.internal.data.local.LifecycleRepository
import com.apphud.sdk.internal.data.local.LocalRulesScreenRepository
import com.apphud.sdk.internal.data.mapper.CustomerMapper
import com.apphud.sdk.internal.data.mapper.PaywallsMapper
import com.apphud.sdk.internal.data.mapper.PlacementsMapper
import com.apphud.sdk.internal.data.mapper.RuleScreenMapper
import com.apphud.sdk.internal.data.mapper.SubscriptionMapper
import com.apphud.sdk.internal.data.network.HostSwitcherInterceptor
import com.apphud.sdk.internal.data.network.PrettyHttpLoggingInterceptor
import com.apphud.sdk.internal.data.network.PrettyJsonFormatter
import com.apphud.sdk.internal.data.network.UrlProvider
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.internal.store.SdkEffect
import com.apphud.sdk.internal.store.SdkEvent
import com.apphud.sdk.internal.store.SdkState
import com.apphud.sdk.internal.store.Store
import com.apphud.sdk.internal.store.sdkReducer
import com.apphud.sdk.storage.SharedPreferencesStorage
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

internal class AppScopeComponent(val applicationContext: Context) {

    val dispatchers: ApphudDispatchers = ApphudDispatchers()

    val gson: Gson = Gson()

    val storage: SharedPreferencesStorage = SharedPreferencesStorage(applicationContext)

    private val paywallsMapper = PaywallsMapper(gson)
    private val placementsMapper = PlacementsMapper(paywallsMapper)
    private val subscriptionMapper = SubscriptionMapper()
    val customerMapper = CustomerMapper(subscriptionMapper, placementsMapper)

    val deviceIdentifiersDataSource: DeviceIdentifiersDataSource =
        DeviceIdentifiersDataSource(applicationContext, storage)

    val deviceIdentifiersRepository: DeviceIdentifiersRepository =
        DeviceIdentifiersRepository(deviceIdentifiersDataSource)

    val urlProvider = UrlProvider()

    val hostSwitcherInterceptor = HostSwitcherInterceptor(OkHttpClient(), urlProvider)
    val hostSwitcherInterceptorWithoutHeaders = HostSwitcherInterceptor(OkHttpClient(), urlProvider)

    val prettyGson: Gson by lazy {
        GsonBuilder().setPrettyPrinting().create()
    }
    val prettyJsonFormatter: PrettyJsonFormatter by lazy { PrettyJsonFormatter(prettyGson) }
    val prettyLoggingInterceptor: PrettyHttpLoggingInterceptor by lazy {
        PrettyHttpLoggingInterceptor(prettyJsonFormatter)
    }

    val localRulesScreenRepository: LocalRulesScreenRepository =
        LocalRulesScreenRepository(
            context = applicationContext,
            gson = gson,
            ruleScreenMapper = RuleScreenMapper(),
            dispatchers = dispatchers,
        )

    val lifecycleRepository: LifecycleRepository = LifecycleRepository(dispatchers)

    val billingWrapper: BillingWrapper by lazy { BillingWrapper(applicationContext) }

    @Volatile
    var effectHandlerDelegate: (suspend (SdkEffect, (SdkEvent) -> Unit) -> Unit)? = null

    val sdkStore: Store<SdkState, SdkEvent, SdkEffect> = Store(
        initialState = SdkState.NotInitialized,
        reducer = ::sdkReducer,
        effectHandler = { effect, dispatch ->
            val handler = effectHandlerDelegate
            if (handler != null) {
                handler(effect, dispatch)
            } else {
                ApphudLog.logE("Store: effect $effect dropped — no active session")
            }
        },
        scope = CoroutineScope(SupervisorJob() + dispatchers.default),
    )
}

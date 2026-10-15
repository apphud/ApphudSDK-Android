package com.apphud.sdk.internal

import android.content.Context
import com.apphud.sdk.ApphudInternal
import com.apphud.sdk.ApphudUtils
import com.apphud.sdk.internal.data.BlockStoreInstallMarkerSource
import com.apphud.sdk.internal.data.ClientSessionRepository
import com.apphud.sdk.internal.data.DeviceIdentifiersDataSource
import com.apphud.sdk.internal.data.DeviceIdentifiersRepository
import com.apphud.sdk.internal.data.IdentityStore
import com.apphud.sdk.internal.data.ReinstallRepository
import com.apphud.sdk.internal.data.local.LifecycleRepository
import com.apphud.sdk.internal.data.local.LocalRulesScreenRepository
import com.apphud.sdk.internal.data.mapper.CustomerMapper
import com.apphud.sdk.internal.data.mapper.PaywallsMapper
import com.apphud.sdk.internal.data.mapper.PlacementsMapper
import com.apphud.sdk.internal.data.mapper.RuleScreenMapper
import com.apphud.sdk.internal.data.mapper.SubscriptionMapper
import com.apphud.sdk.internal.data.network.FirstRegistrationHold
import com.apphud.sdk.internal.data.network.HostSwitcherInterceptor
import com.apphud.sdk.internal.data.network.PrettyHttpLoggingInterceptor
import com.apphud.sdk.internal.data.network.PrettyJsonFormatter
import com.apphud.sdk.internal.data.network.UrlProvider
import com.apphud.sdk.isFreshPackageInstall
import com.apphud.sdk.storage.FileIdentityVersionStorage
import com.apphud.sdk.storage.FileInstallRecordStorage
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

    val urlProvider = UrlProvider(storage) { ApphudInternal.isIdentityV1 }

    val clientSessionRepository: ClientSessionRepository = ClientSessionRepository(storage)

    private val installRecordStorage = FileInstallRecordStorage { applicationContext.noBackupFilesDir }

    val identityStore: IdentityStore by lazy {
        IdentityStore(
            storage = storage,
            installRecords = installRecordStorage,
            versions = FileIdentityVersionStorage { applicationContext.noBackupFilesDir },
            isFreshPackageInstall = { applicationContext.isFreshPackageInstall() },
            clearUserData = { storage.clean() },
        )
    }

    val firstRegistrationHold: FirstRegistrationHold = FirstRegistrationHold()

    val reinstallRepository: ReinstallRepository by lazy {
        ReinstallRepository(
            storage = installRecordStorage,
            markerSource = BlockStoreInstallMarkerSource(applicationContext),
            deviceMarker = {
                if (ApphudUtils.optOutOfTracking) {
                    ReinstallRepository.CONSTANT_MARKER
                } else {
                    ReinstallRepository.deviceMarkerOf(deviceIdentifiersDataSource.fetchAndroidIdSync())
                }
            },
            scope = CoroutineScope(SupervisorJob() + dispatchers.io),
            onDecided = { identityStore.markSyncPending() },
            dropRestoredPerson = { identityStore.dropRestoredPerson() },
        )
    }

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
}

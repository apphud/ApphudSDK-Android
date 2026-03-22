package com.apphud.sdk.internal

import android.content.Context
import com.apphud.sdk.ApphudRuleCallback
import com.apphud.sdk.internal.domain.model.ApiKey

internal class ServiceLocator private constructor() {

    @Volatile
    private var _appScope: AppScopeComponent? = null

    @Volatile
    private var _session: SessionComponent? = null

    val appScope: AppScopeComponent
        get() = _appScope ?: error("App scope not initialized. ApphudInitProvider may have failed to register.")

    val session: SessionComponent
        get() = _session ?: error("Session not initialized. Call Apphud.start() first.")

    // App-scoped passthrough properties
    val gson get() = appScope.gson
    val storage get() = appScope.storage
    val deviceIdentifiersRepository get() = appScope.deviceIdentifiersRepository
    val urlProvider get() = appScope.urlProvider
    val localRulesScreenRepository get() = appScope.localRulesScreenRepository
    val lifecycleRepository get() = appScope.lifecycleRepository
    val billingWrapper get() = appScope.billingWrapper
    val dispatchers get() = appScope.dispatchers

    // Session-scoped passthrough properties
    val coroutineScope get() = session.coroutineScope
    val ruleCallback get() = session.ruleCallback
    val userDataSource get() = session.userDataSource
    val userRepository get() = session.userRepository
    val analyticsTracker get() = session.analyticsTracker
    val remoteRepository get() = session.remoteRepository
    val userRemoteRepository get() = session.userRemoteRepository
    val renderRemoteRepository get() = session.renderRemoteRepository
    val fetchRulesScreenUseCase get() = session.fetchRulesScreenUseCase
    val renderPaywallPropertiesUseCase get() = session.renderPaywallPropertiesUseCase
    val paywallRepository get() = session.paywallRepository
    val renderItemsSerializer get() = session.renderItemsSerializer
    val renderResultMapperWithSerializer get() = session.renderResultMapperWithSerializer
    val ruleController get() = session.ruleController
    val paywallEventManager get() = session.paywallEventManager
    val productRepository get() = session.productRepository
    val userPropertiesManager get() = session.userPropertiesManager
    val offeringsCallbackManager get() = session.offeringsCallbackManager
    val resolveCredentialsUseCase get() = session.resolveCredentialsUseCase
    val registrationUseCase get() = session.registrationUseCase
    val collectDeviceIdentifiersUseCase get() = session.collectDeviceIdentifiersUseCase
    val deviceIdentifiersInteractor get() = session.deviceIdentifiersInteractor
    val fetchNativePurchasesUseCase get() = session.fetchNativePurchasesUseCase

    companion object {
        @Volatile
        private var _instance: ServiceLocator? = null

        val instance: ServiceLocator
            get() = _instance
                ?: throw IllegalStateException(
                    "ServiceLocator not initialized. ApphudInitProvider may be missing."
                )

        @Synchronized
        fun initAppScope(applicationContext: Context) {
            val locator = _instance ?: ServiceLocator().also { _instance = it }
            if (locator._appScope == null) {
                locator._appScope = AppScopeComponent(applicationContext)
            }
        }

        @Synchronized
        fun initSessionScope(
            apiKey: ApiKey,
            ruleCallback: ApphudRuleCallback,
            awaitUserRegistration: suspend () -> Unit,
        ) {
            val locator = instance
            check(locator._session == null) { "Session already initialized" }
            locator._session = SessionComponent(
                appScope = locator.appScope,
                apiKey = apiKey,
                ruleCallback = ruleCallback,
                awaitUserRegistration = awaitUserRegistration,
            )
        }

        fun clearSession() {
            _instance?._session?.cancel()
            _instance?._session = null
        }

        internal fun clearInstance() {
            _instance = null
        }
    }
}

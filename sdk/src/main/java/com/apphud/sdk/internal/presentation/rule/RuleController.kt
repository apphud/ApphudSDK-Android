package com.apphud.sdk.internal.presentation.rule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.apphud.sdk.Apphud
import com.apphud.sdk.ApphudError
import com.apphud.sdk.ApphudInternal
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.ApphudPurchaseResult
import com.apphud.sdk.ApphudRuleCallback
import com.apphud.sdk.DeviceId
import com.apphud.sdk.APPHUD_PAYWALL_SCREEN_LOAD_TIMEOUT
import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.ApphudPaywallScreenShowResult
import com.apphud.sdk.internal.ApphudDispatchers
import com.apphud.sdk.internal.data.local.LifecycleRepository
import com.apphud.sdk.internal.data.local.LocalRulesScreenRepository
import com.apphud.sdk.internal.data.local.PaywallRepository
import com.apphud.sdk.internal.domain.FetchMostActualRuleScreenUseCase
import com.apphud.sdk.internal.domain.FetchRulesScreenUseCase
import com.apphud.sdk.internal.domain.GetPaywallByIdentifierUseCase
import com.apphud.sdk.internal.domain.RuleScreenResult
import com.apphud.sdk.internal.domain.model.FetchRulesScreenResult
import com.apphud.sdk.internal.domain.model.LifecycleEvent
import com.apphud.sdk.internal.domain.model.Rule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext

internal class RuleController(
    private val context: Context,
    private val fetchRulesScreenUseCase: FetchRulesScreenUseCase,
    private val fetchMostActualRuleScreenUseCase: FetchMostActualRuleScreenUseCase,
    private val getPaywallByIdentifierUseCase: GetPaywallByIdentifierUseCase,
    private val lifecycleRepository: LifecycleRepository,
    private val localRulesScreenRepository: LocalRulesScreenRepository,
    private val paywallRepository: PaywallRepository,
    coroutineScope: CoroutineScope,
    private val ruleCallback: ApphudRuleCallback,
    private val dispatchers: ApphudDispatchers,
) {
    @Volatile
    private var fetchRuleScreenJob: Job? = null

    @Volatile
    private var broadcastReceiver: BroadcastReceiver? = null

    @Volatile
    private lateinit var currentDeviceId: DeviceId

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    private val coroutineScope: CoroutineScope = CoroutineScope(
        coroutineScope.coroutineContext + newSingleThreadContext("RuleControllerThread")
    )

    private val state = MutableStateFlow<RuleState>(RuleState.Idle)

    fun start(deviceId: DeviceId) {
        currentDeviceId = deviceId
        fetchRuleScreenJob?.cancel()
        state.value = RuleState.Idle
        fetchRuleScreenJob = lifecycleRepository.get()
            .onEach { lifecycleEvent ->
                when (lifecycleEvent) {
                    LifecycleEvent.Started -> processRuleStateMachine()

                    LifecycleEvent.Stopped -> Unit
                }
            }
            .launchIn(coroutineScope)

        registerBroadcastReceiver()
    }

    /**
     * Returns the id of the rule whose screen is currently pending or on screen, if any.
     * Mirrors iOS `Apphud.pendingRule()` and is used to attach `rule_id` to purchases made
     * from a rule-triggered paywall screen.
     */
    fun activeRuleId(): String? =
        when (val currentState = state.value) {
            is RuleState.PendingRule -> currentState.rule.id
            is RuleState.RuleActivityAlreadyOpen -> currentState.rule.id
            is RuleState.RuleActivityClosed -> currentState.rule.id
            RuleState.Idle -> null
            RuleState.Loading -> null
        }

    fun showPendingScreen(callback: (Boolean) -> Unit) {
        coroutineScope.launch {
            val wasShown = when (val currentState = state.value) {
                is RuleState.RuleActivityAlreadyOpen -> false
                RuleState.Idle -> false
                RuleState.Loading -> false
                is RuleState.PendingRule -> {
                    processPendingRule(currentState.rule)
                    state.value is RuleState.RuleActivityAlreadyOpen
                }
                is RuleState.RuleActivityClosed -> false
            }
            withContext(dispatchers.main) {
                callback(wasShown)
            }
        }
    }

    fun stop() {
        fetchRuleScreenJob?.cancel()
        unregisterBroadcastReceiver()
        state.value = RuleState.Idle
    }

    private fun registerBroadcastReceiver() {
        if (broadcastReceiver != null) return

        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    ACTION_RULE_SCREEN_RESULT -> {
                        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                        state.update { currentState ->
                            when (currentState) {
                                is RuleState.RuleActivityAlreadyOpen -> RuleState.RuleActivityClosed(currentState.rule)
                                RuleState.Idle -> currentState
                                RuleState.Loading -> currentState
                                is RuleState.PendingRule -> currentState
                                is RuleState.RuleActivityClosed -> currentState
                            }
                        }
                        coroutineScope.launch {
                            processRuleStateMachine()
                        }
                    }
                }
            }
        }

        val intentFilter = IntentFilter(ACTION_RULE_SCREEN_RESULT)
        ContextCompat.registerReceiver(context, broadcastReceiver, intentFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun unregisterBroadcastReceiver() {
        broadcastReceiver?.let {
            context.unregisterReceiver(it)
            broadcastReceiver = null
        }
    }

    private suspend fun processRuleStateMachine() {
        when (val currentState = state.value) {
            is RuleState.RuleActivityAlreadyOpen -> Unit
            RuleState.Idle -> fetchRules(currentDeviceId)
            RuleState.Loading -> Unit
            is RuleState.PendingRule -> processPendingRule(currentState.rule)
            is RuleState.RuleActivityClosed -> processRuleActivityClosed(currentState)
        }
    }

    private suspend fun processRuleActivityClosed(ruleActivityClosedState: RuleState.RuleActivityClosed) {
        localRulesScreenRepository.deleteById(ruleActivityClosedState.rule.id)
        state.value = RuleState.Idle
        processRuleStateMachine()
    }

    private suspend fun processPendingRule(pendingRule: Rule) {
        val shouldShowScreen = withContext(dispatchers.main) {
            ruleCallback.shouldShowScreen(pendingRule)
        }
        if (!shouldShowScreen) return

        val paywallIdentifier = pendingRule.paywallIdentifier
        if (paywallIdentifier != null) {
            presentPaywallScreen(pendingRule, paywallIdentifier)
        } else {
            val intent = RuleWebViewActivity.getIntent(context, pendingRule.id)
            context.startActivity(intent)
            state.value = RuleState.RuleActivityAlreadyOpen(pendingRule)
        }
    }

    private suspend fun presentPaywallScreen(pendingRule: Rule, paywallIdentifier: String) {
        val paywall = getPaywallByIdentifierUseCase(paywallIdentifier, currentDeviceId)
        if (paywall == null) {
            ApphudLog.logE("RuleController: no paywall found for identifier: $paywallIdentifier")
            state.value = RuleState.Idle
            return
        }

        // Ensure the screen UI can resolve this paywall even when it was fetched remotely
        // and is not part of the cached user placements.
        paywallRepository.register(paywall)

        val presentationContext: Context = withContext(dispatchers.main) {
            ruleCallback.provideActivity()
        } ?: context

        state.value = RuleState.RuleActivityAlreadyOpen(pendingRule)

        val callbacks = buildPaywallScreenCallbacks(pendingRule, paywall)
        // showPaywallScreen suspends until the screen is dismissed; run it detached so the
        // state machine is not blocked while the screen is on screen. Closure is driven by
        // the callbacks below.
        coroutineScope.launch {
            ApphudInternal.showPaywallScreen(
                context = presentationContext,
                paywall = paywall,
                callbacks = callbacks,
                maxTimeout = APPHUD_PAYWALL_SCREEN_LOAD_TIMEOUT,
            )
        }
    }

    private fun buildPaywallScreenCallbacks(
        pendingRule: Rule,
        paywall: ApphudPaywall,
    ): Apphud.ApphudPaywallScreenCallbacks =
        Apphud.ApphudPaywallScreenCallbacks(
            onScreenShown = {
                ApphudInternal.paywallShown(paywall)
                ruleCallback.onScreenAppeared(pendingRule)
            },
            onTransactionStarted = { product ->
                ruleCallback.onWillPurchase(pendingRule, product)
            },
            onTransactionCompleted = { result ->
                val purchaseResult = result.toApphudPurchaseResult()
                ruleCallback.onPurchaseCompleted(pendingRule, purchaseResult)
                if (result !is ApphudPaywallScreenShowResult.TransactionError) {
                    onPaywallScreenClosed(pendingRule, error = null)
                }
            },
            onCloseButtonTapped = {
                onPaywallScreenClosed(pendingRule, error = null)
            },
            onScreenError = { error ->
                ApphudLog.logE("RuleController: paywall screen error: ${error.message}")
                onPaywallScreenClosed(pendingRule, error = error)
            },
        )

    /**
     * Invoked on the main thread from the paywall screen callbacks when the screen is dismissed
     * (by close, successful transaction, or error). Notifies the rule callback and advances the
     * state machine to clean up and re-evaluate pending rules.
     */
    private fun onPaywallScreenClosed(pendingRule: Rule, error: ApphudError?) {
        ruleCallback.onScreenWillDismiss(pendingRule, error)
        state.update { currentState ->
            when (currentState) {
                is RuleState.RuleActivityAlreadyOpen -> RuleState.RuleActivityClosed(currentState.rule)
                else -> currentState
            }
        }
        ruleCallback.onScreenDidDismiss(pendingRule)
        coroutineScope.launch {
            processRuleStateMachine()
        }
    }

    private fun ApphudPaywallScreenShowResult.toApphudPurchaseResult(): ApphudPurchaseResult =
        when (this) {
            is ApphudPaywallScreenShowResult.SubscriptionResult ->
                ApphudPurchaseResult(subscription = subscription, purchase = purchase)

            is ApphudPaywallScreenShowResult.NonRenewingResult ->
                ApphudPurchaseResult(nonRenewingPurchase = nonRenewingPurchase, purchase = purchase)

            is ApphudPaywallScreenShowResult.TransactionError ->
                ApphudPurchaseResult(error = error)
        }

    private suspend fun getRuleById(ruleId: String): Rule? {
        val ruleScreen = localRulesScreenRepository.getById(ruleId).getOrNull()
        return ruleScreen?.rule
    }

    private suspend fun fetchRules(deviceId: DeviceId) {
        state.value = RuleState.Loading

        when (val fetchResult = fetchRulesScreenUseCase(deviceId)) {
            is FetchRulesScreenResult.Success -> {
                when (val ruleResult = fetchMostActualRuleScreenUseCase()) {
                    is RuleScreenResult.Success -> {
                        val rule = getRuleById(ruleResult.ruleId) ?: return
                        val shouldPerformRule = withContext(dispatchers.main) {
                            ruleCallback.shouldPerformRule(rule)
                        }
                        if (shouldPerformRule) {
                            state.value = RuleState.PendingRule(rule)
                            processPendingRule(rule)
                        } else {
                            state.value = RuleState.Idle
                        }
                    }
                    is RuleScreenResult.NoRules -> {
                        state.value = RuleState.Idle
                    }
                    is RuleScreenResult.Error -> {
                        ApphudLog.logE("Fetch ruleScreen failed: ${ruleResult.message}")
                        state.value = RuleState.Idle
                    }
                }
            }
            is FetchRulesScreenResult.Error -> {
                ApphudLog.logE("Fetch ruleScreen failed: ${fetchResult.exception.message}")
                state.value = RuleState.Idle
            }
        }
    }

    internal companion object {
        const val ACTION_RULE_SCREEN_RESULT = "com.apphud.sdk.ACTION_RULE_SCREEN_RESULT"
        const val EXTRA_RESULT_CODE = "result_code"
    }
}

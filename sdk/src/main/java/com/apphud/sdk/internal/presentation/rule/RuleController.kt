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
import com.apphud.sdk.internal.data.remote.RemoteRepository
import com.apphud.sdk.internal.data.remote.ScreenRemoteRepository
import com.apphud.sdk.internal.domain.FetchMostActualRuleScreenUseCase
import com.apphud.sdk.internal.domain.FetchRulesScreenUseCase
import com.apphud.sdk.internal.domain.GetPaywallByIdentifierUseCase
import com.apphud.sdk.internal.domain.RuleScreenResult
import com.apphud.sdk.internal.domain.TrackRuleEventUseCase
import com.apphud.sdk.internal.domain.mapper.NotificationMapper
import com.apphud.sdk.internal.domain.model.FetchRulesScreenResult
import com.apphud.sdk.internal.domain.model.LifecycleEvent
import com.apphud.sdk.internal.domain.model.RuleScreen
import com.apphud.sdk.domain.Rule
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
    private val trackRuleEventUseCase: TrackRuleEventUseCase,
    private val remoteRepository: RemoteRepository,
    private val screenRemoteRepository: ScreenRemoteRepository,
    private val notificationMapper: NotificationMapper,
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

    @Volatile
    private var appInForeground: Boolean = false

    @Volatile
    private var pendingPushPayload: Map<String, Any>? = null

    // rule_id -> timestamp of last handling; used to dedup pushes within a short window.
    private val handledPushRuleIds = mutableMapOf<String, Long>()

    // rule ids already auto-presented in this session. Prevents re-showing the same screen
    // after it was dismissed: marking notifications as read on the server is eventually
    // consistent, so a fetch right after dismiss can still return the just-shown rule and
    // re-present it, producing an infinite presentation loop. Only mutated on the
    // RuleController thread.
    private val shownRuleIds = mutableSetOf<String>()

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    private val coroutineScope: CoroutineScope = CoroutineScope(
        coroutineScope.coroutineContext + newSingleThreadContext("RuleControllerThread")
    )

    private val state = MutableStateFlow<RuleState>(RuleState.Idle)

    fun start(deviceId: DeviceId) {
        currentDeviceId = deviceId
        fetchRuleScreenJob?.cancel()
        shownRuleIds.clear()
        state.value = RuleState.Idle
        fetchRuleScreenJob = lifecycleRepository.get()
            .onEach { lifecycleEvent ->
                when (lifecycleEvent) {
                    LifecycleEvent.Started -> {
                        appInForeground = true
                        if (!processPendingPush()) {
                            processRuleStateMachine()
                        }
                    }

                    LifecycleEvent.Stopped -> {
                        appInForeground = false
                    }
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

    /**
     * Returns the rule that is currently pending or on screen, if any. Mirrors iOS
     * `Apphud.pendingRule()`.
     */
    fun pendingRule(): Rule? =
        when (val currentState = state.value) {
            is RuleState.PendingRule -> currentState.rule
            is RuleState.RuleActivityAlreadyOpen -> currentState.rule
            is RuleState.RuleActivityClosed -> currentState.rule
            RuleState.Idle -> null
            RuleState.Loading -> null
        }

    /**
     * Forces a rules fetch without waiting for the next app foreground event.
     * Mirrors iOS `ApphudUtils.checkRules()`.
     */
    fun checkRules() {
        if (!this::currentDeviceId.isInitialized) {
            ApphudLog.logE("RuleController: checkRules called before start")
            return
        }
        coroutineScope.launch {
            if (state.value is RuleState.Idle) {
                processRuleStateMachine()
            }
        }
    }

    /**
     * Handles an incoming push notification data payload. Returns true if the payload contains an
     * Apphud rule and was accepted for handling. Mirrors iOS `Apphud.handlePushNotification`.
     */
    fun handlePushNotification(data: Map<String, Any>): Boolean {
        ApphudLog.log("RuleController: incoming push notification payload: $data")

        val ruleId = data["rule_id"] as? String ?: return false

        if (!this::currentDeviceId.isInitialized) {
            ApphudLog.logE("RuleController: handlePushNotification called before start")
            return false
        }

        pendingPushPayload = data
        coroutineScope.launch {
            processPendingPush()
        }
        return true
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

    /**
     * Processes a pending push payload if one is present and the app is in the foreground.
     * Returns true if a push was handled (or is being handled), false otherwise.
     */
    private suspend fun processPendingPush(): Boolean {
        val data = pendingPushPayload ?: return false

        if (!appInForeground) {
            ApphudLog.log("RuleController: got push payload but app is not active, will handle when foregrounded")
            return false
        }

        // Only handle a push when no other rule screen is pending or on screen.
        if (state.value !is RuleState.Idle) {
            return false
        }

        val rule = notificationMapper.mapRuleFromPayload(data)
        if (rule == null) {
            ApphudLog.logE("RuleController: push payload has no rule_id")
            pendingPushPayload = null
            return false
        }

        val now = System.currentTimeMillis()
        val lastHandledAt = handledPushRuleIds[rule.id]
        if (lastHandledAt != null && now - lastHandledAt < PUSH_DEDUP_WINDOW_MS) {
            ApphudLog.log("RuleController: push rule ${rule.id} already handled recently, skipping")
            pendingPushPayload = null
            return true
        }
        handledPushRuleIds[rule.id] = now

        pendingPushPayload = null

        trackRuleEventUseCase(rule.id, rule.screenId.ifEmpty { null }, EVENT_PUSH_OPENED)

        handlePushRule(rule)
        return true
    }

    private suspend fun handlePushRule(rule: Rule) {
        val shouldPerformRule = withContext(dispatchers.main) {
            ruleCallback.shouldPerformRule(rule)
        }
        if (!shouldPerformRule) {
            remoteRepository.readAllNotifications(rule.id, currentDeviceId)
            ApphudLog.log("RuleController: shouldPerformRule returned false for rule ${rule.ruleName}")
            return
        }

        if (rule.paywallId != null) {
            remoteRepository.readAllNotifications(rule.id, currentDeviceId)
            state.value = RuleState.PendingRule(rule)
            processPendingRule(rule)
        } else if (ApphudInternal.legacyRuleScreensEnabled) {
            val html = screenRemoteRepository.loadScreenHtmlData(rule.screenId, currentDeviceId).getOrNull()
            if (html == null) {
                ApphudLog.logE("RuleController: failed to load HTML for push rule ${rule.id}")
                return
            }
            localRulesScreenRepository.save(RuleScreen(System.currentTimeMillis(), rule, html))
            remoteRepository.readAllNotifications(rule.id, currentDeviceId)
            state.value = RuleState.PendingRule(rule)
            processPendingRule(rule)
        } else {
            ApphudLog.log("RuleController: skipping legacy HTML push rule ${rule.ruleName}")
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
        // Do NOT re-fetch from the network here. Marking notifications as read is eventually
        // consistent on the server, so an immediate fetch could return the just-dismissed rule
        // and re-present it, causing an infinite presentation loop. Only present the next rule
        // already cached locally, if any.
        selectAndProcessMostActualLocalRule()
    }

    private suspend fun processPendingRule(pendingRule: Rule) {
        val shouldShowScreen = withContext(dispatchers.main) {
            ruleCallback.shouldShowScreen(pendingRule)
        }
        if (!shouldShowScreen) return

        if (pendingRule.paywallId != null) {
            val paywallConfigId = pendingRule.paywallId ?: pendingRule.paywallIdentifier
            if (paywallConfigId == null) {
                ApphudLog.logE("RuleController: paywall rule ${pendingRule.id} has no paywall id")
                localRulesScreenRepository.deleteById(pendingRule.id)
                state.value = RuleState.Idle
                return
            }
            presentPaywallScreen(pendingRule, paywallConfigId)
        } else if (ApphudInternal.legacyRuleScreensEnabled) {
            val intent = RuleWebViewActivity.getIntent(context, pendingRule.id)
            context.startActivity(intent)
            shownRuleIds.add(pendingRule.id)
            state.value = RuleState.RuleActivityAlreadyOpen(pendingRule)
        } else {
            ApphudLog.log("RuleController: skipping legacy HTML rule ${pendingRule.ruleName}")
            localRulesScreenRepository.deleteById(pendingRule.id)
            state.value = RuleState.Idle
        }
    }

    private suspend fun presentPaywallScreen(pendingRule: Rule, paywallConfigId: String) {
        val paywall = getPaywallByIdentifierUseCase(paywallConfigId, currentDeviceId)
        if (paywall == null) {
            ApphudLog.logE("RuleController: no paywall found for id: $paywallConfigId")
            localRulesScreenRepository.deleteById(pendingRule.id)
            state.value = RuleState.Idle
            return
        }

        // A paywall rule without a screen payload cannot be presented by the SDK. Hand it off to
        // the client so it can present the paywall with its own UI.
        if (paywall.screen == null) {
            ApphudLog.log("RuleController: paywall ${paywall.identifier} has no screen, delegating to client")
            withContext(dispatchers.main) {
                ruleCallback.onRulePaywallWithoutScreen(pendingRule, paywall)
            }
            remoteRepository.readAllNotifications(pendingRule.id, currentDeviceId)
            localRulesScreenRepository.deleteById(pendingRule.id)
            state.value = RuleState.Idle
            return
        }

        // Ensure the screen UI can resolve this paywall even when it was fetched remotely
        // and is not part of the cached user placements.
        paywallRepository.register(paywall)

        val presentationContext: Context = withContext(dispatchers.main) {
            ruleCallback.provideActivity()
        } ?: context

        shownRuleIds.add(pendingRule.id)
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
                trackScreenPresented(pendingRule, paywall)
            },
            onTransactionStarted = { product ->
                ruleCallback.onWillPurchase(pendingRule, product)
            },
            onTransactionCompleted = { result ->
                val purchaseResult = result.toApphudPurchaseResult()
                ruleCallback.onPurchaseCompleted(pendingRule, purchaseResult)
                if (result !is ApphudPaywallScreenShowResult.TransactionError) {
                    trackPurchase(pendingRule, paywall, purchaseResult)
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

    private fun trackScreenPresented(rule: Rule, paywall: ApphudPaywall) {
        coroutineScope.launch {
            trackRuleEventUseCase(
                ruleId = rule.id,
                screenId = resolveRuleScreenId(rule, paywall),
                name = EVENT_SCREEN_PRESENTED,
                paywallId = paywall.id,
            )
        }
    }

    private fun trackPurchase(rule: Rule, paywall: ApphudPaywall, result: ApphudPurchaseResult) {
        coroutineScope.launch {
            trackRuleEventUseCase(
                ruleId = rule.id,
                screenId = resolveRuleScreenId(rule, paywall),
                name = EVENT_PURCHASE,
                properties = purchaseEventProperties(result),
                paywallId = paywall.id,
            )
        }
    }

    /** Prefer rule.screenId, fall back to paywall.screen.id — mirrors iOS `ruleScreenID`. */
    private fun resolveRuleScreenId(rule: Rule, paywall: ApphudPaywall): String? =
        rule.screenId.ifEmpty { null } ?: paywall.screen?.id?.ifEmpty { null }

    private fun purchaseEventProperties(result: ApphudPurchaseResult): Map<String, Any> {
        val properties = mutableMapOf<String, Any>()
        val productId = result.subscription?.productId
            ?: result.nonRenewingPurchase?.productId
            ?: result.purchase?.products?.firstOrNull()
        productId?.let { properties["product_id"] = it }
        result.purchase?.orderId?.let { properties["transaction_id"] = it }
        return properties
    }

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
            is FetchRulesScreenResult.Success -> selectAndProcessMostActualLocalRule()
            is FetchRulesScreenResult.Error -> {
                ApphudLog.logE("Fetch ruleScreen failed: ${fetchResult.exception.message}")
                state.value = RuleState.Idle
            }
        }
    }

    /**
     * Picks the most actual rule from the local cache and presents it if allowed. Rules already
     * presented in this session are skipped and removed from the cache to break re-presentation
     * loops caused by eventually-consistent server read-marking.
     */
    private suspend fun selectAndProcessMostActualLocalRule() {
        when (val ruleResult = fetchMostActualRuleScreenUseCase()) {
            is RuleScreenResult.Success -> {
                val rule = getRuleById(ruleResult.ruleId)
                if (rule == null) {
                    state.value = RuleState.Idle
                    return
                }
                if (rule.id in shownRuleIds) {
                    localRulesScreenRepository.deleteById(rule.id)
                    state.value = RuleState.Idle
                    return
                }
                val shouldPerformRule = withContext(dispatchers.main) {
                    ruleCallback.shouldPerformRule(rule)
                }
                if (shouldPerformRule) {
                    state.value = RuleState.PendingRule(rule)
                    processPendingRule(rule)
                } else {
                    localRulesScreenRepository.deleteById(rule.id)
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

    internal companion object {
        const val ACTION_RULE_SCREEN_RESULT = "com.apphud.sdk.ACTION_RULE_SCREEN_RESULT"
        const val EXTRA_RESULT_CODE = "result_code"

        private const val PUSH_DEDUP_WINDOW_MS = 5_000L
        private const val EVENT_PUSH_OPENED = "\$push_opened"
        private const val EVENT_SCREEN_PRESENTED = "\$screen_presented"
        private const val EVENT_PURCHASE = "\$purchase"
    }
}

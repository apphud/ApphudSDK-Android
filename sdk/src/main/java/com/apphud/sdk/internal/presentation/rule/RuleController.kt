package com.apphud.sdk.internal.presentation.rule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.ApphudRuleCallback
import com.apphud.sdk.DeviceId
import com.apphud.sdk.internal.data.local.LifecycleRepository
import com.apphud.sdk.internal.data.local.LocalRulesScreenRepository
import com.apphud.sdk.internal.domain.FetchMostActualRuleScreenUseCase
import com.apphud.sdk.internal.domain.FetchRulesScreenUseCase
import com.apphud.sdk.internal.domain.RuleScreenResult
import com.apphud.sdk.internal.domain.model.FetchRulesScreenResult
import com.apphud.sdk.internal.domain.model.LifecycleEvent
import com.apphud.sdk.internal.domain.model.Rule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
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
    private val lifecycleRepository: LifecycleRepository,
    private val localRulesScreenRepository: LocalRulesScreenRepository,
    coroutineScope: CoroutineScope,
    private val ruleCallback: ApphudRuleCallback,
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
            withContext(Dispatchers.Main) {
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
        val shouldShowScreen = withContext(Dispatchers.Main) {
            ruleCallback.shouldShowScreen(pendingRule)
        }
        if (shouldShowScreen) {
            val intent = RuleWebViewActivity.getIntent(context, pendingRule.id)
            context.startActivity(intent)
            state.value = RuleState.RuleActivityAlreadyOpen(pendingRule)
        }
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
                        val shouldPerformRule = withContext(Dispatchers.Main) {
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

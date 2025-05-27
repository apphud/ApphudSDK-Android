package com.apphud.sdk.internal.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.internal.ServiceLocator
import com.apphud.sdk.internal.data.local.LocalRulesScreenRepository
import com.apphud.sdk.internal.domain.model.RuleScreen
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal class WebViewViewModel(
    private val localRulesScreenRepository: LocalRulesScreenRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<WebViewState>(WebViewState.Loading)
    val state: StateFlow<WebViewState> = _state

    private val _events = Channel<WebViewEvent>()
    val events = _events.receiveAsFlow()

    fun processRuleId(ruleId: String?) {
        if (ruleId == null) {
            ApphudLog.logE("[WebViewViewModel] Rule ID is null")
            _state.value = WebViewState.Error("Rule ID is null")
            viewModelScope.launch {
                ApphudLog.log("[WebViewViewModel] Sending CloseScreen event due to null ruleId")
                _events.send(WebViewEvent.CloseScreen)
            }
            return
        }

        val currentState = _state.value
        if (currentState is WebViewState.Content && currentState.ruleScreen.rule.id == ruleId) {
            ApphudLog.log("[WebViewViewModel] Rule ID hasn't changed, skipping reload: $ruleId")
            return
        }

        loadRuleScreen(ruleId)
    }

    fun processDismiss() {
        ApphudLog.log("[WebViewViewModel] Processing dismiss action")
        viewModelScope.launch {
            _events.send(WebViewEvent.CloseScreen)
        }
    }

    fun processPurchase(productId: String) {
        ApphudLog.log("[WebViewViewModel] Processing purchase for product: $productId")
        viewModelScope.launch {
            _events.send(WebViewEvent.PurchaseEvent)
        }
    }

    private fun loadRuleScreen(ruleId: String) {
        viewModelScope.launch {
            ApphudLog.log("[WebViewViewModel] Loading rule screen for ID: $ruleId")
            val result = localRulesScreenRepository.getById(ruleId)

            result.fold(
                onSuccess = { ruleScreen ->
                    if (ruleScreen != null) {
                        ApphudLog.log("[WebViewViewModel] Successfully loaded rule screen: ${ruleScreen.rule.id}")
                        _state.value = WebViewState.Content(ruleScreen)
                    } else {
                        ApphudLog.logE("[WebViewViewModel] Rule screen not found for ID: $ruleId")
                        _state.value = WebViewState.Error("Rule screen not found")
                        ApphudLog.log("[WebViewViewModel] Sending CloseScreen event due to missing rule screen")
                        _events.send(WebViewEvent.CloseScreen)
                    }
                },
                onFailure = { error ->
                    ApphudLog.logE("[WebViewViewModel] Failed to load rule screen: ${error.message}")
                    _state.value = WebViewState.Error("Failed to load rule screen: ${error.message}")
                    ApphudLog.log("[WebViewViewModel] Sending CloseScreen event due to error loading rule screen")
                    _events.send(WebViewEvent.CloseScreen)
                }
            )
        }
    }

    companion object {
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val serviceLocator = ServiceLocator.instance
                @Suppress("UNCHECKED_CAST")
                return WebViewViewModel(serviceLocator.localRulesScreenRepository) as T
            }
        }
    }
}

internal sealed class WebViewState {
    object Loading : WebViewState()
    data class Content(val ruleScreen: RuleScreen) : WebViewState()
    data class Error(val message: String) : WebViewState()
}

internal sealed class WebViewEvent {
    object CloseScreen : WebViewEvent()
    object PurchaseEvent : WebViewEvent()
}

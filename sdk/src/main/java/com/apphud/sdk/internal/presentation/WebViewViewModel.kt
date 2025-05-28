package com.apphud.sdk.internal.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.apphud.sdk.ApphudInternal
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.ApphudPurchaseResult
import com.apphud.sdk.ApphudRuleCallback
import com.apphud.sdk.domain.ApphudProduct
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
    private val ruleCallback: ApphudRuleCallback,
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
                _events.send(WebViewEvent.CloseScreen)
            }
            return
        }

        val currentState = _state.value
        if (currentState is WebViewState.Content && currentState.ruleScreen.rule.id == ruleId) {
            return
        }

        loadRuleScreen(ruleId)
    }

    fun processDismiss() {
        viewModelScope.launch {
            _events.send(WebViewEvent.CloseScreen)
        }
    }

    fun processPurchase(productId: String, offerId: String?) {
        val currentState = _state.value
        if (currentState is WebViewState.Content) {
            _state.value = WebViewState.ContentWithPurchaseLoading(currentState.ruleScreen)
        } else {
            return
        }

        val products = ApphudInternal.getPermissionGroups()
            .flatMap { it.products ?: listOf() }
            .distinctBy { it.id }

        val product = products.firstOrNull { it.productId == productId }

        if (product == null) {
            ApphudLog.logE("[WebViewViewModel] Product not found: $productId")
            hidePurchaseLoader()
            return
        }

        var offerToken: String? = null

        if (!offerId.isNullOrEmpty()) {
            val subscriptionOffers = product.subscriptionOfferDetails()
            val matchingOffer = subscriptionOffers?.firstOrNull { offer ->
                offer.offerId == offerId
            }

            if (matchingOffer != null) {
                offerToken = matchingOffer.offerToken
            } else {
                ApphudLog.logE("[WebViewViewModel] Offer not found: $offerId")
                hidePurchaseLoader()
                return
            }
        }

        viewModelScope.launch {
            _events.send(WebViewEvent.StartPurchase(product, offerToken))
        }
    }

    fun onPurchaseResult(result: ApphudPurchaseResult) {
        viewModelScope.launch {
            val currentState = _state.value
            val rule = if (currentState is WebViewState.ContentWithPurchaseLoading) {
                currentState.ruleScreen.rule
            } else {
                ApphudLog.logE("[WebViewViewModel] onPurchaseResult called but not in purchase loading state")
                return@launch
            }

            if (result.error != null) {
                ApphudLog.logE("[WebViewViewModel] Purchase failed: ${result.error}")
                hidePurchaseLoader()
                ruleCallback.onPurchaseCompleted(rule, result)
            } else {
                ruleCallback.onPurchaseCompleted(rule, result)
                _events.send(WebViewEvent.PurchaseCompleted)
            }
        }
    }

    fun processBackPressed() {
        val currentState = _state.value
        if (currentState is WebViewState.ContentWithPurchaseLoading) {
            return
        }
        viewModelScope.launch {
            _events.send(WebViewEvent.CloseScreen)
        }
    }

    private fun hidePurchaseLoader() {
        val currentState = _state.value
        if (currentState is WebViewState.ContentWithPurchaseLoading) {
            _state.value = WebViewState.Content(currentState.ruleScreen)
        }
    }

    private fun loadRuleScreen(ruleId: String) {
        viewModelScope.launch {
            val result = localRulesScreenRepository.getById(ruleId)

            result.fold(
                onSuccess = { ruleScreen ->
                    if (ruleScreen != null) {
                        _state.value = WebViewState.Content(ruleScreen)
                    } else {
                        ApphudLog.logE("[WebViewViewModel] Rule screen not found for ID: $ruleId")
                        _state.value = WebViewState.Error("Rule screen not found")
                        _events.send(WebViewEvent.CloseScreen)
                    }
                },
                onFailure = { error ->
                    ApphudLog.logE("[WebViewViewModel] Failed to load rule screen: ${error.message}")
                    _state.value = WebViewState.Error("Failed to load rule screen: ${error.message}")
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
                return WebViewViewModel(
                    serviceLocator.localRulesScreenRepository,
                    serviceLocator.ruleCallback
                ) as T
            }
        }
    }
}

internal sealed class WebViewState {
    object Loading : WebViewState()
    data class Content(val ruleScreen: RuleScreen) : WebViewState()
    data class ContentWithPurchaseLoading(val ruleScreen: RuleScreen) : WebViewState()
    data class Error(val message: String) : WebViewState()
}

internal sealed class WebViewEvent {
    object CloseScreen : WebViewEvent()
    object PurchaseCompleted : WebViewEvent()
    data class StartPurchase(val product: ApphudProduct, val offerToken: String?) : WebViewEvent()
}

package com.apphud.sdk.internal.presentation.rule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.apphud.sdk.ApphudInternal
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.ApphudPurchaseResult
import com.apphud.sdk.ApphudRuleCallback
import com.apphud.sdk.ApphudScreenDismissAction
import com.apphud.sdk.domain.ApphudProduct
import com.apphud.sdk.domain.Rule
import com.apphud.sdk.internal.ServiceLocator
import com.apphud.sdk.internal.data.local.LocalRulesScreenRepository
import com.apphud.sdk.internal.domain.TrackRuleEventUseCase
import com.apphud.sdk.internal.domain.model.RuleScreen
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal class RuleViewModel(
    private val localRulesScreenRepository: LocalRulesScreenRepository,
    private val ruleCallback: ApphudRuleCallback,
    private val trackRuleEventUseCase: TrackRuleEventUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<WebViewState>(WebViewState.Loading)
    val state: StateFlow<WebViewState> = _state

    private val _events = Channel<WebViewEvent>()
    val events = _events.receiveAsFlow()

    @Volatile
    private var screenAppeared = false

    @Volatile
    private var dismissNotified = false

    private val currentRule: Rule?
        get() = when (val currentState = _state.value) {
            is WebViewState.Content -> currentState.ruleScreen.rule
            is WebViewState.ContentWithPurchaseLoading -> currentState.ruleScreen.rule
            else -> null
        }

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

    /**
     * Invoked once when the screen HTML is loaded and visible. Tracks `$screen_presented` and
     * notifies the rule callback.
     */
    fun onScreenAppeared() {
        if (screenAppeared) return
        screenAppeared = true
        val rule = currentRule ?: return
        ruleCallback.onScreenAppeared(rule)
        viewModelScope.launch {
            trackRuleEventUseCase(
                ruleId = rule.id,
                screenId = rule.screenId.ifEmpty { null },
                name = EVENT_SCREEN_PRESENTED,
                paywallId = rule.paywallId,
            )
        }
    }

    fun processDismiss() {
        closeScreen(error = null)
    }

    fun processBackPressed() {
        val currentState = _state.value
        if (currentState is WebViewState.ContentWithPurchaseLoading) {
            return
        }
        closeScreen(error = null)
    }

    /**
     * Handles a survey answer selection from any intercepted URL with `question` and `answer`
     * params. Always tracks `$survey_answer` on the backend, then closes the screen on Android
     * (linked follow-up screens are not supported).
     */
    fun processSurveyAnswer(question: String, answer: String) {
        val rule = currentRule ?: return
        ruleCallback.onDidSelectSurveyAnswer(rule, question, answer)
        viewModelScope.launch {
            trackRuleEventUseCase(
                rule.id,
                rule.screenId.ifEmpty { null },
                EVENT_SURVEY_ANSWER,
                mapOf("question" to question, "answer" to answer),
            )
            performDismissAction(rule, isSurvey = true)
        }
    }

    /**
     * Handles a feedback submission (`/action?type=post_feedback`) once the feedback text is read
     * from the WebView.
     */
    fun processFeedback(question: String, text: String) {
        if (text.isBlank()) {
            // Tapped send with empty text; keep the screen open, same as iOS.
            return
        }
        val rule = currentRule ?: return
        viewModelScope.launch {
            _events.send(WebViewEvent.StartLoader)
            trackRuleEventUseCase(
                rule.id,
                rule.screenId.ifEmpty { null },
                EVENT_FEEDBACK,
                mapOf("question" to question, "answer" to text),
            )
            _events.send(WebViewEvent.StopLoader)
            performDismissAction(rule, isSurvey = false)
        }
    }

    /**
     * Handles a billing issue action (`/action?type=billing_issue`): tracks the event, opens the
     * store subscriptions page and dismisses the screen.
     */
    fun processBillingIssue() {
        val rule = currentRule ?: return
        viewModelScope.launch {
            trackRuleEventUseCase(rule.id, rule.screenId.ifEmpty { null }, EVENT_BILLING_ISSUE)
            _events.send(WebViewEvent.OpenBillingSubscriptions)
            notifyWillDismiss(rule, error = null)
            _events.send(WebViewEvent.CloseScreen)
            notifyDidDismiss(rule)
        }
    }

    /**
     * Called when the screen fails to load within the timeout window. Closes the screen with an
     * error, mirroring iOS `failedByTimeOut()`.
     */
    fun onLoadTimeout() {
        ApphudLog.logE("[WebViewViewModel] Screen load timed out")
        closeScreen(error = com.apphud.sdk.ApphudError("Timeout error"))
    }

    private suspend fun performDismissAction(rule: Rule, isSurvey: Boolean) {
        when (ruleCallback.onScreenDismissAction(rule)) {
            ApphudScreenDismissAction.THANK_AND_CLOSE -> {
                _events.send(WebViewEvent.ShowThankYouDialog(isSurvey))
            }
            ApphudScreenDismissAction.CLOSE_ONLY -> {
                notifyWillDismiss(rule, error = null)
                _events.send(WebViewEvent.CloseScreen)
                notifyDidDismiss(rule)
            }
            ApphudScreenDismissAction.NONE -> {
                // Keep the screen open; the client handles presentation.
            }
        }
    }

    /**
     * Invoked by the Activity after the "thank you" dialog is dismissed by the user.
     */
    fun onThankYouDialogClosed() {
        closeScreen(error = null)
    }

    private fun closeScreen(error: com.apphud.sdk.ApphudError?) {
        val rule = currentRule
        viewModelScope.launch {
            if (rule != null) notifyWillDismiss(rule, error)
            _events.send(WebViewEvent.CloseScreen)
            if (rule != null) notifyDidDismiss(rule)
        }
    }

    private fun notifyWillDismiss(rule: Rule, error: com.apphud.sdk.ApphudError?) {
        if (dismissNotified) return
        ruleCallback.onScreenWillDismiss(rule, error)
    }

    private fun notifyDidDismiss(rule: Rule) {
        if (dismissNotified) return
        dismissNotified = true
        ruleCallback.onScreenDidDismiss(rule)
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
            viewModelScope.launch {
                _events.send(WebViewEvent.ProductNotFound)
            }
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
                viewModelScope.launch {
                    _events.send(WebViewEvent.ProductNotFound)
                }
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
                trackPurchaseEvent(rule, result)
                ruleCallback.onPurchaseCompleted(rule, result)
                _events.send(WebViewEvent.PurchaseCompleted)
            }
        }
    }

    private fun hidePurchaseLoader() {
        val currentState = _state.value
        if (currentState is WebViewState.ContentWithPurchaseLoading) {
            _state.value = WebViewState.Content(currentState.ruleScreen)
        }
    }

    private suspend fun trackPurchaseEvent(rule: Rule, result: ApphudPurchaseResult) {
        val properties = mutableMapOf<String, Any>()
        val productId = result.subscription?.productId
            ?: result.nonRenewingPurchase?.productId
            ?: result.purchase?.products?.firstOrNull()
        productId?.let { properties["product_id"] = it }
        result.purchase?.orderId?.let { properties["transaction_id"] = it }

        trackRuleEventUseCase(
            ruleId = rule.id,
            screenId = rule.screenId.ifEmpty { null },
            name = EVENT_PURCHASE,
            properties = properties.ifEmpty { null },
            paywallId = rule.paywallId,
        )
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
        private const val EVENT_SCREEN_PRESENTED = "\$screen_presented"
        private const val EVENT_PURCHASE = "\$purchase"
        private const val EVENT_SURVEY_ANSWER = "\$survey_answer"
        private const val EVENT_FEEDBACK = "\$feedback"
        private const val EVENT_BILLING_ISSUE = "\$billing_issue"

        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val serviceLocator = ServiceLocator.instance
                @Suppress("UNCHECKED_CAST")
                return RuleViewModel(
                    serviceLocator.localRulesScreenRepository,
                    serviceLocator.ruleCallback,
                    serviceLocator.trackRuleEventUseCase,
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
    object ProductNotFound : WebViewEvent()
    object StartLoader : WebViewEvent()
    object StopLoader : WebViewEvent()
    object OpenBillingSubscriptions : WebViewEvent()
    data class ShowThankYouDialog(val isSurvey: Boolean) : WebViewEvent()
    data class StartPurchase(val product: ApphudProduct, val offerToken: String?) : WebViewEvent()
}

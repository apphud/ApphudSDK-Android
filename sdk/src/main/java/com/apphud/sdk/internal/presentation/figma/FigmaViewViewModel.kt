package com.apphud.sdk.internal.presentation.figma

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.apphud.sdk.Apphud
import com.apphud.sdk.ApphudError
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.ApphudPurchaseResult
import com.apphud.sdk.ApphudPurchasesRestoreResult
import com.apphud.sdk.ApphudRuleCallback
import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.ApphudPaywallScreenShowResult
import com.apphud.sdk.domain.ApphudProduct
import com.apphud.sdk.domain.PaywallEvent
import com.apphud.sdk.internal.PaywallEventManager
import com.apphud.sdk.internal.ServiceLocator
import com.apphud.sdk.internal.data.local.PaywallRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.Locale

internal class FigmaViewViewModel(
    private val paywallRepository: PaywallRepository,
    private val ruleCallback: ApphudRuleCallback,
    private val eventManager: PaywallEventManager,
) : ViewModel() {

    private val _state = MutableStateFlow<WebViewState>(WebViewState.Loading)
    val state: StateFlow<WebViewState> = _state

    private val _events = Channel<WebViewEvent>()
    val events = _events.receiveAsFlow()

    init {
        eventManager.activate()
    }

    override fun onCleared() {
        super.onCleared()
        eventManager.deactivate()
    }

    fun init(ruleId: String?, renderItemsJson: String?) {
        ApphudLog.log("[WebViewViewModel] Initializing with ruleId: $ruleId")

        if (ruleId == null) {
            ApphudLog.logE("[WebViewViewModel] Rule ID is null")
            _state.value = WebViewState.Error
            val error = ApphudError("Rule ID is null")
            emitPaywallEvent(PaywallEvent.ScreenError(error))
            viewModelScope.launch {
                _events.send(WebViewEvent.CloseScreen)
            }
            return
        }
        if (renderItemsJson == null) {
            ApphudLog.logE("[WebViewViewModel] renderItemsJson is null")
            _state.value = WebViewState.Error
            val error = ApphudError("renderItemsJson is null")
            emitPaywallEvent(PaywallEvent.ScreenError(error))
            viewModelScope.launch {
                _events.send(WebViewEvent.CloseScreen)
            }
            return
        }

        val currentState = _state.value
        when (currentState) {
            is WebViewState.Content -> {
                if (currentState.paywall.identifier == ruleId &&
                    currentState.renderItemsJson == renderItemsJson
                ) return
            }
            is WebViewState.ContentWithPurchaseLoading -> {
                if (currentState.paywall.identifier == ruleId &&
                    currentState.renderItemsJson == renderItemsJson
                ) return
            }
            else -> {}
        }

        viewModelScope.launch {
            paywallRepository.getPaywallById(ruleId).fold(
                onSuccess = { paywall ->
                    ApphudLog.log("[WebViewViewModel] Paywall found: ${paywall.name}")
                    loadContent(paywall, renderItemsJson)
                },
                onFailure = { error ->
                    ApphudLog.logE("[WebViewViewModel] Paywall not found or error: ${error.message}")
                    _state.value = WebViewState.Error
                    val apphudError = ApphudError("Paywall not found: ${error.message}")
                    emitPaywallEvent(PaywallEvent.ScreenError(apphudError))
                    _events.send(WebViewEvent.CloseScreen)
                }
            )
        }
    }


    fun processDismiss() {
        emitPaywallEvent(PaywallEvent.CloseButtonTapped)
        viewModelScope.launch {
            _events.send(WebViewEvent.CloseScreen)
        }
    }

    private fun processPurchase(product: ApphudProduct) {
        val currentState = _state.value
        if (currentState is WebViewState.Content) {
            _state.value = WebViewState.ContentWithPurchaseLoading(
                paywall = currentState.paywall,
                renderItemsJson = currentState.renderItemsJson,
                url = currentState.url
            )
        } else {
            return
        }

        emitPaywallEvent(PaywallEvent.TransactionStarted(product))
        viewModelScope.launch {
            _events.send(WebViewEvent.StartPurchase(product))
        }
    }

    fun onPurchaseResult(result: ApphudPurchaseResult) {
        viewModelScope.launch {
            val currentState = _state.value
            if (currentState !is WebViewState.ContentWithPurchaseLoading) {
                ApphudLog.logE("[WebViewViewModel] onPurchaseResult called but not in purchase loading state")
                return@launch
            }

            if (result.error != null) {
                ApphudLog.logE("[WebViewViewModel] Purchase failed: ${result.error}")
                hidePurchaseLoader()
                emitTransactionCompleted(result)
            } else {
                ApphudLog.log("[WebViewViewModel] Purchase successful")
                emitTransactionCompleted(result)
                _events.send(WebViewEvent.PurchaseCompleted)
            }
        }
    }

    fun processRestore() {
        ApphudLog.log("[WebViewViewModel] Starting restore purchases")

        emitPaywallEvent(PaywallEvent.TransactionStarted(null))

        Apphud.restorePurchases { result ->
            viewModelScope.launch {
                when (result) {
                    is ApphudPurchasesRestoreResult.Success -> {
                        ApphudLog.log("[WebViewViewModel] Restore successful: ${result.subscriptions.size} subscriptions, ${result.purchases.size} purchases")

                        if (result.subscriptions.isNotEmpty()) {
                            val subscriptionResult = ApphudPaywallScreenShowResult.SubscriptionResult(
                                subscription = result.subscriptions.firstOrNull(),
                                purchase = null,
                            )
                            emitPaywallEvent(PaywallEvent.TransactionCompleted(subscriptionResult))
                        }

                        if (result.purchases.isNotEmpty()) {
                            val nonRenewingResult = ApphudPaywallScreenShowResult.NonRenewingResult(
                                nonRenewingPurchase = result.purchases.firstOrNull(),
                                purchase = null,
                            )
                            emitPaywallEvent(PaywallEvent.TransactionCompleted(nonRenewingResult))
                        }

                        if (result.subscriptions.isEmpty() && result.purchases.isEmpty()) {
                            val emptyResult = ApphudPaywallScreenShowResult.SubscriptionResult(
                                subscription = null,
                                purchase = null,
                            )
                            emitPaywallEvent(PaywallEvent.TransactionCompleted(emptyResult))
                        }

                        _events.send(WebViewEvent.RestoreCompleted(true, "Purchases restored successfully"))
                    }
                    is ApphudPurchasesRestoreResult.Error -> {
                        ApphudLog.logE("[WebViewViewModel] Restore failed: ${result.error.message}")

                        val paywallResult = ApphudPaywallScreenShowResult.TransactionError(result.error)
                        emitPaywallEvent(PaywallEvent.TransactionCompleted(paywallResult))

                        _events.send(WebViewEvent.RestoreCompleted(false, "Failed to restore purchases"))
                    }
                }
            }
        }
    }

    fun processPurchaseByIndex(index: Int) {
        ApphudLog.log("[WebViewViewModel] Processing purchase for index: $index")

        val currentState = _state.value
        if (currentState is WebViewState.Content) {
            val products = currentState.paywall.products
            if (products != null && index >= 0 && index < products.size) {
                val product = products[index]
                val productId = product.productId

                ApphudLog.log("[WebViewViewModel] Purchasing product: $productId")
                viewModelScope.launch {
                    _events.send(WebViewEvent.ShowPurchaseLoader)
                }
                processPurchase(product)
            } else {
                ApphudLog.logE("[WebViewViewModel] Invalid product index: $index, products size: ${products?.size ?: 0}")
                val error = ApphudError("Invalid product index: $index, products size: ${products?.size ?: 0}")
                emitPaywallEvent(PaywallEvent.ScreenError(error))
                viewModelScope.launch {
                    _events.send(WebViewEvent.InvalidPurchaseIndex)
                }
            }
        } else {
            ApphudLog.logE("[WebViewViewModel] Cannot process purchase - invalid state: $currentState")
            val error = ApphudError("Cannot process purchase - invalid state: $currentState")
            emitPaywallEvent(PaywallEvent.ScreenError(error))
            viewModelScope.launch {
                _events.send(WebViewEvent.InvalidPurchaseIndex)
            }
        }
    }

    fun processBackPressed() {
        val currentState = _state.value
        if (currentState is WebViewState.ContentWithPurchaseLoading) {
            return
        }
        emitPaywallEvent(PaywallEvent.CloseButtonTapped)
        viewModelScope.launch {
            _events.send(WebViewEvent.CloseScreen)
        }
    }

    fun processWebViewError(errorMessage: String) {
        ApphudLog.logE("[WebViewViewModel] WebView load error: $errorMessage")
        _state.value = WebViewState.WebViewLoadError
        val error = ApphudError("WebView load error: $errorMessage")
        emitPaywallEvent(PaywallEvent.ScreenError(error))
        viewModelScope.launch {
            _events.send(WebViewEvent.CloseScreen)
        }
    }

    fun getRenderItemsJson(): String? {
        val currentState = _state.value
        return when (currentState) {
            is WebViewState.Content -> currentState.renderItemsJson
            is WebViewState.ContentWithPurchaseLoading -> currentState.renderItemsJson
            else -> null
        }
    }

    private fun hidePurchaseLoader() {
        val currentState = _state.value
        if (currentState is WebViewState.ContentWithPurchaseLoading) {
            _state.value = WebViewState.Content(
                paywall = currentState.paywall,
                renderItemsJson = currentState.renderItemsJson,
                url = currentState.url
            )
        }
    }

    private fun getUrlForPaywall(paywall: ApphudPaywall): String? {
        val urls = paywall.screen?.urls ?: return addLiveParameter(paywall.screen?.defaultUrl)

        if (urls.isEmpty()) {
            return addLiveParameter(paywall.screen.defaultUrl)
        }

        val currentLocale = Locale.getDefault().language
        ApphudLog.log("[WebViewViewModel] Current locale: $currentLocale")

        val urlByLocale = urls[currentLocale]
        if (urlByLocale != null) {
            ApphudLog.log("[WebViewViewModel] Found URL for locale $currentLocale: $urlByLocale")
            return addLiveParameter(urlByLocale)
        }

        val englishUrl = urls["en"]
        if (englishUrl != null) {
            ApphudLog.log("[WebViewViewModel] Using English fallback URL: $englishUrl")
            return addLiveParameter(englishUrl)
        }

        val firstUrl = urls.values.firstOrNull()
        if (firstUrl != null) {
            ApphudLog.log("[WebViewViewModel] Using first available URL: $firstUrl")
            return addLiveParameter(firstUrl)
        }

        val defaultUrl = paywall.screen.defaultUrl
        ApphudLog.log("[WebViewViewModel] Using default URL: $defaultUrl")
        return addLiveParameter(defaultUrl)
    }

    private fun addLiveParameter(url: String?): String? {
        if (url == null) return null

        return if (url.contains("?")) {
            "$url&live=true"
        } else {
            "$url?live=true"
        }
    }

    private fun loadContent(paywall: ApphudPaywall, renderItemsJson: String) {
        ApphudLog.log("[WebViewViewModel] Loading content for paywall: ${paywall.name}")

        val url = getUrlForPaywall(paywall)
        if (url == null) {
            ApphudLog.logE("[WebViewViewModel] No URL found for paywall: ${paywall.identifier}")
            _state.value = WebViewState.Error
            val error = ApphudError("No URL found for paywall: ${paywall.identifier}")
            emitPaywallEvent(PaywallEvent.ScreenError(error))
            viewModelScope.launch {
                _events.send(WebViewEvent.CloseScreen)
            }
            return
        }

        _state.value = WebViewState.Content(
            paywall = paywall,
            renderItemsJson = renderItemsJson,
            url = url
        )

        emitPaywallEvent(PaywallEvent.ScreenShown)
    }

    private fun emitPaywallEvent(event: PaywallEvent) {
        eventManager.emitEvent(event)
    }

    private fun emitTransactionCompleted(purchaseResult: ApphudPurchaseResult) {
        val paywallResult = when {
            purchaseResult.error != null -> {
                ApphudPaywallScreenShowResult.TransactionError(
                    error = purchaseResult.error ?: ApphudError("Unknown purchase error")
                )
            }
            purchaseResult.subscription != null -> {
                ApphudPaywallScreenShowResult.SubscriptionResult(
                    subscription = purchaseResult.subscription,
                    purchase = purchaseResult.purchase,
                )
            }
            purchaseResult.nonRenewingPurchase != null -> {
                ApphudPaywallScreenShowResult.NonRenewingResult(
                    nonRenewingPurchase = purchaseResult.nonRenewingPurchase,
                    purchase = purchaseResult.purchase,
                )
            }
            else -> {
                ApphudPaywallScreenShowResult.TransactionError(ApphudError("Unknown purchase error"))
            }
        }
        emitPaywallEvent(PaywallEvent.TransactionCompleted(paywallResult))
    }

    companion object {
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val serviceLocator = ServiceLocator.instance
                @Suppress("UNCHECKED_CAST")
                return FigmaViewViewModel(
                    serviceLocator.paywallRepository,
                    serviceLocator.ruleCallback,
                    serviceLocator.paywallEventManager
                ) as T
            }
        }
    }
}

internal sealed class WebViewState {

    object Loading : WebViewState()

    data class Content(
        val paywall: ApphudPaywall,
        val renderItemsJson: String?,
        val url: String,
    ) : WebViewState()

    data class ContentWithPurchaseLoading(
        val paywall: ApphudPaywall,
        val renderItemsJson: String?,
        val url: String,
    ) : WebViewState()

    object Error : WebViewState()

    object WebViewLoadError : WebViewState()
}

internal sealed class WebViewEvent {
    object CloseScreen : WebViewEvent()
    object PurchaseCompleted : WebViewEvent()
    data class StartPurchase(val product: ApphudProduct) : WebViewEvent()
    data class RestoreCompleted(val isSuccess: Boolean, val message: String) : WebViewEvent()
    object ShowPurchaseLoader : WebViewEvent()
    object InvalidPurchaseIndex : WebViewEvent()
}

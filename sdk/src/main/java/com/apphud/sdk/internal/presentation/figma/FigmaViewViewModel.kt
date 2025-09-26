package com.apphud.sdk.internal.presentation.figma

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.apphud.sdk.Apphud
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.ApphudPurchaseResult
import com.apphud.sdk.ApphudPurchasesRestoreResult
import com.apphud.sdk.ApphudRuleCallback
import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.ApphudProduct
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
) : ViewModel() {

    private val _state = MutableStateFlow<WebViewState>(WebViewState.Loading)
    val state: StateFlow<WebViewState> = _state

    private val _events = Channel<WebViewEvent>()
    val events = _events.receiveAsFlow()

    fun init(ruleId: String?, renderItemsJson: String?) {
        ApphudLog.log("[WebViewViewModel] Initializing with ruleId: $ruleId")

        if (ruleId == null) {
            ApphudLog.logE("[WebViewViewModel] Rule ID is null")
            _state.value = WebViewState.Error
            viewModelScope.launch {
                _events.send(WebViewEvent.CloseScreen)
            }
            return
        }
        if (renderItemsJson == null) {
            ApphudLog.logE("[WebViewViewModel] renderItemsJson is null")
            _state.value = WebViewState.Error
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
                    _events.send(WebViewEvent.CloseScreen)
                }
            )
        }
    }


    fun processDismiss() {
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
                _events.send(WebViewEvent.PurchaseError)
            } else {
                ApphudLog.log("[WebViewViewModel] Purchase successful")
                _events.send(WebViewEvent.PurchaseCompleted)
            }
        }
    }

    fun processRestore() {
        ApphudLog.log("[WebViewViewModel] Starting restore purchases")

        Apphud.restorePurchases { result ->
            viewModelScope.launch {
                when (result) {
                    is ApphudPurchasesRestoreResult.Success -> {
                        ApphudLog.log("[WebViewViewModel] Restore successful: ${result.subscriptions.size} subscriptions, ${result.purchases.size} purchases")
                        _events.send(WebViewEvent.RestoreCompleted(true, "Purchases restored successfully"))
                    }
                    is ApphudPurchasesRestoreResult.Error -> {
                        ApphudLog.logE("[WebViewViewModel] Restore failed: ${result.error.message}")
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
                viewModelScope.launch {
                    _events.send(WebViewEvent.InvalidPurchaseIndex)
                }
            }
        } else {
            ApphudLog.logE("[WebViewViewModel] Cannot process purchase - invalid state: $currentState")
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
        viewModelScope.launch {
            _events.send(WebViewEvent.CloseScreen)
        }
    }

    fun processWebViewError(errorMessage: String) {
        ApphudLog.logE("[WebViewViewModel] WebView load error: $errorMessage")
        _state.value = WebViewState.WebViewLoadError
        viewModelScope.launch {
            _events.send(WebViewEvent.CloseScreen)
        }
    }

    fun getCurrentRenderItemsJson(): String? {
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

        // Ищем URL по текущей локали
        val urlByLocale = urls[currentLocale]
        if (urlByLocale != null) {
            ApphudLog.log("[WebViewViewModel] Found URL for locale $currentLocale: $urlByLocale")
            return addLiveParameter(urlByLocale)
        }

        // Если не найден по локали, берем английский как fallback
        val englishUrl = urls["en"]
        if (englishUrl != null) {
            ApphudLog.log("[WebViewViewModel] Using English fallback URL: $englishUrl")
            return addLiveParameter(englishUrl)
        }

        // Если английского тоже нет, берем первый доступный
        val firstUrl = urls.values.firstOrNull()
        if (firstUrl != null) {
            ApphudLog.log("[WebViewViewModel] Using first available URL: $firstUrl")
            return addLiveParameter(firstUrl)
        }

        // В крайнем случае используем defaultUrl
        val defaultUrl = paywall.screen?.defaultUrl
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
    }

    companion object {
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val serviceLocator = ServiceLocator.instance
                @Suppress("UNCHECKED_CAST")
                return FigmaViewViewModel(
                    serviceLocator.paywallRepository,
                    serviceLocator.ruleCallback
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
    object ProductNotFound : WebViewEvent()
    data class StartPurchase(val product: ApphudProduct) : WebViewEvent()
    data class RestoreCompleted(val isSuccess: Boolean, val message: String) : WebViewEvent()
    object ShowPurchaseLoader : WebViewEvent()
    object InvalidPurchaseIndex : WebViewEvent()
    object PurchaseError : WebViewEvent()
}

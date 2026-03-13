package com.apphud.sdk.internal

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import com.apphud.sdk.APPHUD_NO_REQUEST
import com.apphud.sdk.ApphudError
import com.apphud.sdk.ApphudListener
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.internal.data.AnalyticsTracker
import com.apphud.sdk.internal.data.ProductLoadingState
import com.apphud.sdk.internal.data.ProductRepository
import com.apphud.sdk.internal.data.UserRepository
import java.util.concurrent.CopyOnWriteArrayList

internal class OfferingsCallbackManager(
    private val userRepository: UserRepository,
    private val productRepository: ProductRepository,
    private val analyticsTracker: AnalyticsTracker,
) {
    private val offeringsPreparedCallbacks = CopyOnWriteArrayList<(ApphudError?) -> Unit>()
    @Volatile
    private var customProductsFetchedBlock: ((List<ProductDetails>) -> Unit)? = null
    @Volatile
    private var notifiedAboutPaywallsDidFullyLoaded: Boolean = false

    @Volatile
    private var latestCustomerLoadError: ApphudError? = null

    fun addOfferingsCallback(callback: (ApphudError?) -> Unit) {
        offeringsPreparedCallbacks.add(callback)
    }

    fun hasPendingCallbacks(): Boolean = offeringsPreparedCallbacks.isNotEmpty()

    fun hasPendingWork(): Boolean = !notifiedAboutPaywallsDidFullyLoaded || hasPendingCallbacks()

    fun isFullyLoaded(): Boolean = notifiedAboutPaywallsDidFullyLoaded

    fun notifyProductsFetched(productDetails: List<ProductDetails>) {
        customProductsFetchedBlock?.invoke(productDetails)
    }

    fun setCustomerLoadError(error: ApphudError?) {
        latestCustomerLoadError = error
    }

    fun getCustomerLoadError(): ApphudError? = latestCustomerLoadError

    fun handlePaywallsAndProductsLoaded(
        customerError: ApphudError?,
        isRegisteringUser: Boolean,
        productDetails: List<ProductDetails>,
        hasRespondedToPaywallsRequest: Boolean,
        deferPlacements: Boolean,
        apphudListener: ApphudListener?,
    ) {
        when {
            isDataReady(isRegisteringUser, productDetails) -> handleSuccessfulLoad(apphudListener, productDetails)
            isErrorOccurred(customerError, isRegisteringUser, hasRespondedToPaywallsRequest) -> handleError(customerError, productDetails)
            else -> logNotReadyState(isRegisteringUser, hasRespondedToPaywallsRequest, productDetails, deferPlacements)
        }
    }

    private fun isDataReady(isRegisteringUser: Boolean, productDetails: List<ProductDetails>): Boolean {
        val user = userRepository.getCurrentUser()
        return user != null &&
            user.placements.isNotEmpty() &&
            productDetails.isNotEmpty() &&
            !isRegisteringUser
    }

    private fun isErrorOccurred(
        customerError: ApphudError?,
        isRegisteringUser: Boolean,
        hasRespondedToPaywallsRequest: Boolean,
    ): Boolean =
        !isRegisteringUser &&
            hasResponseOrError(customerError, hasRespondedToPaywallsRequest) &&
            hasDataLoadFailed(customerError)

    private fun hasResponseOrError(customerError: ApphudError?, hasRespondedToPaywallsRequest: Boolean) =
        hasRespondedToPaywallsRequest || customerError != null

    private fun hasDataLoadFailed(customerError: ApphudError?) =
        (customerError != null && (userRepository.getCurrentUser()?.placements?.isEmpty() != false)) || isProductsLoadFailed()

    private fun isProductsLoadFailed(): Boolean {
        val state = productRepository.state.value
        return state is ProductLoadingState.Failed && state.cachedProducts.isEmpty()
    }

    private fun handleSuccessfulLoad(
        apphudListener: ApphudListener?,
        productDetails: List<ProductDetails>,
    ) {
        val user = userRepository.getCurrentUser()
        if (!notifiedAboutPaywallsDidFullyLoaded) {
            apphudListener?.placementsDidFullyLoad(user?.placements?.toList().orEmpty())

            notifiedAboutPaywallsDidFullyLoaded = true
            ApphudLog.logI("Paywalls and Placements ready")
        }

        val errorSnapshot = latestCustomerLoadError
        latestCustomerLoadError = null

        if (offeringsPreparedCallbacks.isNotEmpty()) {
            ApphudLog.log("handle offeringsPreparedCallbacks latestError: $errorSnapshot")
        }
        while (offeringsPreparedCallbacks.isNotEmpty()) {
            val callback = offeringsPreparedCallbacks.removeFirst()
            callback.invoke(null)
        }

        val state = productRepository.state.value
        val responseCode = if (state is ProductLoadingState.Failed) state.responseCode else BillingClient.BillingResponseCode.OK
        analyticsTracker.trackAnalytics(true, errorSnapshot, productDetails.count(), responseCode)
    }

    private fun handleError(
        customerError: ApphudError?,
        productDetails: List<ProductDetails>,
    ) {
        val state = productRepository.state.value
        val responseCode =
            if (state is ProductLoadingState.Failed) state.responseCode else BillingClient.BillingResponseCode.OK
        val errorSnapshot = latestCustomerLoadError
        latestCustomerLoadError = null

        val error = errorSnapshot ?: customerError ?: if (responseCode == APPHUD_NO_REQUEST) {
            ApphudError("Paywalls load error", errorCode = responseCode)
        } else {
            ApphudError("Google Billing error", errorCode = responseCode)
        }

        if (offeringsPreparedCallbacks.isNotEmpty()) {
            ApphudLog.log("handle offeringsPreparedCallbacks with error $error")
        }
        while (offeringsPreparedCallbacks.isNotEmpty()) {
            val callback = offeringsPreparedCallbacks.removeFirst()
            callback.invoke(error)
        }

        analyticsTracker.trackAnalytics(false, errorSnapshot, productDetails.count(), responseCode)
    }

    private fun logNotReadyState(
        isRegisteringUser: Boolean,
        hasRespondedToPaywallsRequest: Boolean,
        productDetails: List<ProductDetails>,
        deferPlacements: Boolean,
    ) {
        val user = userRepository.getCurrentUser()
        val productsState = productRepository.state.value
        val productsResponseCode =
            if (productsState is ProductLoadingState.Failed) productsState.responseCode else BillingClient.BillingResponseCode.OK
        ApphudLog.log("Not yet ready for callbacks invoke: isRegisteringUser: $isRegisteringUser, currentUserExist: ${user != null}, latestCustomerError: $latestCustomerLoadError, placementsEmpty: ${user?.placements?.isEmpty() != false}, productsResponseCode = $productsResponseCode, productsStatus: $productsState, productDetailsEmpty: ${productDetails.isEmpty()}, hasRespondedToPaywallsRequest=$hasRespondedToPaywallsRequest, deferred: $deferPlacements")
    }

    fun productsFetchCallback(callback: (List<ProductDetails>) -> Unit, productDetails: List<ProductDetails>) {
        if (productDetails.isNotEmpty()) {
            callback.invoke(productDetails.toList())
        } else {
            customProductsFetchedBlock = callback
        }
    }

    fun clear() {
        customProductsFetchedBlock = null
        offeringsPreparedCallbacks.clear()
        notifiedAboutPaywallsDidFullyLoaded = false
        latestCustomerLoadError = null
    }
}

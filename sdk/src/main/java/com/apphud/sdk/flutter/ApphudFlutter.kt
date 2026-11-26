package com.apphud.sdk.flutter

import android.app.Activity
import com.apphud.sdk.ApphudError
import com.apphud.sdk.ApphudIdentityVersion
import com.apphud.sdk.ApphudInternal
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.ApphudPurchaseResult
import com.apphud.sdk.ApphudPurchasesRestoreResult
import com.apphud.sdk.internal.util.runCatchingCancellable
import com.apphud.sdk.purchase
import com.apphud.sdk.syncPurchases
import com.apphud.sdk.syncPurchasesReportingFailures
import com.apphud.sdk.toApphudError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

object ApphudFlutter {
    /** Delivers callbacks. It is not the session's scope: a logout cancels the sync, never its answer. */
    private val callbackScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main) }

    /**
     * You should use this method only in Observer mode.
     * Sends the purchases your app made to Apphud and returns once the backend has answered. It never
     * moves a purchase another user owns: only `Apphud.restorePurchases` does. With identity version 1
     * it runs the same sync as [syncPurchases].
     *
     * @return [ApphudPurchasesRestoreResult.Success] when Apphud has the purchases, also when there was
     * nothing new to send. [ApphudPurchasesRestoreResult.Error] when Google Play, the network or the
     * backend failed, when a logout canceled the sync and before `Apphud.start`. It throws only when
     * the calling coroutine is canceled.
     */
    suspend fun syncPurchasesInObserverMode(): ApphudPurchasesRestoreResult = startObserverSync().result()

    /**
     * [syncPurchasesInObserverMode] with a callback, which is called once, on the main thread.
     */
    @kotlin.jvm.JvmStatic
    fun syncPurchasesInObserverMode(callback: (ApphudPurchasesRestoreResult) -> Unit) {
        // Started now, not when the main thread gets to the callback: a call before start stays one.
        val sync = startObserverSync()
        callbackScope.launch {
            callback(sync.result())
        }
    }

    /** The session owns the sync, so a logout cancels it. Without a session it starts canceled. */
    private fun startObserverSync(): Deferred<ApphudPurchasesRestoreResult> =
        ApphudInternal.coroutineScope.async { ApphudInternal.syncPurchasesReportingFailures() }

    private suspend fun Deferred<ApphudPurchasesRestoreResult>.result(): ApphudPurchasesRestoreResult =
        try {
            await()
        } catch (error: CancellationException) {
            currentCoroutineContext().ensureActive()
            ApphudPurchasesRestoreResult.Error(
                ApphudError("Syncing purchases was canceled: the SDK is not started or the user logged out"),
            )
        } catch (error: Throwable) {
            ApphudLog.logE("Error syncing purchases: ${error.message}")
            ApphudPurchasesRestoreResult.Error(error.toApphudError())
        }

    /**
     * The identity version this launch runs, `null` until `Apphud.start` has chosen it.
     */
    @kotlin.jvm.JvmStatic
    fun identityVersion(): ApphudIdentityVersion? = ApphudInternal.chosenIdentityVersion

    /**
     * You should use this method only in Observer mode.
     * This method will send all the purchases to the Apphud server.
     * If you use Apphud SDK as observer, you should call this method after every successful purchase or restoration.
     * Pass `Paywall Identifier` to be able to use A/B tests in Observer Mode. See docs.apphud.com for details.
     */
    @kotlin.jvm.JvmStatic
    fun syncPurchases(
        paywallIdentifier: String? = null,
        placementIdentifier: String? = null,
    ) {
        ApphudInternal.coroutineScope.launch {
            runCatchingCancellable {
                ApphudInternal.syncPurchases(paywallIdentifier, placementIdentifier)
            }.onFailure { error ->
                ApphudLog.logE("Error syncing purchases: ${error.message}")
            }
        }
    }

    /**
     * Purchase product by id and automatically submit Google Play purchase token to Apphud
     * @param activity: Required. Current Activity for use
     * @param productId: Required. The identifier of the product you wish to purchase
     * @param offerIdToken Optional. Specifies the identifier of the offer to initiate purchase with. You must manually select base plan
     * and offer from ProductDetails and pass offer id token.
     * @param oldToken Optional. Specifies the Google Play Billing purchase token that the user is upgrading or downgrading from.
     * @param replacementMode Optional. Replacement mode (https://developer.android.com/reference/com/android/billingclient/api/BillingFlowParams.SubscriptionUpdateParams.ReplacementMode?hl=en)
     * and offer from ProductDetails and pass offer id token.
     * @param consumableInappProduct Optional. Default false. Pass true for consumables products
     * @param block: Optional. Returns `ApphudPurchaseResult` object.
     */
    @kotlin.jvm.JvmStatic
    fun purchase(
        activity: Activity,
        productId: String,
        offerIdToken: String? = null,
        oldToken: String? = null,
        replacementMode: Int? = null,
        consumableInappProduct: Boolean = false,
        block: ((ApphudPurchaseResult) -> Unit)?,
    ) = ApphudInternal.purchase(
        activity,
        null,
        productId,
        offerIdToken,
        oldToken,
        replacementMode,
        fromScreen = false,
        consumableInappProduct,
        block
    )
}

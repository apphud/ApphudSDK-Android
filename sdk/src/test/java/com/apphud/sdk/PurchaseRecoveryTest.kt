package com.apphud.sdk

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.ProductDetails
import android.app.Activity
import com.apphud.sdk.domain.ApphudProduct
import com.apphud.sdk.domain.ApphudSubscription
import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.internal.ApphudDispatchers
import com.apphud.sdk.internal.BillingWrapper
import com.apphud.sdk.internal.PurchaseRecovery
import com.apphud.sdk.internal.ServiceLocator
import com.apphud.sdk.internal.data.UserRepository
import com.apphud.sdk.internal.domain.FetchNativePurchasesUseCase
import com.apphud.sdk.internal.PurchasesUpdatedCallback
import com.apphud.sdk.internal.callback_status.PurchaseUpdatedCallbackStatus
import com.apphud.sdk.managers.RequestManager
import com.apphud.sdk.storage.SharedPreferencesStorage
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PurchaseRecoveryTest {
    private val scope = TestScope()
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)
    private val recovery = PurchaseRecovery()
    private val attempt = recovery.begin("subscription", "old-token")!!
    private val originalPurchaseStartedAt = purchaseStartedAt
    private val listener = java.util.concurrent.atomic.AtomicReference<PurchasesUpdatedCallback?>()
    private val activity: Activity = mockk()
    private val billing: BillingWrapper = mockk {
        every { purchasesCallback } answers { listener.get() }
        every { purchasesCallback = any() } answers { listener.set(firstArg()) }
        coEvery { queryPurchasesSync() } returns (emptyList<Purchase>() to 0)
        coEvery { purchase(any(), any(), any(), any(), any(), any()) } returns null
    }
    private val received = mutableListOf<ApphudPurchaseResult>()
    private val callbacks = mutableListOf<(ApphudPurchaseResult) -> Unit>({ received.add(it) })
    private val details: ProductDetails = mockk {
        every { productId } returns "subscription"
        every { productType } returns BillingClient.ProductType.SUBS
    }
    private val product: ApphudProduct = mockk {
        every { productId } returns "subscription"
        every { id } returns "bundle"
        every { paywallId } returns null
        every { placementId } returns null
        every { productDetails } returns details
        every { basePlanId } returns "plan"
    }
    private val purchasing = java.util.concurrent.atomic.AtomicReference<ApphudProduct?>(product)
    private val oldPurchase: Purchase = mockk {
        every { products } returns listOf("subscription")
        every { purchaseToken } returns "old-token"
        every { orderId } returns "old-order"
        every { purchaseState } returns Purchase.PurchaseState.PURCHASED
    }
    private val nativePurchases: FetchNativePurchasesUseCase = mockk {
        coEvery { this@mockk.invoke(false) } returns (listOf(oldPurchase) to BillingClient.BillingResponseCode.OK)
    }
    private val locator: ServiceLocator = mockk {
        every { fetchNativePurchasesUseCase } returns nativePurchases
    }
    private val storage: SharedPreferencesStorage = mockk {
        every { isNeedSync } returns false
        every { isNeedSync = any() } just runs
    }
    private val subscription: ApphudSubscription = mockk {
        every { productId } returns "subscription"
        every { purchaseToken } returns "new-token"
    }
    private val customer: ApphudUser = mockk {
        every { subscriptions } returns listOf(subscription)
        every { purchases } returns emptyList()
    }

    init {
        mockkObject(ApphudInternal, ApphudUtils, ServiceLocator.Companion, RequestManager)
        every { ApphudUtils.logging } returns false
        every { ApphudInternal.isIdentityV1 } returns false
        every { ApphudInternal.purchaseRecovery } returns recovery
        every { ApphudInternal.billing } returns billing
        every { ServiceLocator.instance } returns locator
        every { ApphudInternal.coroutineScope } returns scope
        every { ApphudInternal.dispatchers } returns ApphudDispatchers(dispatcher, dispatcher, dispatcher)
        every { ApphudInternal.purchasingProduct } answers { purchasing.get() }
        every { ApphudInternal.purchasingProduct = any() } answers { purchasing.set(firstArg()) }
        every { ApphudInternal.purchaseCallbacks } returns callbacks
        every { ApphudInternal.freshPurchase } returns null
        every { ApphudInternal.freshPurchase = any() } just runs
        every { ApphudInternal.storage } returns storage
        every { ApphudInternal.apphudListener } returns null
        every { ApphudInternal.fallbackMode } returns false
        every { ApphudInternal.observedOrders } returns java.util.concurrent.ConcurrentHashMap.newKeySet()
        every { ApphudInternal.userRepository } returns mockk<UserRepository> {
            every { getCurrentUser() } returns customer
        }
        every { ApphudInternal.getProductDetailsByProductId(any()) } returns details
        every { ApphudInternal.notifyLoadingCompleted(any(), any()) } just runs
        every { ApphudInternal.scheduleLookupPurchase(any()) } just runs
        every { ApphudInternal.paywallCheckoutInitiated(any(), any(), any(), any()) } just runs
        every { ApphudInternal.paywallPaymentCancelled(any(), any(), any(), any()) } just runs
        coEvery { RequestManager.purchased(any()) } returns customer
        recovery.prepare(attempt, setOf("old-token"))
        recovery.launched(attempt)
    }

    @After
    fun tearDown() {
        purchaseStartedAt = originalPurchaseStartedAt
        unmockkAll()
    }

    @Test
    fun `GIVEN V2 checkout still open and an old purchase EXPECT no success callback`() = scope.runTest {
        ApphudInternal.lookupFreshPurchase()
        runCurrent()

        assertEquals(emptyList<ApphudPurchaseResult>(), received)
    }

    @Test
    fun `GIVEN V1 checkout and an old purchase EXPECT legacy recovery`() = scope.runTest {
        every { ApphudInternal.isIdentityV1 } returns true
        ApphudInternal.lookupFreshPurchase()
        runCurrent()

        assertEquals(oldPurchase, received.single().purchase)
    }

    @Test
    fun `GIVEN V2 stale fresh purchase EXPECT no success callback`() = scope.runTest {
        every { ApphudInternal.freshPurchase } returns oldPurchase
        ApphudInternal.lookupFreshPurchase()
        runCurrent()

        assertEquals(emptyList<ApphudPurchaseResult>(), received)
    }

    @Test
    fun `GIVEN V2 new purchase after old native purchase EXPECT new token recovered`() = scope.runTest {
        val newPurchase = newPurchase()
        coEvery { nativePurchases(false) } returns (listOf(oldPurchase, newPurchase) to 0)
        ApphudInternal.lookupFreshPurchase()
        runCurrent()

        assertEquals(newPurchase, received.single().purchase)
    }

    @Test
    fun `GIVEN V2 new fresh purchase EXPECT recovery without native query`() = scope.runTest {
        val newPurchase = newPurchase()
        every { ApphudInternal.freshPurchase } returns newPurchase
        ApphudInternal.lookupFreshPurchase()
        runCurrent()

        assertEquals(newPurchase, received.single().purchase)
    }

    @Test
    fun `GIVEN V2 unknown prelaunch snapshot EXPECT no native success`() = scope.runTest {
        recovery.prepare(attempt, null)
        coEvery { nativePurchases(false) } returns (listOf(newPurchase()) to 0)
        ApphudInternal.lookupFreshPurchase()
        runCurrent()

        assertEquals(emptyList<ApphudPurchaseResult>(), received)
    }

    @Test
    fun `GIVEN V2 checkout replaced during native query EXPECT no old callback`() = scope.runTest {
        coEvery { nativePurchases(false) } answers {
            recovery.finish(attempt) { }
            recovery.begin("subscription", null)
            listOf(newPurchase()) to 0
        }
        ApphudInternal.lookupFreshPurchase()
        runCurrent()

        assertEquals(emptyList<ApphudPurchaseResult>(), received)
    }

    @Test
    fun `GIVEN V2 checkout replaced during dev submission EXPECT no old callback`() = scope.runTest {
        coEvery { nativePurchases(false) } returns (listOf(newPurchase()) to 0)
        coEvery { RequestManager.purchased(any()) } answers {
            recovery.finish(attempt) { }
            recovery.begin("subscription", null)
            customer
        }
        ApphudInternal.lookupFreshPurchase()
        runCurrent()

        assertEquals(emptyList<ApphudPurchaseResult>(), received)
    }

    @Test
    fun `GIVEN V2 callback starts another checkout EXPECT its callback retained`() = scope.runTest {
        val next: (ApphudPurchaseResult) -> Unit = { }
        callbacks.clear()
        callbacks.add {
            check(recovery.begin("next-product", null) != null)
            callbacks.add(next)
        }
        coEvery { nativePurchases(false) } returns (listOf(newPurchase()) to 0)
        ApphudInternal.lookupFreshPurchase()
        runCurrent()

        assertEquals(listOf(next), callbacks)
    }

    @Test
    fun `GIVEN V2 lost listener without a user callback EXPECT checkout released`() = scope.runTest {
        callbacks.clear()
        coEvery { nativePurchases(false) } returns (listOf(newPurchase()) to 0)
        ApphudInternal.lookupFreshPurchase()
        runCurrent()

        assertEquals(null, recovery.current)
    }

    @Test
    fun `GIVEN V2 background purchase and unknown checkout snapshot EXPECT no checkout callback`() = scope.runTest {
        recovery.prepare(attempt, null)
        ApphudInternal.handleObservedPurchase(newPurchase(), userInitiated = true)
        runCurrent()

        assertEquals(emptyList<ApphudPurchaseResult>(), received)
    }

    @Test
    fun `GIVEN V2 response with the old plan first EXPECT subscription matching the new token`() = scope.runTest {
        val older: ApphudSubscription = mockk {
            every { productId } returns "subscription"
            every { purchaseToken } returns "old-token"
        }
        every { customer.subscriptions } returns listOf(older, subscription)
        coEvery { nativePurchases(false) } returns (listOf(newPurchase()) to 0)
        ApphudInternal.lookupFreshPurchase()
        runCurrent()

        assertEquals(subscription, received.single().subscription)
    }

    @Test
    fun `GIVEN V2 pending payment EXPECT checkout reservation released`() = scope.runTest {
        beginListenerCheckout()
        val pending = newPurchase()
        every { pending.purchaseState } returns Purchase.PurchaseState.PENDING
        listener.get()!!(PurchaseUpdatedCallbackStatus.Success(listOf(pending)))
        runCurrent()

        assertEquals(null, recovery.current)
    }

    @Test
    fun `GIVEN V2 unknown payment state EXPECT checkout reservation released`() = scope.runTest {
        beginListenerCheckout()
        val unknown = newPurchase()
        every { unknown.purchaseState } returns Purchase.PurchaseState.UNSPECIFIED_STATE
        listener.get()!!(PurchaseUpdatedCallbackStatus.Success(listOf(unknown)))
        runCurrent()

        assertEquals(null, recovery.current)
    }

    @Test
    fun `GIVEN V2 failed snapshot and a real Google listener EXPECT purchase completed`() = scope.runTest {
        coEvery { billing.queryPurchasesSync() } throws IllegalStateException("Cannot read store history")
        beginListenerCheckout()
        listener.get()!!(PurchaseUpdatedCallbackStatus.Success(listOf(newPurchase())))
        runCurrent()

        assertEquals("new-token", received.single().purchase?.purchaseToken)
    }

    @Test
    fun `GIVEN V2 snapshot timeout EXPECT Google checkout still launched`() = scope.runTest {
        coEvery { billing.queryPurchasesSync() } coAnswers { awaitCancellation() }
        beginListenerCheckout()
        advanceTimeBy(5_000L)
        runCurrent()

        coVerify(exactly = 1) { billing.purchase(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `GIVEN V1 checkout EXPECT no additional prelaunch native query`() = scope.runTest {
        every { ApphudInternal.isIdentityV1 } returns true
        beginListenerCheckout()

        coVerify(exactly = 0) { billing.queryPurchasesSync() }
    }

    @Test
    fun `GIVEN V2 overlapping checkout after a second EXPECT its original listener retained`() = scope.runTest {
        beginListenerCheckout()
        val original = listener.get()
        purchaseStartedAt = 0
        ApphudInternal.purchase(activity, product, null, "offer", "old-token", 4, consumableInappProduct = false) { received.add(it) }
        runCurrent()

        assertEquals(original, listener.get())
    }

    @Test
    fun `GIVEN V2 canceled checkout EXPECT only the cancellation callback`() = scope.runTest {
        beginListenerCheckout()
        listener.get()!!(PurchaseUpdatedCallbackStatus.Error(com.android.billingclient.api.BillingResult.newBuilder()
            .setResponseCode(BillingClient.BillingResponseCode.USER_CANCELED).build()))
        runCurrent()

        assertEquals(listOf(BillingClient.BillingResponseCode.USER_CANCELED), received.map { it.error?.errorCode })
    }

    @Test
    fun `GIVEN V2 background purchase for another product EXPECT its own product without checkout metadata`() = scope.runTest {
        val other = newPurchase()
        every { other.products } returns listOf("other")
        val otherDetails: ProductDetails = mockk { every { productId } returns "other" }
        every { ApphudInternal.getProductDetailsByProductId("other") } returns otherDetails
        val context = slot<com.apphud.sdk.internal.domain.model.PurchaseContext>()
        coEvery { RequestManager.purchased(capture(context)) } returns customer
        ApphudInternal.handleObservedPurchase(other, userInitiated = true)
        runCurrent()

        assertEquals("other" to null, context.captured.productDetails?.productId to context.captured.productBundleId)
    }

    @Test
    fun `GIVEN V2 deferred Google listener with a new token and old SKU EXPECT completed checkout`() = scope.runTest {
        every { oldPurchase.products } returns listOf("old-product")
        coEvery { billing.queryPurchasesSync() } returns (listOf(oldPurchase) to 0)
        every { subscription.productId } returns "old-product"
        beginListenerCheckout(com.android.billingclient.api.BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.DEFERRED)
        val deferred = newPurchase()
        every { deferred.products } returns listOf("old-product")
        listener.get()!!(PurchaseUpdatedCallbackStatus.Success(listOf(deferred)))
        runCurrent()

        assertEquals("new-token", received.single().purchase?.purchaseToken)
    }

    @Test
    fun `GIVEN V2 listener loses its customer EXPECT retry needed and checkout reservation released`() = scope.runTest {
        beginListenerCheckout()
        every { ApphudInternal.userRepository } returns mockk<UserRepository> { every { getCurrentUser() } returns null }
        listener.get()!!(PurchaseUpdatedCallbackStatus.Success(listOf(newPurchase())))
        runCurrent()
        assertEquals(null, recovery.current)
        assertEquals(1, received.size)
        check(received.single().error != null)
        verify { storage.isNeedSync = true }
        check(recovery.begin("next-product", null) != null)
    }

    @Test
    fun `GIVEN V2 no callback and an already observed checkout order EXPECT reservation released`() = scope.runTest {
        val observed = java.util.concurrent.ConcurrentHashMap.newKeySet<String>().apply { add("new-order") }
        every { ApphudInternal.observedOrders } returns observed
        beginListenerCheckout(withCallback = false)
        listener.get()!!(PurchaseUpdatedCallbackStatus.Success(listOf(newPurchase())))
        runCurrent()
        assertEquals(null, recovery.current)
        assertEquals(emptyList<ApphudPurchaseResult>(), received)
        coVerify(exactly = 0) { RequestManager.purchased(any()) }
        check(recovery.begin("next-product", null) != null)
    }

    @Test
    fun `GIVEN V2 listener reports another product without a customer EXPECT checkout remains reserved`() = scope.runTest {
        beginListenerCheckout()
        every { ApphudInternal.userRepository } returns mockk<UserRepository> { every { getCurrentUser() } returns null }
        val background = newPurchase()
        every { background.products } returns listOf("other-product")
        listener.get()!!(PurchaseUpdatedCallbackStatus.Success(listOf(background)))
        runCurrent()
        check(recovery.current != null)
        assertEquals(emptyList<ApphudPurchaseResult>(), received)
        coVerify(exactly = 0) { RequestManager.purchased(any()) }
    }

    @Test
    fun `GIVEN V2 listener reports an old token EXPECT it cannot complete the new checkout`() = scope.runTest {
        beginListenerCheckout()
        val old = newPurchase()
        every { old.purchaseToken } returns "old-token"
        listener.get()!!(PurchaseUpdatedCallbackStatus.Success(listOf(old)))
        runCurrent()
        check(recovery.current != null)
        assertEquals(emptyList<ApphudPurchaseResult>(), received)
    }

    @Test
    fun `GIVEN lost Google callback for ten minutes EXPECT timeout once and next checkout allowed`() = scope.runTest {
        beginListenerCheckout()
        advanceTimeBy(600_000L)
        runCurrent()
        assertEquals(APPHUD_ERROR_TIMEOUT, received.single().error?.errorCode)
        check(recovery.current == null)
        check(listener.get() == null)
        check(recovery.begin("next-product", null) != null)
    }

    @Test
    fun `GIVEN checkout open for several minutes EXPECT no premature timeout`() = scope.runTest {
        beginListenerCheckout()
        advanceTimeBy(599_999L)
        runCurrent()
        assertEquals(emptyList<ApphudPurchaseResult>(), received)
        check(recovery.current != null)
    }

    @Test
    fun `GIVEN timeout then late old callback EXPECT no second result`() = scope.runTest {
        beginListenerCheckout()
        val staleListener = listener.get()!!
        advanceTimeBy(600_000L)
        runCurrent()
        staleListener(PurchaseUpdatedCallbackStatus.Success(listOf(newPurchase())))
        runCurrent()
        assertEquals(1, received.size)
        coVerify(exactly = 0) { RequestManager.purchased(any()) }
    }

    @Test
    fun `GIVEN successful checkout EXPECT its timeout cannot cancel the next one`() = scope.runTest {
        beginListenerCheckout()
        listener.get()!!(PurchaseUpdatedCallbackStatus.Success(listOf(newPurchase())))
        runCurrent()
        val next = recovery.begin("next-product", null)!!
        advanceTimeBy(600_000L)
        runCurrent()
        check(recovery.isCurrent(next))
        assertEquals(1, received.size)
    }

    @Test
    fun `GIVEN timed out checkout WHEN late purchase observed EXPECT automatic tracking without another callback`() = scope.runTest {
        beginListenerCheckout()
        advanceTimeBy(600_000L)
        runCurrent()
        val context = slot<com.apphud.sdk.internal.domain.model.PurchaseContext>()
        coEvery { RequestManager.purchased(capture(context)) } returns customer
        ApphudInternal.handleObservedPurchase(newPurchase(), userInitiated = false)
        runCurrent()
        assertEquals("new-token", context.captured.purchase.purchaseToken)
        assertEquals(null, context.captured.productBundleId)
        assertEquals(1, received.size)
    }

    @Test
    fun `GIVEN expired checkout waiting for billing WHEN next checkout starts EXPECT old flow never launched`() = scope.runTest {
        val connection = kotlinx.coroutines.CompletableDeferred<Unit>()
        val launched = mutableListOf<Int>()
        var calls = 0
        coEvery { billing.purchase(any(), any(), any(), any(), any(), any()) } coAnswers {
            val call = ++calls
            if (call == 1) connection.await()
            launched.add(call)
            null
        }
        beginListenerCheckout()
        advanceTimeBy(600_000L)
        runCurrent()
        purchaseStartedAt = 0
        ApphudInternal.purchase(activity, product, null, "offer", "old-token", 4, consumableInappProduct = false) { received.add(it) }
        runCurrent()
        val next = recovery.current!!
        val nextListener = listener.get()
        connection.complete(Unit)
        runCurrent()
        assertEquals(listOf(2), launched)
        check(recovery.isCurrent(next))
        check(listener.get() === nextListener)
    }

    private fun beginListenerCheckout(mode: Int = 4, withCallback: Boolean = true) {
        recovery.reset()
        callbacks.clear()
        ApphudInternal.purchase(activity, product, null, "offer", "old-token", mode, consumableInappProduct = false,
            callback = if (withCallback) ({ received.add(it) }) else null)
        scope.testScheduler.runCurrent()
    }

    private fun newPurchase(): Purchase = mockk {
        every { products } returns listOf("subscription")
        every { purchaseToken } returns "new-token"
        every { orderId } returns "new-order"
        every { purchaseState } returns Purchase.PurchaseState.PURCHASED
        every { isAcknowledged } returns true
    }
}

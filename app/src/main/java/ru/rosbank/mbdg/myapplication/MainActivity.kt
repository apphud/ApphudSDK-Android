package ru.rosbank.mbdg.myapplication

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchaseHistoryRecord
import com.android.billingclient.api.SkuDetails
import ru.rosbank.mbdg.myapplication.client.ApiClient
import ru.rosbank.mbdg.myapplication.internal.BillingWrapper
import ru.rosbank.mbdg.myapplication.mappers.ProductMapper
import ru.rosbank.mbdg.myapplication.view.ProductsAdapter

class MainActivity : AppCompatActivity() {

//    lateinit var billing: BillingClient

    private val mapper = ProductMapper()
    private val adapter = ProductsAdapter()

    private val wrapper = BillingWrapper(App.app)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        wrapper.skuCallback = { details ->
            val products =  details.map { mapper.map(it) }
            adapter.products = adapter.products.filter { it.details != null } + products
        }
        wrapper.purchasesCallback = { purchases ->
            printAllPurchases(purchases)
        }

        Apphud.onLoaded = { products ->
            Log.e("WOW", "MainActivity loaded products: $products")
            runOnUiThread { adapter.products = products.map { mapper.map(it) } }

            val consume = products.filter { it.product_id.contains("subscription") }.map { it.product_id }
            val nonConsume = products.filter { it.product_id.contains("sell") }.map { it.product_id }

            wrapper.details(BillingClient.SkuType.SUBS, consume)
            wrapper.details(BillingClient.SkuType.INAPP, nonConsume)
        }

        adapter.onClick = { model ->
            Log.e("WOW", "onClick model: $model")
            when (model.details) {
                null -> Log.e("WOW", "details is empty")
                else -> wrapper.purchase(this, model.details)
            }
        }

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewId)
        recyclerView.adapter = adapter

        Apphud.start(ApiClient.API_KEY)
    }

    private lateinit var skuDetails: SkuDetails

//    private fun onPurchaseHistoryClick() {
////        val skuType = when {
////            inAppButton.isChecked        -> BillingClient.SkuType.INAPP
////            subscriptionButton.isChecked -> BillingClient.SkuType.SUBS
////            else                         -> error("Not select skuType")
////        }
//        val skuType = BillingClient.SkuType.SUBS
//        val purchaseResult = billing.queryPurchases(skuType)
//        purchaseResult?.let { result ->
//            Log.e("WOW", "RESULT ${result.responseCode}")
//            Log.e("WOW", "purchasesList: ${result.purchasesList}")
//            printAllPurchases(result.purchasesList ?: emptyList())
//            Log.e("WOW", "RESULT ${result.billingResult.debugMessage}")
//            Log.e("WOW", "RESULT ${result.billingResult.responseCode}")
//        }
//        billing.queryPurchaseHistoryAsync(skuType) { result: BillingResult, histories: MutableList<PurchaseHistoryRecord>? ->
//            Log.e("WOW", "queryPurchaseHistory debugMessage: ${result.debugMessage}")
//            Log.e("WOW", "queryPurchaseHistory responseCode: ${result.responseCode}")
//            printAllPurchaseHistories(histories ?: emptyList())
//        }
//    }

    private fun printAllPurchaseHistories(histories: List<PurchaseHistoryRecord>) {
        histories.forEach { purchaseHistory ->
            val sku = purchaseHistory.sku
            val developerPayload = purchaseHistory.developerPayload
            val originalJson = purchaseHistory.originalJson
            val purchaseTime = purchaseHistory.purchaseTime
            val signature = purchaseHistory.signature
            val purchaseToken = purchaseHistory.purchaseToken

            Log.e("WOW", "All history Object: $purchaseHistory")
            Log.e("WOW", "sku: $sku")
            Log.e("WOW", "developerPayload: $developerPayload")
            Log.e("WOW", "originalJson: $originalJson")
            Log.e("WOW", "purchaseTime: $purchaseTime")
            Log.e("WOW", "signature: $signature")
            Log.e("WOW", "purchaseToken: $purchaseToken")
        }
    }

    private fun printPurchaseState(@Purchase.PurchaseState state: Int) = when (state) {
        Purchase.PurchaseState.PENDING           -> "pending"
        Purchase.PurchaseState.PURCHASED         -> "purchased"
        Purchase.PurchaseState.UNSPECIFIED_STATE -> "unspecifiedState"
        else                                     -> "unknown state: $state"
    }

    private fun printAllPurchases(purchases: List<Purchase>) {
        purchases.forEach { purchase ->
            val orderId = purchase.orderId
            val isAcknowledged = purchase.isAcknowledged
            val accountIdentifiers = purchase.accountIdentifiers
            val developerPayload = purchase.developerPayload
            val isAutoRenewing = purchase.isAutoRenewing
            val originalJson = purchase.originalJson
            val packageName = purchase.packageName
            val purchaseState = purchase.purchaseState
            val purchaseTime = purchase.purchaseTime
            val purchaseToken = purchase.purchaseToken
            val signature = purchase.signature
            val sku = purchase.sku

            Log.e(BillingWrapper.TAG, "All purchase Object: $purchase")
            Log.e(BillingWrapper.TAG, "orderId: $orderId")
            Log.e(BillingWrapper.TAG, "isAcknowledged: $isAcknowledged")
            Log.e(BillingWrapper.TAG, "accountIdentifiers: $accountIdentifiers")
            Log.e(BillingWrapper.TAG, "developerPayload: $developerPayload")
            Log.e(BillingWrapper.TAG, "isAutoRenewing: $isAutoRenewing")
            Log.e(BillingWrapper.TAG, "originalJson: $originalJson")
            Log.e(BillingWrapper.TAG, "packageName: $packageName")
            Log.e(BillingWrapper.TAG, "purchaseState: $purchaseState and parse: ${printPurchaseState(purchaseState)}")
            Log.e(BillingWrapper.TAG, "purchaseTime: $purchaseTime")
            Log.e(BillingWrapper.TAG, "purchaseToken: $purchaseToken")
            Log.e(BillingWrapper.TAG, "signature: $signature")
            Log.e(BillingWrapper.TAG, "sku: $sku")
        }
    }
}
package ru.rosbank.mbdg.myapplication

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.android.billingclient.api.*
import ru.rosbank.mbdg.myapplication.client.ApiClient
import ru.rosbank.mbdg.myapplication.client.dto.ProductDto
import ru.rosbank.mbdg.myapplication.mappers.ProductMapper
import ru.rosbank.mbdg.myapplication.view.ProductsAdapter

class MainActivity : AppCompatActivity() {

    lateinit var editText: EditText
    lateinit var inAppButton: RadioButton
    lateinit var subscriptionButton: RadioButton
    lateinit var groupContainer: RadioGroup

    lateinit var billing: BillingClient


    private val mapper = ProductMapper()
    private val adapter = ProductsAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Apphud.onLoaded = { products ->
            Log.e("WOW", "MainActivity loaded products: $products")
            runOnUiThread { adapter.products = products.map { mapper.map(it) } }

            val consume = products.filter { it.product_id.contains("subscription") }.map { it.product_id }
            val nonConsume = products.filter { it.product_id.contains("sell") }.map { it.product_id }
            onSkuDetailsButtonIdClick(BillingClient.SkuType.SUBS, consume)
            onSkuDetailsButtonIdClick(BillingClient.SkuType.INAPP, nonConsume)
        }

        adapter.onClick = { model ->
            Log.e("WOW", "onClick model: $model")
            when (model.details) {
                null -> Log.e("WOW", "details is empty")
                else -> flowOnClick(model.details)
            }
        }

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewId)
        recyclerView.adapter = adapter

        billing = BillingClient.newBuilder(App.app)
            .setListener { result: BillingResult, purchases: MutableList<Purchase>? ->
                Log.e("WOW", "result: $result purchases: $purchases")
                printAllPurchases(purchases ?: emptyList())
            }
            .enablePendingPurchases()
            .build()

        billing.startConnection(object : BillingClientStateListener {
            override fun onBillingServiceDisconnected() {
                Log.e("WOW", "Disconnected")
            }

            override fun onBillingSetupFinished(result: BillingResult) {
                Log.e("WOW", "setup result: $result")
                Log.e("WOW", "setup debugMessage: ${result.debugMessage}")
                Log.e("WOW", "setup responseCode: ${result.responseCode}")
            }
        })

        when (billing.isReady) {
            true -> Log.e("WOW", "billing is Ready")
            else -> Log.e("WOW", "billing is not Ready")
        }

        Apphud.start(ApiClient.API_KEY)
    }

    private lateinit var skuDetails: SkuDetails

    private fun flowOnClick(details: SkuDetails) {
        val params = BillingFlowParams.newBuilder()
            .setSkuDetails(details)
            .build()

        val result = billing.launchBillingFlow(this, params)
        Log.e("WOW", "flow result: $result")
        Log.e("WOW", "flow debugMessage: ${result.debugMessage}")
        Log.e("WOW", "flow responseCode: ${result.responseCode}")
    }

    private fun onConsumeClick() {
        val purchaseToken = editText.text.toString()
        Log.e("WOW", "onConsumeClick token: $purchaseToken")
        val consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()
        billing.consumeAsync(consumeParams) { result: BillingResult, value: String ->
            Log.e("WOW", "consume value: $value")
            Log.e("WOW", "consume result: $result")
            Log.e("WOW", "consume debugMessage: ${result.debugMessage}")
            Log.e("WOW", "consume responseCode: ${result.responseCode}")
        }
    }

    private fun onAcknowledgeClick() {

        val purchaseToken = editText.text.toString()
        Log.e("WOW", "onAcknowledgeClick token: $purchaseToken")
        val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()
        billing.acknowledgePurchase(acknowledgeParams) { result ->
            Log.e("WOW", "acknowledge result: $result")
            Log.e("WOW", "acknowledge debugMessage: ${result.debugMessage}")
            Log.e("WOW", "acknowledge responseCode: ${result.responseCode}")
        }
    }

    private fun onSkuDetailsButtonIdClick(
        @BillingClient.SkuType skuType: String,
        skus: List<String>
    ) {
//        val skuType = when {
//            inAppButton.isChecked        -> BillingClient.SkuType.INAPP
//            subscriptionButton.isChecked -> BillingClient.SkuType.SUBS
//            else                         -> error("Not select skuType")
//        }
//        val sku = editText.text.toString()
        val skuParams = SkuDetailsParams.newBuilder()
            .setSkusList(skus)
            .setType(skuType)
            .build()
        billing.querySkuDetailsAsync(skuParams) { result: BillingResult, details: List<SkuDetails>? ->
            Log.e("WOW", "querySkuDetails result: $result")
            Log.e("WOW", "querySkuDetails debugMessage: ${result.debugMessage}")
            Log.e("WOW", "querySkuDetails responseCode: ${result.responseCode}")
            printAllDetails(details ?: emptyList())
            val values = details ?: emptyList()
            val products =  values.map { mapper.map(it) }
            adapter.products = adapter.products.filter { it.details != null } + products
        }
    }

    private fun onPurchaseHistoryClick() {
        val skuType = when {
            inAppButton.isChecked        -> BillingClient.SkuType.INAPP
            subscriptionButton.isChecked -> BillingClient.SkuType.SUBS
            else                         -> error("Not select skuType")
        }
        val purchaseResult = billing.queryPurchases(skuType)
        purchaseResult?.let { result ->
            Log.e("WOW", "RESULT ${result.responseCode}")
            Log.e("WOW", "purchasesList: ${result.purchasesList}")
            printAllPurchases(result.purchasesList ?: emptyList())
            Log.e("WOW", "RESULT ${result.billingResult.debugMessage}")
            Log.e("WOW", "RESULT ${result.billingResult.responseCode}")
        }
        billing.queryPurchaseHistoryAsync(skuType) { result: BillingResult, histories: MutableList<PurchaseHistoryRecord>? ->
            Log.e("WOW", "queryPurchaseHistory debugMessage: ${result.debugMessage}")
            Log.e("WOW", "queryPurchaseHistory responseCode: ${result.responseCode}")
            printAllPurchaseHistories(histories ?: emptyList())
        }
    }

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

    private fun printAllDetails(details: List<SkuDetails>) {
        skuDetails = details.first()
        details.forEach { detail ->
            val type = detail.type
            val title = detail.title
            val description = detail.description
            val freeTrialPeriod = detail.freeTrialPeriod
            val iconUrl = detail.iconUrl
            val introductoryPrice = detail.introductoryPrice
            val introductoryPriceAmountMicros = detail.introductoryPriceAmountMicros
            val introductoryPriceCycles = detail.introductoryPriceCycles
            val introductoryPricePeriod = detail.introductoryPricePeriod
            val originalJson = detail.originalJson
            val originalPrice = detail.originalPrice
            val originalPriceAmountMicros = detail.originalPriceAmountMicros
            val price = detail.price
            val priceAmountMicros = detail.priceAmountMicros
            val priceCurrencyCode = detail.priceCurrencyCode
            val sku = detail.sku
            val subscriptionPeriod = detail.subscriptionPeriod

            Log.e("WOW", "All detail Object: $detail")
            Log.e("WOW", "type: $type")
            Log.e("WOW", "title: $title")
            Log.e("WOW", "description: $description")
            Log.e("WOW", "freeTrialPeriod: $freeTrialPeriod")
            Log.e("WOW", "iconUrl: $iconUrl")
            Log.e("WOW", "introductoryPrice: $introductoryPrice")
            Log.e("WOW", "introductoryPriceAmountMicros: $introductoryPriceAmountMicros")
            Log.e("WOW", "introductoryPriceCycles: $introductoryPriceCycles")
            Log.e("WOW", "introductoryPricePeriod: $introductoryPricePeriod")
            Log.e("WOW", "originalJson: $originalJson")
            Log.e("WOW", "originalPriceAmountMicros: $originalPriceAmountMicros")
            Log.e("WOW", "price: $price")
            Log.e("WOW", "priceAmountMicros: $priceAmountMicros")
            Log.e("WOW", "priceCurrencyCode: $priceCurrencyCode")
            Log.e("WOW", "sku: $sku")
            Log.e("WOW", "subscriptionPeriod: $subscriptionPeriod")
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

            Log.e("WOW", "All purchase Object: $purchase")
            Log.e("WOW", "orderId: $orderId")
            Log.e("WOW", "isAcknowledged: $isAcknowledged")
            Log.e("WOW", "accountIdentifiers: $accountIdentifiers")
            Log.e("WOW", "developerPayload: $developerPayload")
            Log.e("WOW", "isAutoRenewing: $isAutoRenewing")
            Log.e("WOW", "originalJson: $originalJson")
            Log.e("WOW", "packageName: $packageName")
            Log.e("WOW", "purchaseState: $purchaseState and parse: ${printPurchaseState(purchaseState)}")
            Log.e("WOW", "purchaseTime: $purchaseTime")
            Log.e("WOW", "purchaseToken: $purchaseToken")
            Log.e("WOW", "signature: $signature")
            Log.e("WOW", "sku: $sku")
        }
    }
}
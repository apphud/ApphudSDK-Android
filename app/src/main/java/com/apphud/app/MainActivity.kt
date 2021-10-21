package com.apphud.app

import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.android.billingclient.api.SkuDetails
import com.apphud.sdk.ApphudListener
import com.apphud.sdk.Apphud
import com.apphud.sdk.ProductId
import com.apphud.sdk.domain.ApphudNonRenewingPurchase
import com.apphud.sdk.domain.ApphudSubscription
import com.apphud.app.presentation.ProductModel
import com.apphud.app.presentation.ProductModelMapper
import com.apphud.app.presentation.ProductsAdapter
import com.apphud.sdk.ApphudUserPropertyKey

class MainActivity : AppCompatActivity() {

    private val mapper = ProductModelMapper()
    private val adapter = ProductsAdapter()

    private var products = mutableMapOf<ProductId, ProductModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val listener = object : ApphudListener {
            override fun apphudSubscriptionsUpdated(subscriptions: List<ApphudSubscription>) {

                Log.d("Apphud","SUBSCRIPTIONS UPDATED: ${Apphud.subscriptions() }. Has active subscription: ${Apphud.hasActiveSubscription()}")

                subscriptions.forEach { subscription ->
                    val model = when (val product = products[subscription.productId]) {
                        null -> mapper.map(subscription)
                        else -> mapper.map(product, subscription)
                    }
                    when (val existingSubscription = products[model.productId]?.subscription) {
                        null -> products[model.productId] = model
                        else -> Log.d("Apphud","already has subscription, will not update")
                    }
                }

                adapter.products = products.values.toList()
            }

            override fun apphudNonRenewingPurchasesUpdated(purchases: List<ApphudNonRenewingPurchase>) {
                purchases.forEach { purchase ->
                    val model = when (val product = products[purchase.productId]) {
                        null -> mapper.map(purchase)
                        else -> mapper.map(product, purchase)
                    }
                    products[model.productId] = model
                }
                adapter.products = products.values.toList()
            }

            override fun apphudFetchSkuDetailsProducts(details: List<SkuDetails>) {
                details.forEach { detail ->
                    val model = when (val product = products[detail.sku]) {
                        null -> mapper.map(detail)
                        else -> mapper.map(product, detail)
                    }
                    products[model.productId] = model
                }
                adapter.products = products.values.toList()
            }
        }
        Apphud.setListener(listener)

        adapter.onClick = { model ->
            Log.e("Apphud", "onClick model: $model")
            when (model.details) {
                null -> Log.e("Apphud", "details is empty")
                else -> Apphud.purchase(this, model.details.sku) { result ->
                    Log.d("Apphud","PURCHASE RESULT: ${Apphud.subscriptions() }. Has active subscription: ${Apphud.hasActiveSubscription()}")
                    Log.d("Apphud", "${result.error?.toString()}")
                }
            }
        }

        val restoreButton: Button = findViewById(R.id.syncButtonViewId)
        restoreButton.setOnClickListener {
            Apphud.restorePurchases { subscriptions, purchases, error ->
                Log.d("Apphud", "restorePurchases: subscriptions=${subscriptions?.toString()} purchases=${purchases?.toString()} error=${error?.toString()} ")
            }
        }
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewId)
        recyclerView.adapter = adapter

        val logoutButton: Button = findViewById(R.id.btnLogout)
        logoutButton.setOnClickListener {
            Apphud.logout()
            Log.d("Apphud", "LOGOUT")
        }

        val startButton: Button = findViewById(R.id.btnStart)
        startButton.setOnClickListener {
            Apphud.start(application, Constants.API_KEY)
        }

        val paywallsButton: Button = findViewById(R.id.btnPaywalls)
        paywallsButton.setOnClickListener {
            Apphud.getPaywalls { paywalls, error ->
                error?.let{
                    Log.d("Apphud", "PAYWALLS LOADING 1 FAILED")
                }
                paywalls?.let{
                    Log.d("Apphud", "PAYWALLS LOADED")
                    if(it.isNotEmpty()) {
                        Apphud.paywallShown(it.first())
                    }
                }
            }
            /*Apphud.getPaywalls { paywalls, error ->
                error?.let{
                    Log.d("Apphud", "PAYWALLS LOADING 2 FAILED")
                }
                paywalls?.let{
                    Log.d("Apphud", "PAYWALLS LOADED 2")
                }
            }
            Apphud.getPaywalls { paywalls, error ->
                error?.let{
                    Log.d("Apphud", "PAYWALLS LOADING 3 FAILED")
                }
                paywalls?.let{
                    Log.d("Apphud", "PAYWALLS LOADED 3")
                }
            }*/
        }

        val syncButton: Button = findViewById(R.id.btnSync)
        syncButton.setOnClickListener {
            //Apphud.syncPurchases()
        }
    }
}
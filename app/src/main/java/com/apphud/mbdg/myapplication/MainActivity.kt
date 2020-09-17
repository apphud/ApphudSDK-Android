package com.apphud.mbdg.myapplication

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
import com.apphud.mbdg.myapplication.presentation.ProductModel
import com.apphud.mbdg.myapplication.presentation.ProductModelMapper
import com.apphud.mbdg.myapplication.presentation.ProductsAdapter

class MainActivity : AppCompatActivity() {

    private val mapper = ProductModelMapper()
    private val adapter = ProductsAdapter()

    private var products = mutableMapOf<ProductId, ProductModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val listener = object : ApphudListener {
            override fun apphudSubscriptionsUpdated(subscriptions: List<ApphudSubscription>) {

                subscriptions.forEach { subscription ->
                    val model = when (val product = products[subscription.productId]) {
                        null -> mapper.map(subscription)
                        else -> mapper.map(product, subscription)
                    }
                    products[model.productId] = model
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
                else -> Apphud.purchase(this, model.details) { _ -> }
            }
        }

        val syncButton: Button = findViewById(R.id.syncButtonViewId)
        syncButton.setOnClickListener {
            Apphud.syncPurchases()
        }
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewId)
        recyclerView.adapter = adapter
    }
}
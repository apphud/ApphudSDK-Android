package ru.rosbank.mbdg.myapplication

import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.android.billingclient.api.SkuDetails
import com.apphud.sdk.ApphudListener
import com.apphud.sdk.Apphud
import com.apphud.sdk.domain.ApphudNonRenewingPurchase
import com.apphud.sdk.domain.ApphudSubscription
import ru.rosbank.mbdg.myapplication.presentation.ProductModelMapper
import ru.rosbank.mbdg.myapplication.presentation.ProductsAdapter

class MainActivity : AppCompatActivity() {

    private val mapper = ProductModelMapper()
    private val adapter = ProductsAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val listener = object : ApphudListener {
            override fun apphudSubscriptionsUpdated(subscriptions: List<ApphudSubscription>) {
                val products = adapter.products.map { product ->
                    subscriptions.firstOrNull { it.productId == product.productId }
                        ?.let { subscription -> mapper.map(product, subscription) }
                        ?: product
                }
                adapter.products = adapter.products.filter { it.subscription != null } + products
            }

            override fun apphudNonRenewingPurchasesUpdated(purchases: List<ApphudNonRenewingPurchase>) {
                val products = adapter.products.map { product ->
                    purchases.firstOrNull { it.productId == product.productId }
                        ?.let { purchase -> mapper.map(product, purchase) }
                        ?: product
                }
                adapter.products = adapter.products.filter { it.purchase != null } + products
            }

            override fun apphudFetchSkuDetailsProducts(details: List<SkuDetails>) {
                adapter.products += details.map { mapper.map(it) }
           }
        }
        Apphud.setListener(listener)

        adapter.onClick = { model ->
            Log.e("Apphud", "onClick model: $model")
            when (model.details) {
                null -> Log.e("Apphud", "details is empty")
                else ->  Apphud.purchase(this, model.details) { _ -> }
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
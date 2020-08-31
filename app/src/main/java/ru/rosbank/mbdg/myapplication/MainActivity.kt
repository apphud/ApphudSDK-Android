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
import ru.rosbank.mbdg.myapplication.presentation.ProductModelMapper
import ru.rosbank.mbdg.myapplication.presentation.ProductsAdapter

class MainActivity : AppCompatActivity() {

    private val mapper = ProductModelMapper()
    private val adapter = ProductsAdapter()

//    private val wrapper = BillingWrapper(App.app)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val listener = object : ApphudListener {
            override fun apphudFetchSkuDetailsProducts(details: List<SkuDetails>) {
                val products =  details.map { mapper.map(it) }
                adapter.products = adapter.products.filter { it.details != null } + products
                Log.e("WOW", "details: $details")
            }
        }
        ApphudSdk.setListener(listener)

//        wrapper.skuCallback = { details ->
//            val products =  details.map { mapper.map(it) }
//            adapter.products = adapter.products.filter { it.details != null } + products
//            Log.e("WOW", "details: $details")
//        }
//        wrapper.purchasesCallback = { purchases ->
//            printAllPurchases(purchases)
//        }

//        Apphud.onLoaded = { products ->
//            val ids = products.map { it.product_id }
//            wrapper.details(BillingClient.SkuType.SUBS, ids)
//            wrapper.details(BillingClient.SkuType.INAPP, ids)
//        }

        adapter.onClick = { model ->
            Log.e("WOW", "onClick model: $model")
            when (model.details) {
                null -> Log.e("WOW", "details is empty")
                else -> {
//                    wrapper.purchase(this, model.details)
                    ApphudSdk.purchase(this, model.details) { purchase -> }
                }
            }
        }

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewId)
        recyclerView.adapter = adapter

        //TODO Тест на то, если будем слишком часто вызывать этот метод
        ApphudSdk.start()
        ApphudLog.log("userId: ${ApphudSdk.userId()}")
        ApphudLog.log("subscription: ${ApphudSdk.subscription()}")
        ApphudLog.log("subscriptions: ${ApphudSdk.subscriptions()}")
        ApphudLog.log("nonRenewingPurchases: ${ApphudSdk.nonRenewingPurchases()}")
        ApphudSdk.syncPurchases()
    }
}
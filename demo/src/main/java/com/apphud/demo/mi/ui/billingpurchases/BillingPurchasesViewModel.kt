package com.apphud.demo.mi.ui.billingpurchases

import android.app.Activity
import android.util.Log
import android.widget.Toast
import com.xiaomi.billingclient.api.BillingClient
import com.xiaomi.billingclient.api.BillingClientStateListener
import com.xiaomi.billingclient.api.BillingResult
import com.xiaomi.billingclient.api.PurchasesUpdatedListener


class BillingPurchasesViewModel (val activity: Activity){
    var items = mutableListOf<Any>()
    private var billingClient: BillingClient
    var skuType = BillingClient.SkuType.ALL
    var text :String = ""
    var addLog: ((text: String) -> Unit)? = null

    fun updateData(completionHandler: () -> Unit) {
        items.clear()

        if(billingClient.isReady) {
            getPurchases(BillingClient.SkuType.SUBS){
                getPurchases(BillingClient.SkuType.INAPP){
                    completionHandler()
                }
            }
        } else {
            addLog?.invoke("Billingclient: IS NOT READY")
            Log.d("ApphudDemo", "Billingclient: IS NOT READY")
            completionHandler()
        }
    }

    private fun getPurchases(type: String, completionHandler: () -> Unit){
        billingClient.queryPurchasesAsync(type) { billingResult, list ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                items.addAll(list)
                Log.d("ApphudDemo", "Purchases (${type}): ${list.size}")
                addLog?.invoke("Purchases (${type}): ${list.size}")
            } else {
                Log.d("ApphudDemo", "debug message: ${billingResult.debugMessage}")
                addLog?.invoke("debug message: ${billingResult.debugMessage}")
            }
            completionHandler()
        }
    }

    private val billingClientStateListener: BillingClientStateListener =
        object : BillingClientStateListener {
            override fun onBillingServiceDisconnected() {
                addLog?.invoke("DISCONNECTED")
                Log.d("ApphudDemo", "DISCONNECTED")
            }
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                val code = billingResult.responseCode
                addLog?.invoke("Service.code : $code")
                Log.d("ApphudDemo", "Service.code : $code")
                if (code == BillingClient.BillingResponseCode.OK) {
                    addLog?.invoke("CONNECTED")
                    Log.d("ApphudDemo", "CONNECTED")
                }
            }
        }

    private val purchasesUpdatedListener =
        PurchasesUpdatedListener { billingResult, list ->
            val code = billingResult.responseCode
            Log.d("TAG", "onPurchasesUpdated.code = $code")
            if (code == BillingClient.BillingResponseCode.PAYMENT_SHOW_DIALOG) {
                Log.d("ApphudDemo", "PAYMENT_SHOW_DIALOG")
            } else if (code == BillingClient.BillingResponseCode.OK) {
                Log.d("ApphudDemo", "OK")
            } else if (code == BillingClient.BillingResponseCode.USER_CANCELED) {
                Log.d("ApphudDemo", "USER_CANCELED")
            } else {
                Log.d("ApphudDemo", "debug message: ${billingResult.debugMessage}")
            }
        }

    init{
        billingClient = BillingClient.newBuilder(activity).setListener(purchasesUpdatedListener).build()
        billingClient.enableFloatView(activity)
        billingClient.startConnection(billingClientStateListener)
    }

    fun consume(purchaseToken :String) {
        billingClient.consumeAsync(purchaseToken) { billingResult: BillingResult, str: String? ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val t = Toast.makeText(activity, "Consumed", Toast.LENGTH_LONG)
                t.show()
            } else {
                val t = Toast.makeText(activity, "Consume error", Toast.LENGTH_LONG)
                t.show()
            }
        }
    }
}
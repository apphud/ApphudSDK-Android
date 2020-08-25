package ru.rosbank.mbdg.myapplication.view

import android.annotation.SuppressLint
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.android.billingclient.api.SkuDetails
import ru.rosbank.mbdg.myapplication.R
import ru.rosbank.mbdg.myapplication.domain.ProductModel

class ProductHolder(
    val view: View,
    val productId: TextView,
    val type: TextView,
    val title: TextView,
    val description: TextView,
    val freeTrialPeriod: TextView,
    val introductoryPrice: TextView,
    val introductoryPriceAmountMicros: TextView,
    val introductoryPriceCycles: TextView,
    val introductoryPricePeriod: TextView,
    val originalPrice: TextView,
    val originalPriceAmountMicros: TextView,
    val price: TextView,
    val priceAmountMicros: TextView,
    val priceCurrencyCode: TextView,
    val sku: TextView,
    val subscriptionPeriod: TextView,
    val button: Button
) : RecyclerView.ViewHolder(view) {
    
    @SuppressLint("SetTextI18n")
    fun bind(product: ProductModel) {
        productId.text = product.productId
        type.text = "type: " + product.details?.type
        title.text = "title: " + product.details?.title
        description.text = "description: " + product.details?.description
        freeTrialPeriod.text = "freeTrialPeriod: " + product.details?.freeTrialPeriod
        introductoryPrice.text = "introductoryPrice: " + product.details?.introductoryPrice
        introductoryPriceAmountMicros.text = "introductoryPriceAmountMicros: " + product.details?.introductoryPriceAmountMicros?.toString()
        introductoryPriceCycles.text = "introductoryPriceCycles: " + product.details?.introductoryPriceCycles?.toString()
        introductoryPricePeriod.text = "introductoryPricePeriod: " + product.details?.introductoryPricePeriod
        originalPrice.text = "originalPrice: " + product.details?.originalPrice
        originalPriceAmountMicros.text = "originalPriceAmountMicros: " + product.details?.originalPriceAmountMicros?.toString()
        price.text = "price: " + product.details?.price
        priceAmountMicros.text = "priceAMountMicros: " + product.details?.priceAmountMicros?.toString()
        priceCurrencyCode.text = "priceCurrencyCode: " + product.details?.priceCurrencyCode
        sku.text = "sku: " + product.details?.sku
        subscriptionPeriod.text = "subscriptionPeriod: " + product.details?.subscriptionPeriod
    }
}

fun productHolder(view: View) =
    ProductHolder(
        view = view,
        productId = view.findViewById(R.id.productViewId),
        type = view.findViewById(R.id.typeViewId),
        title = view.findViewById(R.id.titleViewId),
        description = view.findViewById(R.id.textView4),
        freeTrialPeriod = view.findViewById(R.id.freeTrialPeriodId),
        introductoryPrice = view.findViewById(R.id.introductoryPriceId),
        introductoryPriceAmountMicros = view.findViewById(R.id.introductoryPriceAmountMicrosId),
        introductoryPriceCycles = view.findViewById(R.id.introductoryPriceAmountMicrosId5Id),
        introductoryPricePeriod = view.findViewById(R.id.introductoryPricePeriodId),
        originalPrice = view.findViewById(R.id.originalPriceId),
        originalPriceAmountMicros = view.findViewById(R.id.originalPriceAmountMicrosId),
        price = view.findViewById(R.id.priceId),
        priceAmountMicros = view.findViewById(R.id.priceAmountMicrosId),
        priceCurrencyCode = view.findViewById(R.id.priceCurrencyCodeID),
        sku = view.findViewById(R.id.skuId),
        subscriptionPeriod = view.findViewById(R.id.subscriptionPeriodId),
        button = view.findViewById(R.id.buyButtonId)
    )
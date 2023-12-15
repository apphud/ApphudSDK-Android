package com.apphud.demo.ui.products

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.android.billingclient.api.BillingClient
import com.apphud.demo.R
import com.apphud.sdk.domain.ApphudProduct

class ProductsAdapter(private val productsViewModel: ProductsViewModel, private val context: Context?) : RecyclerView.Adapter<ProductsAdapter.BaseViewHolder<*>>() {
    var selectProduct: ((account: ApphudProduct) -> Unit)? = null

    abstract class BaseViewHolder<T>(itemView: View) : RecyclerView.ViewHolder(itemView) {
        abstract fun bind(
            item: T,
            position: Int,
        )
    }

    inner class ApphudProductViewHolder(itemView: View) : BaseViewHolder<ApphudProduct>(itemView) {
        private val productName: TextView = itemView.findViewById(R.id.productName)
        private val productPrice: TextView = itemView.findViewById(R.id.productPrice)

        override fun bind(
            item: ApphudProduct,
            position: Int,
        ) {
            productName.text = "Name: " + item.name + "\nProduct ID: " + item.productId + "\nBase Plan ID: " + item.basePlanId

            item.productDetails?.let { details ->
                if (details.productType == BillingClient.ProductType.SUBS) {
                    productPrice.text = item.subscriptionOffers()?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice ?: ""
                } else {
                    productPrice.text = details.oneTimePurchaseOfferDetails?.formattedPrice ?: ""
                }
            } ?: run {
                productPrice.text = "ProductDetails N/A"
            }

            itemView.setOnClickListener {
                selectProduct?.invoke(item)
            }
        }
    }

    companion object {
        private const val TYPE_PRODUCT = 0
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): BaseViewHolder<*> {
        return when (viewType) {
            TYPE_PRODUCT -> {
                val view =
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.list_item_product_card, parent, false)
                ApphudProductViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(
        holder: BaseViewHolder<*>,
        position: Int,
    ) {
        val element = productsViewModel.items[position]
        when (holder) {
            is ApphudProductViewHolder -> holder.bind(element as ApphudProduct, position)
            else -> throw IllegalArgumentException()
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (productsViewModel.items[position]) {
            is ApphudProduct -> TYPE_PRODUCT
            else -> throw IllegalArgumentException("Invalid type of data " + position)
        }
    }

    override fun getItemCount() = productsViewModel.items.size
}

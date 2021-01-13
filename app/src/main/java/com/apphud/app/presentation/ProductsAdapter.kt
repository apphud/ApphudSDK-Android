package com.apphud.app.presentation

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.apphud.app.R

fun diff(old: List<ProductModel>, new: List<ProductModel>) = object : DiffUtil.Callback() {
    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
        old[oldItemPosition].productId == new[newItemPosition].productId

    override fun getOldListSize(): Int = old.size
    override fun getNewListSize(): Int = new.size
    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
        old[oldItemPosition] == new[newItemPosition]
}

class ProductsAdapter : RecyclerView.Adapter<ProductHolder>() {

    var onClick: ((ProductModel) -> Unit)? = null
    var products: List<ProductModel> = emptyList()
        set(value) {
            val diff = DiffUtil.calculateDiff(diff(field, value))
            field = value
            diff.dispatchUpdatesTo(this)
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.product_item, parent, false)
        return productHolder(view).also { holder ->
            holder.button.setOnClickListener {
                when (val position = holder.adapterPosition) {
                    RecyclerView.NO_POSITION -> Unit
                    else                     -> onClick?.invoke(products[position])
                }
            }
        }
    }

    override fun getItemCount(): Int = products.size
    override fun onBindViewHolder(holder: ProductHolder, position: Int) =
        holder.bind(products[position])
}
package com.apphud.sampleapp.ui.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.annotation.AttrRes
import androidx.constraintlayout.widget.ConstraintLayout
import com.apphud.sampleapp.databinding.ViewProductButtonBinding
import com.apphud.sdk.domain.ApphudProduct
import com.apphud.sdk.domain.ApphudProductType
import com.apphud.sdk.managers.subscriptionPeriod
import java.time.Period

class ProductButton  (context: Context, attrs: AttributeSet?, @AttrRes defStyleAttr: Int) : ConstraintLayout(context, attrs, defStyleAttr){

    private var binding: ViewProductButtonBinding = ViewProductButtonBinding.inflate(LayoutInflater.from(context), this, true)
    private var product :ApphudProduct? = null

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    fun setProduct(product: ApphudProduct){
        this.product = product

        var price = if(product.type() == ApphudProductType.SUBS){
            product.subscriptionOfferDetails()?.let {
                if(it.isNotEmpty()){
                    val period = period(it[0].pricingPhases?.pricingPhaseList?.get(0)?.billingPeriod?:"")
                    val result =  it[0].pricingPhases?.pricingPhaseList?.get(0)?.formattedPrice?:"N/A"
                    result + period
                } else {
                    "N/A"
                }
            }?: "N/A"
        } else {
            "N/A"
        }

        binding.labelTitle.text = product.name
        binding.labelPrice.text = price

        invalidate()
    }

    private fun period (value :String) :String{
        return when(value){
            "P1W" -> "/week"
            "P1Y"-> "/year"
            else -> ""
        }
    }

}
package com.apphud.app.ui.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.widget.doOnTextChanged
import com.apphud.app.R
import com.apphud.app.databinding.CustomPropertyApphudViewBinding


open class CustomPropertyApphudView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    ConstraintLayout(context, attrs, defStyleAttr), IProperties {

    private lateinit var binding: CustomPropertyApphudViewBinding

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = CustomPropertyApphudViewBinding.bind(this)
    }

    init {
        init(attrs)
    }

    private fun init(attrs: AttributeSet?) {
        binding = CustomPropertyApphudViewBinding.inflate(LayoutInflater.from(context), this, true)

        binding.btnRemove.setOnClickListener {
            (this.parent as ViewGroup).removeView(this)
        }

        binding.etValue.doOnTextChanged { text, start, count, after ->
            binding.etValueHolder.error = null
        }

        context?.let{
            ArrayAdapter.createFromResource(
                it,
                R.array.properties_types,
                android.R.layout.simple_spinner_item
            ).also { adapter ->
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.spinnerType.adapter = adapter
            }
        }
    }

    override fun key(): String{
        return binding.spinnerType.selectedItem.toString()
    }

    override fun value(): Any?{
        if(binding.etValue.text.toString().isEmpty()){
            return null
        }

        return return binding.etValue.text.toString()
    }
}
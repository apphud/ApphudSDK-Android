package com.apphud.app.ui.views

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.widget.doOnTextChanged
import com.apphud.app.R
import com.apphud.app.databinding.CustomPropertyViewBinding

open class CustomPropertyView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    ConstraintLayout(context, attrs, defStyleAttr), IProperties {

    private lateinit var binding: CustomPropertyViewBinding

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = CustomPropertyViewBinding.bind(this)
    }

    init {
        init(attrs)
    }

    private fun init(attrs: AttributeSet?) {
        binding = CustomPropertyViewBinding.inflate(LayoutInflater.from(context), this, true)

        binding.btnRemove.setOnClickListener {
            (this.parent as ViewGroup).removeView(this)
        }

        binding.etKey.doOnTextChanged { text, start, count, after ->
            binding.etKeyHolder.error = null
        }

        binding.etValue.doOnTextChanged { text, start, count, after ->
            binding.etValueHolder.error = null
        }

        binding.chkSetOnce.setOnCheckedChangeListener{ _, isChecked ->
            if(isChecked){
                binding.chkIncrement.isChecked = false
            }
        }

        binding.chkIncrement.setOnCheckedChangeListener{ _, isChecked ->
            if(isChecked){
                binding.chkSetOnce.isChecked = false
            }
        }

        context?.let{
            ArrayAdapter.createFromResource(
                it,
                R.array.types_array,
                android.R.layout.simple_spinner_item
            ).also { adapter ->
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.spinnerType.adapter = adapter
            }
        }
    }

    fun isValid(): Boolean{
        val isKeyValid = binding.etKey.text.toString().isNotEmpty()
        if(!isKeyValid){
            binding.etKeyHolder.error = context?.getString(R.string.error_must_be_not_empty)
        }else{
            binding.etKeyHolder.error = null
        }
        /*val isValueValid = binding.etValue.text.toString().isNotEmpty()
        if(!isValueValid){
            binding.etValueHolder.error = context?.getString(R.string.error_must_be_not_empty)
        }else{
            binding.etValueHolder.error = null
        }*/

        return isKeyValid //&& isValueValid
    }

    override fun key(): String{
        return binding.etKey.text.toString()
    }

    override fun value(): Any?{
        if(binding.etValue.text.toString().isEmpty()){
            return null
        }

        try {
            when (binding.spinnerType.selectedItem.toString()) {
                "String" -> {
                    return binding.etValue.text.toString()
                }
                "Boolean" -> {
                    return binding.etValue.text.toString().toBoolean()
                }
                "Integer" -> {
                    return binding.etValue.text.toString().toInt()
                }
                "Float" -> {
                    return binding.etValue.text.toString().toFloat()
                }
                "Double" -> {
                    return binding.etValue.text.toString().toDouble()
                }
            }
        }catch(ex: Exception){
            Log.d("Apphud QA" , ex.message?:"Unable to parse property value. Processed as string.")
            return  binding.etValue.text.toString()
        }

        return null
    }

    fun setOnce(): Boolean{
        return binding.chkSetOnce.isChecked
    }

    fun increment(): Boolean{
        return binding.chkIncrement.isChecked
    }
}
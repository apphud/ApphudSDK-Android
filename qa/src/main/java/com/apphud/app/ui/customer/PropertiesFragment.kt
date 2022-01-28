package com.apphud.app.ui.customer

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.children
import androidx.navigation.fragment.findNavController
import com.apphud.app.R
import com.apphud.app.databinding.FragmentPropertiesBinding
import com.apphud.app.ui.views.CustomPropertyApphudView
import com.apphud.app.ui.views.CustomPropertyView
import com.apphud.sdk.Apphud
import com.apphud.sdk.ApphudUserPropertyKey
import com.google.android.material.snackbar.Snackbar

class PropertiesFragment : Fragment() {

    private var _binding: FragmentPropertiesBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentPropertiesBinding.inflate(inflater, container, false)
        val root: View = binding.root

        activity?.let{
            ArrayAdapter.createFromResource(
                it,
                R.array.genders_array,
                android.R.layout.simple_spinner_item
            ).also { adapter ->
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.spinnerGender.adapter = adapter
            }
        }

        binding.btnAddCustom.setOnClickListener { button ->
            context?.let {
                binding.layoutCustoms.addView(CustomPropertyView(it))
            }
        }

        binding.btnAddProperty.setOnClickListener { button ->
            context?.let {
                binding.layoutCustoms.addView(CustomPropertyApphudView(it))
            }
        }

        binding.btnSend.setOnClickListener {
            processProperties()
        }

        return root
    }

    private fun processProperties() {
        if(binding.spinnerGender.selectedItem.toString() != resources.getStringArray(R.array.genders_array)[0]){
            Apphud.setUserProperty(key = ApphudUserPropertyKey.Gender, value = binding.spinnerGender.selectedItem.toString())
        }
        binding.layoutCustoms.children.forEach { view ->
            if(view is CustomPropertyView) {
                if (view.increment()) {
                    view.value()?.let {
                        Apphud.incrementUserProperty(
                            key = ApphudUserPropertyKey.CustomProperty(view.key()),
                            by = it
                        )
                    }
                } else {
                    Apphud.setUserProperty(
                        key = ApphudUserPropertyKey.CustomProperty(view.key()),
                        value = view.value(),
                        setOnce = view.setOnce()
                    )
                }
            } else if(view is CustomPropertyApphudView) {
                Apphud.setUserProperty(key = getApphudUserPropertyKey(view.key()), view.value())
            }
        }

        //findNavController().popBackStack()
    }

    private fun getApphudUserPropertyKey(key: String) :ApphudUserPropertyKey{
        when(key){
            "Name" -> {
                return ApphudUserPropertyKey.Name
            }
            "Email" -> {
                return ApphudUserPropertyKey.Email
            }
            "Phone" -> {
                return ApphudUserPropertyKey.Phone
            }
            "Cohort" -> {
                return ApphudUserPropertyKey.Cohort
            }
            "Age" -> {
                return ApphudUserPropertyKey.Age
            }
        }
        return ApphudUserPropertyKey.Name
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
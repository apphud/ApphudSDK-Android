package com.apphud.demo.ui.utils

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.android.billingclient.api.ProductDetails
import com.apphud.demo.R
import com.apphud.demo.databinding.FragmentOffersBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class OffersFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentOffersBinding? = null
    private val binding get() = _binding!!

    var offers :List<ProductDetails.SubscriptionOfferDetails> = listOf()
    private lateinit var typesViewModel:OffersViewModel
    private lateinit var viewAdapter: OffersAdapter

    var offerSelected: ((itemSelected: ProductDetails.SubscriptionOfferDetails)->Unit)? = null

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOffersBinding.inflate(inflater, container, false)
        val root: View = binding.root

        typesViewModel = OffersViewModel(offers)
        viewAdapter = OffersAdapter(typesViewModel)
        viewAdapter.selectedOffer = {
            offerSelected?.invoke(it)
            dismiss()
        }
        binding.offersList.adapter = viewAdapter

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun offerSelected(function: () -> Unit) {

    }
}
package com.apphud.demo.mi.ui.products

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.apphud.demo.mi.R
import com.apphud.demo.mi.databinding.FragmentProductsBinding
import com.apphud.demo.mi.ui.utils.OffersFragment
import com.apphud.sdk.Apphud
import com.apphud.sdk.domain.ApphudPaywall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProductsFragment : Fragment() {
    val args: ProductsFragmentArgs by navArgs()
    private var _binding: FragmentProductsBinding? = null
    private val binding get() = _binding!!

    private lateinit var productsViewModel: ProductsViewModel
    private lateinit var viewAdapter: ProductsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        productsViewModel = ViewModelProvider(this)[ProductsViewModel::class.java]
        _binding = FragmentProductsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        viewAdapter = ProductsAdapter(productsViewModel, context)
        viewAdapter.selectProduct = { product ->
            activity?.let { activity ->
                product.skuDetails?.let { details ->

                    val offers = details.subscriptionOfferDetails?.map { it.pricingPhases.pricingPhaseList[0].formattedPrice }
                    offers?.let { _ ->
                        details.subscriptionOfferDetails?.let {
                            val fragment = OffersFragment()
                            fragment.offers = it
                            fragment.offerSelected = { offer ->
                                Apphud.purchase(activity = activity, apphudProduct = product, consumableInAppProduct = true, offerIdToken = offer.offerToken) { result ->
                                    result.error?.let { err ->
                                        Toast.makeText(activity, if (result.userCanceled()) "User Canceled" else err.message, Toast.LENGTH_SHORT).show()
                                    } ?: run {
                                        Toast.makeText(activity, R.string.success, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            fragment.apply {
                                show(activity.supportFragmentManager, tag)
                            }
                        }
                    } ?: run {
                        Apphud.purchase(activity = activity, apphudProduct = product, consumableInAppProduct = true) { result ->
                            result.error?.let { err ->
                                Toast.makeText(activity, if (result.userCanceled()) "User Canceled" else err.message, Toast.LENGTH_SHORT).show()
                            } ?: run {
                                Toast.makeText(activity, R.string.success, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }

        val recyclerView: RecyclerView = binding.productsList
        recyclerView.layoutManager = GridLayoutManager(activity, 1)
        recyclerView.apply {
            adapter = viewAdapter
        }

        lifecycleScope.launch {
            val p = findPaywall(args.paywallId, args.placementId)
            p?.let { Apphud.paywallShown(it) }
            updateData(p)
        }

        return root
    }

    suspend fun findPaywall(
        paywallId: String?,
        placementId: String?,
    ): ApphudPaywall? {
        val paywall =
            if (placementId != null) {
                Apphud.placements().firstOrNull { it.identifier == placementId }?.paywall
            } else {
                Apphud.paywalls().firstOrNull { it.identifier == paywallId }
            }
        return paywall
    }

    @SuppressLint("NotifyDataSetChanged")
    private suspend fun updateData(paywall: ApphudPaywall?) {
        productsViewModel.updateData(paywall)
        withContext(Dispatchers.Main) {
            viewAdapter.notifyDataSetChanged()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

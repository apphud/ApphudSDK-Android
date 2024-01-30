package com.apphud.demo.mi.ui.billing

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.apphud.demo.mi.databinding.FragmentBillingBinding
import com.apphud.demo.mi.ui.products.ProductsAdapter
import com.apphud.demo.mi.ui.utils.OffersFragment
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BillingFragment : Fragment() {
    private var _binding: FragmentBillingBinding? = null
    private val binding get() = _binding!!

    private lateinit var billingViewModel: BillingViewModel
    private lateinit var billingAdapter: BillingAdapter

    val mainScope = CoroutineScope(Dispatchers.Main)
    val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val errorHandler = CoroutineExceptionHandler { _, error ->
        error.message?.let {
            Log.e("BaseManager", it)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        _binding = FragmentBillingBinding.inflate(inflater, container, false)
        val root: View = binding.root

        binding.swipeRefresh.setOnRefreshListener {
            updateData()
        }

        activity?.let{a ->
            billingViewModel = BillingViewModel(a)
            billingAdapter = BillingAdapter(billingViewModel, context)
            billingAdapter.selectProduct = { product ->
                val offers = product.subscriptionOfferDetails?.map { it.pricingPhases.pricingPhaseList[0].formattedPrice }
                offers?.let { _ ->

                    product.subscriptionOfferDetails?.let {
                        val fragment = OffersFragment()
                        fragment.offers = it
                        fragment.offerSelected = { offer ->
                            billingViewModel.buy(
                                skuDetails = product,
                                currentPurchases = null,
                                activity = a,
                                offerIdToken = offer.offerToken,
                            )
                        }
                        fragment.apply {
                            show(a.supportFragmentManager, tag)
                        }
                    }
                } ?: run {
                    billingViewModel.buy(
                        skuDetails = product,
                        currentPurchases = null,
                        activity = a,
                        offerIdToken = null,
                    )
                }
            }

            val recyclerView: RecyclerView = binding.productsList
            recyclerView.layoutManager = GridLayoutManager(activity, 2)
            recyclerView.adapter = billingAdapter

            binding.checkConsume.isChecked = billingViewModel.consumeInapp
            binding.checkConsume.setOnCheckedChangeListener { _ , isChecked ->
                billingViewModel.consumeInapp = isChecked
            }

            updateData()
        }
        return root
    }

    private fun updateData(){
        coroutineScope.launch {
            billingViewModel.updateData {
                mainScope.launch {
                    _binding?.swipeRefresh?.isRefreshing = false
                    billingAdapter.notifyDataSetChanged()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        billingViewModel.stop()
    }
}
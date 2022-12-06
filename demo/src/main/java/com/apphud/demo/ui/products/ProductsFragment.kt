package com.apphud.demo.ui.products

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.billingclient.api.BillingFlowParams
import com.apphud.demo.MainActivity
import com.apphud.demo.R
import com.apphud.demo.databinding.FragmentProductsBinding
import com.apphud.sdk.Apphud


class ProductsFragment : Fragment() {

    val args: ProductsFragmentArgs by navArgs()
    private var _binding: FragmentProductsBinding? = null
    private val binding get() = _binding!!

    private lateinit var productsViewModel: ProductsViewModel
    private lateinit var viewAdapter: ProductsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        productsViewModel = ViewModelProvider(this)[ProductsViewModel::class.java]
        _binding = FragmentProductsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        viewAdapter = ProductsAdapter(productsViewModel, context)
        viewAdapter.selectProduct = { product ->
            activity?.let{ activity ->
                product.skuDetails?.let{
                    val billingFlowParams = BillingFlowParams.newBuilder()
                        .setSkuDetails(it)
                        .build()

                    (activity as MainActivity).billingClient?.launchBillingFlow(activity, billingFlowParams)
                }

                /*Apphud.purchase(activity, product){ result ->
                    result.error?.let{ err->
                        Toast.makeText(activity, err.message, Toast.LENGTH_SHORT).show()
                    }?: run{
                        Toast.makeText(activity, R.string.success, Toast.LENGTH_SHORT).show()
                    }
                }*/
            }
        }

        val recyclerView: RecyclerView = binding.productsList
        recyclerView.layoutManager = GridLayoutManager(activity,2)
        recyclerView.apply {
            adapter = viewAdapter
        }
        updateData(args.paywallId)

        return root
    }

    private fun updateData(pywallId: String){
        productsViewModel.updateData(pywallId)
        viewAdapter.notifyDataSetChanged()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
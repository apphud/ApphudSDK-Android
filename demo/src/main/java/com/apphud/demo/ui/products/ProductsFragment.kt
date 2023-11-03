package com.apphud.demo.ui.products

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.billingclient.api.BillingClient
import com.apphud.demo.R
import com.apphud.demo.databinding.FragmentProductsBinding
import com.apphud.demo.ui.utils.OffersFragment
import com.apphud.sdk.Apphud
import com.apphud.sdk.flutter.ApphudFlutter


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
                product.productDetails?.let { details ->
                    //Use Apphud purchases flow
                    if(details.productType == BillingClient.ProductType.SUBS){
                        product.productDetails?.subscriptionOfferDetails?.let {
                            val fragment = OffersFragment()
                            fragment.offers = it
                            fragment.offerSelected = { offer ->
                                Apphud.purchase(activity, product, offer.offerToken){ result ->
                                    result.error?.let{ err->
                                        Toast.makeText(activity, err.message, Toast.LENGTH_SHORT).show()
                                        Log.d("Apphud", "Fallback DEMO: purchas error ${product.product_id}")
                                    }?: run{
                                        Toast.makeText(activity, R.string.success, Toast.LENGTH_SHORT).show()
                                        Log.d("Apphud", "Fallback DEMO: purchased ${product.product_id}")
                                    }
                                }
                            }
                            fragment.apply {
                                show(activity.supportFragmentManager, tag)
                            }
                        }
                    } else {
                        if(product.product_id == "com.apphud.demo.nonconsumable.premium"){
                            Apphud.purchase(activity = activity, apphudProduct = product, consumableInappProduct = false){ result ->
                                result.error?.let{ err->
                                    Toast.makeText(activity, err.message, Toast.LENGTH_SHORT).show()
                                    Log.d("Apphud", "Fallback DEMO: purchas error ${product.product_id}")
                                }?: run{
                                    Toast.makeText(activity, R.string.success, Toast.LENGTH_SHORT).show()
                                    Log.d("Apphud", "Fallback DEMO: purchased ${product.product_id}")
                                }
                            }
                        } else {
                            Apphud.purchase(activity = activity, apphudProduct = product, consumableInappProduct = true){ result ->
                                result.error?.let{ err->
                                    Toast.makeText(activity, err.message, Toast.LENGTH_SHORT).show()
                                    Log.d("Apphud", "Fallback DEMO: purchas error ${product.product_id}")
                                }?: run{
                                    Toast.makeText(activity, R.string.success, Toast.LENGTH_SHORT).show()
                                    Log.d("Apphud", "Fallback DEMO: purchased ${product.product_id}")
                                }
                            }
                        }
                    }
                }
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
package com.apphud.demo.ui.products

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.billingclient.api.BillingClient
import com.apphud.demo.R
import com.apphud.demo.databinding.FragmentProductsBinding
import com.apphud.demo.ui.utils.getOfferDescription
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
                product.productDetails?.let { details ->
                    //Use Apphud purchases flow
                    if(details.productType == BillingClient.ProductType.SUBS){
                        val offers = details.subscriptionOfferDetails?.map{details.getOfferDescription(it.offerToken)}
                        offers?.let{ offers ->
                            val builder = AlertDialog.Builder(activity)
                            builder.setTitle(R.string.select_offer)
                                .setItems(offers.toTypedArray()) { dialog, which ->
                                    val offer = details.subscriptionOfferDetails?.get(which)
                                    offer?.let{
                                        Apphud.purchase(activity, product, it.offerToken){ result ->
                                            result.error?.let{ err->
                                                Toast.makeText(activity, err.message, Toast.LENGTH_SHORT).show()
                                            }?: run{
                                                Toast.makeText(activity, R.string.success, Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                    dialog.dismiss()
                                }
                            val dlg = builder.create()
                            dlg.show()
                        }
                    }else{
                        Apphud.purchase(activity, product){ result ->
                            result.error?.let{ err->
                                Toast.makeText(activity, err.message, Toast.LENGTH_SHORT).show()
                            }?: run{
                                Toast.makeText(activity, R.string.success, Toast.LENGTH_SHORT).show()
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
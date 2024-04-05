package com.apphud.sampleapp.ui.paywall

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.apphud.sampleapp.MainActivity
import com.apphud.sampleapp.R
import com.apphud.sampleapp.databinding.FragmentPaywallBinding
import com.apphud.sampleapp.ui.onboarding.UnlimitedFragmentDirections
import com.apphud.sampleapp.ui.utils.Placement
import com.apphud.sampleapp.ui.utils.PurchaseManager
import com.apphud.sampleapp.ui.utils.ResourceManager
import com.apphud.sampleapp.ui.views.ProductButton
import com.apphud.sdk.domain.ApphudProduct

class PaywallFragment: Fragment() {

    val args: PaywallFragmentArgs by navArgs()

    private var _binding: FragmentPaywallBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPaywallBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val viewModel = ViewModelProvider(this)[PaywallViewModel::class.java]
        viewModel.productsList.observe(viewLifecycleOwner) { list ->
            list?.let{
                binding.progressBar.visibility = View.GONE
                if(it.isEmpty()){
                    Toast.makeText(context, ResourceManager.getString(R.string.error_default), Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                } else {
                    activity?.let{ a->
                        for(product in list){
                            val button = ProductButton(a)
                            button.setProduct(product)
                            button.setOnClickListener {
                                purchase(product)
                            }
                            binding.productsList.addView(button)
                        }
                    }
                }
            }?: run {
                binding.progressBar.visibility = View.VISIBLE
            }
        }
        viewModel.loadProducts(Placement.getPlacementByName(args.placementId))

        viewModel.screenColor.observe(viewLifecycleOwner) { color ->
            color?.let{
                binding.mainLayout.setBackgroundColor(Color.parseColor(it))
            }
        }

        binding.buttonContinue.setOnClickListener {
            activity?.let { a ->
                when (Placement.getPlacementByName(args.placementId)) {
                    Placement.onboarding -> {
                        val i = Intent(a, MainActivity::class.java)
                        startActivity(i)
                        a.finish()
                    }
                    Placement.main -> {
                        findNavController().popBackStack()
                    }
                    Placement.settings -> {
                        findNavController().popBackStack()
                    }
                }
            }
        }

        return root
    }

    private fun purchase(product: ApphudProduct){
        showProgress(true)
        activity?.let{ a->
            PurchaseManager.purchaseProduct(a, product) { isSuccess, error ->
                showProgress(false)
                if(isSuccess){
                    Toast.makeText(a, ResourceManager.getString(R.string.success), Toast.LENGTH_SHORT).show()
                }
                error?.let{
                    Toast.makeText(a, it.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showProgress(isVisible :Boolean){
        if(isVisible){
            _binding?.progressView?.visibility = View.VISIBLE
        } else {
            _binding?.progressView?.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
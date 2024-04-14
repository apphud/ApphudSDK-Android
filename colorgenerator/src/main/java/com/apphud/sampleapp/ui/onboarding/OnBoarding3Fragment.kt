package com.apphud.sampleapp.ui.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import com.apphud.sampleapp.MainActivity
import com.apphud.sampleapp.databinding.FragmentUnlimitedBinding
import com.apphud.sampleapp.ui.paywall.PaywallActivity
import com.apphud.sampleapp.ui.utils.Placement

class UnlimitedFragment :Fragment() {

    private var _binding: FragmentUnlimitedBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUnlimitedBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val viewModel = ViewModelProvider(this)[UnlimitedViewModel::class.java]
        viewModel.isPremium.observe(viewLifecycleOwner) { isPremium ->
            isPremium?.let{
                binding.progressBar.visibility = View.INVISIBLE
                binding.buttonContinue.visibility = View.VISIBLE
            }?: run {
                binding.progressBar.visibility = View.VISIBLE
                binding.buttonContinue.visibility = View.INVISIBLE
            }
        }

        binding.buttonContinue.setOnClickListener {
            activity?.let{
                if(viewModel.isPremium.value == true){
                    val i = Intent(it, MainActivity::class.java)
                    startActivity(i)
                    it.finish()
                } else {
                    val i = Intent(it, PaywallActivity::class.java)
                    i.putExtra("placement_id", Placement.onboarding.placementId)
                    startActivity(i)
                    it.finish()
                }
            }
        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
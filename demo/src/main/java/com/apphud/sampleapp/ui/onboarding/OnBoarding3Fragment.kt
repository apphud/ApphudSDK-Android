package com.apphud.sampleapp.ui.onboarding

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.apphud.sampleapp.ui.main.MainActivity
import com.apphud.sampleapp.databinding.FragmentOnboarding3Binding
import com.apphud.sampleapp.ui.paywall.PaywallActivity
import com.apphud.sampleapp.ui.utils.Placement

class OnBoarding3Fragment :Fragment() {

    private var _binding: FragmentOnboarding3Binding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboarding3Binding.inflate(inflater, container, false)
        val root: View = binding.root

        (activity as OnboardingActivity).viewModel.onBoarding?.let{ i ->
            val screenInfo = i.onboarding.firstOrNull{it.id == "onboarding_screen_3"}
            screenInfo?.let {
                binding.colorLayout.setBackgroundColor(Color.parseColor(it.color))
                binding.buttonContinue.text = it.buttonTitle
            }
        }

        binding.buttonContinue.setOnClickListener {
            activity?.let{
                if((activity as OnboardingActivity).viewModel.isPremium()== true){
                    val i = Intent(it, MainActivity::class.java)
                    startActivity(i)
                    it.finish()
                } else {
                    val i = Intent(it, PaywallActivity::class.java)
                    i.putExtra("placement_id", Placement.onboarding.placementId)
                    startActivity(i)
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
package com.apphud.sampleapp.ui.onboarding

import android.graphics.Color
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.apphud.sampleapp.OnboardingActivity
import com.apphud.sampleapp.databinding.FragmentOnboarding2Binding

class OnBoarding2Fragment :Fragment() {

    private var _binding: FragmentOnboarding2Binding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboarding2Binding.inflate(inflater, container, false)
        val root: View = binding.root

        (activity as OnboardingActivity).viewModel.onBoarding?.let{ i ->
            val screenInfo = i.onboarding.firstOrNull{it.id == "onboarding_screen_2"}
            screenInfo?.let {
                binding.colorLayout.setBackgroundColor(Color.parseColor(it.color))
                binding.buttonContinue.text = it.buttonTitle
                binding.buttonContinue.setOnClickListener {
                    findNavController().navigate(OnBoarding2FragmentDirections.actionIntroFragmentToUnlimitedFragment())
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
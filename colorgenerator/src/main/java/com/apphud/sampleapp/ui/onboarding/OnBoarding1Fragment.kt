package com.apphud.sampleapp.ui.onboarding

import android.graphics.Color
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.apphud.sampleapp.OnboardingActivity
import com.apphud.sampleapp.databinding.FragmentOnboarding1Binding
import com.apphud.sampleapp.ui.utils.Placement
import com.apphud.sampleapp.ui.utils.PurchaseManager

class OnBoarding1Fragment :Fragment() {

    private var _binding: FragmentOnboarding1Binding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboarding1Binding.inflate(inflater, container, false)
        val root: View = binding.root

        return root
    }

    override fun onStart() {
        super.onStart()

        (activity as OnboardingActivity).viewModel.isReady.observe(viewLifecycleOwner) { isReady ->
            isReady?.let{
                binding.progressBar.visibility = View.INVISIBLE
                binding.buttonContinue.visibility = View.VISIBLE

                (activity as OnboardingActivity).viewModel.getOnboardingInfo(Placement.onboarding){ info ->
                    info?.let{ i->
                        val screenInfo = i.onboarding.firstOrNull{it.id == "onboarding_screen_1"}
                        screenInfo?.let{
                            binding.colorLayout.setBackgroundColor(Color.parseColor(it.color))
                            binding.buttonContinue.text = it.buttonTitle

                            binding.buttonContinue.setOnClickListener {
                                findNavController().navigate(OnBoarding1FragmentDirections.actionOnboardingFragmentToIntroFragment())
                            }
                        }
                    }
                }
            }?: run {
                binding.progressBar.visibility = View.VISIBLE
                binding.buttonContinue.visibility = View.INVISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
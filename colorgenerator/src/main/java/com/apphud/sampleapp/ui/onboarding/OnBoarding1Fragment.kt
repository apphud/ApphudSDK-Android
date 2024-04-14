package com.apphud.sampleapp.ui.onboarding

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.apphud.sampleapp.databinding.FragmentWelcomeBinding

class WelcomeFragment :Fragment() {

    private var _binding: FragmentWelcomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWelcomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        binding.buttonContinue.setOnClickListener {
            findNavController().navigate(WelcomeFragmentDirections.actionOnboardingFragmentToIntroFragment())
        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
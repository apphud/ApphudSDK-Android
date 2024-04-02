package com.apphud.sampleapp.ui.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.apphud.sampleapp.MainActivity
import com.apphud.sampleapp.databinding.FragmentUnlimitedBinding

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

        binding.buttonContinue.setOnClickListener {
            activity?.let{
                val i = Intent(it, MainActivity::class.java)
                startActivity(i)
                it.finish()
            }
        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
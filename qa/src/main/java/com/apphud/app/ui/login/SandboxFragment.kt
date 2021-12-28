package com.apphud.app.ui.login

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.apphud.app.*
import com.apphud.app.databinding.FragmentSandboxBinding
import com.apphud.app.ui.storage.StorageManager
import com.apphud.app.ui.utils.Constants

class SandboxFragment : Fragment() {
    private var _binding: FragmentSandboxBinding? = null
    private val binding get() = _binding!!
    private val storage by lazy { StorageManager(ApphudApplication.applicationContext()) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentSandboxBinding.inflate(inflater, container, false)
        val view = binding.root

        binding.btnDev.setOnClickListener {
            storage.apiKey = Constants.API_KEY_DEV
            storage.host = Constants.HOST_DEV
            storage.sandbox = getString(R.string.development)
            findNavController().navigate(SandboxFragmentDirections.actionSandboxFragmentToUsernameFragment())
        }

        binding.btnStage.setOnClickListener {
            storage.apiKey = Constants.API_KEY_STAGE
            storage.host = Constants.HOST_STAGE
            storage.sandbox = getString(R.string.stage)
            findNavController().navigate(SandboxFragmentDirections.actionSandboxFragmentToUsernameFragment())
        }

        binding.btnProd.setOnClickListener {
            storage.apiKey = Constants.API_KEY_PROD
            storage.host = Constants.HOST_PROD
            storage.sandbox = getString(R.string.production)
            findNavController().navigate(SandboxFragmentDirections.actionSandboxFragmentToUsernameFragment())
        }

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
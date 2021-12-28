package com.apphud.app.ui.login

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.apphud.app.ApphudApplication
import com.apphud.app.databinding.FragmentUsernameBinding
import com.apphud.app.BaseActivity
import com.apphud.app.ui.storage.StorageManager

class UsernameFragment : Fragment() {

    private var _binding: FragmentUsernameBinding? = null
    private val binding get() = _binding!!
    private val storage by lazy { StorageManager(ApphudApplication.applicationContext()) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentUsernameBinding.inflate(inflater, container, false)
        val view = binding.root

        //Init default integration set
        storage.integrations = storage.integrations

        binding.btnNext.setOnClickListener {
            storage.userId = binding.etUserId.text.toString()
            storage.username = binding.etUserId.text.toString()
            (activity as BaseActivity).startMainActivity()
        }

        binding.btnClear.setOnClickListener {
            binding.etUserId.setText("")
        }

        binding.etUserId.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun afterTextChanged(editable: Editable?) {
                if(editable.toString().isEmpty()){
                    binding.btnClear.visibility = View.GONE
                }else{
                    binding.btnClear.visibility = View.VISIBLE
                }
            }
        })
        binding.etUserId.setText(storage.username?:"")

        binding.btnIntegrations.setOnClickListener {
            findNavController().navigate(UsernameFragmentDirections.actionUsernameFragmentToIntegrationsFragment())
        }

        return view
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
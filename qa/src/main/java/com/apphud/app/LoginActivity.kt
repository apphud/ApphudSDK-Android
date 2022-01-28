package com.apphud.app

import android.os.Bundle
import com.apphud.app.databinding.ActivityLoginBinding
import com.apphud.app.ui.storage.StorageManager

class LoginActivity : BaseActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val storage by lazy { StorageManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        storage.userId?.let{
            startMainActivity()
            finish()
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}
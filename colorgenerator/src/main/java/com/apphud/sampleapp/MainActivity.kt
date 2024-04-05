package com.apphud.sampleapp

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.apphud.sampleapp.databinding.ActivityMainBinding

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        navController = findNavController(R.id.nav_host_fragment_activity_main)
        val appBarConfiguration = AppBarConfiguration(setOf(R.id.navigation_generator, R.id.navigation_settings))

        setSupportActionBar(binding.toolbar)
        supportActionBar?.hide()

        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.navView.setupWithNavController(navController)

        binding.navView.setOnItemSelectedListener { menuItem: MenuItem ->
            return@setOnItemSelectedListener when (menuItem.itemId) {
                R.id.navigation_generator -> {
                    if(navController.currentDestination?.id != R.id.navigation_generator) {
                        navController.navigate(R.id.navigation_generator)
                    }
                    true
                }
                R.id.navigation_settings -> {
                    if(navController.currentDestination?.id != R.id.navigation_settings) {
                        navController.navigate(R.id.navigation_settings)
                    }
                    true
                }
                else -> true
            }
        }

        navController.addOnDestinationChangedListener { _, nd: NavDestination, _ ->
            when(nd.id){
                R.id.paywallFragment2 -> {
                    binding.navView.clearAnimation()
                    binding.navView.animate().translationY(binding.navView.height.toFloat()).setDuration(300).setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            binding.navView.visibility = View.GONE
                        }
                    })
                } else -> {
                binding.navView.clearAnimation()
                binding.navView.animate().translationY(0F).setDuration(300).setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        binding.navView.visibility = View.VISIBLE
                    }
                })
            }
            }
        }
    }
}
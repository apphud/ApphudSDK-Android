package com.apphud.app

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.core.view.GravityCompat
import com.android.billingclient.api.SkuDetails
import com.apphud.app.databinding.ActivityMainBinding
import com.apphud.sdk.Apphud
import com.apphud.sdk.ApphudListener
import com.apphud.sdk.domain.ApphudNonRenewingPurchase
import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.ApphudSubscription

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var me: MainActivity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        me = this

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_main)

        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.nav_customer,
                  R.id.nav_groups,
                  R.id.nav_purchases
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)

        navView.setNavigationItemSelectedListener {
            navController.navigate(it.itemId)
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private var backPress: Long = 0
    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            if(!findNavController(R.id.nav_host_fragment_content_main).navigateUp()){
                if (backPress + 2000 > System.currentTimeMillis()) {
                    super.onBackPressed()
                } else {
                    Toast.makeText(
                        baseContext, "Please press again to exit!",
                        Toast.LENGTH_SHORT
                    ).show()
                    backPress = System.currentTimeMillis()
                }
            }
        }
    }
}
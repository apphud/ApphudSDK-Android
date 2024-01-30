package com.apphud.demo.mi

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.apphud.demo.mi.databinding.ActivityMainBinding
import com.apphud.sdk.Apphud
import com.apphud.sdk.ApphudUtils


class MainActivity : AppCompatActivity() {
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var me: MainActivity

    var API_KEY = "app_oBcXz2z9j8spKPL2T7sZwQaQN5Jzme"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        me = this

        Apphud.enableDebugLogs()
        // Apphud.optOutOfTracking()
        if (BuildConfig.DEBUG) {
            ApphudUtils.enableAllLogs()
        }
        Apphud.start(this, API_KEY)
        Apphud.collectDeviceIdentifiers()


        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar?.let {
            setSupportActionBar(it)
        }

        val navController = findNavController(R.id.nav_host_fragment_content_main)

        appBarConfiguration =
            AppBarConfiguration(
                setOf(
                    R.id.nav_customer,
                    R.id.nav_billing,
                    R.id.nav_billing_purchases,
                    R.id.nav_groups,
                    R.id.nav_purchases,
                ),
                binding.drawerLayout,
            )
        setupActionBarWithNavController(navController, appBarConfiguration)

        binding.navView.setNavigationItemSelectedListener {
            navController.navigate(it.itemId)
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        navController.addOnDestinationChangedListener { controller, destination, arguments ->
            for (i in 0 until binding.navView.menu.size()) {
                binding.navView.menu.getItem(i).setChecked(false)
            }
            binding.navView.setCheckedItem(destination.id)
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
            if (!findNavController(R.id.nav_host_fragment_content_main).navigateUp()) {
                if (backPress + 2000 > System.currentTimeMillis()) {
                    super.onBackPressed()
                } else {
                    Toast.makeText(
                        baseContext,
                        "Please press again to exit!",
                        Toast.LENGTH_SHORT,
                    ).show()
                    backPress = System.currentTimeMillis()
                }
            }
        }
    }
}

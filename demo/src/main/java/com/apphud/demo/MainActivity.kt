package com.apphud.demo

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.android.billingclient.api.*
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.apphud.demo.databinding.ActivityMainBinding
import com.apphud.sdk.Apphud
import com.google.android.material.navigation.NavigationView


class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var me: MainActivity

    var billingClient: BillingClient? = null
    var skuDetails: SkuDetails? = null
    var paywallIdentifier: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        me = this

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar?.let{
            setSupportActionBar(it)
        }

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
        billingClient = BillingClient.newBuilder(this)
            .setListener { billingResult, list ->
                if(billingResult.responseCode == BillingResponseCode.OK){
                    /*list?.let{ l ->
                        Log.d("Apphud", "Just purchasesd: $list")
                        for (purchase in l){
                            purchase?.let{ p ->
                                skuDetails?.let{ details ->
                                    Apphud.trackPurchase(p, details, paywallIdentifier)
                                }
                            }
                        }
                    }*/
                    list?.let { l ->
                        for (purchase in l) {
                            purchase?.let{ p ->
                                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                                    .setPurchaseToken(p.purchaseToken)
                                    .build()
                                billingClient?.acknowledgePurchase(acknowledgePurchaseParams) {
                                    skuDetails?.let{ details ->
                                        Apphud.trackPurchase(p, details, paywallIdentifier)
                                    }
                                }
                            }
                        }
                    }
                }
            }.enablePendingPurchases().build()

        billingClient?.startConnection(object: BillingClientStateListener{
            override fun onBillingServiceDisconnected() {
            }

            override fun onBillingSetupFinished(p0: BillingResult) {
            }
        })
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

package com.apphud.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.core.view.GravityCompat
import com.apphud.app.databinding.ActivityMainBinding
import com.apphud.app.ui.managers.AnalyticsManager
import com.apphud.app.ui.storage.StorageManager
import com.apphud.sdk.Apphud
import com.apphud.sdk.managers.HeadersInterceptor
import com.apphud.sdk.managers.RequestManager

class MainActivity : BaseActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private val storage by lazy { StorageManager(this) }
    private lateinit var me: MainActivity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        me = this

        Apphud.enableDebugLogs()

        storage.host?.let{
            HeadersInterceptor.HOST = it
        }?:run{
            storage.clean()
            startLoginActivity()
        }

        storage.apiKey?.let{
            Apphud.start(ApphudApplication.applicationContext(), it, storage.userId)
        }?:run{
            storage.clean()
            startLoginActivity()
        }

        if(storage.userId.isNullOrEmpty()){
            storage.userId = Apphud.userId()
        }


        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_main)

        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.nav_customer,
                  R.id.nav_demo,
                  R.id.nav_purchases,
                  R.id.nav_paywalls,
                  R.id.nav_groups,
                  R.id.nav_promotional
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        //navView.setupWithNavController(navController)

        navView.setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.nav_logout -> {
                    Apphud.logout()
                    storage.clean()
                    startLoginActivity()
                }
                else -> navController.navigate(it.itemId)
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        AnalyticsManager.initAnalytics(application = ApphudApplication.application())
    }

    fun startLoginActivity() {
        val intent = Intent(ApphudApplication.applicationContext(), LoginActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent)
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
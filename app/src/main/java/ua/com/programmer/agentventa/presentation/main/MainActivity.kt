package ua.com.programmer.agentventa.presentation.main

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ua.com.programmer.agentventa.BuildConfig
import ua.com.programmer.agentventa.R
import ua.com.programmer.agentventa.data.local.entity.getGuid
import ua.com.programmer.agentventa.databinding.ActivityMainBinding
import ua.com.programmer.agentventa.infrastructure.location.LocationUpdatesService
import ua.com.programmer.agentventa.presentation.features.settings.UserOptions
import ua.com.programmer.agentventa.presentation.common.viewmodel.SharedViewModel
import ua.com.programmer.agentventa.presentation.common.viewmodel.WebSocketSnackbarEvent

private lateinit var drawerLayout: DrawerLayout

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startLocationUpdatesService()
        }
    }

    private val sharedViewModel: SharedViewModel by viewModels()
    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!

    private var backPressedTime: Long = 0

    private var currentFragment: Fragment? = null
    private var barcode = StringBuilder()
    private var lastKeystrokeTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        binding.lifecycleOwner = this
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        sharedViewModel.setCacheDir(this.cacheDir)

        drawerLayout = binding.navigationDrawerLayout

        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.orderListFragment,
                R.id.cashListFragment,
                R.id.taskListFragment
            ), drawerLayout)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_container) as NavHostFragment
        val navController = navHostFragment.navController

        NavigationUI.setupWithNavController(binding.toolbar, navController, appBarConfiguration)
        NavigationUI.setupWithNavController(binding.navigationView, navController)

        onBackPressedDispatcher.addCallback(this) {
            if (navController.previousBackStackEntry == null) {
                if (backPressedTime + 2000 > System.currentTimeMillis()) {
                    finish()
                }else {
                    Toast.makeText(this@MainActivity, R.string.hint_press_back, Toast.LENGTH_SHORT)
                        .show()
                    backPressedTime = System.currentTimeMillis()
                }
            } else {
                navController.popBackStack()
            }
        }

        navHostFragment.childFragmentManager.addFragmentOnAttachListener { _, fragment ->
            currentFragment = fragment
        }

        sharedViewModel.currentAccount.observe(this) { account ->
            account?.let {
                val textUserID = "${BuildConfig.VERSION_NAME} (${account.getGuid()})"
                val header = binding.navigationView.getHeaderView(0)
                val textVersion = header.findViewById<TextView>(R.id.text_agent_id)
                textVersion.text = textUserID
                textVersion.setOnLongClickListener {
                    copyToClipboard(account.getGuid())
                    true
                }
                val textAccount = header.findViewById<TextView>(R.id.connection_name)
                textAccount.text = account.description
            }
            updateViewWithOptions(sharedViewModel.options)
        }

        // Observe snackbar events for WebSocket notifications
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.snackbarEvents.collect { event ->
                    showSnackbar(event)
                }
            }
        }

    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = this.findNavController(R.id.nav_host_container)
        return NavigationUI.navigateUp(navController, drawerLayout)
    }

    private fun updateViewWithOptions(options: UserOptions) {
        val menu = binding.navigationView.menu
        menu.findItem(R.id.locationHistoryFragment).isVisible = options.locations
        menu.findItem(R.id.clientsMapFragment).isVisible = options.clientsLocations
        menu.findItem(R.id.fiscalFragment).isVisible = options.fiscalProvider.isNotBlank()

        if (options.locations) {
            if (!checkPermissions()) {
                requestPermissions()
            } else {
                startLocationUpdatesService()
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(getString(R.string.pref_title_user_id), text)
        clipboard.setPrimaryClip(clip)

        Toast.makeText(this, getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
    }

    private fun showSnackbar(event: WebSocketSnackbarEvent) {
        val (message, duration) = when (event) {
            is WebSocketSnackbarEvent.ConnectionError ->
                getString(R.string.snackbar_connection_error, event.error) to Snackbar.LENGTH_LONG

            is WebSocketSnackbarEvent.LicenseError ->
                getString(R.string.snackbar_license_error, event.reason) to Snackbar.LENGTH_LONG

            is WebSocketSnackbarEvent.PendingApproval ->
                getString(R.string.snackbar_pending_approval) to Snackbar.LENGTH_LONG

            is WebSocketSnackbarEvent.DataSent -> {
                val msg = when (event.type) {
                    "order" -> getString(R.string.snackbar_orders_sent, event.count)
                    "cash" -> getString(R.string.snackbar_cash_sent, event.count)
                    "image" -> getString(R.string.snackbar_images_sent, event.count)
                    "location" -> getString(R.string.snackbar_locations_sent, event.count)
                    else -> "${event.type}: ${event.count}"
                }
                msg to Snackbar.LENGTH_SHORT
            }

            is WebSocketSnackbarEvent.CatalogReceived ->
                getString(R.string.snackbar_catalog_received, event.count) to Snackbar.LENGTH_SHORT

            is WebSocketSnackbarEvent.SyncError ->
                getString(R.string.snackbar_sync_error, event.message) to Snackbar.LENGTH_LONG
        }

        Snackbar.make(binding.navigationDrawerLayout, message, duration).show()
    }

    private fun checkPermissions(): Boolean {
        val permissionState = ActivityCompat.checkSelfPermission(this,
            Manifest.permission.ACCESS_FINE_LOCATION)
        return permissionState == PackageManager.PERMISSION_GRANTED
    }

    private fun startLocationUpdatesService() {
        startService(Intent(this, LocationUpdatesService::class.java))
    }

    private fun requestPermissions() {
        val shouldProvideRationale = ActivityCompat.shouldShowRequestPermissionRationale(this,
            Manifest.permission.ACCESS_FINE_LOCATION)

        if (shouldProvideRationale) {
            showLocationPermissionRationale()
        } else {
            makePermissionRequest()
        }
    }

    private fun showLocationPermissionRationale() {
        AlertDialog.Builder(this)
            .setTitle(R.string.warning)
            .setMessage(R.string.location_permission_rationale)
            .setPositiveButton(R.string.OK) { _, _ ->
                //Prompt the user once explanation has been shown
                makePermissionRequest()
            }
            .create()
            .show()
    }

    private fun makePermissionRequest() {
        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        event.let {
            if (it.action == KeyEvent.ACTION_DOWN) {
                val currentTime = System.currentTimeMillis()

                if (barcode.isNotEmpty() && currentTime - lastKeystrokeTime > 60) {
                    barcode.clear()
                }
                when (it.keyCode) {
                    KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_TAB -> {
                        sharedViewModel.onBarcodeRead(barcode.toString())
                        barcode.clear()
                        return true
                    }
                    else -> {
                        val char = it.unicodeChar.toChar()
                        if (Character.isDigit(char) || Character.isLetter(char)) {
                            barcode.append(char)
                        }
                    }
                }

                lastKeystrokeTime = currentTime
            }
        }
        return super.dispatchKeyEvent(event)
    }

}
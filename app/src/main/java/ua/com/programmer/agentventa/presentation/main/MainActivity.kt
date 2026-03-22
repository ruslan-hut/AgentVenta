package ua.com.programmer.agentventa.presentation.main

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
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
import android.view.View
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ua.com.programmer.agentventa.BuildConfig
import ua.com.programmer.agentventa.R
import ua.com.programmer.agentventa.data.local.entity.getGuid
import ua.com.programmer.agentventa.databinding.ActivityMainBinding
import ua.com.programmer.agentventa.infrastructure.location.LocationUpdatesService
import ua.com.programmer.agentventa.presentation.features.settings.ScannerDiagnostics
import ua.com.programmer.agentventa.presentation.features.settings.ScannerSettings
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

    private lateinit var scannerSettings: ScannerSettings
    lateinit var diagnostics: ScannerDiagnostics
        private set
    private var keyEventListener: ((KeyEvent) -> Boolean)? = null

    private var backPressedTime: Long = 0

    private var currentFragment: Fragment? = null
    private var barcode = StringBuilder()
    private var lastKeystrokeTime = 0L
    private var barcodeConsumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        super.onCreate(savedInstanceState)

        scannerSettings = ScannerSettings(this)
        diagnostics = ScannerDiagnostics(this)

        _binding = ActivityMainBinding.inflate(layoutInflater)
        binding.lifecycleOwner = this
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        sharedViewModel.setCacheDir(this.cacheDir)

        drawerLayout = binding.navigationDrawerLayout
        drawerLayout.setStatusBarBackgroundColor(
            com.google.android.material.color.MaterialColors.getColor(
                drawerLayout, com.google.android.material.R.attr.colorSurface
            )
        )

        val topLevelDestinations = setOf(
            R.id.orderListFragment,
            R.id.cashListFragment,
            R.id.taskListFragment,
            R.id.clientListFragment,
            R.id.productListFragment,
            R.id.syncFragment
        )

        val appBarConfiguration = AppBarConfiguration(topLevelDestinations, drawerLayout)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_container) as NavHostFragment
        val navController = navHostFragment.navController

        NavigationUI.setupWithNavController(binding.toolbar, navController, appBarConfiguration)
        NavigationUI.setupWithNavController(binding.navigationView, navController)
        NavigationUI.setupWithNavController(binding.bottomNavigation, navController)

        // Handle select mode and group navigation: hide bottom nav, lock drawer, show back arrow
        navController.addOnDestinationChangedListener { _, destination, arguments ->
            val isSelectMode = arguments?.getBoolean("modeSelect", false) == true
            val isInGroup = arguments?.getString("groupGuid")?.isNotEmpty() == true
            val isTopLevel = destination.id in topLevelDestinations
            val showAsNested = isSelectMode || isInGroup

            binding.bottomNavigation.visibility =
                if (isTopLevel && !showAsNested) View.VISIBLE else View.GONE

            if (isTopLevel && showAsNested) {
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                binding.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
                binding.toolbar.setNavigationOnClickListener { navController.popBackStack() }
            } else {
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                binding.toolbar.setNavigationOnClickListener {
                    NavigationUI.navigateUp(navController, appBarConfiguration)
                }
            }
        }

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
                diagnostics.deviceId = account.getGuid()
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
            is WebSocketSnackbarEvent.ConnectionError -> return // Connection errors are not shown

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
        try {
            startService(Intent(this, LocationUpdatesService::class.java))
        } catch (e: IllegalStateException) {
            // Android 12+: not allowed to start service from background
            // Will be retried when the activity is fully in foreground
        }
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

    /**
     * Allows a fragment (e.g. ScannerTestFragment) to intercept all key events
     * before the normal barcode pipeline runs.
     */
    fun setKeyEventListener(listener: ((KeyEvent) -> Boolean)?) {
        keyEventListener = listener
    }

    /**
     * Intercepts key events to capture input from a barcode scanner.
     *
     * Barcode scanners connected as external keyboards send a rapid burst of
     * keystrokes followed by an ENTER or TAB terminator. This method buffers
     * those keystrokes using a configurable timeout to separate consecutive scans.
     *
     * When a terminator arrives and the buffer contains at least minBarcodeLength
     * characters, the assembled string is forwarded to SharedViewModel.onBarcodeRead
     * and both ACTION_DOWN and ACTION_UP for the terminator are consumed so they
     * never reach the focused view.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // When a test listener is active, forward all events to it instead
        keyEventListener?.let { listener ->
            if (listener(event)) return true
        }

        diagnostics.recordKeyEvent(event)

        if (event.action == KeyEvent.ACTION_DOWN) {
            val isTerminator = scannerSettings.isTerminator(event.keyCode)

            if (isTerminator) {
                if (barcode.length >= scannerSettings.minBarcodeLength) {
                    val raw = barcode.toString()
                    val cleaned = scannerSettings.cleanBarcode(raw)
                    Log.d("AV_MainActivity", "barcode: $cleaned")
                    diagnostics.recordDetection(raw, cleaned, success = true)
                    sharedViewModel.onBarcodeRead(cleaned)
                    barcode.clear()
                    barcodeConsumed = true
                    return true
                }
                // Buffer had some chars but too few — still consume the
                // terminator to prevent TAB from switching tabs.
                if (barcode.isNotEmpty()) {
                    Log.d("AV_MainActivity", "barcode too short: $barcode (${barcode.length} < ${scannerSettings.minBarcodeLength})")
                    diagnostics.recordDetection(barcode.toString(), "", success = false)
                    barcode.clear()
                    barcodeConsumed = false
                    return true
                }
                // Buffer empty — normal keyboard TAB/ENTER, let it through
                diagnostics.recordNote("terminator pass-through (empty buffer)")
                barcode.clear()
                barcodeConsumed = false
                return super.dispatchKeyEvent(event)
            }

            val currentTime = System.currentTimeMillis()
            if (barcode.isNotEmpty() && currentTime - lastKeystrokeTime > scannerSettings.keystrokeTimeout) {
                diagnostics.recordBuffer("timeout_clear", barcode.toString(), barcode.length)
                barcode.clear()
            }

            val char = event.unicodeChar.toChar()
            if (char.code in 0x20..0x7E) {
                barcode.append(char)
                lastKeystrokeTime = currentTime
                diagnostics.recordBuffer("append", barcode.toString(), barcode.length)
            }
        }

        if (event.action == KeyEvent.ACTION_UP) {
            if (scannerSettings.isTerminator(event.keyCode)) {
                if (barcodeConsumed) {
                    barcodeConsumed = false
                    return true
                }
            }
        }

        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        diagnostics.stop()
        diagnostics.clear()
        super.onDestroy()
    }

}
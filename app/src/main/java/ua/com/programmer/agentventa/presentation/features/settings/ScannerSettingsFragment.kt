package ua.com.programmer.agentventa.presentation.features.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import ua.com.programmer.agentventa.R
import ua.com.programmer.agentventa.databinding.FragmentScannerSettingsBinding
import ua.com.programmer.agentventa.presentation.main.MainActivity

class ScannerSettingsFragment : Fragment(), MenuProvider {

    private var _binding: FragmentScannerSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var scannerSettings: ScannerSettings
    private var diagnostics: ScannerDiagnostics? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScannerSettingsBinding.inflate(inflater)
        scannerSettings = ScannerSettings(requireContext())
        diagnostics = (activity as? MainActivity)?.diagnostics

        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        loadSettings()
        setupDiagnostics()

        binding.testScanner.setOnClickListener {
            val navController = findNavController()
            if (navController.currentDestination?.id != R.id.scannerSettingsFragment) return@setOnClickListener
            navController.navigate(R.id.action_scannerSettingsFragment_to_scannerTestFragment)
        }

        return binding.root
    }

    private fun loadSettings() {
        binding.apply {
            keystrokeTimeout.setText(scannerSettings.keystrokeTimeout.toString())
            minBarcodeLength.setText(scannerSettings.minBarcodeLength.toString())
            prefixToStrip.setText(scannerSettings.prefixToStrip)
            suffixToStrip.setText(scannerSettings.suffixToStrip)

            when (scannerSettings.terminatorKey) {
                ScannerSettings.TERMINATOR_ENTER -> terminatorEnter.isChecked = true
                ScannerSettings.TERMINATOR_TAB -> terminatorTab.isChecked = true
                else -> terminatorBoth.isChecked = true
            }
        }
    }

    private fun setupDiagnostics() {
        val diag = diagnostics ?: return

        binding.diagToggle.isChecked = diag.enabled
        updateDiagStatus()

        binding.diagToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                diag.start()
            } else {
                diag.stop()
            }
            updateDiagStatus()
        }

        binding.diagCopy.setOnClickListener {
            if (diag.eventCount == 0) {
                Toast.makeText(activity, getString(R.string.diag_no_data), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val text = diag.exportAsText()
            val clipboard = activity?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Scanner Diagnostics", text))
            Toast.makeText(activity, getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateDiagStatus() {
        val diag = diagnostics ?: return
        binding.diagStatus.text = if (diag.enabled) {
            getString(R.string.diag_status_recording, diag.eventCount)
        } else if (diag.eventCount > 0) {
            getString(R.string.diag_status_stopped, diag.eventCount)
        } else {
            getString(R.string.diag_status_idle)
        }
    }

    private fun saveSettings() {
        val timeoutText = binding.keystrokeTimeout.text.toString()
        val timeout = timeoutText.toLongOrNull() ?: ScannerSettings.DEFAULT_TIMEOUT
        scannerSettings.keystrokeTimeout = timeout

        val minLengthText = binding.minBarcodeLength.text.toString()
        val minLength = minLengthText.toIntOrNull() ?: ScannerSettings.DEFAULT_MIN_LENGTH
        scannerSettings.minBarcodeLength = minLength

        scannerSettings.terminatorKey = when {
            binding.terminatorEnter.isChecked -> ScannerSettings.TERMINATOR_ENTER
            binding.terminatorTab.isChecked -> ScannerSettings.TERMINATOR_TAB
            else -> ScannerSettings.TERMINATOR_BOTH
        }

        scannerSettings.prefixToStrip = binding.prefixToStrip.text.toString()
        scannerSettings.suffixToStrip = binding.suffixToStrip.text.toString()

        Toast.makeText(activity, getString(R.string.toast_saved), Toast.LENGTH_SHORT).show()
        findNavController().popBackStack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_scanner_settings, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.save -> saveSettings()
            else -> return false
        }
        return true
    }
}

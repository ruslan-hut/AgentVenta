package ua.com.programmer.agentventa.infrastructure.printer

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ua.com.programmer.agentventa.R
import ua.com.programmer.agentventa.databinding.PrinterSettingsFragmentBinding
import ua.com.programmer.agentventa.extensions.asNumber
import javax.inject.Inject

@AndroidEntryPoint
class PrinterSettingsFragment: Fragment() {

    private val viewModel: PrinterViewModel by activityViewModels()
    private var _binding: PrinterSettingsFragmentBinding? = null
    private val binding get() = _binding

    @Inject
    lateinit var webhookPrintService: WebhookPrintService

    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = PrinterSettingsFragmentBinding.inflate(inflater,container,false)
        binding?.lifecycleOwner = viewLifecycleOwner
        binding?.viewModel = viewModel
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding?.let {
            it.bluetoothPrintEnabled.isChecked = viewModel.isBluetoothPrintEnabled()
            it.bluetoothPrintEnabled.setOnClickListener { view ->
                viewModel.saveBluetoothPrintEnabled((view as android.widget.CompoundButton).isChecked) }
            it.useInFiscalService.isChecked = viewModel.useInFiscalService()
            it.useInFiscalService.setOnClickListener { view ->
                viewModel.saveUseInFiscalService((view as android.widget.CompoundButton).isChecked) }
            it.autoPrintSavedOrders.isChecked = viewModel.autoPrint()
            it.autoPrintSavedOrders.setOnClickListener { view ->
                viewModel.saveAutoPrint((view as android.widget.CompoundButton).isChecked) }
            it.printAreaWidth.setText(viewModel.readPrintAreaWidth().toString())
            it.printAreaWidth.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val width = it.printAreaWidth.text.toString().asNumber()
                    viewModel.savePrintAreaWidth(width)
                }
            }
        }

        viewModel.devices.observe(viewLifecycleOwner) {
            setupDeviceDropdown(it)
        }

        viewModel.currentDeviceIndex.observe(viewLifecycleOwner) { index ->
            val adapter = binding?.deviceDropdown?.adapter
            if (adapter != null && index < adapter.count) {
                binding?.deviceDropdown?.setText(adapter.getItem(index).toString(), false)
            }
        }

        viewModel.progress.observe(viewLifecycleOwner) { isProgress ->
            binding?.let {
                it.progressBar.visibility = if (isProgress) View.VISIBLE else View.INVISIBLE
                it.testButton.isEnabled = !isProgress
                it.deviceDropdown.isEnabled = !isProgress
            }
        }

        viewModel.permissionGranted.observe(viewLifecycleOwner) { granted ->
            binding?.let {
                if (granted) {
                    it.permissionMessage.visibility = View.GONE
                    it.testButton.isEnabled = true
                    it.testButton.setOnClickListener {
                        viewModel.printTest()
                    }
                } else {
                    it.testButton.isEnabled = false
                    it.requestPermissionButton.setOnClickListener {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            requestNeededPermissions()
                        } else {
                            requestNeededPermissionsOld()
                        }
                    }
                    it.permissionMessage.visibility = View.VISIBLE
                }
            }
        }

        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions.entries.all { it.value }
            viewModel.onCheckPermission(granted)
        }

        checkPermissionGranted()

        // Setup webhook settings
        setupWebhookSettings()
    }

    private fun setupWebhookSettings() {
        binding?.let { b ->
            // Initialize webhook enabled checkbox
            b.webhookEnabled.isChecked = webhookPrintService.isEnabled()
            b.webhookEnabled.setOnCheckedChangeListener { _, isChecked ->
                webhookPrintService.setEnabled(isChecked)
            }

            // Initialize URL field
            b.webhookUrl.setText(webhookPrintService.getUrl())
            b.webhookUrl.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    webhookPrintService.setUrl(b.webhookUrl.text.toString())
                }
            }

            // Initialize HTTP method dropdown
            val methods = resources.getStringArray(R.array.webhook_method_entries)
            val methodAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, methods)
            b.webhookMethodDropdown.setAdapter(methodAdapter)

            val currentMethod = webhookPrintService.getMethod()
            val methodIndex = methods.indexOf(currentMethod)
            if (methodIndex >= 0) {
                b.webhookMethodDropdown.setText(methods[methodIndex], false)
            }

            b.webhookMethodDropdown.setOnItemClickListener { _, _, position, _ ->
                webhookPrintService.setMethod(methods[position])
            }

            // Initialize auth checkbox
            b.webhookUseAuth.isChecked = webhookPrintService.isAuthEnabled()
            updateAuthVisibility(b.webhookUseAuth.isChecked)
            b.webhookUseAuth.setOnCheckedChangeListener { _, isChecked ->
                webhookPrintService.setAuthEnabled(isChecked)
                updateAuthVisibility(isChecked)
            }

            // Initialize username field
            b.webhookUsername.setText(webhookPrintService.getUsername())
            b.webhookUsername.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    webhookPrintService.setUsername(b.webhookUsername.text.toString())
                }
            }

            // Initialize password field
            b.webhookPassword.setText(webhookPrintService.getPassword())
            b.webhookPassword.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    webhookPrintService.setPassword(b.webhookPassword.text.toString())
                }
            }

            // Test button
            b.webhookTestButton.setOnClickListener {
                testWebhook()
            }
        }
    }

    private fun updateAuthVisibility(visible: Boolean) {
        binding?.webhookAuthContainer?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun testWebhook() {
        // Save current values first
        binding?.let { b ->
            webhookPrintService.setUrl(b.webhookUrl.text.toString())
            webhookPrintService.setUsername(b.webhookUsername.text.toString())
            webhookPrintService.setPassword(b.webhookPassword.text.toString())
        }

        if (!webhookPrintService.isConfigured()) {
            binding?.webhookStatus?.text = getString(R.string.webhook_print_no_url)
            return
        }

        binding?.webhookTestButton?.isEnabled = false
        binding?.webhookStatus?.text = getString(R.string.loading)

        lifecycleScope.launch {
            val result = webhookPrintService.testConnection()
            binding?.webhookTestButton?.isEnabled = true

            result.fold(
                onSuccess = {
                    binding?.webhookStatus?.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
                    binding?.webhookStatus?.text = getString(R.string.webhook_print_success)
                },
                onFailure = { error ->
                    binding?.webhookStatus?.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
                    binding?.webhookStatus?.text = getString(R.string.webhook_print_error, error.message)
                }
            )
        }
    }

    private fun checkPermissionGranted() {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_ADMIN
            ) == PackageManager.PERMISSION_GRANTED
        }
        viewModel.onCheckPermission(granted)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun requestNeededPermissions() {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT
            )
        )
    }

    private fun requestNeededPermissionsOld() {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        )
    }

    @SuppressLint("MissingPermission")
    private fun setupDeviceDropdown(devices: List<BluetoothDevice>) {
        val deviceNames = devices.map { it.name }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, deviceNames)
        binding?.deviceDropdown?.setAdapter(adapter)

        binding?.deviceDropdown?.setOnItemClickListener { _, _, position, _ ->
            val description = deviceNames[position]
            viewModel.onDeviceSelected(description)
        }
    }

    override fun onDestroy() {
        _binding = null
        super.onDestroy()
    }

}
package ua.com.programmer.agentventa.printer

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import dagger.hilt.android.AndroidEntryPoint
import ua.com.programmer.agentventa.R
import ua.com.programmer.agentventa.databinding.PrinterSettingsFragmentBinding

@AndroidEntryPoint
class PrinterSettingsFragment: Fragment() {

    private val viewModel: PrinterViewModel by activityViewModels()
    private var _binding: PrinterSettingsFragmentBinding? = null
    private val binding get() = _binding

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

        viewModel.devices.observe(viewLifecycleOwner) {
            setupDeviceSpinner(it)
        }

        viewModel.currentDeviceIndex.observe(viewLifecycleOwner) {
            binding?.deviceSpinner?.setSelection(it)
        }

        viewModel.progress.observe(viewLifecycleOwner) { isProgress ->
            binding?.let {
                it.progressBar.visibility = if (isProgress) View.VISIBLE else View.INVISIBLE
                it.testButton.isEnabled = !isProgress
                it.deviceSpinner.isEnabled = !isProgress
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
    private fun setupDeviceSpinner(devices: List<BluetoothDevice>) {

        val spinnerList = devices.map { it.name }
        val adapter = ArrayAdapter(requireContext(), R.layout.simple_spinner_item, spinnerList)
        adapter.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item)
        binding?.deviceSpinner?.adapter = adapter

        binding?.deviceSpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val description = spinnerList[position]
                viewModel.onDeviceSelected(description)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Another interface callback
            }
        }
    }

    override fun onDestroy() {
        _binding = null
        super.onDestroy()
    }

}
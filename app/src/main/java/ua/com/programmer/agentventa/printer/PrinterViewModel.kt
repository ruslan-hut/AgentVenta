package ua.com.programmer.agentventa.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.SharedPreferences
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PrinterViewModel @Inject constructor(
    private val bluetoothAdapter: BluetoothAdapter?,
    private val preferences: SharedPreferences
): ViewModel() {

    val currentDeviceIndex = MutableLiveData<Int>()
    val devices = MutableLiveData<List<BluetoothDevice>>()
    val permissionGranted = MutableLiveData(false)

    @SuppressLint("MissingPermission")
    fun onCheckPermission(result: Boolean) {
        permissionGranted.value = result
        if (result) {
            devices.value = bluetoothAdapter?.bondedDevices?.toList()
            val address = preferences.getString("printer_address", "")
            val index = devices.value?.indexOfFirst { it.address == address } ?: -1
            currentDeviceIndex.value = index
        }
    }

    @SuppressLint("MissingPermission")
    fun onDeviceSelected(deviceName: String) {
        val device = devices.value?.find { it.name == deviceName } ?: return
        preferences.edit().putString("printer_address", device.address).apply()
    }

}
package ua.com.programmer.agentventa.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.SharedPreferences
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PrinterViewModel @Inject constructor(
    private val bluetoothAdapter: BluetoothAdapter?,
    private val preferences: SharedPreferences
): ViewModel() {

    val currentDeviceIndex = MutableLiveData<Int>()
    val devices = MutableLiveData<List<BluetoothDevice>>()
    val permissionGranted = MutableLiveData(false)
    val status = MutableLiveData("")
    val progress = MutableLiveData(false)

    // Standard SerialPortService ID
    private val serialPortId = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

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
        setStatus("")
        val device = devices.value?.find { it.name == deviceName } ?: return
        preferences.edit().putString("printer_address", device.address).apply()
    }

    @SuppressLint("MissingPermission")
    fun printTest() {
        setStatus("")
        if (permissionGranted.value != true)  return setStatus("Permission not granted")
        val address = preferences.getString("printer_address", "") ?: return setStatus("No printer selected")
        setProgress(true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val device = bluetoothAdapter?.getRemoteDevice(address) ?: return@launch setStatus("Not connected")
                val socket = device.createRfcommSocketToServiceRecord(serialPortId)
                socket.connect()

                val outputStream = socket.outputStream
                outputStream.write(byteArrayOf(0x1B, 0x40)) // ESC @

                // Print a regular line of text
                outputStream.write("Regular text line\n".toByteArray())

                // Bold text
                outputStream.write(byteArrayOf(0x1B, 0x45, 0x1))  // ESC E n
                outputStream.write("Bold text line\n".toByteArray())
                outputStream.write(byteArrayOf(0x1B, 0x45, 0x0))  // Cancel bold

                // Double height
                outputStream.write(byteArrayOf(0x1D, 0x21, 0x01)) // GS ! n
                outputStream.write("Double height text line\n".toByteArray())
                outputStream.write(byteArrayOf(0x1D, 0x21, 0x00)) // Cancel double height

                // Center Align
                outputStream.write(byteArrayOf(0x1B, 0x61, 0x1))  // ESC a n
                outputStream.write("Centered text\n".toByteArray())

                // Left Align
                outputStream.write(byteArrayOf(0x1B, 0x61, 0x0))  // ESC a n
                outputStream.write("Left aligned text\n".toByteArray())

                // Right Align
                outputStream.write(byteArrayOf(0x1B, 0x61, 0x2))  // ESC a n
                outputStream.write("Right aligned text\n".toByteArray())

                // Reset to left align (if necessary)
                outputStream.write(byteArrayOf(0x1B, 0x61, 0x0))

                // Feed and Cut
                outputStream.write(byteArrayOf(0x0A))  // Print and line feed
                outputStream.write(byteArrayOf(0x1D, 0x56, 0x01)) // Partial cut

                outputStream.close()
                socket.close()
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    setStatus("Output error: ${e.message}")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setStatus("Error: ${e.message}")
                }
            } finally {
                withContext(Dispatchers.Main) {
                    setProgress(false)
                }
            }
        }
    }

    private fun setStatus(newStatus: String) {
        status.value = newStatus
    }

    private fun setProgress(inProgress: Boolean) {
        progress.value = inProgress
    }
}
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
import ua.com.programmer.agentventa.BuildConfig
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
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
            val address = printerAddress()
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
        val address = printerAddress()
        if (address.isEmpty()) return setStatus("Printer not selected")
        setProgress(true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val charset = Charset.forName("cp866")

                val device = bluetoothAdapter?.getRemoteDevice(address) ?: return@launch setStatus("Not connected")
                val socket = device.createRfcommSocketToServiceRecord(serialPortId)
                socket.connect()

                val outputStream = socket.outputStream
                outputStream.write(byteArrayOf(0x1B, 0x40)) // ESC @

                // Print a regular line of text
//                outputStream.write("Regular text line\n".toByteArray())

                // Bold text
                outputStream.write(byteArrayOf(0x1B, 0x45, 0x1))  // ESC E n
                outputStream.write("Printing test. Build ${BuildConfig.VERSION_CODE} ${BuildConfig.FLAVOR}\n".toByteArray())
                outputStream.write(byteArrayOf(0x1B, 0x45, 0x0))  // Cancel bold

                // Double height
//                outputStream.write(byteArrayOf(0x1D, 0x21, 0x01)) // GS ! n
//                outputStream.write("Double height text line\n".toByteArray())
//                outputStream.write(byteArrayOf(0x1D, 0x21, 0x00)) // Cancel double height

                // Center Align
                //outputStream.write(byteArrayOf(0x1B, 0x61, 0x1))  // ESC a n

                // Double height
//                outputStream.write(byteArrayOf(0x1D, 0x21, 0x01)) // GS ! n
//                outputStream.write("Agent Venta\n".toByteArray())
//                outputStream.write(byteArrayOf(0x1D, 0x21, 0x00)) // Cancel double height

                // Bold text
//                outputStream.write(byteArrayOf(0x1B, 0x45, 0x1))
//                outputStream.write("${BuildConfig.VERSION_NAME}\n".toByteArray())
//                outputStream.write(byteArrayOf(0x1B, 0x45, 0x0))  // Cancel bold

                //outputStream.write("printing test\n".toByteArray())
                outputStream.write("Begin -->\n".toByteArray())
                // Reset to left align (if necessary)
                //outputStream.write(byteArrayOf(0x1B, 0x61, 0x0))
                //outputStream.write("123456789*123456789*123456789*123456789*\n".toByteArray())
                //outputStream.write("ABCDEFGHIJKLMNOPQRSTUVWXYZ\n".toByteArray())
                val testString = "Їхав єдиний москаль. Чув, що віз царю жезл, п'ять шуб і гофр."
                outputStream.write(byteArrayOf(27, 116, 17)) // ESC t n (switch to cp866)
                outputStream.write(testString.toByteArray(charset))
                outputStream.write(byteArrayOf(13, 10)) // CR

//                val text = "Съешь еще этих мягких французских булок"
//                val byteArray = text.toByteArray(Charset.forName("CP866"))
//                Log.d("Printer", "byteArray: $byteArray")

                outputStream.write("<-- End\n".toByteArray())

//                // Left Align
//                outputStream.write(byteArrayOf(0x1B, 0x61, 0x0))  // ESC a n
//                outputStream.write("Left aligned text\n".toByteArray())
//
//                // Right Align
//                outputStream.write(byteArrayOf(0x1B, 0x61, 0x2))  // ESC a n
//                outputStream.write("Right aligned text\n".toByteArray())

                // Feed and Cut
                outputStream.write(byteArrayOf(0x0A))  // Print and line feed
                outputStream.write(byteArrayOf(0x1D, 0x56, 0x01)) // Partial cut

                outputStream.flush()
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

    @SuppressLint("MissingPermission")
    fun printTextFile(filePath: String) {
        if (!readyToPrint()) return
        val address = printerAddress()
        val charset = Charset.forName("cp866")

        viewModelScope.launch(Dispatchers.IO) {

            try {

                val device = bluetoothAdapter?.getRemoteDevice(address) ?: return@launch
                val socket = device.createRfcommSocketToServiceRecord(serialPortId)
                socket.connect()

                val outputStream = socket.outputStream
                outputStream.write(byteArrayOf(27, 116, 17)) // ESC t n (switch to cp866)
                outputStream.write("\n".toByteArray())

                val file = File(filePath)
                file.inputStream().use { fileInputStream ->
                    val buffer = ByteArray(1024)
                    var bytesRead: Int

                    while (fileInputStream.read(buffer).also { bytesRead = it } != -1) {
                        val string = String(buffer, 0, bytesRead)
                        val cp866Bytes = string.toByteArray(charset)
                        outputStream.write(cp866Bytes, 0, cp866Bytes.size)
                    }
                }

                outputStream.write("\n".toByteArray())
                outputStream.flush()
                socket.close()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setStatus(e.message ?: "Unknown error")
                }
            }

        }

    }

    fun setStatus(newStatus: String) {
        status.value = newStatus
    }

    private fun setProgress(inProgress: Boolean) {
        progress.value = inProgress
    }

    fun useInFiscalService(): Boolean {
        return preferences.getBoolean("use_in_fiscal_service", false) && readyToPrint()
    }

    fun saveUseInFiscalService(use: Boolean) {
        preferences.edit().putBoolean("use_in_fiscal_service", use).apply()
    }

    fun autoPrint(): Boolean {
        return preferences.getBoolean("auto_print", false) && readyToPrint()
    }

    fun saveAutoPrint(use: Boolean) {
        preferences.edit().putBoolean("auto_print", use).apply()
    }

    fun readPrintAreaWidth(): Int {
        return preferences.getInt("print_area_width", 32)
    }

    fun savePrintAreaWidth(width: Int) {
        val correctWidth = if (width < 10 || width > 250) {
            32
        } else {
            width
        }
        preferences.edit().putInt("print_area_width", correctWidth).apply()
    }

    private fun printerAddress(): String {
        return preferences.getString("printer_address", "") ?: ""
    }

    fun readyToPrint(): Boolean {
        return printerAddress().isNotEmpty() && permissionGranted.value == true
    }
}
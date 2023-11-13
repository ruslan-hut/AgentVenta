package ua.com.programmer.agentventa.printer

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PrinterViewModel @Inject constructor(
    private val bluetoothAdapter: BluetoothAdapter?
): ViewModel() {

    val devices = MutableLiveData<List<BluetoothDevice>>()
    val permissionGranted = MutableLiveData(false)

    init {
//        if (ActivityCompat.checkSelfPermission(
//                this,
//                Manifest.permission.BLUETOOTH_CONNECT
//            ) != PackageManager.PERMISSION_GRANTED
//        ) {
//            // TODO: Consider calling
//            //    ActivityCompat#requestPermissions
//            // here to request the missing permissions, and then overriding
//            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
//            //                                          int[] grantResults)
//            // to handle the case where the user grants the permission. See the documentation
//            // for ActivityCompat#requestPermissions for more details.
//            return
//        }
//        devices.value = bluetoothAdapter?.bondedDevices?.toList()
    }

    fun onCheckPermission(result: Boolean) {
        permissionGranted.value = result
    }

}
package ua.com.programmer.agentventa.presentation.common.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles barcode scanning logic.
 * Extracted from SharedViewModel to follow Single Responsibility Principle.
 */
@Singleton
class BarcodeHandler @Inject constructor() {

    private val _barcode = MutableStateFlow("")
    val barcode: StateFlow<String> = _barcode.asStateFlow()

    private val _ignoreSequentialBarcodes = MutableStateFlow(false)
    val ignoreSequentialBarcodes: StateFlow<Boolean> = _ignoreSequentialBarcodes.asStateFlow()

    private var lastBarcode: String = ""
    private var lastBarcodeTime: Long = 0

    /**
     * Process a scanned barcode.
     * @param code The scanned barcode
     * @param currentTime Current timestamp in milliseconds
     * @param sequentialThreshold Time threshold in milliseconds to consider barcodes as sequential
     */
    fun processBarcode(code: String, currentTime: Long = System.currentTimeMillis(), sequentialThreshold: Long = 1000) {
        if (_ignoreSequentialBarcodes.value) {
            // Check if this is a sequential scan of the same barcode
            if (code == lastBarcode && (currentTime - lastBarcodeTime) < sequentialThreshold) {
                // Ignore sequential scans
                return
            }
        }

        lastBarcode = code
        lastBarcodeTime = currentTime
        _barcode.value = code
    }

    /**
     * Clear the current barcode.
     */
    fun clearBarcode() {
        _barcode.value = ""
    }

    /**
     * Set whether to ignore sequential barcode scans.
     */
    fun setIgnoreSequentialBarcodes(ignore: Boolean) {
        _ignoreSequentialBarcodes.value = ignore
    }
}

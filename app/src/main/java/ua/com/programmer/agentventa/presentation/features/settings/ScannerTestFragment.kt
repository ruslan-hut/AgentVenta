package ua.com.programmer.agentventa.presentation.features.settings

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import ua.com.programmer.agentventa.R
import ua.com.programmer.agentventa.databinding.FragmentScannerTestBinding
import ua.com.programmer.agentventa.presentation.main.MainActivity

class ScannerTestFragment : Fragment() {

    private var _binding: FragmentScannerTestBinding? = null
    private val binding get() = _binding!!

    private lateinit var scannerSettings: ScannerSettings

    private val buffer = StringBuilder()
    private var lastKeystrokeTime = 0L
    private var scanCount = 0
    private val logLines = StringBuilder()
    private var logLineCount = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScannerTestBinding.inflate(inflater)
        scannerSettings = ScannerSettings(requireContext())

        showSettingsSummary()

        binding.clearLog.setOnClickListener {
            logLines.clear()
            logLineCount = 0
            binding.keyEventLog.text = ""
            binding.barcodeResult.text = getString(R.string.scanner_test_waiting)
            binding.barcodeInfo.text = ""
            binding.bufferContent.text = ""
            binding.bufferInfo.text = ""
            scanCount = 0
        }

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        (activity as? MainActivity)?.setKeyEventListener { event ->
            onKeyEvent(event)
        }
    }

    override fun onPause() {
        super.onPause()
        (activity as? MainActivity)?.setKeyEventListener(null)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun showSettingsSummary() {
        val terminator = when (scannerSettings.terminatorKey) {
            ScannerSettings.TERMINATOR_ENTER -> "Enter"
            ScannerSettings.TERMINATOR_TAB -> "Tab"
            else -> "Enter+Tab"
        }
        val prefix = scannerSettings.prefixToStrip.ifEmpty { "-" }
        val suffix = scannerSettings.suffixToStrip.ifEmpty { "-" }
        binding.settingsSummary.text = getString(
            R.string.scanner_test_settings_summary,
            scannerSettings.keystrokeTimeout,
            scannerSettings.minBarcodeLength,
            terminator,
            prefix,
            suffix
        )
    }

    /**
     * Processes a raw key event from MainActivity and returns true to consume it.
     * Mirrors the detection logic from MainActivity.dispatchKeyEvent but shows
     * diagnostic output instead of dispatching the barcode.
     */
    private fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val isTerminator = scannerSettings.isTerminator(event.keyCode)
            val keyName = KeyEvent.keyCodeToString(event.keyCode)
            val char = event.unicodeChar.toChar()
            val currentTime = System.currentTimeMillis()

            if (isTerminator) {
                val gap = if (lastKeystrokeTime > 0) currentTime - lastKeystrokeTime else 0
                appendLog("$keyName [TERMINATOR] gap=${gap}ms buf=\"$buffer\" len=${buffer.length}")

                if (buffer.length >= scannerSettings.minBarcodeLength) {
                    val raw = buffer.toString()
                    val cleaned = scannerSettings.cleanBarcode(raw)
                    scanCount++

                    binding.barcodeResult.text = cleaned
                    val info = StringBuilder()
                    info.append(getString(R.string.scanner_test_scan_number, scanCount))
                    info.append(" | ${getString(R.string.scanner_test_length, cleaned.length)}")
                    if (raw != cleaned) {
                        info.append(" | ${getString(R.string.scanner_test_raw, raw)}")
                    }
                    binding.barcodeInfo.text = info

                    appendLog(">>> BARCODE DETECTED: \"$cleaned\"")
                } else {
                    appendLog("--- buffer too short (${buffer.length} < ${scannerSettings.minBarcodeLength}), ignored")
                }

                buffer.clear()
                updateBufferDisplay()
                lastKeystrokeTime = 0L
                return true
            }

            // Timeout check
            if (buffer.isNotEmpty() && lastKeystrokeTime > 0 && currentTime - lastKeystrokeTime > scannerSettings.keystrokeTimeout) {
                appendLog("--- timeout (${currentTime - lastKeystrokeTime}ms > ${scannerSettings.keystrokeTimeout}ms), buffer cleared")
                buffer.clear()
            }

            if (char.code in 0x20..0x7E) {
                val gap = if (lastKeystrokeTime > 0) currentTime - lastKeystrokeTime else 0
                buffer.append(char)
                lastKeystrokeTime = currentTime
                appendLog("'$char' ($keyName) gap=${gap}ms")
                updateBufferDisplay()
            } else {
                appendLog("$keyName (non-printable, skipped)")
            }

            return true
        }

        // Consume ACTION_UP silently
        if (event.action == KeyEvent.ACTION_UP) {
            return true
        }

        return false
    }

    private fun updateBufferDisplay() {
        binding.bufferContent.text = buffer.toString()
        binding.bufferInfo.text = getString(R.string.scanner_test_buffer_info, buffer.length, scannerSettings.minBarcodeLength)
    }

    private fun appendLog(line: String) {
        if (logLineCount >= MAX_LOG_LINES) {
            // Remove first line
            val firstNewline = logLines.indexOf('\n')
            if (firstNewline >= 0) {
                logLines.delete(0, firstNewline + 1)
            }
            logLineCount--
        }
        if (logLines.isNotEmpty()) logLines.append('\n')
        logLines.append(line)
        logLineCount++

        binding.keyEventLog.text = logLines
        binding.logScroll.post {
            binding.logScroll.fullScroll(View.FOCUS_DOWN)
        }
    }

    companion object {
        private const val MAX_LOG_LINES = 100
    }
}

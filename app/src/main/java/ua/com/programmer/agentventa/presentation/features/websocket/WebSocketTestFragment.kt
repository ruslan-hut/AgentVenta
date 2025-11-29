package ua.com.programmer.agentventa.presentation.features.websocket

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import ua.com.programmer.agentventa.databinding.FragmentWebsocketTestBinding

@AndroidEntryPoint
class WebSocketTestFragment : Fragment() {

    private var _binding: FragmentWebsocketTestBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WebSocketTestViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWebsocketTestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupObservers()
        setupListeners()
    }

    private fun setupObservers() {
        viewModel.connectionState.observe(viewLifecycleOwner) { state ->
            binding.connectionStatusText.text = state
        }

        viewModel.messageLog.observe(viewLifecycleOwner) { messages ->
            binding.messageLogText.text = if (messages.isEmpty()) {
                "No messages"
            } else {
                messages.joinToString("\n")
            }
        }
    }

    private fun setupListeners() {
        binding.connectButton.setOnClickListener {
            viewModel.connect()
        }

        binding.disconnectButton.setOnClickListener {
            viewModel.disconnect()
        }

        binding.sendTestButton.setOnClickListener {
            viewModel.sendTestMessage()
        }

        binding.clearLogButton.setOnClickListener {
            viewModel.clearLog()
        }

        binding.copyLogButton.setOnClickListener {
            copyLogToClipboard()
        }
    }

    private fun copyLogToClipboard() {
        val logText = binding.messageLogText.text.toString()
        if (logText.isNotEmpty() && logText != "No messages") {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("WebSocket Log", logText)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), "Log copied to clipboard", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "No log to copy", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

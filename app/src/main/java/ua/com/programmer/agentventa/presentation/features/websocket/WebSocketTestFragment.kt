package ua.com.programmer.agentventa.presentation.features.websocket

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import ua.com.programmer.agentventa.R
import ua.com.programmer.agentventa.databinding.FragmentWebsocketTestBinding

/**
 * WebSocket Status Fragment.
 * Shows connection status, pending data counts, and provides manual sync trigger.
 * Connection is managed automatically by WebSocketConnectionManager.
 */
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

    override fun onResume() {
        super.onResume()
        // Refresh pending data when screen becomes visible
        viewModel.refreshPendingData()
    }

    private fun setupObservers() {
        viewModel.connectionState.observe(viewLifecycleOwner) { state ->
            binding.connectionStatusText.text = state
        }

        viewModel.pendingDataInfo.observe(viewLifecycleOwner) { info ->
            binding.pendingDataText.text = info
        }

        viewModel.lastSyncTime.observe(viewLifecycleOwner) { time ->
            binding.lastSyncText.text = time
        }

        viewModel.messageLog.observe(viewLifecycleOwner) { messages ->
            binding.messageLogText.text = if (messages.isEmpty()) {
                getString(R.string.websocket_no_messages)
            } else {
                messages.joinToString("\n")
            }
        }

        viewModel.settingsSyncStatus.observe(viewLifecycleOwner) { status ->
            if (status.isNotEmpty()) {
                binding.settingsSyncStatus.text = status
                binding.settingsSyncStatus.visibility = View.VISIBLE
            } else {
                binding.settingsSyncStatus.visibility = View.GONE
            }
        }

        viewModel.isSyncing.observe(viewLifecycleOwner) { isSyncing ->
            binding.syncNowButton.isEnabled = !isSyncing
            binding.syncNowButton.text = if (isSyncing) {
                "Syncing..."
            } else {
                getString(R.string.websocket_sync_now)
            }
        }
    }

    private fun setupListeners() {
        binding.syncNowButton.setOnClickListener {
            viewModel.syncNow()
        }

        binding.clearLogButton.setOnClickListener {
            viewModel.clearLog()
        }

        // Settings sync listeners
        binding.userEmailInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                viewModel.setUserEmail(s?.toString() ?: "")
            }
        })

        binding.uploadSettingsButton.setOnClickListener {
            viewModel.uploadSettings()
        }

        binding.downloadSettingsButton.setOnClickListener {
            viewModel.downloadSettings()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

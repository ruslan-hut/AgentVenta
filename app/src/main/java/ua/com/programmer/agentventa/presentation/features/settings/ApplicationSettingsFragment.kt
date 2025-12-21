package ua.com.programmer.agentventa.presentation.features.settings

import android.content.SharedPreferences
import android.os.Bundle
import androidx.fragment.app.activityViewModels
import androidx.preference.PreferenceFragmentCompat
import dagger.hilt.android.AndroidEntryPoint
import ua.com.programmer.agentventa.R
import ua.com.programmer.agentventa.infrastructure.websocket.WebSocketConnectionManager
import ua.com.programmer.agentventa.infrastructure.websocket.WebSocketSyncWorker
import ua.com.programmer.agentventa.presentation.common.viewmodel.SharedViewModel
import javax.inject.Inject

@AndroidEntryPoint
class ApplicationSettingsFragment: PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {

    private val sharedModel: SharedViewModel by activityViewModels()

    @Inject
    lateinit var connectionManager: WebSocketConnectionManager

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_general, rootKey)
    }

    override fun onResume() {
        super.onResume()
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        val pref = key ?: return
        when (pref) {
            "show_rests_only" -> {
                sharedModel.setRestsOnly(sharedPreferences?.getBoolean(pref, false) ?: false)
            }
            "websocket_idle_interval_minutes" -> {
                val intervalMinutes = sharedPreferences?.getString(pref, "15")?.toIntOrNull() ?: 15
                connectionManager.setIdleIntervalMinutes(intervalMinutes)
                // Update WorkManager periodic task with new interval
                WebSocketSyncWorker.schedule(requireContext(), intervalMinutes.toLong())
            }
        }
    }
}

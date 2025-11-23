package ua.com.programmer.agentventa.presentation.features.settings

import android.content.SharedPreferences
import android.os.Bundle
import androidx.fragment.app.activityViewModels
import androidx.preference.PreferenceFragmentCompat
import ua.com.programmer.agentventa.R
import ua.com.programmer.agentventa.presentation.common.viewmodel.SharedViewModel

class ApplicationSettingsFragment: PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {

    private val sharedModel: SharedViewModel by activityViewModels()

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
        }
    }
}
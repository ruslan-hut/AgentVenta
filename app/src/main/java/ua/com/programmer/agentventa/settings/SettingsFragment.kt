package ua.com.programmer.agentventa.settings

import android.os.Bundle
import androidx.navigation.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import ua.com.programmer.agentventa.R

class SettingsFragment: PreferenceFragmentCompat() {
    override fun onCreatePreferences(bundle: Bundle?, s: String?) {
        setPreferencesFromResource(R.xml.preference, s)

        val optionsPref = findPreference<Preference>("pref_options")
        optionsPref?.setOnPreferenceClickListener {
            val action = SettingsFragmentDirections.actionSettingsFragmentToOptionsFragment()
            view?.findNavController()?.navigate(action)
            true
        }

        val printerPref = findPreference<Preference>("printer")
        printerPref?.setOnPreferenceClickListener {
            val action = SettingsFragmentDirections.actionSettingsFragmentToPrinterSettingsFragment()
            view?.findNavController()?.navigate(action)
            true
        }
    }
}